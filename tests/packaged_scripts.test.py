"""Regression: inspect compiled DEX, including R8's inlined script copies."""
import struct
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
strings=[]
with zipfile.ZipFile(root/'output/SpotX-WebView-2.0.2.apk') as apk:
    for name in apk.namelist():
        if not (name.startswith('classes') and name.endswith('.dex')):continue
        dex=apk.read(name)
        count,offset=struct.unpack_from('<II',dex,0x38)
        for index in range(count):
            pos=struct.unpack_from('<I',dex,offset+index*4)[0]
            while dex[pos]&0x80:pos+=1
            pos+=1
            strings.append(dex[pos:dex.index(0,pos)].decode('utf-8',errors='replace'))

checks=0
def verify(marker,required):
    global checks
    candidates=[s for s in strings if marker in s]
    assert candidates, f'Missing executable script: {marker}'
    for value in candidates:
        for token in required:
            assert token in value, f'Unpatched script copy: {marker}: {token}'
            checks+=1

verify('window.updMedia = function(){',['var spotxMeta=window.spotxNowPlaying();'])
verify('window.splUpdate=function(){',['window.spotxUpdateMetadata(ci,tk,ar,pl);','window.spotxAccessibility('])
verify('window.initSpotilolPlayer=function(){',['<img id="spl-cover-img" hidden alt="">'])
verify('var reqPause=false,firstPlay=true',['window.__spotxExperience','document.documentElement.lang || navigator.language'])
verify('Close library',['Fermer la bibliothèque'])
verify('window.__npStop && window.__npStop();',['now-playing-widget-v2'])
verify('var reqPause=false,firstPlay=true',['max-height:64px','data-spotx-library=closed'])
verify('window.splUpdate=function(){',["e.target.closest('.spl-row2')"])
verify("var BTN_ID = 'spl-cols-btn';",['Tri et colonnes','intl-'])
verify("var exp=ls.querySelector(",['ls.dataset.spotxLibrary'])
verify('var reqPause=false,firstPlay=true',['state.failures<3','spotxPlaybackIcon','meta.title === title'])
verify('window.splUpdate=function(){',['window.spotxPlaybackIcon(pp,isPlaying,ph);','window.spotxSetText(ps,posEl.textContent);'])
verify('window.mngFetch=async function(input,options)',['new Request(source,options)','new Response(','untilAbort('])
verify('window.actPlayPause = function(play)', ['spotxPlaybackRecovery', 'BACKGROUND = 120000', 'STALL = 6000', 'AndBridge.reloadPlayer()', 'function restore()'])
verify('window.firstFuck = function()', ['spotxPlaybackRecovery.isArmed()'])
verify('window.addAutoFeatures = function ()', ['spotxConnect.canAutoPlay()', 'Device selection belongs exclusively'])
verify('window.oriFetch = orig;', ['window.spotxConnect', 'active_device_id', 'ObservedSocket', 'info.epoch !== revision'])
verify('window.playFromUri = function(uri, contextUri)', ['spotxConnect.target()', 'spotxConnect.local()', "command/from/' + splSource"])
verify('var reqPause=false,firstPlay=true', ['spotxBlockingDialog', '#onetrust-banner-sdk', 'visibility:hidden!important'])
assert not any('if(ft && window.__splTakeControl' in s for s in strings), 'Legacy automatic device clicks remain in DEX'
checks+=1
for stale in ['2.0 aperçu','https://github.com/lyssadev/Spotilol',
              'https://github.com/lyssadev/Spotilol/releases/latest',
              'https://api.github.com/repos/lyssadev/Spotilol/releases/latest','https://discord.gg/95dAE2UkqP']:
    assert stale not in strings, f'Obsolete settings value remains: {stale}'
    checks+=1
for current in ['https://github.com/BoiledStone/SpotX-WebView',
                'https://github.com/BoiledStone/SpotX-WebView/issues',
                'https://api.github.com/repos/BoiledStone/SpotX-WebView/releases/latest']:
    assert current in strings, f'Missing current settings link: {current}'
    checks+=1
print(f'PASS {checks} compiled script checks, including inlined runtime copies')
