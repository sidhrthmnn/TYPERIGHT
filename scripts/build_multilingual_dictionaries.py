"""Verify or reproduce multilingual tables from the pinned, attributed corpus."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import unicodedata
import urllib.error
import urllib.request

folder = Path(__file__).resolve().parents[1] / "app/src/main/assets/dictionaries/multilingual"
spec = json.loads((folder / "provenance.json").read_text(encoding="utf-8"))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--rebuild", action="store_true", help="Fetch and reproduce the exact adapted tables")
parser.add_argument("--check-sources", action="store_true", help="Verify source hashes and repair 50k/full source paths")
parser.add_argument("--language", help="Verify just one language")
args = parser.parse_args()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def check(item):
    path = folder / (item["language"] + ".tsv")
    data = path.read_bytes()
    assert digest(data) == item["assetSha256"], f"Asset checksum: {path.name}"
    assert len(data.decode("utf-8").splitlines()) == item["words"]
    if args.rebuild or args.check_sources:
        url = item["source"]
        try:
            raw = urllib.request.urlopen(url, timeout=60).read()
        except urllib.error.HTTPError as error:
            if error.code != 404 or "_50k.txt" not in url:
                raise
            url = url.replace("_50k.txt", "_full.txt")
            raw = urllib.request.urlopen(url, timeout=60).read()
        assert digest(raw) == item["sourceSha256"], f"Source checksum: {path.name}"
        rows = []
        for line in raw.decode("utf-8").splitlines():
            word, count = line.rsplit(" ", 1)
            word = unicodedata.normalize("NFC", word.casefold().replace("’", "'"))
            if 2 <= len(word) <= 32 and any(c.isalpha() for c in word) and all(
                unicodedata.category(c)[0] in "LM" or c == "'" for c in word
            ):
                rows.append((word, int(count)))
                if len(rows) == 8000:
                    break
        adapted = "".join(f"{word}\t{count}\n" for word, count in rows).encode("utf-8")
        assert digest(adapted) == item["assetSha256"], f"Adaptation checksum: {path.name}"
        item["source"] = url
        if args.rebuild:
            path.write_bytes(adapted)
    return item["language"]


items = [item for item in spec["languages"] if not args.language or item["language"] == args.language]
assert items, "Unknown language"
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for language in pool.map(check, items):
        print(f"Verified {language}", flush=True)
if args.check_sources:
    spec["adaptation"] = "First 8000 source rows with NFC casefolded words, curly apostrophes normalized, 2-32 code points, Unicode letters/marks/apostrophes only. Raw occurrence counts retained; duplicate normalized forms retained in source order."
    (folder / "provenance.json").write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Verified {len(items)} tables, {sum(item['words'] for item in items):,} rows")
