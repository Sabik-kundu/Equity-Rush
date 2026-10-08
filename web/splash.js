/* Equity Rush intro animation. Total animation <= 2.4s, then the splash hands over to the app. */
(function () {
  var el = document.getElementById("splash");
  if (!el) return;
  var done = false,
    ready = false,
    played = false,
    t0 = Date.now();
  var reduce = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

  function remove() {
    if (done) return;
    done = true;
    if (el.parentNode) el.parentNode.removeChild(el);
    document.documentElement.classList.remove("splashing");
    window.dispatchEvent(new Event("eq:intro-done"));
  }
  function leave() {
    if (done) return;
    if (!window.gsap || reduce) {
      el.style.transition = "opacity .25s";
      el.style.opacity = "0";
      setTimeout(remove, 260);
      return;
    }
    var tl = gsap.timeline({ onComplete: remove });
    tl.to(".sp-core", { scale: 1.12, opacity: 0, duration: 0.34, ease: "power2.in" }, 0)
      .to(".sp-bar,.sp-note", { opacity: 0, duration: 0.2 }, 0)
      .to(el, { opacity: 0, duration: 0.3, ease: "power1.out" }, 0.1);
  }
  function tryLeave() {
    if (ready && played) leave();
  }
  window.eqReady = function () {
    ready = true;
    tryLeave();
  };

  /* hard fallbacks so a user is never stuck on the splash */
  var note = el.querySelector(".sp-note");
  setTimeout(function () {
    if (!done && !ready && note) note.classList.add("on");
  }, 6000);
  setTimeout(function () {
    if (!done) {
      ready = true;
      played = true;
      leave();
    }
  }, 20000);
  var retry = el.querySelector(".sp-note button");
  if (retry) retry.onclick = function () { location.reload(); };

  /* split the title into letters */
  el.querySelectorAll(".sp-title .wd").forEach(function (w) {
    var t = w.textContent;
    w.textContent = "";
    t.split("").forEach(function (c) {
      var s = document.createElement("span");
      s.className = "ch";
      s.textContent = c;
      w.appendChild(s);
    });
  });
  var logoBox = el.querySelector(".sp-logo");

  if (!window.gsap || reduce) {
    el.querySelectorAll(".sp-logo,.sp-tag,.sp-bar,.sp-title .ch").forEach(function (n) { n.style.opacity = 1; });
    played = true;
    setTimeout(function () { played = true; tryLeave(); }, reduce ? 350 : 900);
    return;
  }
  if (window.DrawSVGPlugin) gsap.registerPlugin(DrawSVGPlugin);
  var hasDraw = !!window.DrawSVGPlugin;

  gsap.set(".lg-reveal", { attr: { width: 0 } });
  gsap.set(".lg-tooth", { opacity: 0 });
  gsap.set(".lg-candle", { opacity: 0 });
  gsap.set(".lg-shine", { opacity: 0 });
  if (hasDraw) gsap.set(".lg-ring,.lg-rim", { drawSVG: "0%" });
  else gsap.set(".lg-ring,.lg-rim", { opacity: 0 });

  var tl = gsap.timeline({
    defaults: { ease: "power3.out" },
    onComplete: function () {
      played = true;
      tryLeave();
    },
  });
  /* backdrop + logo in */
  tl.to(".sp-grid", { opacity: 1, duration: 0.8, ease: "power1.out" }, 0)
    .to(".sp-bar", { opacity: 1, duration: 0.25 }, 0.05)
    .to(".sp-bar i", { scaleX: 1, duration: 1.85, ease: "power1.inOut" }, 0.05)
    .fromTo(logoBox, { opacity: 0, scale: 0.82, rotate: -6 }, { opacity: 1, scale: 1, rotate: 0, duration: 0.55, ease: "back.out(1.5)" }, 0)
    .to(".sp-glow", { opacity: 1, duration: 0.7 }, 0.1);
  /* rings sweep around */
  if (hasDraw) tl.to(".lg-ring", { drawSVG: "100%", duration: 0.65, ease: "power2.inOut" }, 0.08);
  else tl.to(".lg-ring", { opacity: 1, duration: 0.4 }, 0.08);
  /* teeth and treads pop out from the hub */
  tl.fromTo(".lg-tooth", { opacity: 0, scale: 0.35, svgOrigin: "250 250" }, { opacity: 1, scale: 1, svgOrigin: "250 250", duration: 0.34, ease: "back.out(2.2)", stagger: 0.022 }, 0.25);
  /* inner rim */
  if (hasDraw) tl.to(".lg-rim", { drawSVG: "100%", duration: 0.5, ease: "power2.inOut" }, 0.55);
  else tl.to(".lg-rim", { opacity: 1, duration: 0.3 }, 0.55);
  /* candles grow like a live chart */
  tl.fromTo(".lg-candle", { opacity: 0, scaleY: 0, transformOrigin: "50% 100%" }, { opacity: 1, scaleY: 1, duration: 0.42, ease: "back.out(1.7)", stagger: 0.075 }, 0.72);
  /* arrow swoops through */
  tl.to(".lg-reveal", { attr: { width: 500 }, duration: 0.6, ease: "power3.inOut" }, 1.02);
  tl.fromTo(".lg-arrowwrap", { x: -16, y: 10 }, { x: 0, y: 0, duration: 0.6 }, 1.02);
  /* shine + settle */
  tl.set(".lg-shine", { opacity: 0.6 }, 1.5);
  if (hasDraw) tl.fromTo(".lg-shine", { drawSVG: "0%" }, { drawSVG: "100%", duration: 0.4, ease: "power2.inOut" }, 1.5);
  tl.fromTo(logoBox, { scale: 1 }, { scale: 1.05, duration: 0.14, yoyo: true, repeat: 1, ease: "sine.inOut" }, 1.55);
  tl.fromTo(".sp-glow", { scale: 1 }, { scale: 1.35, opacity: 0.2, duration: 0.5, ease: "power2.out" }, 1.5);
  /* wordmark */
  tl.fromTo(".sp-title .ch", { opacity: 0, yPercent: 110 }, { opacity: 1, yPercent: 0, duration: 0.38, stagger: 0.032, ease: "power3.out" }, 1.0);
  tl.fromTo(".sp-tag", { opacity: 0, y: 8 }, { opacity: 1, y: 0, duration: 0.4 }, 1.55);
  /* total timeline ends at ~2.05s; exit (0.4s) keeps the whole thing under 2.5s */
  tl.set({}, {}, 2.05);
  tl.timeScale(1.3);
})();
