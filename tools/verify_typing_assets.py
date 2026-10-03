"""Offline provenance, split and runtime-feature checks used by CI."""
import gzip, hashlib, json, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/dictionaries/manglish'
manifest=json.loads((ASSETS/'provenance.json').read_text(encoding='utf-8'))
for source in manifest['sources']:
    path=ROOT/'tools/data/dakshina'/pathlib.PurePosixPath(source['member']).name
    assert hashlib.sha256(path.read_bytes()).hexdigest()==source['sha256'],path
for filename,digest in manifest['artifacts'].items():
    assert hashlib.sha256((ASSETS/filename).read_bytes()).hexdigest()==digest,filename
data=json.loads((ROOT/'app/src/test/resources/manglish-cases.json').read_text(encoding='utf-8'))
groups={partition:set() for partition in ('training','calibration','holdout')}
for row in data['corrections']:
    if row['typed']!=row['expected']:groups[row['split']].add((row['language'],row['family']))
for a in groups:
    for b in groups:
        if a!=b:assert not groups[a]&groups[b],(a,b)
templates={}
for line in (ROOT/'tools/data/manglish-conversations.tsv').read_text(encoding='utf-8').splitlines():
    if not line or line.startswith('#'):continue
    family,partition,text=line.split('\t')
    assert family not in templates or templates[family]==partition,family
    templates[family]=partition
training=json.loads((ROOT/'tools/typing-training-manifest.json').read_text())
calibration=json.loads((ROOT/'tools/bilingual-calibration.json').read_text())
expected=''.join(f'{lang}\t{calibration[lang]["temperature"]}\t{calibration[lang]["threshold"]}\n' for lang in ('en','ml-Latn'))
assert (ROOT/'app/src/main/assets/dictionaries/ranker-calibration.tsv').read_text()==expected
runtime=ROOT/'tools/data/runtime-ranking-training.jsonl.gz'
assert hashlib.sha256(runtime.read_bytes()).hexdigest()==training['runtimeSHA256']
for row in map(json.loads,gzip.decompress(runtime.read_bytes()).decode().splitlines()):
    assert row['family'].startswith('context:') or (row['language'],row['family']) in groups['training'],row['family']
    assert len(row['chosen'])==len(training['features'])==21
    assert len(row['alternativeWords'])==len(row['alternatives'])
    assert all(len(a)==21 for a in row['alternatives'])
assert sum(r['language']=='ml-Latn' and r['typed']!=r['expected'] and r['split']=='holdout' for r in data['corrections'])>=200
assert sum(r['typed']==r['expected'] and r['split']=='holdout' for r in data['corrections'])>=1000
assert sum(r['language']=='en' and r['typed']!=r['expected'] and r['split']=='holdout' for r in data['corrections'])>=100
print('Source hashes, vocabulary/model hashes, family/template splits and runtime features verified')
