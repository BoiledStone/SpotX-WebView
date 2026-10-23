import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const read=name=>fs.readFileSync(new URL('../'+name,import.meta.url),'utf8');
const timers=[],sockets=[],sent=[];
class Socket extends EventTarget{constructor(url){super();this.url=url;sockets.push(this);}message(data){this.dispatchEvent(new MessageEvent('message',{data:JSON.stringify(data)}));}}
const c={Request,Response,Headers,URL,JSON,Promise,Array,Object,Number,Date,EventTarget,MessageEvent,WebSocket:Socket,
    location:{href:'https://open.spotify.com/',hostname:'open.spotify.com',pathname:'/'},navigator:{onLine:true},
    document:{querySelector:()=>null,addEventListener(){}},addEventListener(){},setTimeout:()=>1,clearTimeout(){},
    setInterval:fn=>{timers.push(fn);return timers.length;},clearInterval(){},firstPlay:true,afint:null,reqPause:false,ulFlag:false,
    spotAuthToken:'Bearer test-only',spotCliToken:'test-only',featVer:'fixture',autoPlayMode:'permanent',
    splIsPlaying:()=>false,closeNowPlay(){},pBtn:{click(){throw Error('Unexpected automatic click');}},
    AndBridge:{nFetch:async(url,options)=>{sent.push({url,options:JSON.parse(options)});return JSON.stringify({status:204,headers:{},body:''});}}};
c.window=c;vm.createContext(c);vm.runInContext(read('patches/connect-state.js'),c);
const connect=c.spotxConnect;
const reg=()=>connect.describe('https://gue1-spclient.spotify.com/track-playback/v1/devices','POST',JSON.stringify({device:{device_id:'local-player'}}));
reg();assert.equal(connect.local(),'local-player');assert.equal(connect.canAutoPlay(),false,'unknown active device never autoplays');
const socket=new c.WebSocket('wss://gue1-dealer.spotify.com/');
assert.ok(socket instanceof Socket);socket.message({payloads:[{cluster:{active_device_id:'living-room'}}]});
assert.equal(connect.isRemote(),true,'incoming server event recognizes a remote device without an outgoing transfer');
reg();assert.equal(connect.target(),'living-room','registration cannot take over the active device');assert.equal(c.spotDevId,'local-player');
vm.runInContext(read('patches/auto-features.js'),c);c.addAutoFeatures();timers.at(-1)();assert.equal(c.firstPlay,true,'remote playback blocks initial and periodic autoplay');
const transfer=connect.describe('https://gue1-spclient.spotify.com/connect-state/v1/player/transfer/from/local-player/to/local-player','POST','{}');
await connect.response(transfer,Promise.resolve(new Response(null,{status:403})));assert.equal(connect.target(),'living-room','failed transfer cannot change the active device');
await connect.response(transfer,Promise.resolve(new Response(null,{status:204})));assert.equal(connect.canAutoPlay(),true,'accepted explicit selection returns playback to the local device');
const older=connect.describe('https://gue1-spclient.spotify.com/connect-state/v1/player/command/from/local-player/to/local-player','POST','{}');
socket.message({payloads:[{active_device_id:'desktop'}]});
await connect.response(older,Promise.resolve(new Response(null,{status:204})));assert.equal(connect.target(),'desktop','late local response cannot override newer remote state');
const oldRead=connect.describe('https://gue1-spclient.spotify.com/connect-state/v1/devices','GET',null);
socket.message({active_device_id:'new-speaker'});
await connect.response(oldRead,Promise.resolve(new Response(JSON.stringify({active_device_id:'local-player'}),{status:200})));
assert.equal(connect.target(),'new-speaker','late response bodies cannot override newer server state');
socket.message({active_device_id:'desktop'});
await connect.response(connect.describe('https://gue1-spclient.spotify.com/track-playback/v1/devices/local-player/state','PUT','{}'),Promise.resolve(new Response(JSON.stringify({device:{device_id:'local-player'}}),{status:200})));
assert.equal(connect.target(),'desktop','local state acknowledgements do not identify the active device');
const repeatedRead=connect.describe('https://gue1-spclient.spotify.com/connect-state/v1/devices','GET',null);
socket.message({payloads:[{cluster:{active_device_id:'desktop'}}]});
await connect.response(repeatedRead,Promise.resolve(new Response(JSON.stringify({active_device_id:'local-player'}),{status:200})));
assert.equal(connect.target(),'desktop','a repeated server confirmation defeats an older conflicting HTTP snapshot');
assert.equal(connect.canAutoPlay(),false,'an older snapshot cannot re-enable local autoplay after a remote confirmation');
vm.runInContext(read('patches/native-fetch.js'),c);
vm.runInContext(read('build/scripts/PlaybackControls.CONTENT.js').split('(function () {')[0],c);
c.playFromUri('spotify:track:0123456789abcdefghijkl');
await new Promise(resolve=>setTimeout(resolve,10));
assert.match(sent.at(-1).url,/\/from\/local-player\/to\/desktop$/,'custom track selection targets the remote device while retaining the local sender');
assert.equal(c.spotDevId,'local-player');
const external=new c.WebSocket('wss://example.invalid/');external.message({active_device_id:'spoofed'});assert.equal(connect.target(),'desktop','unrelated sockets do not supply Connect state');
console.log('PASS Connect handoff, stable local ID, remote commands, no autoplay takeover and late-response ordering');
