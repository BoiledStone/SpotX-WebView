(function () {
    'use strict';
    if (window.spotxPlaybackRecovery) return;
    var BACKGROUND = 120000, WINDOW = 180000, STALL = 6000;
    var KEY = 'spotx.playback-recovery.v1', BUDGET = 'spotx.playback-recovery.budget.v1';
    var backgroundAt = null, armedUntil = 0, pending = null, timer = null, restoring = false;
    var restoreTimer = null, restoreSeek = null;
    function remote() { try { return !!(window.__splRemoteActive && window.__splRemoteActive()); } catch (_) { return false; } }
    function uri(value) { return typeof value === 'string' && /^spotify:(track|episode):[A-Za-z0-9]{22}$/.test(value) ? value : ''; }
    function context(value) {
        return typeof value === 'string' && /^(spotify:(track|episode|album|playlist|artist|show):[A-Za-z0-9]{22}|spotify:collection:tracks|spotify:user:[A-Za-z0-9._-]{1,128}:collection)$/.test(value) ? value : '';
    }
    function playing() { try { return window.splIsPlaying && window.splIsPlaying() === true; } catch (_) { return false; } }
    function snapshot() {
        var range = document.querySelector('[data-testid="playback-progressbar"] input[type="range"]');
        var position = range ? Number(range.value) : Number(window.position);
        var duration = range ? Number(range.max) : Number(window.duration);
        var id = typeof window.splPlayingTrackId === 'function' ? window.splPlayingTrackId() : null;
        var link = document.querySelector(':is([data-testid="now-playing-widget"],[data-testid="now-playing-widget-v2"]) a:is([href*="/track/"],[href*="/episode/"])');
        var match = link && (link.getAttribute('href') || '').match(/\/(track|episode)\/([A-Za-z0-9]{22})/);
        return { uri: match ? 'spotify:' + match[1] + ':' + match[2] : (id && /^[A-Za-z0-9]{22}$/.test(id) ? 'spotify:track:' + id : uri(window.__curTrackUri)),
            position: Number.isFinite(position) && position >= 0 ? position : 0,
            duration: Number.isFinite(duration) && duration > 0 ? duration : 0 };
    }
    function removeResume() { try { sessionStorage.removeItem(KEY); } catch (_) {} }
    function cancel() {
        pending = null; clearTimeout(timer); timer = null;
        clearTimeout(restoreTimer); restoreTimer = null; restoreSeek = null; removeResume();
    }
    function background() { if (backgroundAt === null) backgroundAt = Date.now(); }
    function resume() {
        var now = Date.now();
        if (backgroundAt !== null && now - backgroundAt >= BACKGROUND) armedUntil = now + WINDOW;
        backgroundAt = null;
        if (pending) { pending.since = now; pending.sample = snapshot(); pending.advanced = 0; }
    }
    function armed() {
        var now = Date.now();
        return now < armedUntil || (backgroundAt !== null && now - backgroundAt >= BACKGROUND);
    }
    function schedule() { clearTimeout(timer); timer = setTimeout(check, 1000); }
    function intent(play, target, targetContext) {
        if (restoring) return;
        if (restoreTimer !== null || restoreSeek) { clearTimeout(restoreTimer); restoreTimer = null; restoreSeek = null; removeResume(); }
        if (!play) { cancel(); return; }
        if (!armed() || remote()) return;
        // Repeated clicks must not postpone detection forever.
        var now = Date.now(), wanted = uri(target);
        if (!pending || (wanted && pending.uri !== wanted)) {
            pending = { since: now, createdAt: now, sample: snapshot(), advanced: 0, uri: wanted, context: context(targetContext) };
        }
        schedule();
    }
    function reload() {
        var now = Date.now(), sample = snapshot(), state = pending;
        pending = null;
        if (!state || typeof AndBridge === 'undefined' || typeof AndBridge.reloadPlayer !== 'function') return;
        try {
            var budget = JSON.parse(sessionStorage.getItem(BUDGET) || '[]');
            if (!Array.isArray(budget)) return;
            budget = budget.filter(function (at) { return typeof at === 'number' && now - at < 600000; });
            if (budget.length >= 2 || budget.some(function (at) { return now - at < 120000; })) return;
            var target = state.uri || sample.uri;
            var record = { createdAt: now, uri: target, context: state.context,
                position: target && target === sample.uri ? sample.position : 0 };
            sessionStorage.setItem(KEY, JSON.stringify(record));
            budget.push(now); sessionStorage.setItem(BUDGET, JSON.stringify(budget));
        } catch (_) { return; }
        Promise.resolve(AndBridge.reloadPlayer()).then(function (accepted) {
            if (accepted !== true) removeResume();
        }).catch(function () { /* pagehide can reject an already accepted reload */ });
    }
    function check() {
        timer = null;
        if (!pending) return;
        var now = Date.now(), sample = snapshot();
        if (remote() || now - pending.createdAt > 60000 || !armed()) { pending = null; return; }
        if (sample.uri === pending.sample.uri && sample.position > pending.sample.position + 250 && playing()) {
            pending.advanced += sample.position - pending.sample.position;
            if (pending.advanced >= 1500) { pending = null; return; }
        }
        pending.sample = sample;
        if (navigator.onLine === false) { pending.since = now; schedule(); return; }
        if (now - pending.since >= STALL) { reload(); return; }
        schedule();
    }
    function wrap(name, before) {
        var original = window[name];
        if (typeof original !== 'function') return;
        window[name] = function () { before.apply(this, arguments); return original.apply(this, arguments); };
    }
    wrap('actPlayPause', function (play) { intent(play == null ? !playing() : play === true); });
    wrap('playFromUri', function (target, targetContext) { intent(true, target, targetContext); });
    ['actSkipForward', 'actSkipBack'].forEach(function (name) { wrap(name, function () { if (playing() || pending) intent(true); }); });
    // Spotify's own buttons bypass actPlayPause; only trusted user gestures count here.
    document.addEventListener('click', function (event) {
        if (!event.isTrusted || restoring || !event.target.closest) return;
        var button = event.target.closest('button');
        if (!button) return;
        var testid = button.getAttribute('data-testid') || '';
        if (testid === 'control-button-playpause') intent(!playing());
        else if (testid === 'play-button' || testid === 'play-button-one-track') {
            var row = button.closest('[data-testid="tracklist-row"]');
            var link = row && row.querySelector('a[href*="/track/"]');
            var match = link && (link.getAttribute('href') || '').match(/\/track\/([A-Za-z0-9]{22})/);
            intent(true, match ? 'spotify:track:' + match[1] : '');
        }
    }, true);
    document.addEventListener('visibilitychange', function () { if (document.hidden) background(); else resume(); });
    window.addEventListener('online', function () { if (pending) { pending.since = Date.now(); schedule(); } });
    window.addEventListener('pagehide', function () { clearTimeout(timer); clearTimeout(restoreTimer); });
    window.spotxPlaybackRecovery = { background: background, resume: resume, isArmed: armed };
    if (document.hidden || window.__splBg) background();

    var record;
    try { record = JSON.parse(sessionStorage.getItem(KEY) || 'null'); } catch (_) { removeResume(); }
    if (!record) return;
    if (typeof record.createdAt !== 'number' || Date.now() - record.createdAt < 0 || Date.now() - record.createdAt > 90000 ||
        (record.uri && !uri(record.uri)) || (record.context && !context(record.context)) ||
        typeof record.position !== 'number' || !Number.isFinite(record.position) || record.position < 0 || record.position > 86400000) { removeResume(); return; }
    armedUntil = Date.now() + WINDOW;
    var readyAt = null, expiresAt = Date.now() + 45000;
    function restore() {
        if (Date.now() > expiresAt || remote()) { removeResume(); return; }
        if (navigator.onLine === false || !window.ffDone || !window.splEnsurePb || !window.splEnsurePb()) {
            readyAt = null; restoreTimer = setTimeout(restore, 500); return;
        }
        if (readyAt === null) readyAt = Date.now();
        if (Date.now() - readyAt < 1500) { restoreTimer = setTimeout(restore, 500); return; }
        var sample = snapshot();
        if (record.uri && record.uri !== sample.uri && !(window.spotDevId && window.spotAuthToken)) {
            restoreTimer = setTimeout(restore, 500); return;
        }
        removeResume(); restoring = true;
        try {
            if (record.uri && record.uri !== sample.uri) window.playFromUri(record.uri, record.context || undefined);
            else window.actPlayPause(true);
        } finally { restoring = false; }
        restoreSeek = record.position > 1000 && record.uri ? record : null;
        var seekUntil = Date.now() + 15000;
        function seek() {
            if (!restoreSeek || Date.now() > seekUntil || remote()) { restoreSeek = null; return; }
            var current = snapshot();
            if (playing() && current.uri === restoreSeek.uri && current.duration > restoreSeek.position) {
                if (Math.abs(current.position - restoreSeek.position) > 2000 && typeof window.actSeek === 'function') window.actSeek(restoreSeek.position);
                restoreSeek = null; return;
            }
            restoreTimer = setTimeout(seek, 500);
        }
        if (restoreSeek) restoreTimer = setTimeout(seek, 500);
    }
    restoreTimer = setTimeout(restore, 500);
})();
