"""Build canonical spelling evidence from SCOWL 2020.12.07 (no typo labels)."""
import hashlib, pathlib, re, tarfile, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = 'https://downloads.sourceforge.net/wordlist/scowl-2020.12.07.tar.gz'
def build(archive):
    data = pathlib.Path(archive).read_bytes()
    words = set()
    with tarfile.open(archive) as source:
        for member in source:
            name = member.name.rsplit('/', 1)[-1]
            if '/final/' in member.name and re.fullmatch(r'(english|american|british)-words\.\d+', name) and int(name.split('.')[-1]) <= 60:
                for word in source.extractfile(member).read().decode('latin-1').splitlines():
                    if re.fullmatch("[a-z]+(?:'[a-z]+)?", word): words.add(word)
        license_text = source.extractfile('scowl-2020.12.07/Copyright').read().decode('utf-8')
    target = ROOT / 'app/src/main/assets/dictionaries'
    (target / 'english_canonical.txt').write_text('\n'.join(sorted(words)) + '\n', encoding='utf-8')
    (target / 'SCOWL-LICENSE.txt').write_text(f'Source: {URL}\nArchive SHA256: {hashlib.sha256(data).hexdigest()}\nSCOWL sizes <=60, English/American/British lowercase words.\n\n' + license_text, encoding='utf-8')
    print(f'{len(words)} canonical spellings')
if __name__ == '__main__':
    import sys
    archive = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path('scowl-2020.12.07.tar.gz')
    if not archive.exists(): urllib.request.urlretrieve(URL, archive)
    build(archive)
