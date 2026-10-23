import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
const {chromium}=require('../tools/test-runtime/node_modules/playwright-core');
const browser=await chromium.launch({executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',headless:true});
const results=[];
try{
    for(const size of [{width:320,height:740},{width:360,height:800},{width:412,height:915},{width:740,height:360}]){
        const context=await browser.newContext({viewport:size,deviceScaleFactor:1,isMobile:true,hasTouch:true,locale:'fr-CA',reducedMotion:'reduce'});
        await context.route('https://i.scdn.co/image/spotx-fixture',route=>route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>'}));
        const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
        await page.goto(new URL('../validation/player-fixture.html',import.meta.url).href);
        await page.waitForFunction(()=>document.getElementById('spl-track')?.textContent==='Evening Light');
        await page.waitForTimeout(500);
        const metrics=await page.evaluate(()=>{
            const p=document.getElementById('spotilolPlayerControls'),s=getComputedStyle(p),r=p.getBoundingClientRect();
            return {viewport:innerWidth,left:r.left,right:r.right,playerHeight:r.height,opacity:s.opacity,background:s.backgroundColor,
                classes:p.className,titleColor:getComputedStyle(document.getElementById('spl-track')).color,
                targets:[...p.querySelectorAll('button.spl-btn')].map(b=>({id:b.id,width:b.getBoundingClientRect().width,height:b.getBoundingClientRect().height})).filter(b=>b.width>0),
                playLabel:document.getElementById('spl-play').getAttribute('aria-label'),fixtureErrors:window.fixtureErrors};
        });
        assert.equal(errors.length,0,errors.join('\n'));assert.equal(metrics.fixtureErrors.length,0,metrics.fixtureErrors.join('\n'));
        assert.ok(metrics.left>=0&&metrics.right<=size.width+1,JSON.stringify(metrics));
        assert.ok(metrics.playerHeight<=170,'compact player must stay close to the original height: '+metrics.playerHeight);
        assert.ok(metrics.right-metrics.left<=561,'landscape player must keep a bounded width');
        assert.ok(metrics.targets.every(t=>t.width>=33.5&&t.height>=33.5),JSON.stringify(metrics.targets));
        assert.ok(metrics.targets.filter(t=>/^spl-(play|prev|next)/.test(t.id)).every(t=>t.width>=43.5&&t.height>=43.5),'primary controls remain 44px');
        assert.equal(metrics.playLabel,'Lecture');assert.equal(metrics.opacity,'1');
        const drag=await page.evaluate(()=>{
            const button=document.getElementById('spl-play'),player=document.getElementById('spotilolPlayerControls');
            const touch=y=>new Touch({identifier:1,target:button,clientX:180,clientY:y});
            button.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,touches:[touch(500)]}));
            button.dispatchEvent(new TouchEvent('touchmove',{bubbles:true,touches:[touch(590)]}));
            button.dispatchEvent(new TouchEvent('touchend',{bubbles:true,changedTouches:[touch(590)],touches:[]}));
            return player.classList.contains('spl-mini');
        });
        assert.equal(drag,false,'a gesture starting on a control must not collapse the player');
        await page.waitForFunction(()=>!window.__splBusy);
        await page.evaluate(()=>{
            document.querySelector('[data-testid="context-item-info-title"]').textContent='<img src=x onerror="alert(1)">';
            splUpdate();
        });
        assert.equal(await page.locator('#spl-track img').count(),0,'metadata must remain text');
        assert.equal(await page.locator('#spl-track').textContent(),'<img src=x onerror="alert(1)">');
        await page.evaluate(()=>{document.querySelector('[data-testid="context-item-info-title"]').textContent='Evening Light';splUpdate();});
        const v1=await page.locator('[data-testid="now-playing-widget"]').evaluate(e=>e.outerHTML);
        await page.locator('[data-testid="now-playing-widget"]').evaluate(e=>{
            e.dataset.testid='now-playing-widget-v2';
            e.innerHTML='<a href="/intl-fr/album/0123456789abcdefghijkl"><span>Evening Light</span></a><span data-testid="now-playing-widget-v2-byline"><a href="/intl-fr/artist/0123456789abcdefghijkl">Luna &amp; The Coast</a>, Guest</span>';
            splUpdate();
        });
        assert.equal(await page.locator('#spl-track').textContent(),'Evening Light','v2 album link contains the playing title');
        assert.equal(await page.locator('#spl-artist').textContent(),'Luna & The Coast, Guest','v2 byline contains all artists');
        await page.locator('[data-testid="now-playing-widget-v2"]').evaluate((e,html)=>{e.outerHTML=html;splUpdate();},v1);
        await page.screenshot({path:fileURLToPath(new URL(`../validation/player-${size.width}x${size.height}.png`,import.meta.url))});
        const imageOK=await page.locator('#spl-cover-img').evaluate(img=>img.hidden&&!img.hasAttribute('src'));
        assert.ok(imageOK,'missing artwork must not request an empty URL');
        await page.evaluate(()=>{
            const root=document.querySelector('[data-testid="now-playing-widget"]');
            root.insertAdjacentHTML('beforeend','<img data-testid="cover-art-image" src="https://i.scdn.co/image/spotx-fixture">');
            splUpdate();
            root.querySelector('img').remove();splUpdate();
            root.insertAdjacentHTML('beforeend','<img data-testid="cover-art-image" src="https://i.scdn.co/image/spotx-fixture">');
            splUpdate();
        });
        assert.equal(await page.locator('#spl-cover-img').getAttribute('src'),'https://i.scdn.co/image/spotx-fixture','same artwork returns after empty state');
        await page.evaluate(()=>{document.querySelector('[data-testid="now-playing-widget"] img').remove();splUpdate();splSetMini(true);});
        const mini=await page.locator('#spotilolPlayerControls').evaluate(e=>({height:e.getBoundingClientRect().height,row:getComputedStyle(e.querySelector('.spl-row2')).display}));
        assert.ok(mini.height<=64,'mini player remains a single compact bar');assert.equal(mini.row,'none');
        await page.screenshot({path:fileURLToPath(new URL(`../validation/player-mini-${size.width}x${size.height}.png`,import.meta.url))});
        await page.evaluate(()=>{window.__splFullPlayer=true;splSetMini(false);splUpdate();});
        await page.waitForFunction(()=>document.getElementById('spotilolPlayerControls').getBoundingClientRect().top<=1);
        const full=await page.locator('#spotilolPlayerControls').evaluate(e=>({height:e.getBoundingClientRect().height,width:e.getBoundingClientRect().width,title:e.querySelector('#spl-track').getBoundingClientRect().toJSON(),transport:e.querySelector('.spl-transport').getBoundingClientRect().toJSON()}));
        assert.ok(full.height<=size.height+1&&full.width<=size.width+1,'fullscreen player fits viewport');
        assert.ok(full.title.top>=0&&full.title.bottom<=size.height,'fullscreen metadata remains visible');
        assert.ok(full.transport.top>=0&&full.transport.bottom<=size.height,'fullscreen transport remains visible');
        await page.screenshot({path:fileURLToPath(new URL(`../validation/player-full-${size.width}x${size.height}.png`,import.meta.url))});
        await page.evaluate(()=>{
            const sidebar=document.createElement('aside');sidebar.id='Desktop_LeftSidebar_Id';
            sidebar.innerHTML='<nav><div><div class="a b c"><header><div><div><h1>Bibliothèque</h1></div></div></header></div><div style="height:1000px">Liste</div></div></nav>';
            document.body.appendChild(sidebar);switchLs();
        });
        assert.equal(await page.locator('#Desktop_LeftSidebar_Id').evaluate(e=>getComputedStyle(e).overflow),'hidden','collapsed library must clip its overflowing contents');
        await page.evaluate(()=>{document.querySelector('#Desktop_LeftSidebar_Id nav>div>div').className='a b';switchLs();});
        assert.equal(await page.locator('#Desktop_LeftSidebar_Id').getAttribute('data-spotx-library'),'open');
        results.push({...size,...metrics,mini,full});await context.close();
    }
    const columnsContext=await browser.newContext({locale:'fr-CA'});
    const columnsPage=await columnsContext.newPage();
    await columnsContext.route('https://open.spotify.com/**',r=>r.fulfill({contentType:'text/html',body:'<html lang="fr"><body><div data-testid="entity-header"><h1>Album</h1></div><div data-testid="playlist-tracklist"><button data-skip-in-keyboard-nav onclick="window.columnClicks=(window.columnClicks||0)+1">Options</button></div></body></html>'}));
    await columnsPage.goto('https://open.spotify.com/intl-fr/album/0123456789abcdefghijkl');
    await columnsPage.evaluate(fs.readFileSync(new URL('../patches/experience.js',import.meta.url),'utf8'));
    await columnsPage.evaluate(fs.readFileSync(new URL('../build/scripts/ColumnsButton.CONTENT.js',import.meta.url),'utf8'));
    assert.equal(await columnsPage.locator('#spl-cols-btn').textContent(),'Colonnes','localized album paths display the French columns button');
    await columnsPage.locator('#spl-cols-btn').click();
    assert.equal(await columnsPage.evaluate(()=>window.columnClicks),1,'columns button opens the original menu once');
    await columnsContext.close();
    fs.writeFileSync(new URL('../validation/experience-browser.json',import.meta.url),JSON.stringify({status:'PASS',scenarios:results},null,2));
    console.log(`PASS ${results.length} viewports, compact/mini/full modes, v1/v2 widgets, metadata escaping, artwork restoration and primary touch targets`);
}finally{await browser.close();}
