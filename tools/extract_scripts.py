"""Recover the shipped JavaScript constants, without treating JADX output as source."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def extract(decoded, out):
    out.mkdir(parents=True,exist_ok=True)
    count=0
    for path in (decoded / 'smali/com/project/lol/webview/injections').glob('*.smali'):
        source = path.read_text(encoding='utf-8')
        constants = {}
        for match in re.finditer(r'^\.field .*? ([A-Z][A-Z_0-9]*):Ljava/lang/String; = (".*")$', source, re.M):
            constants[match[1]] = json.loads(match[2].replace("\\'", "'"))
        for name, value in constants.items():
            (out / f'{path.stem}.{name}.js').write_text(value, encoding='utf-8')
            count += 1
    return count

if __name__=='__main__':
    print(f"Extracted {extract(ROOT/'decoded', ROOT/'baseline/scripts')} shipped script constants")
