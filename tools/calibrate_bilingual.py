"""Freeze language-specific gates from calibration only, without accessing holdout.

First run unit evaluations and tools/calibrate_typing_ranker.py. The checked-in
calibration reports record the selection; normal typing only reads the tiny TSV.
"""
import pathlib,json,math,hashlib
root=pathlib.Path(__file__).resolve().parents[1];english=json.loads((root/'tools/typing-calibration.json').read_text())
rows=[r for r in json.loads((root/'app/build/reports/autocorrect/bilingual-calibration.json').read_text()) if r['language']=='ml-Latn']
options=[]
for temperature in (.10,.15,.20,.35,.50,.75,1.0):
 for threshold in (.985,.99,.995,.999):
  changes=hits=0
  for r in rows:
   scores=r['scores'];best=max(scores) if scores else 0
   confidence=1/sum(math.exp((s-best)/temperature) for s in scores) if scores else 0
   change=r['eligible'] and confidence>=threshold;changes+=change;hits+=change and r['suggestion']==r['expected']
  options.append(dict(temperature=temperature,threshold=threshold,precision=hits/max(1,changes),recall=hits/len(rows),changes=changes))
qualified=[r for r in options if r['precision']>=.995]
selected=max(qualified,key=lambda r:(r['recall'],r['threshold'],r['temperature']))
print('English',english['selected']);print('Manglish',selected)
path=root/'tools/bilingual-calibration.json';path.write_text(json.dumps({'partition':'calibration only; word-family disjoint; holdout never accessed','selectionRule':'precision>=99.5%; maximize recall, then stricter threshold, then temperature','sourceSHA256':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest(),'cases':len(rows),'en':english['selected'],'ml-Latn':selected,'sweep':options},indent=2)+'\n')
(root/'app/src/main/assets/dictionaries/ranker-calibration.tsv').write_text(''.join(f'{lang}\t{row["temperature"]}\t{row["threshold"]}\n' for lang,row in [('en',english['selected']),('ml-Latn',selected)]),encoding='utf-8',newline='\n')
