"""Rebuild the exact installed 1.2.4 with reviewable patches. No Kotlin sources are fabricated."""
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor
from threading import Lock
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_HASH = 'a48b347b00120ffb223932448fee3a503b22e9b73e399f831f8f512604268077'
APP_ID = 'com.boiled.spotx.webview'
VERSION = '2.0.2'
VERSION_CODE = 20007
ANDROID = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', ANDROID[1:-1])

def run(*args, env=None):
    result = subprocess.run([str(x) for x in args], cwd=ROOT, env=env, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(f'{Path(str(args[0])).name} failed ({result.returncode}):\n{result.stdout[-12000:]}')
    return result.stdout

def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def literal(value): return json.dumps(value, ensure_ascii=False)
def decode_literal(value): return json.loads(value.replace("\\'", "'"))

def replace_once(source, old, new, label):
    if source.count(old) != 1: raise ValueError(f'{label}: expected one occurrence, got {source.count(old)}')
    return source.replace(old, new)

def replace_method(source, signature, body):
    pattern = re.compile(r'(^\.method '+re.escape(signature)+r'\n).*?^\.end method', re.M|re.S)
    result, count = pattern.subn(lambda m: m[1]+body.rstrip()+'\n.end method', source)
    if count != 1: raise ValueError(f'Method not unique: {signature} ({count})')
    return result

def settings_drawable_id(work):
    nodes=ET.parse(work/'res/values/public.xml').getroot().findall('public')
    existing=next((n for n in nodes if n.get('type')=='drawable' and n.get('name')=='spotx_settings'),None)
    return int(existing.get('id'),16) if existing is not None else max(int(n.get('id'),16) for n in nodes if n.get('type')=='drawable')+1

def script_changes():
    originals = {p.name:p.read_text(encoding='utf-8') for p in (ROOT/'baseline/scripts').glob('*.js')}
    changed = dict(originals)
    fetch=originals['FetchOverride.CONTENT.js']
    start=fetch.index('                window.__splLocalDevId =')
    end=fetch.index('                var orig =',start)
    fetch=fetch[:start]+fetch[end:]
    start=fetch.index('                        var m = url.match(')
    end=fetch.index('                        var m4 =',start)
    fetch=fetch[:start]+fetch[end:]
    fetch=replace_once(fetch,'                    var p = orig.call(window, input, init);',
        '                    var observation=window.spotxConnect.request(input,init);\n                    var p = orig.call(window, input, init);\n                    window.spotxConnect.response(observation,p);','observe Connect responses')
    changed['FetchOverride.CONTENT.js']=(ROOT/'patches/connect-state.js').read_text(encoding='utf-8')+'\n'+fetch
    changed['AutoFeatures.CONTENT.js']=(ROOT/'patches/auto-features.js').read_text(encoding='utf-8')
    changed['ClassicBridge.CONTENT.js']=(ROOT/'patches/native-fetch.js').read_text(encoding='utf-8')
    playback=replace_once(originals['PlaybackControls.CONTENT.js'],
        "var splTarget = (typeof window.__splRemoteActive === 'function' && window.__splRemoteActive() && window.__splActiveDevId) ? window.__splActiveDevId : window.spotDevId;",
        "var splTarget=window.spotxConnect?window.spotxConnect.target():window.spotDevId;\n                var splSource=window.spotxConnect?window.spotxConnect.local():window.spotDevId;\n                if(!splSource||!splTarget)return;",'stable Connect source and active target')
    playback=replace_once(playback,"/command/from/' + window.spotDevId + '/to/' + splTarget", "/command/from/' + splSource + '/to/' + splTarget",'Connect command sender')
    changed['PlaybackControls.CONTENT.js']=playback+'\n'+(ROOT/'patches/playback-recovery.js').read_text(encoding='utf-8')
    changed['MainLoop.CONTENT.js']=replace_once(originals['MainLoop.CONTENT.js'],
        "if(ulFlag && window.splIsPlaying()===false)",
        "if(!window.spotxConnect||!window.spotxConnect.canAutoPlay()||(window.spotxPlaybackRecovery&&window.spotxPlaybackRecovery.isArmed())){ulFlag=false;return;}\n                            if(ulFlag && window.splIsPlaying()===false)",
        'avoid skipping a stalled track during recovery')
    changed['PlayerCore.CONTENT.js'] = (ROOT/'patches/modal-visibility.js').read_text(encoding='utf-8')+'\n'+(ROOT/'patches/experience.js').read_text(encoding='utf-8')+'\n'+originals['PlayerCore.CONTENT.js']
    p1 = originals['SpotilolPlayer.PART_1.js']
    p1 = replace_once(p1, '<img id="spl-cover-img" src="" alt="">', '<img id="spl-cover-img" hidden alt="">', 'initial artwork')
    p1 = p1.replace("!t||!t.textContent||t.textContent==='No track'", "!t||!t.textContent||pl.dataset.spotxEmpty==='true'")
    p1 = replace_once(p1,"if(window.__splHideEmpty&&empty) pl.classList.add('spl-empty');\n                        else pl.classList.remove('spl-empty');",
        "pl.classList.toggle('spl-empty',!!window.__splHideEmpty&&!!empty);",'stable empty state')
    changed['SpotilolPlayer.PART_1.js'] = p1
    p2 = originals['SpotilolPlayer.PART_2.js']
    start = p2.index('                        var npb=window.splQ(\'[data-testid="now-playing-widget"]\');')
    end = p2.index('                        var rg=window.splQ(',start)
    p2 = p2[:start]+"                        window.spotxUpdateMetadata(ci,tk,ar,pl);\n\n"+p2[end:]
    p2 = replace_once(p2, '                        splApplyEmpty();',
        '                        window.spotxAccessibility(pl,window.splIsPlayingSticky());\n                        splApplyEmpty();', 'accessible controls')
    p2 = replace_once(p2, "if(e.target.closest('#spl-bar')||e.target.closest('#spl-edgebar'))return;var t=e.touches[0];",
        "if(e.target.closest('button')||e.target.closest('.spl-row2')||e.target.closest('#spl-bar')||e.target.closest('#spl-edgebar'))return;var t=e.touches[0];", 'avoid dragging controls')
    p2=replace_once(p2,'if(pp)pp.innerHTML=ph;','window.spotxPlaybackIcon(pp,isPlaying,ph);','stable play icon')
    p2=replace_once(p2,'if(ppm)ppm.innerHTML=ph;','window.spotxPlaybackIcon(ppm,isPlaying,ph);','stable mini play icon')
    p2=replace_once(p2,'if(ps&&posEl) ps.textContent=posEl.textContent;','if(ps&&posEl) window.spotxSetText(ps,posEl.textContent);','stable position text')
    p2=replace_once(p2,'if(ds&&durEl) ds.textContent=durEl.textContent;','if(ds&&durEl) window.spotxSetText(ds,durEl.textContent);','stable duration text')
    changed['SpotilolPlayer.PART_2.js'] = p2
    columns=originals['ColumnsButton.CONTENT.js']
    columns=replace_once(columns,"sortAvailable() ? 'Sort & columns' : 'Columns'",
        "sortAvailable() ? window.spotxText('Sort & columns','Tri et colonnes') : window.spotxText('Columns','Colonnes')",'columns translation')
    columns=replace_once(columns,"var p = location.pathname;","var p = location.pathname.replace(/^\\/intl-[^/]+(?=\\/)/,'');",'localized collection paths')
    changed['ColumnsButton.CONTENT.js']=columns
    media = originals['MediaUpdater.CONTENT.js']
    media = replace_once(media, '            window.updMedia = function(){', '''            window.updMedia = function(){
                var spotxMeta=window.spotxNowPlaying();
                if(!spotxMeta.empty){track=spotxMeta.title;artist=spotxMeta.artist;cover=spotxMeta.cover;}
                else {track='';artist='';cover='';playing=false;position=0;duration=0;}
''', 'notification metadata')
    changed['MediaUpdater.CONTENT.js'] = media
    csshack=originals['CssHack.CONTENT.js']
    csshack=replace_once(csshack,"lh.innerHTML='\\u2716 &nbsp; Close Library'", "lh.textContent=window.spotxText('Close library','Fermer la bibliothèque')",'library label')
    csshack=replace_once(csshack,"var exp=ls.querySelector('nav>div>div:first-child').classList.length;",
        "var exp=ls.querySelector('nav>div>div:first-child').classList.length;ls.dataset.spotxLibrary=exp==2?'open':'closed';",'library collapse state')
    changed['CssHack.CONTENT.js']=csshack
    changed['LoginDetection.CONTENT.js'] = '''(function(){
        var link=document.querySelector('button[data-testid=web-player-link]');
        if(link && !link.dataset.spotxLoginPending){
            link.dataset.spotxLoginPending='true';
            Promise.resolve(AndBridge.loginDetected()).then(function(){link.click();})
                .catch(function(){delete link.dataset.spotxLoginPending;});
        }
    })();'''
    spicy = originals['SpicyLyrics.CONTENT.js']
    spicy = spicy.replace('function resolve(id){','async function resolve(id){')
    spicy = spicy.replace('var raw = AndBridge.spicyLyrics(', 'var raw = await AndBridge.spicyLyrics(')
    spicy = spicy.replace('function patchMetadata(json){','async function patchMetadata(json){')
    spicy = spicy.replace('if (!resolve(id))', 'if (!(await resolve(id)))')
    spicy = spicy.replace('.then(function(json){', '.then(async function(json){')
    spicy = spicy.replace('if (!patchMetadata(json))', 'if (!(await patchMetadata(json)))')
    spicy = spicy.replace('.then(function(resp){', '.then(async function(resp){')
    spicy = spicy.replace('var payload = resolve(lyr[1]);', 'var payload = await resolve(lyr[1]);')
    spicy = spicy.replace('window.splOnTrackChange(function(uri, id){', 'window.splOnTrackChange(async function(uri, id){')
    spicy = spicy.replace('has: function(id){ return !!resolve(id); }', 'has: function(id){ return !!cache[id]; }')
    changed['SpicyLyrics.CONTENT.js'] = spicy
    # Spotify now uses a v2 now-playing widget. Keep every scoped consumer (track
    # observer, media state, favourites, lyrics and downloads) on the same root.
    for name,value in list(changed.items()):
        if name=='PlayerCore.CONTENT.js':continue  # experience.js already handles both forms.
        for selector in ['[data-testid="now-playing-widget"]','[data-testid=now-playing-widget]']:
            value=value.replace(selector, ':is([data-testid="now-playing-widget"],[data-testid="now-playing-widget-v2"])')
        changed[name]=value
    # Keep a reviewable copy of precisely what will be installed in the JavaScript layer.
    out = ROOT/'build/scripts';out.mkdir(parents=True,exist_ok=True)
    for name, value in changed.items(): (out/name).write_text(value, encoding='utf-8')
    return {originals[k]:v for k,v in changed.items() if originals[k]!=v}

def patch_smali(work):
    mapping=script_changes(); counts={k:0 for k in mapping}
    string_pattern=re.compile(r'(?P<prefix>^\s*(?:\.field [^\n]*? = |const-string(?:/jumbo)? [^,]+, ))(?P<value>".*")$',re.M)
    files=list(work.glob('smali*/**/*.smali'))
    counter_lock=Lock()
    def transform_file(path):
        source=path.read_text(encoding='utf-8')
        def transform(m):
            raw=decode_literal(m['value'])
            # R8 also inlines concatenated scripts into executable methods. Updating
            # only standalone CONTENT fields leaves the real WebView using old code.
            updated=raw
            for old,new in mapping.items():
                occurrences=updated.count(old)
                if occurrences:
                    with counter_lock:counts[old]+=occurrences
                    updated=updated.replace(old,new)
            if updated!=raw:return m['prefix']+literal(updated)
            if raw=='com.project.lol': return m['prefix']+literal(APP_ID)
            if raw=='Spotilol': return m['prefix']+literal('SpotX WebView')
            if raw=='1.2.4': return m['prefix']+literal(VERSION)
            if raw=='spotilol-log_': return m['prefix']+literal('spotx-webview-log_')
            links={
                'https://github.com/lyssadev/Spotilol':'https://github.com/BoiledStone/SpotX-WebView',
                'https://github.com/lyssadev/Spotilol/releases':'https://github.com/BoiledStone/SpotX-WebView/releases',
                'https://github.com/lyssadev/Spotilol/releases/latest':'https://github.com/BoiledStone/SpotX-WebView/releases/latest',
                'https://api.github.com/repos/lyssadev/Spotilol/releases/latest':'https://api.github.com/repos/BoiledStone/SpotX-WebView/releases/latest',
                'https://discord.gg/95dAE2UkqP':'https://github.com/BoiledStone/SpotX-WebView/issues'}
            if raw in links:return m['prefix']+literal(links[raw])
            if raw.startswith('# spotilol log export'):
                return m['prefix']+literal(raw.replace('spotilol log export','SpotX WebView log export').replace('1.2.4 (23)',f'{VERSION} ({VERSION_CODE})'))
            return m[0]
        output=string_pattern.sub(transform,source)
        if path.relative_to(work).parts[0]=='smali':
            # The shipped primary dex is near 64K strings. Insertions must not overflow 16-bit string operands.
            output=re.sub(r'(?m)^(\s*)const-string ',r'\1const-string/jumbo ',output)
        if output!=source:path.write_text(output,encoding='utf-8')
    with ThreadPoolExecutor(max_workers=16) as pool:
        list(pool.map(transform_file,files))
    if any(c==0 for c in counts.values()): raise ValueError('Missing script constants')

    def edit(rel, fn):
        path=work/'smali'/rel
        path.write_text(fn(path.read_text(encoding='utf-8')),encoding='utf-8')

    def host(s):
        s=replace_once(s,'invoke-virtual {v1, v0, v2}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V',
            'invoke-static {v1, v0, v2}, Lcom/boiled/spotx/SecureBridge;->install(Landroid/webkit/WebView;Ljava/lang/Object;Ljava/lang/String;)V','origin transport') if 'invoke-virtual {v1, v0, v2}, Landroid/webkit/WebView;->addJavascriptInterface' in s else s
        # The registers are compiler-specific; preserve the actual three operand registers.
        s,n=re.subn(r'invoke-virtual (\{[^\n]+\}), Landroid/webkit/WebView;->addJavascriptInterface\(Ljava/lang/Object;Ljava/lang/String;\)V',
            r'invoke-static \1, Lcom/boiled/spotx/SecureBridge;->install(Landroid/webkit/WebView;Ljava/lang/Object;Ljava/lang/String;)V',s)
        if n!=1 and 'Lcom/boiled/spotx/SecureBridge;->install' not in s:raise ValueError('Missing bridge installation')
        s,n=re.subn(r'invoke-virtual (\{[^\n]+\}), Landroid/webkit/WebView;->removeJavascriptInterface\(Ljava/lang/String;\)V',
            r'invoke-static \1, Lcom/boiled/spotx/SecureBridge;->close(Landroid/webkit/WebView;Ljava/lang/String;)V',s)
        if n!=1:raise ValueError('Missing bridge lifecycle hook')
        return s
    edit('oy4.smali',host)
    # The upstream build tag is computed lazily, rather than stored as a literal.
    edit('dz.smali',lambda s:replace_method(s,'public static a()Ljava/lang/String;', '''    .locals 1
    const-string/jumbo v0, ""
    return-object v0'''))
    edit('uh.smali',lambda s:replace_once(s,
        '    invoke-static {}, Ly13;->z()Ly32;\n\n    move-result-object v8\n\n    const v0, 0x7f110141',
        '    invoke-static {}, Lzd4;->k()Ly32;\n\n    move-result-object v8\n\n    const v0, 0x7f110141','header settings gear'))
    edit('b15.smali',lambda s:replace_once(s,
        '.method public static final E(Ljava/lang/String;Ljava/lang/String;Ly32;ZLro1;Lyg0;I)V\n    .locals 27',
        '''.method public static final E(Ljava/lang/String;Ljava/lang/String;Ly32;ZLro1;Lyg0;I)V
    .locals 27
    invoke-static/range {p0 .. p0}, Lcom/boiled/spotx/Hardening;->isTelemetrySetting(Ljava/lang/String;)Z
    move-result v0
    if-eqz v0, :spotx_toggle_setting
    move-object/from16 v0, p0
    move-object/from16 v1, p1
    move-object/from16 v2, p2
    const/4 v3, 0x0
    const/4 v4, 0x0
    const/4 v5, 0x0
    const/4 v6, 0x0
    const/4 v7, 0x0
    const/4 v8, 0x0
    move-object/from16 v9, p5
    const/4 v10, 0x0
    const/16 v11, 0x1f8
    invoke-static/range {v0 .. v11}, Lb15;->F(Ljava/lang/String;Ljava/lang/String;Ly32;Lvx3;Lqo1;Lqo1;ZZLjava/lang/String;Lyg0;II)V
    return-void
    :spotx_toggle_setting
    invoke-static/range {p0 .. p0}, Lcom/boiled/spotx/Hardening;->hideToggleSetting(Ljava/lang/String;)Z
    move-result v0
    if-eqz v0, :spotx_setting_visible
    return-void
    :spotx_setting_visible''','remove automatic takeover setting'))
    edit('b15.smali',lambda s:replace_once(s,
        '.method public static final F(Ljava/lang/String;Ljava/lang/String;Ly32;Lvx3;Lqo1;Lqo1;ZZLjava/lang/String;Lyg0;II)V\n    .locals 41',
        '''.method public static final F(Ljava/lang/String;Ljava/lang/String;Ly32;Lvx3;Lqo1;Lqo1;ZZLjava/lang/String;Lyg0;II)V
    .locals 41
    invoke-static/range {p0 .. p0}, Lcom/boiled/spotx/Hardening;->hideInfoSetting(Ljava/lang/String;)Z
    move-result v0
    if-eqz v0, :spotx_info_visible
    return-void
    :spotx_info_visible
    move-object/from16 v0, p0
    move-object/from16 v1, p4
    invoke-static {v0, v1}, Lcom/boiled/spotx/ThemePicker;->wrap(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lqo1;
    move-object/16 p4, v0''','theme gallery action and obsolete mode row'))
    def remove_mode_divider(s):
        start=s.index('    const v11, 0x7f1102ad')
        end=s.index('    const v11, 0x7f11027f',start)
        section=s[start:end]
        section=replace_once(section,'    invoke-static/range {v11 .. v17}, Ly13;->c(Lk93;FJLyg0;II)V',
            '    # The unavailable mode row is hidden; keep a single adjacent divider.','obsolete mode divider')
        return s[:start]+section+s[end:]
    edit('h05.smali',remove_mode_divider)
    edit('b05.smali',lambda s:replace_once(s,'    sget-object v9, Lmc;->o:Ly32;',
        '    invoke-static {}, Lwb0;->x()Ly32;\n    move-result-object v9','issue reporting icon'))

    def client(s):
        for name, policy in [('isGoogleAuthUrl','google'),('isSpotifyHost','spotify'),('isWebPlayerUrl','player')]:
            s=replace_method(s,f'private final {name}(Ljava/lang/String;)Z',f'''    .locals 1
    invoke-static {{p1}}, Lcom/boiled/spotx/Policy;->{policy}(Ljava/lang/String;)Z
    move-result v0
    return v0''')
        needle='    invoke-super {p0, p1, p2, p3}, Landroid/webkit/WebViewClient;->onPageStarted(Landroid/webkit/WebView;Ljava/lang/String;Landroid/graphics/Bitmap;)V'
        s=replace_once(s,needle,needle+'\n\n    invoke-static {p1, p2}, Lcom/boiled/spotx/SecureBridge;->navigation(Landroid/webkit/WebView;Ljava/lang/String;)V','navigation generation')
        s+='''
.method public shouldOverrideUrlLoading(Landroid/webkit/WebView;Ljava/lang/String;)Z
    .locals 1
    invoke-static {p1, p2}, Lcom/boiled/spotx/SecureBridge;->override(Landroid/webkit/WebView;Ljava/lang/String;)Z
    move-result v0
    return v0
.end method
.method public shouldOverrideUrlLoading(Landroid/webkit/WebView;Landroid/webkit/WebResourceRequest;)Z
    .locals 1
    invoke-static {p1, p2}, Lcom/boiled/spotx/SecureBridge;->override(Landroid/webkit/WebView;Landroid/webkit/WebResourceRequest;)Z
    move-result v0
    return v0
.end method
'''
        return s
    edit('com/project/lol/webview/SpotifyWebViewClient.smali',client)
    def chrome(s):
        for name, policy in [('isGoogleUrl','google'),('isOAuthUrl','oauth'),('isSpotifyUrl','bridge')]:
            s=replace_method(s,f'private final {name}(Ljava/lang/String;)Z',f'''    .locals 1
    invoke-static {{p1}}, Lcom/boiled/spotx/Policy;->{policy}(Ljava/lang/String;)Z
    move-result v0
    return v0''')
        s=replace_method(s,'private static final onPermissionRequest$lambda$0(Landroid/webkit/PermissionRequest;)V','''    .locals 0
    invoke-static {p0}, Lcom/boiled/spotx/Hardening;->permission(Landroid/webkit/PermissionRequest;)V
    return-void''')
        s=replace_method(s,'public onConsoleMessage(Landroid/webkit/ConsoleMessage;)Z','''    .locals 1
    const/4 v0, 0x1
    return v0''')
        # Popups keep their existing authentication routing, with identical WebView restrictions.
        marker='    invoke-virtual {p3, v0}, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V'
        # Locate the WebView receiver of getSettings rather than assuming its register.
        method=re.search(r'\.method public onCreateWindow.*?\.end method',s,re.S)[0]
        match=re.search(r'invoke-virtual \{(\w+)\}, Landroid/webkit/WebView;->getSettings\(\)Landroid/webkit/WebSettings;',method)
        if not match:raise ValueError('Popup WebView missing')
        view_register=match[1]
        s=replace_once(s,marker,marker+f'\n\n    invoke-static {{{view_register}}}, Lcom/boiled/spotx/SecureBridge;->harden(Landroid/webkit/WebView;)V','popup hardening')
        return s
    edit('com/project/lol/webview/SpotifyWebChromeClient.smali',chrome)

    def main(s):
        quick_button=re.search(r'^\.method private final QuickAccessOverlay\(.*?^\.end method',s,re.M|re.S)
        if not quick_button:raise ValueError('Quick settings button missing')
        button=quick_button[0]
        button=replace_once(button,'move-object/from16 v3, p2','move-object/from16 v3, p3','settings opens directly')
        button=replace_once(button,'    if-eqz p1, :cond_f','    goto :cond_f','remove quick settings popup')
        button=replace_once(button,'Ly41;->g:Lou;','Ly41;->h:Lou;','settings button top end')
        button=replace_once(button,'const/16 v20, 0xd','const/16 v20, 0x9','settings right padding mask')
        button=replace_once(button,'const/16 v18, 0x0','const/high16 v18, 0x41000000','settings right padding')
        shadow=button.index('    sget-object v9, Lql4;->a:Lpl4;')
        shadow_end=button.index('    const/4 v2, 0x0',shadow)
        button=button[:shadow]+button[shadow_end:]
        button=replace_once(button,'const v0, 0x7f0700cb',f'const v0, {hex(settings_drawable_id(work))}','plain settings drawable')
        s=s[:quick_button.start()]+button+s[quick_button.end():]
        # This lambda renders only the top bar service switch and its spacer.
        s=replace_method(s,'private static final onCreate$lambda$3$15$17$0$2(ZLcom/project/lol/ui/MainActivity;Ldm4;Lyg0;I)Llx5;', '''    .locals 1
    sget-object v0, Llx5;->a:Llx5;
    return-object v0''')
        # Keep the settings entry and its Compose groups, omit the divider/service row.
        quick=re.search(r'^\.method private static final QuickAccessOverlay\$lambda\$0\$2\$0\(.*?^\.end method',s,re.M|re.S)
        if not quick:raise ValueError('Quick access content missing')
        body=quick[0]
        divider=body.index('    invoke-static/range {v9 .. v15}, Ly13;->c(')
        start=body.rfind('    invoke-static {v14}, Lhc0;->x(',0,divider)
        end=body.index('    goto :goto_b',divider)
        removed=body[start:end]
        if '0x7f11013d' not in removed or 'Lyb6;->i(' not in removed:raise ValueError('Service row layout changed')
        body=body[:start]+'    invoke-virtual {v14, v12}, Lcq1;->p(Z)V\n\n'+body[end:]
        s=s[:quick.start()]+body+s[quick.end():]
        s=replace_once(s,'window.__splBg = true;\\n', 'window.__splBg = true;window.spotxPlaybackRecovery&&window.spotxPlaybackRecovery.background();\\n','recovery background signal')
        s=replace_once(s,'window.__splBg = false;\\n', 'window.__splBg = false;window.spotxPlaybackRecovery&&window.spotxPlaybackRecovery.resume();\\n','recovery resume signal')
        s=replace_once(s,"if(typeof splAutoPlay === \\'function\\') splAutoPlay();", "if(typeof splAutoPlay === \\'function\\' && window.spotxConnect && window.spotxConnect.canAutoPlay()) splAutoPlay();",'resume respects Connect device')
        s=replace_method(s,'private final extractSpotifyLink(Landroid/content/Intent;)Ljava/lang/String;','''    .locals 1
    invoke-static {p1}, Lcom/boiled/spotx/Hardening;->link(Landroid/content/Intent;)Ljava/lang/String;
    move-result-object v0
    return-object v0''')
        s=replace_method(s,'private final loadProfile(Ljava/lang/String;)V','''    .locals 1
    invoke-static {p0, p1}, Lcom/boiled/spotx/ProfileSessions;->restore(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :spotx_profile_started
    invoke-static {p0}, Lcom/boiled/spotx/Hardening;->profileFailure(Landroid/content/Context;)V
    :spotx_profile_started
    return-void''')
        # Settings offers the battery exemption explicitly; don't prompt on every cold launch.
        m=re.search(r'\.method static constructor <clinit>\(\)V.*?\.end method',s,re.S)
        body=m[0]
        body=body.replace('    return-void','    const/4 v0, 0x1\n    sput-boolean v0, Lcom/project/lol/ui/MainActivity;->batteryPromptChecked:Z\n    return-void')
        s=s[:m.start()]+body+s[m.end():]
        s=replace_method(s,'private final fetchPipCover(Ljava/lang/String;)V','''    .locals 1
    const/4 v0, 0x1
    invoke-static {p0, p1, v0}, Lcom/boiled/spotx/ArtworkLoader;->load(Ljava/lang/Object;Ljava/lang/String;Z)V
    return-void''')
        s=re.sub(r'(\.method public onDestroy\(\)V\n    \.locals \d+)',r'\1\n    invoke-static/range {p0 .. p0}, Lcom/boiled/spotx/ArtworkLoader;->cancel(Ljava/lang/Object;)V',s,count=1)
        return s
    edit('com/project/lol/ui/MainActivity.smali',main)
    def bridge(s):
        s=replace_method(s,'public final nFetch(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;','''    .locals 1
    invoke-static {p1, p2}, Lcom/boiled/spotx/NativeHttp;->fetch(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    return-object v0''')
        return re.sub(r'    \.annotation runtime Landroid/webkit/JavascriptInterface;\n    \.end annotation\n','',s)
    edit('com/project/lol/bridge/SpotifyBridge.smali',bridge)
    def app(s):
        needle='    invoke-super {p0}, Landroid/app/Application;->onCreate()V'
        return replace_once(s,needle,needle+'\n\n    invoke-static {p0}, Lcom/boiled/spotx/Hardening;->initialize(Landroid/content/Context;)V','app defaults')
    edit('com/project/lol/SpotilolApp.smali',app)
    def media(s):
        needle='.method public onGetRoot(Ljava/lang/String;ILandroid/os/Bundle;)Le43;\n    .locals 0'
        s=replace_once(s,needle,needle.replace('.locals 0','.locals 1')+'''
    invoke-static {p0, p1, p2}, Lcom/boiled/spotx/Hardening;->mediaClient(Landroid/content/Context;Ljava/lang/String;I)Z
    move-result v0
    if-nez v0, :spotx_trusted_client
    const/4 v0, 0x0
    return-object v0
    :spotx_trusted_client
''','media client authorization')
        s=replace_method(s,'private final loadCoverArt(Ljava/lang/String;)V','''    .locals 1
    const/4 v0, 0x0
    invoke-static {p0, p1, v0}, Lcom/boiled/spotx/ArtworkLoader;->load(Ljava/lang/Object;Ljava/lang/String;Z)V
    return-void''')
        s=re.sub(r'(\.method public onDestroy\(\)V\n    \.locals \d+)',r'\1\n    invoke-static/range {p0 .. p0}, Lcom/boiled/spotx/ArtworkLoader;->cancel(Ljava/lang/Object;)V',s,count=1)
        needle='.method public onStartCommand(Landroid/content/Intent;II)I\n    .locals 3'
        s=replace_once(s,needle,needle+'''
    invoke-static {p1}, Lcom/boiled/spotx/Hardening;->validMediaIntent(Landroid/content/Intent;)Z
    move-result v0
    if-nez v0, :spotx_valid_start
    const/4 v0, 0x2
    return v0
    :spotx_valid_start
''','media intent authorization')
        actions=['ACTION_PLAY_PAUSE','ACTION_NEXT','ACTION_PREV','ACTION_SHUFFLE','ACTION_REPEAT','ACTION_FAVORITE','ACTION_WIDGET_REFRESH']
        body='''    .locals 3
    new-instance v0, Landroid/content/IntentFilter;
    invoke-direct {v0}, Landroid/content/IntentFilter;-><init>()V
'''
        for action in actions:
            body+=f'    const-string/jumbo v1, "com.project.lol.{action}"\n    invoke-virtual {{v0, v1}}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V\n'
        body+='''    iget-object v2, p0, Lcom/project/lol/service/MediaNotificationService;->actionReceiver:Lp53;
    invoke-static {p0, v2, v0}, Lcom/boiled/spotx/Hardening;->privateReceiver(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;
    return-void'''
        s=replace_method(s,'private final registerReceivers()V',body)
        return s
    edit('com/project/lol/service/MediaNotificationService.smali',media)
    def media_receiver(s):
        pattern=r'(\.method public final onReceive\(Landroid/content/Context;Landroid/content/Intent;\)V\n    \.locals \d+)'
        s,n=re.subn(pattern,r'\1\n    invoke-static {p2}, Lcom/boiled/spotx/Hardening;->stampMediaIntent(Landroid/content/Intent;)V',s,count=1)
        if n!=1:raise ValueError('Media button receiver missing')
        return s
    edit('androidx/media/session/MediaButtonReceiver.smali',media_receiver)
    def profile(s):
        s=replace_method(s,'public static a(Landroid/content/Context;Ljava/lang/String;)Z','''    .locals 1
    invoke-static {p0, p1}, Lcom/boiled/spotx/ProfileSessions;->restore(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v0
    return v0''')
        return replace_method(s,'public static e(Landroid/content/Context;)Landroid/content/SharedPreferences;','''    .locals 3
    :spotx_try
    sget-object v0, Ll23;->a:Landroid/security/keystore/KeyGenParameterSpec;
    invoke-static {v0}, Ll23;->a(Landroid/security/keystore/KeyGenParameterSpec;)Ljava/lang/String;
    move-result-object v0
    sget-object v1, Ld81;->AES256_SIV:Ld81;
    sget-object v2, Le81;->AES256_GCM:Le81;
    invoke-static {v0, p0, v1, v2}, Lf81;->a(Ljava/lang/String;Landroid/content/Context;Ld81;Le81;)Lf81;
    move-result-object v0
    :spotx_end
    .catch Ljava/lang/Exception; {:spotx_try .. :spotx_end} :spotx_failure
    return-object v0
    :spotx_failure
    move-exception v0
    invoke-static {p0}, Lcom/boiled/spotx/Hardening;->unavailablePrefs(Landroid/content/Context;)Landroid/content/SharedPreferences;
    move-result-object v0
    return-object v0''')
    edit('aa4.smali',profile)
    def logging(s):
        needle='.method public static f(Ldu2;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V\n    .locals 11'
        return replace_once(s,needle,needle+'''
    invoke-static {p2}, Lcom/boiled/spotx/Privacy;->scrub(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p2
    invoke-static {p3}, Lcom/boiled/spotx/Privacy;->scrubError(Ljava/lang/Throwable;)Ljava/lang/Throwable;
    move-result-object p3
''','credential redaction')
    edit('ku2.smali',logging)
    return {'script_replacements':sum(counts.values()),'smali_files_examined':len(files)}

def patch_resources(work):
    manifest=ET.parse(work/'AndroidManifest.xml');root=manifest.getroot();root.set('package',APP_ID)
    for node in list(root):
        name=node.get(ANDROID+'name','')
        if name in ['com.google.android.gms.permission.AD_ID','android.permission.ACCESS_ADSERVICES_ATTRIBUTION','android.permission.ACCESS_ADSERVICES_AD_ID','com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE']:
            root.remove(node)
        elif name.startswith('com.project.lol.DYNAMIC_'):node.set(ANDROID+'name',name.replace('com.project.lol',APP_ID,1))
    app=root.find('application');app.set(ANDROID+'networkSecurityConfig','@xml/spotx_network_security')
    app.set(ANDROID+'usesCleartextTraffic','false');app.set(ANDROID+'allowBackup','false')
    for p in app.findall('provider'):
        value=p.get(ANDROID+'authorities','');p.set(ANDROID+'authorities',value.replace('com.project.lol',APP_ID))
    for receiver in app.findall('receiver'):
        if receiver.get(ANDROID+'name')=='androidx.media.session.MediaButtonReceiver':
            receiver.set(ANDROID+'permission','android.permission.MEDIA_CONTENT_CONTROL')
    for key,value in {'firebase_analytics_collection_deactivated':'true','firebase_performance_collection_deactivated':'true',
        'firebase_crashlytics_collection_enabled':'false','firebase_data_collection_default_enabled':'false',
        'google_analytics_adid_collection_enabled':'false','google_analytics_default_allow_ad_personalization_signals':'false'}.items():
        node=next((n for n in app.findall('meta-data') if n.get(ANDROID+'name')==key),None)
        if node is None:node=ET.SubElement(app,'meta-data',{ANDROID+'name':key})
        node.set(ANDROID+'value',value)
    queries=ET.SubElement(root,'queries')
    for package in ['com.google.android.gms','com.google.android.projection.gearhead','com.google.android.wearable.app','com.google.android.wearable.app.cn']:
        ET.SubElement(queries,'package',{ANDROID+'name':package})
    manifest.write(work/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    settings_id=settings_drawable_id(work)
    public=ET.parse(work/'res/values/public.xml')
    ET.SubElement(public.getroot(),'public',{'type':'drawable','name':'spotx_settings','id':f'0x{settings_id:08x}'})
    public.write(work/'res/values/public.xml',encoding='utf-8',xml_declaration=True)
    (work/'res/drawable/spotx_settings.xml').write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="44dp" android:height="44dp" android:viewportWidth="32" android:viewportHeight="32"><group android:translateX="4" android:translateY="4"><path android:fillColor="#00000000" android:strokeColor="#f1f4f2" android:strokeWidth="1.6" android:strokeLineJoin="round" android:pathData="M10,3 L14,3 L14.6,5.2 L16.3,6.2 L18.5,5.6 L20.5,9 L18.9,10.7 L18.9,13.3 L20.5,15 L18.5,18.4 L16.3,17.8 L14.6,18.8 L14,21 L10,21 L9.4,18.8 L7.7,17.8 L5.5,18.4 L3.5,15 L5.1,13.3 L5.1,10.7 L3.5,9 L5.5,5.6 L7.7,6.2 L9.4,5.2 Z M15.5,12 A3.5,3.5 0,1 0,8.5,12 A3.5,3.5 0,1 0,15.5,12"/></group></vector>''',encoding='utf-8')
    for path in work.glob('res/values*/strings.xml'):
        tree=ET.parse(path);changed=False
        for node in tree.getroot().findall('string'):
            name=node.get('name','');text=node.text or ''
            if 'Spotilol' in text and not any(x in name for x in ['github','credit','license','website','changelog']):
                node.text=text.replace('Spotilol','SpotX WebView');changed=True
            if name=='settings_dialog_version':node.text='SpotX WebView · %1$s';changed=True
            if name=='main_version_label':node.text='%1$s';changed=True
            if name=='splash_version_label':node.text='%1$s';changed=True
            if name in ['settings_version_format','offline_version']:node.text='v%1$s';changed=True
            if name=='settings_github_subtitle':node.text='github.com/BoiledStone/SpotX-WebView';changed=True
            french=path.parent.name=='values-fr'
            if name=='settings_discord_server':node.text='Signaler un problème' if french else 'Report an issue';changed=True
            if name=='settings_discord_subtitle':node.text='GitHub · BoiledStone/SpotX-WebView';changed=True
            if name=='settings_telemetry_subtitle':
                node.text='Collecte analytique et rapports automatiques désactivés dans cette version.' if french else 'Analytics and automatic crash reporting are disabled in this edition.';changed=True
        if changed:tree.write(path,encoding='utf-8',xml_declaration=True)
    (work/'res/xml/spotx_network_security.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<network-security-config><base-config cleartextTrafficPermitted="false"><trust-anchors><certificates src="system"/></trust-anchors></base-config></network-security-config>''',encoding='utf-8')
    vector='''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#00000000" android:strokeColor="COLOR" android:strokeWidth="7" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M32,36 L48,54 L32,72 M76,36 L60,54 L76,72"/><path android:fillColor="COLOR" android:pathData="M51,48 L61,54 L51,60 Z"/></vector>'''
    (work/'res/drawable/spotx_foreground.xml').write_text(vector.replace('COLOR','#9ce4bf'),encoding='utf-8')
    (work/'res/drawable/ic_launcher_monochrome.xml').write_text(vector.replace('COLOR','#ffffff'),encoding='utf-8')
    (work/'res/drawable/ic_launcher_background.xml').write_text('''<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#101c16"/></shape>''',encoding='utf-8')
    for name in ['ic_launcher.xml','ic_launcher_round.xml']:
        path=work/'res/mipmap-anydpi'/name
        path.write_text(path.read_text(encoding='utf-8').replace('@mipmap/ic_launcher_foreground','@drawable/spotx_foreground'),encoding='utf-8')
    yml=work/'apktool.yml';s=yml.read_text(encoding='utf-8')
    s=replace_once(s,'versionCode: 23','versionCode: '+str(VERSION_CODE),'versionCode')
    s=replace_once(s,'versionName: 1.2.4','versionName: '+VERSION,'versionName');yml.write_text(s,encoding='utf-8')
    assets=work/'assets/spotx';assets.mkdir(exist_ok=True)
    shutil.copy2(ROOT/'patches/secure-bridge.js',assets/'secure-bridge.js')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--patch-only',action='store_true')
    parser.add_argument('--resume-patched',action='store_true',help='Reuse already patched APK resources after a Java-only repair')
    parser.add_argument('--java-home',default=r'C:\Program Files\Java\jdk-21.0.10')
    parser.add_argument('--sdk',default=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
    args=parser.parse_args()
    base=ROOT/'baseline/spotilol-1.2.4-build23.apk'
    if digest(base)!=BASE_HASH:raise ValueError('Wrong baseline APK; refusing patches')
    java=Path(args.java_home)/'bin/java.exe';javac=java.with_name('javac.exe');keytool=java.with_name('keytool.exe')
    sdk=Path(args.sdk);bt=sdk/'build-tools/36.1.0';android=sdk/'platforms/android-37.0/android.jar'
    env=dict(os.environ,JAVA_HOME=args.java_home)
    work=ROOT/'build/patched'
    if args.resume_patched:
        if 'com.boiled.spotx.webview' not in (work/'AndroidManifest.xml').read_text(encoding='utf-8'):
            raise ValueError('No verified patched workspace to resume')
        stats={'reused_patched_workspace':True}
    else:
        if work.exists():
            if work.resolve().parent!=(ROOT/'build').resolve():raise ValueError('Unsafe build directory')
            shutil.rmtree(work)
        print('Decode verified installed APK',flush=True)
        run(java,'-jar',ROOT/'tools/apktool.jar','d',base,'-o',work)
        from extract_scripts import extract
        if extract(work,ROOT/'baseline/scripts')<40:raise ValueError('Missing baseline JavaScript')
        print('Apply security and experience patches',flush=True)
        stats=patch_smali(work);patch_resources(work)
    if args.patch_only:print(json.dumps(stats));return
    # R8 filled the primary dex nearly to its 65,536-method limit. Native multidex
    # is available on every supported Android version; move an intact library package.
    shard_source=work/'smali/com/google';shard_target=work/'smali_classes4/com/google'
    if shard_source.exists():
        if not shard_source.resolve().is_relative_to(work.resolve()) or not shard_target.resolve().is_relative_to(work.resolve()):
            raise ValueError('Unsafe dex shard paths')
        shard_target.parent.mkdir(parents=True,exist_ok=True)
        shutil.move(str(shard_source),str(shard_target))
    classes=ROOT/'build/java';classes.mkdir(exist_ok=True)
    sources=list((ROOT/'patches/java').rglob('*.java'))
    print('Compile independently authored Java helpers',flush=True)
    run(javac,'--release','8','-encoding','UTF-8','-classpath',android,'-d',classes,*sources)
    dex=ROOT/'build/dex';dex.mkdir(exist_ok=True)
    run(java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','28','--lib',android,'--output',dex,*classes.rglob('*.class'))
    print('Rebuild resources and original dex files',flush=True)
    unsigned=ROOT/'build/unsigned.apk'
    run(java,'-jar',ROOT/'tools/apktool.jar','b',work,'-o',unsigned)
    with zipfile.ZipFile(unsigned,'a') as z:
        dex_indices=[1 if name=='classes.dex' else int(re.fullmatch(r'classes(\d+)\.dex',name)[1])
                     for name in z.namelist() if re.fullmatch(r'classes(?:\d+)?\.dex',name)]
        extra_name=f'classes{max(dex_indices)+1}.dex'
        z.write(dex/'classes.dex',extra_name,compress_type=zipfile.ZIP_STORED)
    local=ROOT/'.local';local.mkdir(exist_ok=True)
    config=local/'signing.json';keystore=local/'spotx-webview.p12'
    if config.exists()!=keystore.exists():
        raise ValueError('Incomplete signing identity: restore both .local files; refusing to replace the update key')
    if not config.exists():
        password=secrets.token_urlsafe(32)
        config.write_text(json.dumps({'password':password}),encoding='utf-8')
    password=json.loads(config.read_text(encoding='utf-8'))['password'];env['SPOTX_WEBVIEW_KEY_PASS']=password
    if not keystore.exists():
        run(keytool,'-genkeypair','-keystore',keystore,'-storetype','PKCS12','-alias','spotx-webview',
            '-storepass:env','SPOTX_WEBVIEW_KEY_PASS','-keypass:env','SPOTX_WEBVIEW_KEY_PASS','-keyalg','RSA','-keysize','3072',
            '-validity','10000','-dname','CN=SpotX WebView Local',env=env)
    aligned=ROOT/'build/aligned.apk'
    run(bt/'zipalign.exe','-f','-P','16','4',unsigned,aligned)
    output=ROOT/'output'/f'SpotX-WebView-{VERSION}.apk';output.parent.mkdir(exist_ok=True)
    print('Sign and verify independent application',flush=True)
    run(bt/'apksigner.bat','sign','--ks',keystore,'--ks-key-alias','spotx-webview',
        '--ks-pass','env:SPOTX_WEBVIEW_KEY_PASS','--key-pass','env:SPOTX_WEBVIEW_KEY_PASS','--out',output,aligned,env=env)
    verification=run(bt/'apksigner.bat','verify','--verbose','--print-certs',output,env=env)
    run(bt/'zipalign.exe','-c','-P','16','4',output)
    (ROOT/'validation').mkdir(exist_ok=True)
    (ROOT/'validation/signature.txt').write_text(verification,encoding='utf-8')
    report={'baseline_sha256':BASE_HASH,'application_id':APP_ID,'version':VERSION,'version_code':VERSION_CODE,'apk_sha256':digest(output),
        'apk_bytes':output.stat().st_size,'patch_stats':stats,'signature_verified':True,'alignment_16k_verified':True,
        'date':datetime.now(timezone.utc).date().isoformat(),'sources':'installed APK + independent Java/JavaScript patches'}
    (ROOT/'output/build-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2),flush=True)

if __name__=='__main__':main()
