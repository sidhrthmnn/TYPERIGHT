"""Conservative whole-source call-site inventory; framework/annotated methods are retained.

This is an audit aid, not a Kotlin semantic analyzer. Removal always requires
inspection plus compilation/tests. It deliberately counts callable references,
Java/XML/native references and string names as uses.
"""
import collections, pathlib, re
ROOT=pathlib.Path(__file__).resolve().parents[1]
files=[p for p in (ROOT/'app/src').rglob('*') if p.suffix in ('.kt','.java','.xml','.cpp','.h')]
tokens=collections.Counter()
for path in files: tokens.update(re.findall(r'\b[A-Za-z_]\w*\b',path.read_text(encoding='utf-8')))
for path in sorted((ROOT/'app/src/main').rglob('*.kt')):
    source=path.read_text(encoding='utf-8')
    for match in re.finditer(r'(?m)^([ \t]*)(?:(?:private|internal|public|protected|inline|suspend|tailrec|operator|infix|open|final|override|external)\s+)*fun\s+([A-Za-z_]\w*)\s*\(',source):
        name=match.group(2); line=source.count('\n',0,match.start())+1
        prefix=source[max(0,source.rfind('\n\n',0,match.start())):match.end()]
        if tokens[name]!=1 or re.search(r'\b(?:override|external)\b',match.group(0)) or '@' in prefix: continue
        print(f'{path.relative_to(ROOT)}:{line} {name}')
