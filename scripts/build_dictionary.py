"""Rebuild the attributed, frequency-ranked English asset from a pinned corpus."""
import hashlib
import json
import math
from pathlib import Path
import re
import urllib.request

root = Path(__file__).resolve().parents[1]
folder = root / "app/src/main/assets/dictionaries"
spec = json.loads((folder / "provenance.json").read_text())
url = f"https://raw.githubusercontent.com/hermitdave/FrequencyWords/{spec['revision']}/{spec['source_file']}"
raw = urllib.request.urlopen(url, timeout=60).read()
assert hashlib.sha256(raw).hexdigest() == spec["source_sha256"], "Corpus checksum mismatch"
rows = []
for line in raw.decode("utf-8").splitlines():
    word, count = line.rsplit(" ", 1)
    if re.fullmatch("[a-z]+(?:'[a-z]+)?", word) and 2 <= len(word) <= 24:
        rows.append((word, int(count)))
maximum = max(count for _, count in rows)
entries = {word: max(1, round(math.sqrt(count / maximum) * 1000)) for word, count in rows}
entries.update(a=700, i=1000)
data = "".join(f"{word}\t{frequency}\n" for word, frequency in sorted(entries.items())).encode("utf-8")
assert len(entries) == spec["entries"]
assert hashlib.sha256(data).hexdigest() == spec["sha256"], "Adapted corpus checksum mismatch"
(folder / "english_frequency.tsv").write_bytes(data)
print(f"Verified {len(entries):,} words ({len(data):,} bytes)")
