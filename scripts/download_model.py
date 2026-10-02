"""Download a pinned catalog model or verify all repository GGUF weights."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
catalog = json.loads((root / "models/model-catalog.json").read_text())
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--model", default=catalog["default_model"], choices=[m["id"] for m in catalog["models"]])
parser.add_argument("--all", action="store_true", help="Download all catalog models")
parser.add_argument("--verify-repository", action="store_true", help="Verify every checked-in Git LFS model file")
args = parser.parse_args()

def verified(path, spec):
    if not path.is_file() or path.stat().st_size != spec["bytes"]:
        return False
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest() == spec["sha256"]

if args.verify_repository:
    for model in catalog["models"]:
        for artifact in model["repository_files"]:
            path = root / "models" / artifact["filename"]
            if not verified(path, artifact):
                raise RuntimeError(f"Model size or checksum mismatch: {path}")
            print(f"Verified: {path.name}")
    raise SystemExit(0)

for model in catalog["models"]:
    if not args.all and model["id"] != args.model:
        continue
    target = root / "models" / model["filename"]
    if verified(target, model):
        print(f"Already verified: {target.name}")
        continue
    partial = target.with_suffix(".gguf.part")
    try:
        with urllib.request.urlopen(model["url"], timeout=60) as response, partial.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
                if output.tell() > model["bytes"]:
                    raise RuntimeError("Download exceeds expected size")
        if not verified(partial, model):
            raise RuntimeError("Model size or checksum mismatch")
        partial.replace(target)
        print(f"Downloaded and verified: {target.name}")
    finally:
        partial.unlink(missing_ok=True)
