/**
 * Check an href for an anchor. If exists, and in document, scroll to it.
 * If href argument omitted, assumes context (this) is HTML Element,
 * which will be the case when invoked by jQuery after an event
 */
function scroll_if_anchor(href) {
    href = typeof(href) == "string" ? href : $(this).attr("href");

    // If href missing, ignore
    if (!href) return;

    // You could easily calculate this dynamically if you prefer
    var fromTop = 60;

    // If our Href points to a valid, non-empty anchor, and is on the same page (e.g. #foo)
    // Legacy jQuery and IE7 may have issues: http://stackoverflow.com/q/1593174
    if (href.charAt(0) == "#") {
        var $target = $(href);

        // Older browsers without pushState might flicker here, as they momentarily
        // jump to the wrong position (IE < 10)
        if ($target.length) {
            $('html, body').animate({ scrollTop: $target.offset().top - fromTop }, 100);
            if (history && "pushState" in history) {
                history.pushState({}, document.title, window.location.pathname + href);
                return false;
            }
        }
    }
}
