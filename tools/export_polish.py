"""Export a MERGED custom checkpoint, never relabel pretrained weights as TypeRight."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import time
from huggingface_hub import snapshot_download
from polish_data import BASE, REVISION, ROOT


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--baseline-only", action="store_true")
    args=parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.baseline_only:
        model = snapshot_download(BASE, revision=REVISION, ignore_patterns=["*.bin", "*.h5", "*.msgpack"])
    else:
        if not args.checkpoint or not (args.checkpoint/"config.json").is_file():
            raise ValueError("A real merged trained checkpoint is required")
        config=json.loads((args.checkpoint/"config.json").read_text())
        if config.get("typeright_trained") is not True:
            raise ValueError("Refusing to publish an untrained placeholder")
        model=str(args.checkpoint)
    from litert_torch.generative.export_hf import export
    started=time.monotonic()
    export.export(model=model, output_dir=str(args.output), prefill_lengths=[128], cache_length=2048,
        quantization_recipe="dynamic_wi4b32_afp32", use_jinja_template=True,
        jinja_chat_template_override=str(ROOT/"tools/polish-template.jinja"),
        bundle_litert_lm=True, export_vision_encoder=False, export_audio_encoder=False,
        sampler_top_k=1, sampler_top_p=1.0, sampler_temperature=0.0)
    artifacts=list(args.output.glob("*.litertlm"))
    if len(artifacts)!=1: raise ValueError("Expected exactly one deployment bundle")
    artifact=artifacts[0]
    if not 300_000_000 <= artifact.stat().st_size <= 600_000_000:
        raise ValueError(f"Bundle misses the mobile size target: {artifact.stat().st_size}")
    receipt=dict(base=BASE, base_revision=REVISION, trained=not args.baseline_only,
        artifact=artifact.name, bytes=artifact.stat().st_size,
        sha256=hashlib.file_digest(artifact.open("rb"), "sha256").hexdigest(),
        duration_seconds=time.monotonic()-started, quantization="dynamic_wi4b32_afp32",
        context_tokens=2048, template_sha256=hashlib.sha256((ROOT/"tools/polish-template.jinja").read_bytes()).hexdigest())
    (args.output/"export.json").write_text(json.dumps(receipt,indent=2)+"\n")
    environment=subprocess.check_output(["python", "-m", "pip", "freeze"],text=True)
    (args.output/"export-environment.lock").write_text(environment)
    print(json.dumps(receipt),flush=True)


if __name__=="__main__": main()
