"""Collect real generation results without tuning on holdout. Android gates validate them.

The native path uses the exact deployed runtime, isolated conversations and deterministic
sampling. PyTorch evaluation measures the merged model before INT4 export separately.
"""
import argparse
import hashlib
import json
import pathlib
import time
from polish_data import ROOT, BASE, REVISION


def native(model):
    import litert_lm as lm
    engine=lm.Engine(str(model), backend=lm.Backend.CPU(thread_count=4), max_num_tokens=2048,
                     vision_backend=None, audio_backend=None)
    def generate(row):
        with engine.create_conversation(system_message=row["system"], tools=[], automatic_tool_calling=False,
                thinking_config=lm.ThinkingConfig(False,0), extra_context={"enable_thinking":False},
                sampler_config=lm.SamplerConfig(top_k=1,top_p=1.0,temperature=0.0,seed=193), max_output_tokens=256) as conversation:
            message=conversation.send_message(row["user"])
            return "".join(c.get("text","") for c in message.get("content",[]) if c.get("type")=="text")
    return generate, engine.close


def pytorch(model, device):
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    torch.set_num_threads(4)
    tokenizer=AutoTokenizer.from_pretrained(model)
    engine=AutoModelForCausalLM.from_pretrained(model, torch_dtype=torch.float32, attn_implementation="eager").to(device).eval()
    def generate(row):
        text=tokenizer.apply_chat_template([dict(role="system",content=row["system"]),dict(role="user",content=row["user"])],tokenize=False,add_generation_prompt=True,enable_thinking=False)
        ids=tokenizer(text,return_tensors="pt").to(device)
        with torch.inference_mode():
            output=engine.generate(**ids,max_new_tokens=256,do_sample=False,pad_token_id=tokenizer.eos_token_id)
        return tokenizer.decode(output[0,ids.input_ids.shape[-1]:],skip_special_tokens=True).strip()
    return generate, lambda: None


def main():
    p=argparse.ArgumentParser()
    p.add_argument("model",type=pathlib.Path)
    p.add_argument("--backend",choices=["native","pytorch"],default="native")
    p.add_argument("--device",default="cpu")
    p.add_argument("--split",choices=["calibration","holdout"],required=True)
    p.add_argument("--output",type=pathlib.Path,required=True)
    p.add_argument("--limit",type=int,default=0,help="Development smoke only; release uses all examples")
    args=p.parse_args()
    source=ROOT/f"tools/data/polish/{args.split}.jsonl"
    rows=[json.loads(line) for line in source.read_text(encoding="utf-8").splitlines()]
    if args.limit: rows=rows[:args.limit]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    prior=[]
    if args.output.exists():
        prior=[json.loads(line) for line in args.output.read_text(encoding="utf-8").splitlines()]
        for row, saved in zip(rows,prior):
            assert saved["input"]==row["input"] and saved["mode"]==row["mode"], "Mismatched evaluation resume"
    started=time.monotonic()
    run,close=native(args.model) if args.backend=="native" else pytorch(str(args.model),args.device)
    print(json.dumps(dict(loaded_seconds=time.monotonic()-started,examples=len(rows),completed=len(prior))),flush=True)
    try:
        with args.output.open("a",encoding="utf-8",newline="\n") as out:
            for index,row in enumerate(rows[len(prior):],len(prior)):
                start=time.monotonic()
                output=run(row)
                result={k:v for k,v in row.items() if k not in ("system","user")}
                result.update(output=output,duration_seconds=time.monotonic()-start)
                out.write(json.dumps(result,ensure_ascii=False,sort_keys=True)+"\n");out.flush()
                if (index+1)%16==0:
                    print(json.dumps(dict(example=index+1,examples=len(rows),elapsed_seconds=time.monotonic()-started)),flush=True)
    finally: close()
    receipt=dict(model=str(args.model),backend=args.backend,split=args.split,examples=len(rows),limited_smoke=bool(args.limit),
                 data_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),duration_seconds=time.monotonic()-started)
    args.output.with_suffix(".receipt.json").write_text(json.dumps(receipt,indent=2)+"\n")


if __name__=="__main__": main()
