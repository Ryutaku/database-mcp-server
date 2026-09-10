/* ===================================================================
   Database MCP Admin — Click Feedback (ripple)
   Emits a ripple from the exact pointer position on every
   interactive control, so a click always produces visible feedback.
   =================================================================== */
(function () {
  "use strict";

  var RIPPLE_SELECTOR = ".btn, .UnderlineNav-item, .page-btn, [data-ripple]";

  function createRipple(host, clientX, clientY) {
    var rect = host.getBoundingClientRect();
    var size = Math.max(rect.width, rect.height);
    if (!size) return;

    var previous = host.querySelector(".ripple");
    if (previous) previous.parentNode.removeChild(previous);

    var ripple = document.createElement("span");
    ripple.className = "ripple";
    ripple.style.width = size + "px";
    ripple.style.height = size + "px";
    ripple.style.left = (clientX - rect.left - size / 2) + "px";
    ripple.style.top = (clientY - rect.top - size / 2) + "px";

    host.appendChild(ripple);
    ripple.addEventListener("animationend", function () {
      if (ripple.parentNode) ripple.parentNode.removeChild(ripple);
    });
  }

  function handlePress(event) {
    if (event.button !== undefined && event.button !== 0) return;
    var host = event.target.closest ? event.target.closest(RIPPLE_SELECTOR) : null;
    if (!host || host.disabled) return;
    createRipple(host, event.clientX, event.clientY);
  }

  if (window.PointerEvent) {
    document.addEventListener("pointerdown", handlePress, { passive: true });
  } else {
    document.addEventListener("mousedown", handlePress, { passive: true });
    document.addEventListener("touchstart", function (event) {
      var touch = event.touches && event.touches[0];
      if (touch) handlePress({ target: event.target, clientX: touch.clientX, clientY: touch.clientY, button: 0 });
    }, { passive: true });
  }
})();
