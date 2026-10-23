(function(){
    'use strict';
    function abortError(signal){return signal.reason || new DOMException('Request aborted','AbortError');}
    function untilAbort(promise,signal){
        return new Promise(function(resolve,reject){
            if(signal.aborted){reject(abortError(signal));return;}
            function aborted(){signal.removeEventListener('abort',aborted);reject(abortError(signal));}
            signal.addEventListener('abort',aborted,{once:true});
            promise.then(function(value){signal.removeEventListener('abort',aborted);resolve(value);},function(error){signal.removeEventListener('abort',aborted);reject(error);});
        });
    }
    function decorate(response,url,redirected){
        Object.defineProperty(response,'url',{value:url});
        Object.defineProperty(response,'redirected',{value:redirected});
        var clone=response.clone.bind(response);
        response.clone=function(){return decorate(clone(),url,redirected);};
        return response;
    }
    window.mngFetch=async function(input,options){
        var source=input instanceof Request?input.clone():new URL(String(input),location.href).href;
        var request=new Request(source,options);
        if(request.signal.aborted)throw abortError(request.signal);
        var headers={};request.headers.forEach(function(value,key){headers[key]=value;});
        var body=request.method==='GET'||request.method==='HEAD'?null:await untilAbort(request.text(),request.signal);
        var observation=window.spotxConnect?window.spotxConnect.describe(request.url,request.method,body):null;
        if(request.signal.aborted)throw abortError(request.signal);
        var raw=await untilAbort(AndBridge.nFetch(request.url,JSON.stringify({method:request.method,headers:headers,body:body})),request.signal);
        var data;
        try{data=JSON.parse(raw);}catch(_){throw new TypeError('Invalid native response');}
        if(!data||data.status===0)throw new TypeError('Native network request unavailable');
        var empty=request.method==='HEAD'||[204,205,304].includes(data.status);
        var response=new Response(empty?null:(data.body||''),{status:data.status,headers:data.headers||{}});
        if(window.spotxConnect)window.spotxConnect.response(observation,Promise.resolve(response));
        var url=data.url||request.url;
        return decorate(response,url,url!==request.url);
    };
})();
