"use strict";
const CH_LITE = !!window.EQ_LITE;
class Chart {
  static colors() {
    if (!Chart._c) {
      const cs = getComputedStyle(document.documentElement),
        g = (n) => cs.getPropertyValue(n).trim();
      Chart._c = {
        up: g("--up"),
        dn: g("--dn"),
        ink: g("--ink"),
        mut: g("--mut"),
        line: g("--line"),
        sf: g("--sf"),
        pri: g("--pri"),
        gold: g("--gold"),
      };
    }
    return Chart._c;
  }

  static alpha(hex, a) {
    let h = hex.replace("#", "");
    if (h.length === 3)
      h = h
        .split("")
        .map((x) => x + x)
        .join("");
    const n = parseInt(h, 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }

  constructor(host, o = {}) {
    this.host = host;
    this.o = Object.assign(
      { interactive: false, fmt: (v) => v.toFixed(2), base: null, minSpan: 20 },
      o,
    );
    this.s = o.source || { t: [], v: [] };
    this.lines = [];
    this.marks = [];
    this.span = o.span || 600;
    this.full = false;
    this.follow = true;
    this.x1 = 0;
    this.cross = null;
    this.pl = 8;
    this.pr = 62;
    this.fs = this.o.fs || 1;
    this.pt = 12 * this.fs;
    this.pb = 24 * this.fs;
    this.pw = 300;
    this.W = 0;
    this.H = 0;
    this.raf = 0;
    this.dpr = Math.min(window.devicePixelRatio || 1, CH_LITE ? 1.5 : 2);
    this.cv = document.createElement("canvas");
    this.cv.style.cssText = "width:100%;height:100%;display:block";
    if (this.o.interactive) this.cv.style.touchAction = this.o.touch || "none";
    host.appendChild(this.cv);
    this.cx = this.cv.getContext("2d");
    this.ro = new ResizeObserver(() => this.resize());
    this.ro.observe(host);
    this.bind();
    this.snapRight();
    this.resize();
  }

  destroy() {
    this.dead = true;
    this.ro.disconnect();
    cancelAnimationFrame(this.raf);
    this.cv.remove();
  }

  first() {
    const t = this.s.t;
    return t.length ? t[0] : Date.now() / 1000;
  }
  last() {
    const t = this.s.t;
    return t.length ? t[t.length - 1] : Date.now() / 1000;
  }

  lb(x) {
    const t = this.s.t;
    let lo = 0,
      hi = t.length;
    while (lo < hi) {
      const m = (lo + hi) >> 1;
      if (t[m] < x) lo = m + 1;
      else hi = m;
    }
    return lo;
  }

  maxX1() {
    return Math.max(this.first() + this.span, this.last() + this.span * 0.04);
  }
  minX1() {
    return this.first() + this.span;
  }

  snapRight() {
    if (this.full)
      this.span = Math.max(
        this.o.minSpan,
        (this.last() - this.first()) * 1.04 + 1,
      );
    this.x1 = this.maxX1();
  }

  setSpan(sec) {
    this.full = !sec;
    if (sec) this.span = sec;
    this.follow = true;
    this.snapRight();
    this.draw();
  }

  fit() {
    this.setSpan(0);
  }
  appended() {
    if (this.follow || this.full) this.snapRight();
    this.draw();
  }

  clampView() {
    const last = this.last(),
      first = this.first();
    const hi = this.maxX1();
    this.x1 = Math.max(this.minX1(), Math.min(hi, this.x1));
    this.follow = this.x1 >= hi - this.span * 0.02;
  }

  pan(dx) {
    this.full = false;
    this.x1 -= (dx / this.pw) * this.span;
    this.clampView();
    this.draw();
  }

  zoom(factor, px) {
    const ratio = Math.max(0, Math.min(1, (px - this.pl) / this.pw));
    const tx = this.x1 - this.span + ratio * this.span;
    const maxSpan = Math.max(60, (this.last() - this.first()) * 1.04 + 1);
    const ns = Math.max(this.o.minSpan, Math.min(maxSpan, this.span * factor));
    this.full = false;
    this.span = ns;
    this.x1 = tx - ratio * ns + ns;
    this.clampView();
    this.draw();
  }

  zoomCenter(factor) {
    this.zoom(factor, this.pl + this.pw / 2);
  }

  resize() {
    const W = this.host.clientWidth,
      H = this.host.clientHeight;
    if (!W || !H) return;
    this.W = W;
    this.H = H;
    this.dpr = Math.min(window.devicePixelRatio || 1, CH_LITE ? 1.5 : 2);
    this.cv.width = Math.round(W * this.dpr);
    this.cv.height = Math.round(H * this.dpr);
    this.paint();
  }

  draw() {
    if (this.dead) return;
    cancelAnimationFrame(this.raf);
    this.raf = requestAnimationFrame(() => this.paint());
  }

  bind() {
    const cv = this.cv,
      P = new Map();
    let moved = 0,
      last2 = 0;
    const dist = () => {
      const a = [...P.values()];
      return Math.hypot(a[0].x - a[1].x, a[0].y - a[1].y);
    };
    const midx = () => {
      const a = [...P.values()];
      return (a[0].x + a[1].x) / 2;
    };
    if (!this.o.interactive) {
      cv.addEventListener("pointermove", (e) => {
        if (e.pointerType === "mouse") {
          this.cross = { x: e.offsetX };
          this.draw();
        }
      });
      cv.addEventListener("pointerleave", () => {
        this.cross = null;
        this.draw();
      });
      return;
    }
    cv.addEventListener("pointerdown", (e) => {
      cv.setPointerCapture(e.pointerId);
      P.set(e.pointerId, { x: e.offsetX, y: e.offsetY });
      moved = 0;
      if (P.size === 2) last2 = dist();
    });
    cv.addEventListener("pointermove", (e) => {
      const p = P.get(e.pointerId);
      if (!p) {
        if (e.pointerType === "mouse") {
          this.cross = { x: e.offsetX };
          this.draw();
        }
        return;
      }
      const dx = e.offsetX - p.x;
      p.x = e.offsetX;
      p.y = e.offsetY;
      if (P.size === 2) {
        const d = dist();
        if (last2 > 0 && d > 0) this.zoom(last2 / d, midx());
        last2 = d;
        moved = 99;
        this.cross = null;
      } else {
        moved += Math.abs(dx);
        if (moved > 4) {
          this.cross = e.pointerType === "mouse" ? { x: e.offsetX } : null;
          this.pan(dx);
        }
      }
    });
    const up = (e) => {
      const had = P.delete(e.pointerId);
      if (had && moved <= 4 && P.size === 0) {
        this.cross = { x: e.offsetX };
        this.draw();
      }
      if (P.size < 2) last2 = 0;
    };
    cv.addEventListener("pointerup", up);
    cv.addEventListener("pointercancel", (e) => {
      P.delete(e.pointerId);
      last2 = 0;
    });
    cv.addEventListener("pointerleave", (e) => {
      if (e.pointerType === "mouse" && !P.size) {
        this.cross = null;
        this.draw();
      }
    });
    cv.addEventListener(
      "wheel",
      (e) => {
        e.preventDefault();
        this.zoom(e.deltaY > 0 ? 1.18 : 1 / 1.18, e.offsetX);
      },
      { passive: false },
    );
    cv.addEventListener("dblclick", () => this.fit());
  }

  static niceTicks(mn, mx, n) {
    const raw = (mx - mn) / n,
      mag = Math.pow(10, Math.floor(Math.log10(raw))),
      f = raw / mag;
    const step = (f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10) * mag,
      out = [];
    for (let v = Math.ceil(mn / step) * step; v <= mx + 1e-9; v += step)
      out.push(+v.toFixed(8));
    return out;
  }

  static rr(cx, x, y, w, h, r) {
    cx.beginPath();
    cx.moveTo(x + r, y);
    cx.arcTo(x + w, y, x + w, y + h, r);
    cx.arcTo(x + w, y + h, x, y + h, r);
    cx.arcTo(x, y + h, x, y, r);
    cx.arcTo(x, y, x + w, y, r);
    cx.closePath();
  }

  static time(sec, withSec) {
    const d = new Date(sec * 1000),
      p = (n) => String(n).padStart(2, "0");
    return (
      p(d.getHours()) +
      ":" +
      p(d.getMinutes()) +
      (withSec ? ":" + p(d.getSeconds()) : "")
    );
  }

  paint() {
    if (this.dead) return;
    const cx = this.cx,
      W = this.W,
      H = this.H;
    if (!W || !H) return;
    const fs = this.fs,
      C = Chart.colors(),
      t = this.s.t,
      v = this.s.v,
      fmt = this.o.fmt;
    cx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    cx.clearRect(0, 0, W, H);
    const narrow = W < 460;
    this.pl = narrow ? 4 : 8;
    cx.font =
      "600 " +
      (narrow ? 10 : 11) * fs +
      'px "Plus Jakarta Sans", system-ui, sans-serif';
    cx.textBaseline = "middle";
    if (t.length < 2) {
      cx.fillStyle = C.mut;
      cx.textAlign = "center";
      cx.fillText("Waiting for data", W / 2, H / 2);
      return;
    }
    const x1 = this.x1,
      x0 = x1 - this.span;
    let i0 = this.lb(x0) - 1,
      i1 = this.lb(x1);
    if (i0 < 0) i0 = 0;
    if (i1 > t.length - 1) i1 = t.length - 1;
    if (i1 <= i0) {
      cx.fillStyle = C.mut;
      cx.textAlign = "center";
      cx.fillText("No data in this window", W / 2, H / 2);
      return;
    }
    let mn = Infinity,
      mx = -Infinity;
    for (let i = i0; i <= i1; i++) {
      if (v[i] < mn) mn = v[i];
      if (v[i] > mx) mx = v[i];
    }
    for (const L of this.lines)
      if (L.range) {
        if (L.y < mn) mn = L.y;
        if (L.y > mx) mx = L.y;
      }
    if (mx - mn < 1e-9) {
      mn -= mn * 0.002;
      mx += mx * 0.002;
    }
    const pad = (mx - mn) * 0.14;
    mn -= pad;
    mx += pad;
    const ticks = Chart.niceTicks(
      mn,
      mx,
      Math.max(
        2,
        Math.min(5, Math.floor((H - this.pt - this.pb) / ((narrow ? 42 : 48) * fs))),
      ),
    );
    let lw = 0;
    for (const tv of ticks) lw = Math.max(lw, cx.measureText(fmt(tv)).width);
    this.pr = Math.ceil(lw) + (narrow ? 10 : 16);
    const L0 = this.pl,
      R = W - this.pr,
      T = this.pt,
      B = H - this.pb,
      pw = R - L0,
      ph = B - T;
    this.pw = pw;
    const X = (tt) => L0 + ((tt - x0) / this.span) * pw,
      Y = (vv) => T + ((mx - vv) / (mx - mn)) * ph;

    cx.lineWidth = 1;
    cx.strokeStyle = C.line;
    cx.fillStyle = C.mut;
    cx.textAlign = "left";
    for (const tv of ticks) {
      const y = Y(tv);
      cx.beginPath();
      cx.moveTo(L0, y);
      cx.lineTo(R, y);
      cx.stroke();
      cx.fillText(fmt(tv), R + (narrow ? 5 : 8), y);
    }
    const steps = [
      5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 21600,
    ];
    const step =
      steps.find(
        (s) =>
          this.span / s <= Math.max(3, Math.floor(pw / ((narrow ? 64 : 90) * fs))),
      ) || 21600;
    const off = -new Date().getTimezoneOffset() * 60;
    cx.textAlign = "center";
    for (
      let tk = Math.ceil((x0 + off) / step) * step - off;
      tk <= x1;
      tk += step
    ) {
      const x = X(tk);
      if (x < L0 + 14 || x > R - 14) continue;
      cx.beginPath();
      cx.moveTo(x, B);
      cx.lineTo(x, B + 4);
      cx.stroke();
      cx.fillText(Chart.time(tk, step < 60), x, H - this.pb / 2 + 2);
    }

    const up = v[i1] >= v[i0],
      col = (this.o.color && this.o.color()) || (up ? C.up : C.dn);
    cx.save();
    cx.beginPath();
    cx.rect(L0, T - 2, pw, ph + 4);
    cx.clip();
    const n = i1 - i0 + 1,
      stride = Math.max(1, Math.floor(n / (pw * (CH_LITE ? 0.6 : 1.2))));
    const path = new Path2D();
    let lastI = i0;
    for (let i = i0; i <= i1; i += stride) {
      const x = X(t[i]),
        y = Y(v[i]);
      if (i === i0) path.moveTo(x, y);
      else path.lineTo(x, y);
      lastI = i;
    }
    if (lastI !== i1) path.lineTo(X(t[i1]), Y(v[i1]));
    const area = new Path2D(path);
    area.lineTo(X(t[i1]), B);
    area.lineTo(X(t[i0]), B);
    area.closePath();
    const g = cx.createLinearGradient(0, T, 0, B);
    g.addColorStop(0, Chart.alpha(col, 0.3));
    g.addColorStop(1, Chart.alpha(col, 0));
    cx.fillStyle = g;
    cx.fill(area);
    cx.lineWidth = 2 * Math.min(fs, 1.6);
    cx.lineJoin = "round";
    cx.lineCap = "round";
    cx.strokeStyle = col;
    cx.stroke(path);

    let prevY = -99;
    cx.textAlign = "left";
    for (const Ln of this.lines) {
      if (Ln.y < mn || Ln.y > mx) continue;
      const y = Y(Ln.y);
      cx.beginPath();
      cx.setLineDash(Ln.dash || []);
      cx.lineWidth = Ln.width || 1;
      cx.strokeStyle = Ln.color;
      cx.moveTo(L0, y);
      cx.lineTo(R, y);
      cx.stroke();
      cx.setLineDash([]);
      if (Ln.label && Math.abs(y - prevY) > 13) {
        cx.fillStyle = Ln.color;
        cx.fillText(Ln.label, L0 + 6, y - 7);
        prevY = y;
      }
    }
    for (const M of this.marks) {
      if (M.t < x0 || M.t > x1 || M.y < mn || M.y > mx) continue;
      const x = X(M.t),
        y = Y(M.y);
      cx.fillStyle = M.color;
      cx.beginPath();
      cx.moveTo(x, y - 6);
      cx.lineTo(x + 5, y + 3);
      cx.lineTo(x - 5, y + 3);
      cx.closePath();
      cx.fill();
    }
    cx.restore();

    if (i1 === t.length - 1 || t[t.length - 1] <= x1) {
      const lv = v[v.length - 1],
        ly = Y(lv),
        lx = X(t[t.length - 1]);
      if (ly >= T - 4 && ly <= B + 4 && lx <= R + 1) {
        cx.setLineDash([2, 4]);
        cx.strokeStyle = Chart.alpha(col, 0.55);
        cx.lineWidth = 1;
        cx.beginPath();
        cx.moveTo(Math.max(L0, lx), ly);
        cx.lineTo(R, ly);
        cx.stroke();
        cx.setLineDash([]);
        cx.fillStyle = Chart.alpha(col, 0.25);
        cx.beginPath();
        cx.arc(lx, ly, 7, 0, 6.3);
        cx.fill();
        cx.fillStyle = col;
        cx.beginPath();
        cx.arc(lx, ly, 3.5, 0, 6.3);
        cx.fill();
        const label = fmt(lv),
          w = cx.measureText(label).width + 12;
        Chart.rr(cx, R + 2, ly - 10 * fs, w, 20 * fs, 7);
        cx.fillStyle = col;
        cx.fill();
        cx.fillStyle = "#fff";
        cx.textAlign = "left";
        cx.fillText(label, R + 8, ly + 0.5);
      }
    }

    if (this.cross) {
      const px = Math.max(L0, Math.min(R, this.cross.x)),
        tx = x0 + ((px - L0) / pw) * this.span;
      let i = this.lb(tx);
      if (i >= t.length) i = t.length - 1;
      if (i > 0 && Math.abs(t[i - 1] - tx) < Math.abs(t[i] - tx)) i--;
      const xx = X(t[i]),
        yy = Y(v[i]);
      if (xx >= L0 && xx <= R) {
        cx.setLineDash([3, 3]);
        cx.strokeStyle = C.mut;
        cx.lineWidth = 1;
        cx.beginPath();
        cx.moveTo(xx, T);
        cx.lineTo(xx, B);
        cx.stroke();
        cx.setLineDash([]);
        cx.fillStyle = C.sf;
        cx.strokeStyle = col;
        cx.lineWidth = 2;
        cx.beginPath();
        cx.arc(xx, yy, 4.5, 0, 6.3);
        cx.fill();
        cx.stroke();
        const base = this.o.base ? this.o.base() : v[i0],
          pct = base ? (v[i] / base - 1) * 100 : 0;
        const l1 = Chart.time(t[i], true),
          l2 = fmt(v[i]),
          l3 = (pct >= 0 ? "+" : "") + pct.toFixed(2) + "%";
        cx.font =
          "700 " +
          (narrow ? 11 : 12) * fs +
          'px "Plus Jakarta Sans", system-ui, sans-serif';
        const bw =
            Math.max(
              cx.measureText(l1).width,
              cx.measureText(l2).width,
              cx.measureText(l3).width,
            ) + (narrow ? 14 : 20),
          bh = (narrow ? 56 : 62) * fs;
        let bx = xx + 12;
        if (bx + bw > R + this.pr - 4) bx = xx - 12 - bw;
        const by = Math.max(
          T,
          Math.min(B - bh, yy - bh - 10 < T ? yy + 12 : yy - bh - 10),
        );
        cx.save();
        if (!CH_LITE) {
          cx.shadowColor = "rgba(0,0,0,.22)";
          cx.shadowBlur = 14;
          cx.shadowOffsetY = 4;
        }
        Chart.rr(cx, bx, by, bw, bh, 12);
        cx.fillStyle = C.sf;
        cx.fill();
        if (CH_LITE) {
          cx.strokeStyle = C.line;
          cx.lineWidth = 1;
          cx.stroke();
        }
        cx.restore();
        cx.textAlign = "left";
        cx.fillStyle = C.mut;
        cx.fillText(l1, bx + 8, by + 13 * fs);
        cx.fillStyle = C.ink;
        cx.fillText(l2, bx + 8, by + 30 * fs);
        cx.fillStyle = pct >= 0 ? C.up : C.dn;
        cx.fillText(l3, bx + 8, by + (narrow ? 44 : 47) * fs);
      }
    }
  }
}
