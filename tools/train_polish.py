"""Train a real TypeRight LoRA; holdout evaluation is separate from selection.

Uses FP32 on Pascal GPUs (no BF16 requirement), bounded sequences, response-only
loss and an isolated environment. No adaptive user stores are read or uploaded.
"""
import argparse
import hashlib
import json
import os
import pathlib
import random
import time

os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("CUBLAS_WORKSPACE_CONFIG", ":4096:8")
from polish_data import BASE, REVISION, ROOT


def main():
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    from peft import LoraConfig, get_peft_model
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--max-examples", type=int, default=0, help="Calibration-selected early stopping; shuffle the FULL training partition first")
    parser.add_argument("--resume", type=pathlib.Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    seed = 193
    random.seed(seed); torch.manual_seed(seed)
    torch.set_num_threads(4)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(json.dumps(dict(device=device, torch=torch.__version__, gpu=torch.cuda.get_device_name(0) if device == "cuda" else None)), flush=True)
    tokenizer = AutoTokenizer.from_pretrained(BASE, revision=REVISION)
    base = AutoModelForCausalLM.from_pretrained(BASE, revision=REVISION, torch_dtype=torch.float32,
                                               attn_implementation="eager").to(device)
    base.config.use_cache = False
    config = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05,
                        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"], task_type="CAUSAL_LM")
    model = get_peft_model(base, config)
    if args.resume:
        from peft import set_peft_model_state_dict
        from safetensors.torch import load_file
        set_peft_model_state_dict(model, load_file(str(args.resume / "adapter_model.safetensors")))
    model.gradient_checkpointing_enable(gradient_checkpointing_kwargs={"use_reentrant": False})
    rows = [json.loads(line) for line in (ROOT / "tools/data/polish/train.jsonl").read_text(encoding="utf-8").splitlines()]
    if args.limit: rows = rows[:args.limit]
    samples = []
    for row in rows:
        messages = [{"role": "system", "content": row["system"]}, {"role": "user", "content": row["user"]}]
        prefix = tokenizer.apply_chat_template(messages, tokenize=True, add_generation_prompt=True, enable_thinking=False)
        answer = tokenizer.encode(row["expected"] + tokenizer.eos_token, add_special_tokens=False)
        if len(prefix) + len(answer) > 384: continue
        samples.append((prefix + answer, [-100] * len(prefix) + answer))
    optimizer = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=1e-4, weight_decay=0.01)
    accumulator = 16
    model.train()
    started = time.monotonic()
    updates = 0
    seen = 0
    for epoch in range(args.epochs):
        random.Random(seed + epoch).shuffle(samples)
        summed = 0.0
        optimizer.zero_grad(set_to_none=True)
        for index, (ids, labels) in enumerate(samples):
            if args.max_examples and seen >= args.max_examples: break
            seen += 1
            result = model(input_ids=torch.tensor([ids], device=device), labels=torch.tensor([labels], device=device))
            loss = result.loss
            summed += float(loss.detach())
            (loss / accumulator).backward()
            if (index + 1) % accumulator == 0 or index == len(samples) - 1 or seen == args.max_examples:
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step(); optimizer.zero_grad(set_to_none=True); updates += 1
            if (index + 1) % 64 == 0:
                elapsed = time.monotonic() - started
                print(json.dumps(dict(epoch=epoch + 1, example=index + 1, examples=len(samples), mean_loss=summed / (index + 1), elapsed_seconds=round(elapsed, 1))), flush=True)
            if (index + 1) % 512 == 0:
                model.save_pretrained(args.output / "checkpoint")
        model.save_pretrained(args.output / f"epoch-{epoch + 1}")
        if args.max_examples and seen >= args.max_examples: break
    model.save_pretrained(args.output / "adapter")
    tokenizer.save_pretrained(args.output / "adapter")
    receipt = dict(version=193, base=BASE, revision=REVISION, seed=seed, epochs=args.epochs,
                   examples=len(samples), seen_examples=seen, max_examples=args.max_examples,
                   updates=updates, learning_rate=1e-4,
                   lora_rank=16, lora_alpha=32, dropout=0.05, max_sequence_length=384,
                   precision="float32", device=device, duration_seconds=time.monotonic() - started,
                   data_manifest=json.loads((ROOT / "tools/data/polish/manifest.json").read_text()))
    receipt["adapter_sha256"] = hashlib.sha256((args.output / "adapter/adapter_model.safetensors").read_bytes()).hexdigest()
    (args.output / "training.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt), flush=True)


if __name__ == "__main__": main()
