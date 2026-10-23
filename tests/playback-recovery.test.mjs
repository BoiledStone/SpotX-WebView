import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source=fs.readFileSync(new URL('../patches/playback-recovery.js',import.meta.url),'utf8');
const key='spotx.playback-recovery.v1',track='spotify:track:0123456789abcdefghijkl';
let scenarios=0;
function harness({storage=new Map(),playStarts=false}={}){
    let now=1000000,next=0;const timers=new Map(),events={};
    const range={value:'72000',max:'192000'};
    const state={playing:false,remote:false,reloads:0,playCalls:0,seeks:[],from:[]};
    const c={console,Promise,Number,JSON,Array,String,Date:class extends Date{static now(){return now;}},
        setTimeout(fn,delay){const id=++next;timers.set(id,{at:now+delay,fn});return id;},clearTimeout(id){timers.delete(id);},
        navigator:{onLine:true},sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
        document:{hidden:false,querySelector:selector=>selector.includes('playback-progressbar')?range:null,addEventListener:(name,fn)=>events[name]=fn},
        addEventListener:(name,fn)=>events[name]=fn,ffDone:true,spotDevId:'fixture',spotAuthToken:'test-only',
        splPlayingTrackId:()=>track.split(':')[2],splIsPlaying:()=>state.playing,__splRemoteActive:()=>state.remote,
        splEnsurePb:()=>({}),actPlayPause:play=>{state.playCalls++;if(playStarts)state.playing=play;},
        actSkipForward(){},actSkipBack(){},playFromUri:(...args)=>state.from.push(args),actSeek:pos=>state.seeks.push(pos),
        AndBridge:{reloadPlayer:()=>{state.reloads++;return Promise.resolve(true);}}};
    c.window=c;vm.createContext(c);vm.runInContext(source,c);
    function advance(ms){const end=now+ms;let count=0;while(true){let chosen=null;for(const [id,t]of timers)if(t.at<=end&&(!chosen||t.at<chosen[1].at))chosen=[id,t];if(!chosen)break;if(++count>20000)throw Error('Timer loop');now=chosen[1].at;timers.delete(chosen[0]);chosen[1].fn();}now=end;}
    function background(ms=120001){c.spotxPlaybackRecovery.background();advance(ms);c.spotxPlaybackRecovery.resume();}
    return {c,state,range,events,storage,advance,background};
}
{
    const h=harness();h.c.actPlayPause(true);h.advance(20000);assert.equal(h.state.reloads,0);scenarios++;
    h.background(30000);h.c.actPlayPause(true);h.advance(20000);assert.equal(h.state.reloads,0);scenarios++;
}
{
    const h=harness();h.background();h.advance(20000);assert.equal(h.state.reloads,0);scenarios++;
    h.c.actPlayPause(true);h.advance(5000);assert.equal(h.state.reloads,0);h.advance(1000);assert.equal(h.state.reloads,1);
    const saved=JSON.parse(h.storage.get(key));assert.equal(saved.uri,track);assert.equal(saved.position,72000);scenarios++;
    h.c.actPlayPause(true);h.advance(15000);assert.equal(h.state.reloads,1);scenarios++;
    h.background();h.c.actPlayPause(true);h.advance(15000);assert.equal(h.state.reloads,2);
    h.background();h.c.actPlayPause(true);h.advance(15000);assert.equal(h.state.reloads,2);scenarios++;
}
{
    const h=harness();h.background();h.c.actPlayPause(true);h.state.playing=true;
    for(let i=0;i<16;i++){h.range.value=String(Number(h.range.value)+1000);h.advance(1000);}
    assert.equal(h.state.reloads,0);scenarios++;
}
{
    const h=harness();h.background();h.c.actPlayPause(true);h.advance(4000);h.c.actPlayPause(false);h.advance(10000);assert.equal(h.state.reloads,0);scenarios++;
}
{
    const h=harness();h.background();h.c.navigator.onLine=false;h.c.actPlayPause(true);h.advance(15000);assert.equal(h.state.reloads,0);
    h.c.navigator.onLine=true;h.events.online();h.advance(5000);assert.equal(h.state.reloads,0);h.advance(1000);assert.equal(h.state.reloads,1);scenarios++;
}
{
    const h=harness();h.background();h.state.remote=true;h.c.actPlayPause(true);h.advance(20000);assert.equal(h.state.reloads,0);scenarios++;
}
{
    const h=harness();h.background();h.c.actPlayPause(true);h.advance(4000);h.c.actPlayPause(true);h.advance(2000);assert.equal(h.state.reloads,1);scenarios++;
}
{
    const storage=new Map([[key,JSON.stringify({createdAt:1000000,uri:track,context:'',position:72000})]]);
    const h=harness({storage,playStarts:true});h.c.ffDone=false;h.advance(3000);assert.equal(h.state.playCalls,0);
    h.c.ffDone=true;h.advance(2000);assert.equal(h.state.playCalls,1);assert.equal(storage.has(key),false);
    h.range.value='0';h.advance(500);assert.deepEqual(h.state.seeks,[72000]);assert.equal(h.state.reloads,0);scenarios++;
}
{
    const storage=new Map([[key,JSON.stringify({createdAt:1000000,uri:track,context:'',position:72000})]]);
    const h=harness({storage});h.c.actPlayPause(false);h.advance(6000);assert.equal(h.state.playCalls,1);assert.equal(storage.has(key),false);scenarios++;
}
{
    for(const record of [{createdAt:1,uri:track,position:72000},{createdAt:1000000,uri:'javascript:alert(1)',position:0}]){
        const h=harness({storage:new Map([[key,JSON.stringify(record)]])});h.advance(6000);assert.equal(h.state.playCalls,0);assert.equal(h.storage.has(key),false);
    }scenarios++;
}
{
    const h=harness();h.background();h.c.playFromUri('spotify:track:abcdefghijkl0123456789','spotify:playlist:0123456789abcdefghijkl');h.advance(12000);
    const record=JSON.parse(h.storage.get(key));assert.equal(record.uri,'spotify:track:abcdefghijkl0123456789');assert.equal(record.context,'spotify:playlist:0123456789abcdefghijkl');assert.equal(record.position,0);scenarios++;
}
{
    const h=harness();delete h.c.splPlayingTrackId;
    h.c.document.querySelector=selector=>selector.includes('playback-progressbar')?h.range:{getAttribute:()=>'/track/0123456789abcdefghijkl'};
    h.background();h.c.actPlayPause(true);h.advance(12000);assert.equal(JSON.parse(h.storage.get(key)).uri,track);scenarios++;
}
await Promise.resolve();
console.log(`PASS ${scenarios} playback recovery scenarios: background, stall, pause, progress, network, remote, budget and resume`);
