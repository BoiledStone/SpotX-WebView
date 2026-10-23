import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{chromium}=require('../tools/test-runtime/node_modules/playwright-core');
const browser=await chromium.launch({executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',headless:true});
try{
    const page=await browser.newPage({viewport:{width:384,height:800},locale:'fr-CA',reducedMotion:'reduce'});
    await page.route('https://i.scdn.co/**',r=>r.abort());
    await page.goto(new URL('../validation/player-fixture.html',import.meta.url).href);
    await page.waitForFunction(()=>document.getElementById('spl-track')?.textContent==='Evening Light');
    const stale=await page.evaluate(()=>{
        const artist=document.querySelector('[data-testid="context-item-info-artist"]'),saved=artist.textContent;artist.textContent='';
        navigator.mediaSession.metadata=new MediaMetadata({title:'Previous song',artist:'Wrong artist',artwork:[{src:'https://i.scdn.co/image/previous'}]});
        const value=spotxNowPlaying();artist.textContent=saved;navigator.mediaSession.metadata=null;return value;
    });
    assert.equal(stale.artist,'','old MediaSession artist cannot attach to a new DOM title');
    assert.equal(stale.cover,'','old MediaSession artwork cannot attach to a new DOM title');
    const mutations=await page.evaluate(()=>{
        const p=document.getElementById('spotilolPlayerControls'),cover=document.getElementById('spl-cover-img'),title=document.getElementById('spl-track'),artist=document.getElementById('spl-artist');
        spotxUpdateMetadata(cover,title,artist,p);spotxAccessibility(p,false);
        const observer=new MutationObserver(()=>{});observer.observe(p,{subtree:true,childList:true,attributes:true,characterData:true});
        for(let i=0;i<30;i++){spotxUpdateMetadata(cover,title,artist,p);spotxAccessibility(p,false);}
        const count=observer.takeRecords().length;observer.disconnect();return count;
    });
    assert.equal(mutations,0,'unchanged metadata and accessibility must not rewrite the DOM');
    const fullUpdates=await page.evaluate(()=>{
        splUpdate();const observer=new MutationObserver(()=>{});observer.observe(document.getElementById('spotilolPlayerControls'),{subtree:true,childList:true,attributes:true,characterData:true});
        for(let i=0;i<30;i++)splUpdate();const records=observer.takeRecords();observer.disconnect();return records.length;
    });
    assert.equal(fullUpdates,0,'stable full updates must not recreate SVG icons, labels or empty-state classes');
    const artwork=await page.evaluate(()=>{
        const p=document.getElementById('spotilolPlayerControls'),c=document.getElementById('spl-cover-img'),t=document.getElementById('spl-track'),a=document.getElementById('spl-artist');
        const root=document.querySelector('[data-testid="now-playing-widget"]');
        root.insertAdjacentHTML('beforeend','<img src="https://i.scdn.co/image/missing" data-testid="cover-art-image">');
        let attempts=0;Object.defineProperty(c,'src',{configurable:true,set(){attempts++}});
        const originalNow=Date.now,base=originalNow();let now=base;Date.now=()=>now;
        const update=()=>spotxUpdateMetadata(c,t,a,p);
        update();c.onerror();for(let i=0;i<50;i++)update();const burst=attempts;
        now=base+2000;update();c.onerror();now=base+10000;update();c.onerror();
        now=base+3600000;for(let i=0;i<50;i++)update();const bounded=attempts;
        window.dispatchEvent(new Event('online'));update();const online=attempts;
        Date.now=originalNow;return {burst,bounded,online};
    });
    assert.equal(artwork.burst,1,'failed artwork must not be retried on every render tick');
    assert.equal(artwork.bounded,3,'artwork attempts must stop after three failures');
    assert.equal(artwork.online,4,'network recovery permits one new attempt');
    const fs=await import('node:fs');fs.writeFileSync(new URL('../validation/optimization-metrics.json',import.meta.url),JSON.stringify({unchangedUpdates:30,metadataMutations:mutations,fullPlayerMutations:fullUpdates,artwork,staleMediaSessionRejected:true},null,2));
    console.log('PASS zero redundant DOM writes, bounded artwork retries and recovery after online');
}finally{await browser.close();}
