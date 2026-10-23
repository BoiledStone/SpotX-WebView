"""Framework-only instrumentation; no test hooks are shipped in the product APK."""
import json
import os
import zipfile
from pathlib import Path
from build import ROOT, run

def fixture():
    scripts=ROOT/'build/scripts'
    # Use the current experience layer while iterating; build.py embeds this same file.
    content=(ROOT/'patches/experience.js').read_text(encoding='utf-8')
    content+='\n'+(scripts/'PlayerCore.CONTENT.js').read_text(encoding='utf-8')
    content+='\n'+(scripts/'PlaybackControls.CONTENT.js').read_text(encoding='utf-8')
    content+='\n'+(scripts/'CssHack.CONTENT.js').read_text(encoding='utf-8')
    content+='\n'+(scripts/'SpotilolPlayer.PART_1.js').read_text(encoding='utf-8')+(scripts/'SpotilolPlayer.PART_2.js').read_text(encoding='utf-8')
    page='''<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;background:#101411;color:#f3f8f5;font-family:system-ui}header{padding:24px;font-size:23px;font-weight:700;border-bottom:1px solid #ffffff12}main{padding:24px}h1{font-size:30px;line-height:1.2}.note{color:#aabbaf;font-size:14px}.shelf{display:grid;grid-template-columns:1fr 1fr;gap:16px}.tile{height:125px;border-radius:16px;padding:16px;display:flex;align-items:end;font-weight:600;background:linear-gradient(145deg,#2e5a45,#15271d)}.tile+div{background:linear-gradient(145deg,#444a65,#1e2133)}</style></head><body>
<header>SpotX WebView</header><main><p class="note">TA BIBLIOTHÈQUE</p><h1>À ton rythme.</h1><div class="shelf"><div class="tile">Titres favoris</div><div class="tile">Pour ce soir</div></div><p class="note">Scénario de validation du lecteur · hors connexion</p></main>
<aside data-testid="now-playing-bar"><div data-testid="now-playing-widget"><a href="/track/0123456789abcdefghijkl" data-testid="context-item-info-title">Evening Light</a><a href="/artist/0123456789abcdefghijkl" data-testid="context-item-info-artist">Luna &amp; The Coast</a></div><button data-testid="control-button-playpause" aria-label="Play"><svg><path d="M3 1.713"/></svg></button><div data-testid="playback-progressbar"><input type="range" min="0" max="192000" value="72000"><div data-testid="progress-bar" style="--progress-bar-transform:37.5%"></div></div><span data-testid="playback-position">1:12</span><span data-testid="playback-duration">3:12</span></aside>
<script>window.fixtureErrors=[];window.onerror=function(m){fixtureErrors.push(String(m))};window.__splFullPlayer=false;window.__splHideEmpty=true;window.__splCanvasEnabled=false;window.__splVideosEnabled=false;window.__splWebCanvas=false;</script><script>CONTENT</script></body></html>'''
    return page.replace('CONTENT',content)

def main():
    java=Path(r'C:\Program Files\Java\jdk-21.0.10\bin\java.exe');sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
    android=sdk/'platforms/android-37.0/android.jar';bt=sdk/'build-tools/36.1.0'
    dest=ROOT/'build/tests';dest.mkdir(exist_ok=True)
    classes=dest/'classes';classes.mkdir(exist_ok=True);dex=dest/'dex';dex.mkdir(exist_ok=True)
    source=list((ROOT/'tests/android').rglob('*.java'))
    run(java.with_name('javac.exe'),'--release','8','-encoding','UTF-8','-classpath',str(android)+os.pathsep+str(ROOT/'build/java'),'-d',classes,*source)
    run(java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--classpath',ROOT/'build/java','--output',dex,*classes.rglob('*.class'))
    unsigned=dest/'unsigned.apk'
    run(bt/'aapt2.exe','link','-I',android,'--manifest',ROOT/'tests/android/AndroidManifest.xml','-o',unsigned)
    page=fixture();(ROOT/'validation/player-fixture.html').write_text(page,encoding='utf-8')
    with zipfile.ZipFile(unsigned,'a') as z:
        z.write(dex/'classes.dex','classes.dex',compress_type=zipfile.ZIP_STORED)
        z.writestr('assets/player.html',page,compress_type=zipfile.ZIP_DEFLATED)
        z.writestr('assets/native-fetch.js',(ROOT/'patches/native-fetch.js').read_text(encoding='utf-8'),compress_type=zipfile.ZIP_DEFLATED)
    aligned=dest/'aligned.apk';output=ROOT/'output/SpotX-WebView-tests.apk'
    run(bt/'zipalign.exe','-f','4',unsigned,aligned)
    env=dict(os.environ,JAVA_HOME=str(java.parents[1]),SPOTX_WEBVIEW_KEY_PASS=json.loads((ROOT/'.local/signing.json').read_text())['password'])
    run(bt/'apksigner.bat','sign','--ks',ROOT/'.local/spotx-webview.p12','--ks-key-alias','spotx-webview',
        '--ks-pass','env:SPOTX_WEBVIEW_KEY_PASS','--out',output,aligned,env=env)
    run(bt/'apksigner.bat','verify',output,env=env)
    print(output)

if __name__=='__main__':main()
