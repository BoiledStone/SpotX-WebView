window.addAutoFeatures = function () {
    function allowed() { return !!(window.spotxConnect && window.spotxConnect.canAutoPlay()); }
    if ('pBtn' in window && firstPlay && window.autoPlayMode !== 'disabled' && allowed() && window.splIsPlaying() === false) {
        pBtn.click(); firstPlay = false;
    }
    if (afint) clearInterval(afint);
    afint = setInterval(function () {
        if (window.__splBg || window.__splBusy) return;
        if (window.closeNpPref) closeNowPlay();
        // Device selection belongs exclusively to Spotify Connect's user interface.
        if (!allowed()) return;
        if (window.autoPlayMode === 'permanent' && 'pBtn' in window && !reqPause && !ulFlag && window.splIsPlaying() === false) pBtn.click();
        if (window.autoPlayMode === 'onetime' && !window.__splApDone && !window.__splApActive && 'pBtn' in window && !reqPause && window.splIsPlaying() === false) {
            if (typeof splAutoPlay === 'function') splAutoPlay();
        }
    }, 5000);
};
