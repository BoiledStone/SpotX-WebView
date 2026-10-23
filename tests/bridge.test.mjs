import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source=fs.readFileSync(new URL('../patches/secure-bridge.js',import.meta.url),'utf8');
let assertions=0;
function check(condition){assert.ok(condition);assertions++;}
function harness({frame=false,native=true}={}){
    const sent=[],timers=new Map(),events={};let timerId=0;
    const window={addEventListener:(name,fn)=>events[name]=fn};window.top=frame?{}:window;
    if(native)window.SpotXNative={postMessage:msg=>sent.push(JSON.parse(msg))};
    const ctx=vm.createContext({window,document:{visibilityState:'visible'},Map,Promise,JSON,Array,Error,
        setTimeout:fn=>{const id=++timerId;timers.set(id,()=>{timers.delete(id);fn()});return id},clearTimeout:id=>timers.delete(id)});
    vm.runInContext(source,ctx);
    return {window,sent,timers,events};
}
const h=harness();check(typeof h.window.AndBridge==='object');check(h.window.AndBridge.isWoke());
check(!harness({frame:true}).window.AndBridge);check(!harness({native:false}).window.AndBridge);
h.window.AndBridge.recMediaStatus('{"track":"Example"}');check(h.sent.at(-1).method==='recMediaStatus');
const fetch=h.window.AndBridge.nFetch('https://api.spotify.com/test','{}');let last=h.sent.at(-1);
h.window.SpotXNative.onmessage({data:JSON.stringify({id:last.id,result:'ok'})});check(await fetch==='ok');check(h.timers.size===0);
const login=h.window.AndBridge.loginDetected();last=h.sent.at(-1);
check(last.method==='loginDetected' && last.id>0);
h.window.SpotXNative.onmessage({data:JSON.stringify({id:last.id,result:true})});check(await login===true);
const reload=h.window.AndBridge.reloadPlayer();last=h.sent.at(-1);
check(last.method==='reloadPlayer' && last.args.length===0);
h.window.SpotXNative.onmessage({data:JSON.stringify({id:last.id,result:false})});check(await reload===false);
const timeout=h.window.AndBridge.nFetch('x','{}');const timeoutCheck=assert.rejects(timeout,/timed out/);
[...h.timers.values()][0]();await timeoutCheck;assertions++;
const closing=h.window.AndBridge.spicyLyrics('id','title','artist',0);const closeCheck=assert.rejects(closing,/Page closed/);
h.events.pagehide();await closeCheck;assertions++;check(h.timers.size===0);
const flood=harness();const pending=[];for(let i=0;i<12;i++)pending.push(flood.window.AndBridge.nFetch('x','{}'));
await assert.rejects(flood.window.AndBridge.nFetch('x','{}'),/Too many/);assertions++;
const outcomes=Promise.allSettled(pending);flood.events.pagehide();await outcomes;
console.log(`PASS ${assertions} bridge protocol assertions`);
