import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{chromium}=require('../tools/test-runtime/node_modules/playwright-core');
const source=fs.readFileSync(new URL('../patches/modal-visibility.js',import.meta.url),'utf8');
const browser=await chromium.launch({executablePath:'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',headless:true});
try{
    const page=await browser.newPage({viewport:{width:384,height:800}});
    await page.route('https://open.spotify.com/**',route=>route.fulfill({contentType:'text/html',body:'<html><head></head><body><div id="spotilolPlayerControls" style="position:fixed;bottom:0;height:163px;width:100%;z-index:2147483647;background:black"></div><div id="onetrust-banner-sdk" style="position:fixed;bottom:0;height:180px;width:100%;background:white"><button id="consent-close">X</button><p>Cookies and terms of use</p><button id="accept">Accept</button></div><script>window.accepted=0;document.getElementById("accept").onclick=()=>{accepted++;document.getElementById("onetrust-banner-sdk").hidden=true}</script></body></html>'}));
    await page.goto('https://open.spotify.com/');await page.addScriptTag({content:source});
    assert.equal(await page.locator('#spotilolPlayerControls').isVisible(),false,'player cannot cover consent');
    assert.equal(await page.evaluate(()=>accepted),0,'consent is never automatically accepted or dismissed');
    await page.locator('#accept').click();await page.waitForFunction(()=>document.documentElement.dataset.spotxModal==='false');
    assert.equal(await page.locator('#spotilolPlayerControls').isVisible(),true,'player returns after a user choice');
    await page.evaluate(()=>{const d=document.createElement('div');d.id='terms';d.role='dialog';d.ariaModal='true';d.textContent='Conditions';document.body.append(d)});
    await page.waitForFunction(()=>document.documentElement.dataset.spotxModal==='true');assert.equal(await page.locator('#spotilolPlayerControls').isVisible(),false);
    await page.evaluate(()=>document.getElementById('terms').remove());await page.waitForFunction(()=>document.documentElement.dataset.spotxModal==='false');
    assert.equal(await page.locator('#spotilolPlayerControls').isVisible(),true);
    await page.goto('https://open.spotify.com/login');await page.addScriptTag({content:source});assert.equal(await page.locator('#spotilolPlayerControls').isVisible(),false,'login route cannot expose player over login controls');
    console.log('PASS consent stays visible and user-controlled; player restores after dismissal and stays hidden during login');
}finally{await browser.close();}
