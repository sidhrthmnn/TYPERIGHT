"""Merge a real selected LoRA adapter into the pinned foundation checkpoint."""
import argparse
import hashlib
import json
import pathlib
import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer
from polish_data import BASE, REVISION, ROOT

p=argparse.ArgumentParser()
p.add_argument("adapter",type=pathlib.Path)
p.add_argument("--output",type=pathlib.Path,required=True)
args=p.parse_args()
torch.set_num_threads(2)
assert (args.adapter/"adapter_model.safetensors").is_file(), "A trained adapter is required"
base=AutoModelForCausalLM.from_pretrained(BASE,revision=REVISION,torch_dtype=torch.float32,attn_implementation="eager")
model=PeftModel.from_pretrained(base,str(args.adapter)).merge_and_unload(safe_merge=True)
model.config.typeright_trained=True
model.config.typeright_base_revision=REVISION
model.config.typeright_adapter_sha256=hashlib.sha256((args.adapter/"adapter_model.safetensors").read_bytes()).hexdigest()
model.save_pretrained(args.output,safe_serialization=True)
tokenizer=AutoTokenizer.from_pretrained(BASE,revision=REVISION)
tokenizer.chat_template=(ROOT/"tools/polish-template.jinja").read_text()
tokenizer.save_pretrained(args.output)
print(json.dumps(dict(merged=str(args.output),adapter_sha256=model.config.typeright_adapter_sha256)),flush=True)
