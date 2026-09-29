"""Download the pinned GGUF to models/ and verify it before atomic installation."""
import hashlib
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
spec = json.loads((root / "models/gemma-polish.json").read_text())
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
