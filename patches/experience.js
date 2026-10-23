(function () {
    'use strict';
    if (window.__spotxExperience) return;
    window.__spotxExperience = true;
    var fr = /^fr\b/i.test(document.documentElement.lang || navigator.language || '');
    window.spotxText = function (en, french) { return fr ? french : en; };
    function text(node) { return node ? (node.textContent || '').trim() : ''; }
    function setText(node,value) { if(node && node.textContent!==value) node.textContent=value; }
    window.spotxSetText=setText;
    window.spotxPlaybackIcon=function(button,playing,markup){
        if(button && button.dataset.spotxPlaying!==String(playing)){
            button.innerHTML=markup;button.dataset.spotxPlaying=String(playing);
        }
    };
    function attr(node,name,value) { value=String(value);if(node && node.getAttribute(name)!==value)node.setAttribute(name,value); }
    function hidden(node,value) { if(node.hidden!==value)node.hidden=value; }
    var artwork=new WeakMap();
    window.addEventListener('online',function(){
        var cover=document.getElementById('spl-cover-img'),state=cover&&artwork.get(cover);
        if(state&&state.status==='failed'){state.failures=0;state.retryAt=0;}
    });
    function imageURL(value) {
        if (!value) return '';
        try {
            var u = new URL(value), h = u.hostname.toLowerCase();
            return u.protocol === 'https:' && !u.username && !u.password && (!u.port || u.port === '443') &&
                ['scdn.co', 'spotifycdn.com', 'spotify.com'].some(function (d) { return h === d || h.endsWith('.' + d); }) ? u.href : '';
        } catch (_) { return ''; }
    }
    window.spotxNowPlaying = function () {
        var root = document.querySelector('[data-testid="now-playing-widget"], [data-testid="now-playing-widget-v2"]') ||
            document.querySelector('aside[data-testid="now-playing-bar"]');
        var title = '', artist = '', cover = '';
        if (root) {
            title = text(root.querySelector('[data-testid="context-item-info-title"], [data-testid="context-item-link"], a[href*="/track/"], a[href*="/episode/"]'));
            // Spotify's v2 widget links the song title to its album, without a test id.
            if (!title && root.dataset.testid === 'now-playing-widget-v2') title = text(root.querySelector('a[href*="/album/"]'));
            artist = text(root.querySelector('[data-testid="now-playing-widget-v2-byline"], [data-testid="context-item-info-artist"], [data-testid="context-item-info-show"], a[href*="/artist/"], a[href*="/show/"]'));
            var img = root.querySelector('img[data-testid="cover-art-image"], img');
            if (img) cover = imageURL(img.currentSrc || img.src);
        }
        var meta = navigator.mediaSession && navigator.mediaSession.metadata;
        if (meta && (!title || meta.title === title)) {
            title = title || meta.title || '';
            artist = artist || meta.artist || '';
            if (!cover && meta.artwork && meta.artwork.length) cover = imageURL(meta.artwork[meta.artwork.length - 1].src);
        }
        if (title === 'No track') title = '';
        // Do not search unrelated playlist rows: they need not be the playing song.
        return {title: title, artist: artist, cover: cover, empty: !title};
    };
    window.spotxUpdateMetadata = function (cover, title, artist, player) {
        var now = window.spotxNowPlaying();
        setText(title,now.title || window.spotxText('Choose a track', 'Choisis un titre'));
        setText(artist,now.artist || (now.empty ? window.spotxText('Your music starts here', 'Ta musique commence ici') : ''));
        if (player) attr(player,'data-spotx-empty',now.empty ? 'true' : 'false');
        if (cover) {
            if (!now.cover) {
                artwork.delete(cover);hidden(cover,true);cover.removeAttribute('src');cover.removeAttribute('data-src');
            } else {
                var state=artwork.get(cover);
                if(!state||state.url!==now.cover){state={url:now.cover,status:'idle',failures:0,retryAt:0};artwork.set(cover,state);}
                if(state.status==='idle'||(state.status==='failed'&&state.failures<3&&Date.now()>=state.retryAt)){
                    state.status='loading';attr(cover,'data-src',state.url);hidden(cover,false);
                    cover.onerror=function(){
                        if(artwork.get(cover)!==state||state.status!=='loading')return;
                        state.status='failed';state.failures++;state.retryAt=Date.now()+(state.failures===1?1000:4000);
                        hidden(cover,true);cover.removeAttribute('src');
                    };
                    cover.onload=function(){
                        if(artwork.get(cover)!==state||state.status!=='loading')return;
                        state.status='ready';state.failures=0;hidden(cover,false);
                    };
                    cover.src=state.url;
                }
            }
        }
        return now;
    };
    window.spotxAccessibility = function (player, playing) {
        if (!player) return;
        if (fr && !player.dataset.spotxLabels) {
            player.dataset.spotxLabels = 'true';
            var labels = {'spl-prev':'Précédent','spl-prev-mini':'Précédent','spl-next':'Suivant','spl-next-mini':'Suivant',
                'spl-liked':'Favoris','spl-shuffle':'Lecture aléatoire','spl-repeat':'Répétition','spl-timer':'Minuterie',
                'spl-pip':'Fenêtre flottante','spl-lyrics':'Paroles','spl-nptoggle':'Vue de lecture','spl-queue':"File d’attente",
                'spl-download':'Télécharger','spl-dl-cancel':'Annuler le téléchargement','spl-connect':'Choisir un appareil',
                'spl-vol-btn':'Volume','spl-speed':'Vitesse de lecture','spl-collapse':'Réduire le lecteur'};
            Object.keys(labels).forEach(function (id) {
                var node = document.getElementById(id);
                if (node) { attr(node,'aria-label',labels[id]);attr(node,'title',labels[id]); }
            });
            var heading=player.querySelector('.spl-np-title');
            setText(heading,'En cours de lecture');
        }
        ['spl-play', 'spl-play-mini'].forEach(function (id) {
            var b = document.getElementById(id);
            attr(b,'aria-label',playing ? window.spotxText('Pause', 'Pause') : window.spotxText('Play', 'Lecture'));
        });
        ['spl-shuffle', 'spl-repeat', 'spl-liked'].forEach(function (id) {
            var b = document.getElementById(id);
            if (b) attr(b,'aria-pressed',b.classList.contains('spl-active') ? 'true' : 'false');
        });
        var progress = document.getElementById('spl-bar');
        if (progress && !progress.hasAttribute('data-spotx-keyboard')) {
            progress.setAttribute('data-spotx-keyboard', 'true');
            progress.setAttribute('role', 'slider'); progress.tabIndex = 0;
            progress.setAttribute('aria-label', window.spotxText('Playback position', 'Position de lecture'));
            progress.addEventListener('keydown', function (event) {
                var input = document.querySelector('[data-testid="playback-progressbar"] input[type="range"]');
                if (!input || !['ArrowRight','ArrowLeft','Home','End'].includes(event.key)) return;
                event.preventDefault();
                var min = Number(input.min) || 0, max = Number(input.max) || 100;
                var step = max > 1000 ? 5000 : 5;
                var value = event.key === 'Home' ? min : event.key === 'End' ? max : Number(input.value) + (event.key === 'ArrowRight' ? step : -step);
                var next = Math.max(min, Math.min(max, value));
                var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                setter.call(input, String(next));
                input.dispatchEvent(new Event('input', {bubbles:true}));
                input.dispatchEvent(new Event('change', {bubbles:true}));
            });
        }
        var range = document.querySelector('[data-testid="playback-progressbar"] input[type="range"]');
        if (progress && range) {
            attr(progress,'aria-valuemin',range.min || '0');
            attr(progress,'aria-valuemax',range.max || '100');
            attr(progress,'aria-valuenow',range.value || '0');
            var position = document.getElementById('spl-pos');
            if (position) attr(progress,'aria-valuetext',position.textContent);
        }
    };
    function styles() {
        if (!document.head || document.getElementById('spotx-experience-style')) return;
        var style = document.createElement('style'); style.id = 'spotx-experience-style';
        style.textContent = `
            #Desktop_LeftSidebar_Id[data-spotx-library=closed] {overflow:hidden!important}
            #spotilolPlayerControls {background:#151a18!important;border:1px solid #ffffff1f!important;box-shadow:0 8px 32px #0008!important;box-sizing:border-box!important;color:#f4f7f5!important}
            #spotilolPlayerControls:not(.spl-full):not(.spl-mini) {border-radius:16px!important;left:8px!important;right:8px!important;width:auto!important;max-width:min(560px,calc(100% - 16px))!important;padding:7px 10px!important}
            #spotilolPlayerControls .spl-info {min-width:0!important;overflow:hidden!important}
            #spotilolPlayerControls .spl-track,#spotilolPlayerControls .spl-artist {overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%}
            #spotilolPlayerControls .spl-track {font-weight:650!important;line-height:1.35!important}
            #spotilolPlayerControls .spl-artist {color:#b9c8c0!important;line-height:1.5!important}
            #spotilolPlayerControls button.spl-btn {touch-action:manipulation}
            #spotilolPlayerControls .spl-transport .spl-btn,#spotilolPlayerControls .spl-mini-transport .spl-btn {min-width:44px!important;min-height:44px!important}
            #spotilolPlayerControls:not(.spl-full) .spl-top {gap:8px;margin-bottom:3px}
            #spotilolPlayerControls:not(.spl-full) .spl-cover,#spotilolPlayerControls:not(.spl-full) .spl-cover img {width:40px;height:40px;min-width:40px;min-height:40px}
            #spotilolPlayerControls:not(.spl-full) .spl-liked-btn {min-width:36px!important;min-height:36px!important}
            #spotilolPlayerControls:not(.spl-full) .spl-row2 {max-height:34px;margin-bottom:2px;overflow-x:auto;overflow-y:hidden;scrollbar-width:none;touch-action:pan-x}
            #spotilolPlayerControls:not(.spl-full) .spl-actions-left {display:flex;flex-wrap:nowrap;gap:0!important;justify-content:space-between;width:100%;min-width:max-content}
            #spotilolPlayerControls:not(.spl-full) .spl-row2 .spl-btn {min-width:34px!important;min-height:34px!important;padding:5px}
            #spotilolPlayerControls:not(.spl-full) .spl-bottom {margin-bottom:2px;min-height:18px}
            #spotilolPlayerControls:not(.spl-full) .spl-bar-wrap {min-height:18px;height:18px}
            #spotilolPlayerControls:not(.spl-full) .spl-transport {gap:12px;min-height:44px}
            #spotilolPlayerControls:not(.spl-full) .spl-transport .spl-play {min-width:44px!important;min-height:44px!important;padding:10px!important}
            #spotilolPlayerControls.spl-mini .spl-row2,#spotilolPlayerControls.spl-mini .spl-bottom,#spotilolPlayerControls.spl-mini .spl-transport {display:none!important}
            #spotilolPlayerControls.spl-mini .spl-top {margin-bottom:0}
            #spotilolPlayerControls.spl-mini {padding:8px 10px 10px;max-height:64px}
            #spotilolPlayerControls .spl-cover {border-radius:10px;overflow:hidden;background:linear-gradient(145deg,#324d40,#1c2922)}
            #spotilolPlayerControls img[hidden] {display:none!important}
            #spotilolPlayerControls .spl-time {font-variant-numeric:tabular-nums;color:#b9c8c0!important;font-size:11px!important}
            #spotilolPlayerControls .spl-bar {cursor:pointer}
            #spotilolPlayerControls.spl-full {padding-bottom:max(16px,env(safe-area-inset-bottom))!important;max-height:100dvh!important;overflow-y:auto!important;overscroll-behavior:contain}
            #spotilolPlayerControls.spl-full.spl-canvas {background:#151a18!important}
            #spotilolPlayerControls.spl-full .spl-cover {background:transparent}
            #spotilolPlayerControls.spl-full .spl-track {white-space:normal;overflow-wrap:anywhere}
            #spotilolPlayerControls .spl-sheet-card {max-height:75dvh;overflow-y:auto;overscroll-behavior:contain}
            #spotilolPlayerControls button:focus-visible,#spl-bar:focus-visible {outline:2px solid #a8f0cc!important;outline-offset:3px}
            #spotilolPlayerControls button[aria-disabled=true] {opacity:.45}
            @media(max-width:380px) {#spotilolPlayerControls:not(.spl-full) .spl-actions-left{gap:0!important}#spotilolPlayerControls:not(.spl-full) .spl-track{font-size:14px!important}}
            @media(prefers-reduced-motion:reduce) {#spotilolPlayerControls *,#spotilolPlayerControls{animation:none!important;transition:none!important;scroll-behavior:auto!important}}
        `;
        document.head.appendChild(style);
    }
    styles();
    if (!document.head) document.addEventListener('DOMContentLoaded', styles, {once:true});
})();
