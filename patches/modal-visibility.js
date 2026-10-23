(function () {
    'use strict';
    if (window.spotxBlockingDialog) return;
    var selector = '[role="dialog"][aria-modal="true"],dialog[open],#onetrust-banner-sdk,#onetrust-pc-sdk,[data-testid="cookie-banner"]';
    function visible(node) {
        if (node.closest('#spotilolPlayerControls,[hidden],[aria-hidden="true"]')) return false;
        var style = getComputedStyle(node);
        return node.getClientRects().length > 0 && style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) !== 0;
    }
    function blocked() {
        if (location.hostname === 'accounts.spotify.com' || /^\/(?:intl-[^/]+\/)?login(?:\/|$)/.test(location.pathname)) return true;
        return Array.from(document.querySelectorAll(selector)).some(visible);
    }
    function update() {
        queued = false;
        var value = blocked() ? 'true' : 'false';
        if (document.documentElement.getAttribute('data-spotx-modal') !== value) document.documentElement.setAttribute('data-spotx-modal', value);
    }
    var queued = false;
    function schedule() { if (!queued) { queued = true; setTimeout(update, 60); } }
    var style = document.createElement('style');
    style.textContent = 'html[data-spotx-modal="true"] #spotilolPlayerControls{visibility:hidden!important;pointer-events:none!important}';
    (document.head || document.documentElement).appendChild(style);
    new MutationObserver(function (records) {
        if (records.some(function (record) { return !(record.target.closest && record.target.closest('#spotilolPlayerControls')); })) schedule();
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-hidden','aria-modal','open','hidden','style','class'] });
    window.addEventListener('popstate', schedule);
    document.addEventListener('visibilitychange', schedule);
    window.spotxBlockingDialog = blocked;
    update();
})();
