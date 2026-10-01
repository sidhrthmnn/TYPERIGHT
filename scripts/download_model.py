"""Download the pinned GGUF to models/ and verify it before atomic installation."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
spec = json.loads((root / "models/gemma-polish.json").read_text())
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--verify-repository", action="store_true", help="Verify the checked-in Git LFS GGUF shards")
args = parser.parse_args()
if args.verify_repository:
    for shard in spec["repository_files"]:
        path = root / "models" / shard["filename"]
        if path.stat().st_size != shard["bytes"]:
            raise RuntimeError(f"Shard size mismatch: {path}")
        with path.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != shard["sha256"]:
                raise RuntimeError(f"Shard checksum mismatch: {path}")
        print(f"Verified shard: {path.name}")
    raise SystemExit(0)
target = root / "models" / spec["filename"]


def valid(path):
    if not path.exists() or path.stat().st_size != spec["bytes"]:
        return False
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest() == spec["sha256"]


if not valid(target):
    partial = target.with_suffix(".gguf.part")
    try:
        print(f"Downloading {spec['name']} ({spec['bytes']:,} bytes)", flush=True)
        urllib.request.urlretrieve(spec["url"], partial)
        if not valid(partial):
            raise RuntimeError("Model size/checksum mismatch")
        partial.replace(target)
    finally:
        partial.unlink(missing_ok=True)
print(f"Verified: {target}")
