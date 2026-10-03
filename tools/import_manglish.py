"""Reproducible Dakshina Malayalam import; fetch only the three lexicon members.

Raw, licensed input is checked in for offline CI reproduction. --fetch traverses
the official uncompressed tar with validated HTTP ranges, without downloading
unrelated languages or extracting untrusted archive paths.
"""
import argparse, collections, hashlib, json, math, pathlib, random, re, tarfile, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
DATA = ROOT / 'tools/data/dakshina'
ASSETS = ROOT / 'app/src/main/assets/dictionaries/manglish'
URL = 'https://storage.googleapis.com/gresearch/dakshina/dakshina_dataset_v1.0.tar'
MEMBERS = {f'dakshina_dataset_v1.0/ml/lexicons/ml.translit.sampled.{s}.tsv' for s in ('train','dev','test')}

def fetch():
    DATA.mkdir(parents=True, exist_ok=True)
    def read_range(start, size):
        request = urllib.request.Request(URL, headers={'Range':f'bytes={start}-{start+size-1}'})
        with urllib.request.urlopen(request, timeout=60) as response:
            if response.status != 206 or not response.headers['Content-Range'].startswith(f'bytes {start}-'):
                raise RuntimeError('Server did not return the requested archive range')
            payload = response.read()
        if len(payload) != size: raise RuntimeError('Truncated archive member')
        return payload
    offset = 0; found = set(); count = 0
    while found != MEMBERS:
        header = read_range(offset,512)
        if not any(header): raise RuntimeError('Missing Malayalam source members')
        member = tarfile.TarInfo.frombuf(header,'utf-8','strict')
        if member.name in MEMBERS:
            payload = read_range(offset+512,member.size)
            (DATA/pathlib.PurePosixPath(member.name).name).write_bytes(payload)
            found.add(member.name)
            print('Fetched',member.name,member.size,flush=True)
        offset += 512 + ((member.size+511)//512)*512
        count += 1
        if count % 40 == 0: print('Scanned',count,'archive headers',flush=True)

def split(family):
    bucket = int(hashlib.sha256(family.encode()).hexdigest()[:8],16)%10
    return 'training' if bucket<6 else 'calibration' if bucket<8 else 'holdout'

def grams(word):
    word = '^'+word+'$'
    return [word[i:i+n] for n in (3,4) for i in range(len(word)-n+1)]

def fixtures(entries):
    rng=random.Random(192193)
    rows=[]; all_words=set(entries)
    # Spelling-error generation excludes existing romanizations. Family partitioning
    # precedes mutation; all spellings of a native family share their evaluation split.
    families=collections.defaultdict(list)
    for word,entry in entries.items():
        native=[f for f in entry['families'] if not f.startswith('chat:')]
        if len(native)==1 and 5<=len(word)<=12: families[native[0]].append(word)
    for partition in ('training','calibration','holdout'):
        eligible=[f for f in sorted(families) if split(f)==partition]
        rng.shuffle(eligible)
        for family in eligible[:240]:
            word=min(families[family],key=lambda w:(-entries[w]['frequency'],-entries[w]['attestations'],w))
            i=rng.randrange(1,len(word)-1)
            errors=[word[:i]+word[i+1:],word[:i]+word[i+1]+word[i]+word[i+2:],word[:i]+word[i]+word[i:],word[:i]+rng.choice('abcdefghijklmnopqrstuvwxyz')+word[i+1:]]
            for kind,typed in zip(('missing','transpose','repeated','substitution'),errors):
                if typed==word or typed in all_words: continue
                rows.append({'typed':typed,'expected':word,'context':['njan','innu'],'kind':kind,'language':'ml-Latn','family':family,'split':partition})
    protected=sorted(entries)
    rng.shuffle(protected)
    for word in protected[:1100]:
        rows.append({'typed':word,'expected':word,'context':['njan','innu'],'kind':'protected','language':'ml-Latn','family':'control:'+word,'split':'holdout'})
    for word,row in sorted(entries.items()):
        if row['core']: rows.append({'typed':word,'expected':word,'context':['njan','innu'],'kind':'variant','language':'ml-Latn','family':'control:'+word,'split':'holdout'})
    english=json.loads((ROOT/'app/src/test/resources/typing-generated.json').read_text(encoding='utf-8'))
    for partition in ('training','calibration','holdout'):
        eligible=[row for row in english if row['split']==partition]
        rng.shuffle(eligible)
        for row in eligible[:900]:
            rows.append(dict(row,language='en',family=row['expected']))
    prediction=[]; counts=collections.Counter(); conversations=ROOT/'tools/data/manglish-conversations.tsv'
    for line in conversations.read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        family,partition,text=line.split('\t'); words=text.split()
        if partition=='training':
            for i,word in enumerate(words):
                for size in range(1,min(i,5)+1): counts[(' '.join(words[i-size:i]),word)]+=1
        else:
            for i in range(2,len(words)):
                prediction.append({'context':words[max(0,i-5):i],'expected':words[i],'family':family,'split':partition})
    (ASSETS/'context.tsv').write_text(''.join(f'{ctx}\t{word}\t{count*24}\n' for (ctx,word),count in sorted(counts.items())),encoding='utf-8',newline='\n')
    resource=ROOT/'app/src/test/resources/manglish-cases.json'
    resource.write_text(json.dumps({'corrections':rows,'predictions':prediction},ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
    return {'correctionCases':dict(collections.Counter((row['language'],row['split']) for row in rows)), 'predictionCases':len(prediction)}

def build():
    ASSETS.mkdir(parents=True,exist_ok=True)
    native_freq = dict(line.split('\t') for line in (ROOT/'app/src/main/assets/dictionaries/multilingual/ml.tsv').read_text(encoding='utf-8').splitlines())
    entries = {}; sources = []; train_ml = []
    for partition in ('train','dev','test'):
        path = DATA/f'ml.translit.sampled.{partition}.tsv'; payload = path.read_bytes()
        sources.append({'member':f'ml/lexicons/{path.name}','sha256':hashlib.sha256(payload).hexdigest(),'bytes':len(payload)})
        for line in payload.decode().splitlines():
            native, roman, attest = line.split('\t'); roman=roman.lower()
            if not re.fullmatch('[a-z]{2,32}',roman): continue
            record = entries.setdefault(roman,{'families':set(),'frequency':1,'attestations':0,'core':False})
            record['families'].add(native); record['attestations']+=int(attest)
            # Native-script subtitle count is a usage proxy; annotator votes are not usage.
            record['frequency']=max(record['frequency'],min(500,int(native_freq.get(native,1))))
            if partition=='train' and split(native)=='training': train_ml.append((roman,native))
    # Preserve and share all existing hand-authored transliteration entries.
    trie=(ROOT/'app/src/main/java/com/example/MalayalamManglishTrie.kt').read_text(encoding='utf-8')
    for roman,native,freq in re.findall(r'insert\("([a-z]+)", "([^"]+)", (\d+)',trie):
        record=entries.setdefault(roman,{'families':set(),'frequency':1,'attestations':0,'core':False})
        record['families'].add(native); record['frequency']=max(record['frequency'],int(freq)); record['core']=True
    extras=json.loads((ROOT/'tools/data/manglish-conversational-variants.json').read_text(encoding='utf-8'))
    for family,forms in extras.items():
        for roman in forms:
            record=entries.setdefault(roman,{'families':set(),'frequency':1,'attestations':0,'core':False})
            record['families'].add('chat:'+family); record['frequency']=max(record['frequency'],900); record['core']=True
    output=''.join(f"{word}\t{'|'.join(sorted(row['families']))}\t{row['frequency']}\t{row['attestations']}\t{int(row['core'])}\n" for word,row in sorted(entries.items()))
    (ASSETS/'lexicon.tsv').write_text(output,encoding='utf-8',newline='\n')
    # Character model sees training native-word families only, never Dakshina dev/test.
    english=(ROOT/'app/src/main/assets/dictionaries/english_canonical.txt').read_text().splitlines()
    en=collections.Counter(); ml=collections.Counter()
    for word in english:
        if re.fullmatch('[a-z]{2,24}',word) and split(word)=='training': en.update(set(grams(word)))
    for word,_ in sorted(set(train_ml)): ml.update(set(grams(word)))
    selected=sorted((en+ml),key=lambda g:(-(en[g]+ml[g]),g))[:16384]
    en_total=sum(en.values())+len(selected); ml_total=sum(ml.values())+len(selected)
    model=''.join(f'{g}\t{math.log((en[g]+1)/en_total):.8f}\t{math.log((ml[g]+1)/ml_total):.8f}\n' for g in sorted(selected))
    (ASSETS/'character-language.tsv').write_text(model,encoding='utf-8',newline='\n')
    fixture_info=fixtures(entries)
    manifest={'source':URL,'dataset':'Dakshina v1.0','license':'CC BY-SA 4.0','sources':sources,'words':len(entries),
              'nativeFamilies':len(set(f for row in entries.values() for f in row['families'])),
              'characterModel':'Laplace-smoothed character 3/4-gram naive Bayes; equal language priors; training partitions only',
              'characterGrams':len(selected),'characterTrainingEnglish':sum(1 for w in english if re.fullmatch('[a-z]{2,24}',w) and split(w)=='training'),
              'characterTrainingMalayalamPairs':len(set(train_ml)),
              'usage':'Native-script frequency proxy and authored conversational priors, separately from romanization attestations',
              'fixtureCounts':{f'{lang}/{part}':count for (lang,part),count in fixture_info['correctionCases'].items()},'predictionCases':fixture_info['predictionCases'],
              'artifacts':{name:hashlib.sha256((ASSETS/name).read_bytes()).hexdigest() for name in ('lexicon.tsv','character-language.tsv','context.tsv')}}
    (ASSETS/'provenance.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps({k:manifest[k] for k in ('words','nativeFamilies','characterGrams')}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--fetch',action='store_true'); args=parser.parse_args()
    if args.fetch: fetch()
    build()
