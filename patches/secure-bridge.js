(function () {
    'use strict';
    if (window.top !== window || !window.SpotXNative || window.AndBridge) return;
    var pending = new Map(), nextId = 0, transport = window.SpotXNative;
    var api = {isWoke: function () { return document.visibilityState === 'visible'; }};
    transport.onmessage = function (event) {
        var message;
        try { message = JSON.parse(event.data); } catch (_) { return; }
        var request = pending.get(message.id);
        if (!request) return;
        pending.delete(message.id); clearTimeout(request.timer);
        if (message.error) request.reject(new Error(message.error));
        else request.resolve(message.result);
    };
    function request(method, args) {
        if (pending.size >= 12) return Promise.reject(new Error('Too many pending native requests'));
        return new Promise(function (resolve, reject) {
            var id = ++nextId;
            var timer = setTimeout(function () {
                pending.delete(id); reject(new Error('Native request timed out'));
            }, 30000);
            pending.set(id, {resolve: resolve, reject: reject, timer: timer});
            try { transport.postMessage(JSON.stringify({id: id, method: method, args: args})); }
            catch (e) { clearTimeout(timer); pending.delete(id); reject(e); }
        });
    }
    ['deferMessage','wakeUp','wakeOff','cssInjected','clearDebugLog','recAdContentIds',
     'playLoaded','recMediaPosition','recMediaStatus','onMediaItemsLoaded','onSearchCompleted',
     'manageTShut','manageTSleep','recAccountName','openTimerDialog','openVolumeDialog',
     'enterPip','enterPipVideo','enterLyricsPip','enterVideoFullscreen','exitVideoFullscreen',
     'playerExpanded','wideVideo','ctxMenuState','downloadTrack','downloadCollection',
     'skipDownload','cancelDownload'].forEach(function (method) {
        api[method] = function () { transport.postMessage(JSON.stringify({method: method, args: Array.from(arguments)})); };
    });
    api.dbg = function () {};
    api.nFetch = function (url, options) { return request('nFetch', [url, options]); };
    api.spicyLyrics = function (id, title, artist, duration) { return request('spicyLyrics', [id, title, artist, duration]); };
    api.loginDetected = function () { return request('loginDetected', []); };
    api.reloadPlayer = function () { return request('reloadPlayer', []); };
    window.addEventListener('pagehide', function () {
        pending.forEach(function (p) { clearTimeout(p.timer); p.reject(new Error('Page closed')); });
        pending.clear();
    });
    window.AndBridge = api;
})();
