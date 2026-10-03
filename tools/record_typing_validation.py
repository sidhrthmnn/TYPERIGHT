"""Record current unit/device evidence only after both suites have passed."""
import datetime, json, pathlib, re, xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
BUILD=ROOT/'app/build'
def stats(paths):
    roots=[ET.parse(path).getroot() for path in paths]
    assert roots,'Missing test evidence'
    result={k:sum(int(root.get(k,0)) for root in roots) for k in ('tests','failures','errors','skipped')}
    assert result['tests']>0 and result['failures']==result['errors']==result['skipped']==0,result
    return result
unit=stats((BUILD/'test-results/testDebugUnitTest').glob('TEST-*.xml'))
device=stats((BUILD/'outputs/androidTest-results/connected/debug').glob('TEST-*.xml'))
logs=list((BUILD/'outputs/androidTest-results/connected/debug').rglob('logcat-com.example.EditingInputConnectionDeviceTest-measureActualImeHandlersAndWorkerRanking.txt'))
assert len(logs)==1
match=re.search(r'workerP95=([\d.E+-]+) imeExclusiveP95=([\d.E+-]+) endToEndP95=([\d.E+-]+)',logs[0].read_text(encoding='utf-8'))
assert match,'Missing actual IME metrics'
latency=dict(zip(('workerP95Ms','imeExclusiveHandlerP95Ms','nativeEditorEndToEndP95Ms'),map(float,match.groups())))
assert latency['workerP95Ms']<=15 and latency['imeExclusiveHandlerP95Ms']<2,latency
report_dir=BUILD/'reports/autocorrect'
def read(name):return json.loads((report_dir/name).read_text(encoding='utf-8'))
independent={k:{key:value for key,value in row.items() if key!='records'} for k,row in read('independent.json').items()}
authored=read('metrics.json')
version=re.search(r'versionName\s*=\s*"([^"]+)"',(ROOT/'app/build.gradle.kts').read_text()).group(1)
removed=json.loads((ROOT/'tools/unused-method-removal.json').read_text())
result={
 'version':version,'validatedUTC':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'unitTests':unit,'deviceTests':dict(device,environment='Pixel_9 emulator, Android 16, x86_64, SwiftShader',
   editors=['native EditText','Compose BasicTextField','WebView textarea'],latency=latency,
   latencyDefinition='240 warm worker rankings with fresh context/personal scoring; 150 actual IME callbacks. IME-exclusive subtracts delegated InputConnection work; end-to-end includes it.'),
 'independent':independent,'bilingualHoldout':read('manglish.json'),
 'previousMainBilingual':json.loads((ROOT/'tools/manglish-baseline.json').read_text()),
 'authored':{k:authored[k] for k in ('cases','englishTypos','automaticPrecision','falseCorrectionRate','automaticRecall')},
 'undoTrace':read('undo.json'),'removedUnusedMethods':len(removed['removed']),
 'assetReproduction':'Pinned sources, derived SHA256 values and family/template splits verified; vocabulary, character model, ranker and evaluation splits regenerate byte-identically.',
 'delivery':'Unit tests and temporary device instrumentation packages only. No standalone APK build, delivery or CI APK artifact.',
 'limitations':'Synthetic word-family and authored conversation-template holdouts are development regression gates, not hidden external or natural-chat evaluations. Unambiguous English recall labels the only closest canonical spelling; overall recall includes ambiguous cases. Valid vocabulary is available for every partition, but training weights/character evidence use training families and confidence gates use calibration only. Cold startup queues checks during asset loading; warm timing excludes startup. Undo is a deterministic regression trace, not observed user behavior. Emulator timings are not physical-device guarantees; competing host workloads can exceed the warm target.'
}
(ROOT/'tools/typing-validation.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')
print(json.dumps({'unit':unit,'device':device,'latency':latency,'removed':len(removed['removed'])}))
