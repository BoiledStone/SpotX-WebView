(function () {
    'use strict';
    if (window.spotxConnect) return;
    var local = null, active = null, revision = 0;
    function id(value) { return typeof value === 'string' && /^[A-Za-z0-9_-]{1,160}$/.test(value) ? value : null; }
    function setLocal(value) { value = id(value); if (value) { local = value; window.__splLocalDevId = value; window.spotDevId = value; } }
    function setActive(value) {
        // A server observation is newer even when it confirms the current device.
        // Otherwise an older HTTP snapshot can overwrite a more recent push.
        value = id(value); revision++;
        active = value; window.__splActiveDevId = value;
    }
    // Inspect only explicit active-device fields, never arbitrary device_id acknowledgements.
    function state(value) {
        var queue = [value], seen = 0;
        while (queue.length && seen++ < 200) {
            var item = queue.shift();
            if (!item || typeof item !== 'object') continue;
            for (var key of ['active_device_id', 'activeDeviceId']) {
                if (Object.prototype.hasOwnProperty.call(item, key) && (id(item[key]) || item[key] === '' || item[key] === null)) {
                    setActive(item[key]); return;
                }
            }
            if (item.is_active === true && id(item.id || item.device_id)) { setActive(item.id || item.device_id); return; }
            if (item.devices && typeof item.devices === 'object') {
                for (var device of Object.values(item.devices)) {
                    if (device && device.is_active === true && id(device.id || device.device_id)) { setActive(device.id || device.device_id); return; }
                }
            }
            for (var child of Object.values(item)) if (child && typeof child === 'object' && queue.length < 200) queue.push(child);
        }
    }
    function json(text) {
        if (typeof text !== 'string' || text.length > 1048576) return;
        try { state(JSON.parse(text)); } catch (_) {}
    }
    function describe(url, method, body) {
        var u;
        try { u = new URL(url, location.href); } catch (_) { return null; }
        if (!/(^|\.)spotify\.com$/.test(u.hostname) || !['https:', 'wss:'].includes(u.protocol)) return null;
        var path = u.pathname, register = /\/track-playback\/v1\/devices\/?$/.test(path);
        var command = path.match(/\/connect-state\/v1\/player\/(command|transfer)\/from\/([A-Za-z0-9_-]+)\/to\/([A-Za-z0-9_-]+)/);
        if (!register && !command && !/connect-state|\/track-playback\/v1\/devices\/|\/me\/player/.test(path)) return null;
        if (register && method === 'POST' && typeof body === 'string' && body.length <= 2097152) {
            try { var data = JSON.parse(body); if (data.device) setLocal(data.device.device_id); } catch (_) {}
        }
        return { target: command ? id(command[3]) : null, epoch: revision };
    }
    function request(input, init) {
        try {
            var epoch = revision;
            var source = input instanceof Request ? input.clone() : new URL(String(input), location.href).href;
            var req = new Request(source, init);
            if (!/(connect-state|track-playback|\/me\/player)/.test(req.url)) return Promise.resolve(null);
            if (req.method === 'GET' || req.method === 'HEAD') return Promise.resolve(describe(req.url, req.method, null));
            return req.text().then(function (body) { var info = describe(req.url, req.method, body); if (info) info.epoch = epoch; return info; }).catch(function () { return null; });
        } catch (_) { return Promise.resolve(null); }
    }
    function response(observation, promise) {
        return Promise.all([observation, promise]).then(function (values) {
            var info = values[0], reply = values[1];
            if (!info || !reply || !reply.ok || info.epoch !== revision) return;
            // A newer server push wins over a late response to an older command.
            if (info.target && info.epoch === revision) setActive(info.target);
            var expected = revision;
            if (reply.status === 204 || Number(reply.headers.get('content-length')) > 1048576) return;
            return reply.clone().text().then(function (body) { if (revision === expected) json(body); });
        }).catch(function () {});
    }
    function remote() { return !!(local && active && active !== local); }
    window.spotxConnect = {
        request: request, describe: describe, response: response,
        isRemote: remote,
        canAutoPlay: function () { return !!(local && active === local) && !(window.spotxBlockingDialog && window.spotxBlockingDialog()); },
        target: function () { return active || local; },
        local: function () { return local; }
    };
    window.__splRemoteActive = remote;
    var Socket = window.WebSocket;
    if (typeof Socket === 'function') {
        function ObservedSocket(url, protocols) {
            var socket = arguments.length > 1 ? new Socket(url, protocols) : new Socket(url);
            try {
                var host = new URL(String(url), location.href).hostname;
                if (/(^|\.)spotify\.com$/.test(host)) socket.addEventListener('message', function (event) { json(event.data); });
            } catch (_) {}
            return socket;
        }
        ObservedSocket.prototype = Socket.prototype;
        Object.setPrototypeOf(ObservedSocket, Socket);
        window.WebSocket = ObservedSocket;
    }
})();
