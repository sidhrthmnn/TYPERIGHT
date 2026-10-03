"""Select a confidence gate using calibration scores only, then freeze it for holdout.

Usage: python tools/calibrate_typing_ranker.py app/build/reports/autocorrect/independent.json
The holdout partition is deliberately never accessed. Candidate eligibility is an
independent conservative edit/language policy; probabilities use all alternatives.
"""
import hashlib,json,math,pathlib,sys
source=pathlib.Path(sys.argv[1])
rows=json.loads(source.read_text())['calibration']['records']
required=json.loads((source.parent/'required.json').read_text())
guards=[r for r in required.values() if isinstance(r,dict) and 'candidates' in r]
options=[]
for temperature in (.10,.15,.20,.35,.50,.75,1.0,1.5,2.0):
 for threshold in (.985,.99,.995,.999):
  changes=hits=unique=unique_hits=0
  for row in rows:
   scores=row['scores']; best=max(scores) if scores else 0
   confidence=1/sum(math.exp((s-best)/temperature) for s in scores) if scores else 0
   change=row['eligible'] and confidence>=threshold
   hit=change and row['suggestion']==row['expected']
   changes+=change;hits+=hit;unique+=row['unambiguous'];unique_hits+=hit and row['unambiguous']
  precision=hits/changes if changes else 0; recall=unique_hits/unique if unique else 0
  guard_ok=True
  for guard in guards:
   scores=[float(c.split(':')[1]) for c in guard['candidates']]
   posterior=1/sum(math.exp((s-max(scores))/temperature) for s in scores)
   guard_ok &= guard['candidates'][0].split(':')[0]==guard['expected'] and posterior>=threshold
  options.append(dict(temperature=temperature,threshold=threshold,precision=precision,recall=recall,changes=changes,requiredMissesPass=guard_ok))
qualified=[o for o in options if o['precision']>=.995 and o['recall']>=.90 and o['requiredMissesPass']]
if not qualified: raise SystemExit('Calibration targets not met')
# Prefer recall, then a less sharply peaked distribution, then a stricter gate.
selected=max(qualified,key=lambda o:(o['recall'],o['temperature'],o['threshold']))
root=pathlib.Path(__file__).resolve().parents[1]
evidence=[{k:r.get(k) for k in ('typed','expected','suggestion','scores','eligible','unambiguous')} for r in rows]
report={'partition':'calibration plus authored required-miss guards; no holdout access','cases':len(rows),'sourceSHA256':hashlib.sha256(json.dumps(evidence,sort_keys=True).encode()).hexdigest(),'selectionRule':'precision>=.995, recall>=.90, required misses pass; maximize recall then temperature then threshold','selected':selected,'sweep':options}
(root/'tools/typing-calibration.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(selected))
