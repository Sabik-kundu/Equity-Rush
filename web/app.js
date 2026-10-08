"use strict";
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const app = $("#app");

const S = {
  token: localStorage.getItem("br_token"),
  me: null,
  meta: { badges: [], start: 10000 },
  co: {},
  order: [],
  sectors: [],
  news: [],
  chat: [],
  view: "market",
  filter: "all",
  q: "",
  floor: "wire",
  open: null,
  viewer: null,
  side: "buy",
  qty: 1,
  se: 0,
  skew: 0,
  paused: false,
  on: 0,
  evq: [],
  evBusy: false,
  es: null,
  down: false,
  unseen: 0,
  lastW: 0,
  tickN: 0,
  qTier: "",
  detail: {},
  timers: [],
  mvSig: "",
  ix: { v: 0, open: 0, high: 0, low: 0 },
  ser: {},
  charts: {},
  spans: { ix: 600, co: 600, vw: 0 },
  dis: new Set(),
  sound: localStorage.getItem("br_snd") !== "0",
  authMode: "reg",
  avatar: "🦊",
  realmPick: "alpha",
};

const AVATARS = [
  "🦊",
  "🐯",
  "🦁",
  "🐺",
  "🐼",
  "🐸",
  "🦄",
  "🐙",
  "🦉",
  "🐲",
  "🤖",
  "👽",
  "🥷",
  "🧙",
  "🧑‍🚀",
  "🕶️",
  "🤠",
  "😎",
  "🦈",
  "🐧",
  "🦖",
  "🐻",
  "🦅",
  "🐝",
];
const NAV = [
  ["market", "market", "Market"],
  ["news", "news", "News"],
  ["portfolio", "portfolio", "Portfolio"],
  ["ranks", "ranks", "Ranks"],
  ["me", "user", "You"],
];
const TITLES = {
  market: "Market",
  news: "News and floor",
  portfolio: "Portfolio",
  ranks: "Leaderboard",
  me: "Your profile",
};
const RANGES = [
  ["10m", 600],
  ["30m", 1800],
  ["2h", 7200],
  ["All", 0],
];
const SENT = {
  2: ["s2", "Very positive"],
  1: ["s1", "Mild positive"],
  0: ["s0", "Neutral"],
  "-1": ["sn1", "Mild negative"],
  "-2": ["sn2", "Very negative"],
};
const REALM_HUE = { alpha: 250, blitz: 25, zen: 150 };
const EVENT_ICON = {
  level: "level",
  badge: "star",
  mission: "check",
};
const QUOTE = {
  hi: [
    "We've never been stronger.",
    "Numbers don't lie. Neither do I.",
    "Thank you for believing in us.",
  ],
  up: ["Momentum is on our side.", "Steady growth, as promised.", "Onward."],
  mid: [
    "Nothing to report. Genuinely.",
    "We remain focused on execution.",
    "Business as usual.",
  ],
  dn: [
    "Headwinds are temporary.",
    "We remain cautiously optimistic.",
    "This is a marathon.",
  ],
  lo: [
    "Please stop refreshing the app.",
    "We're looking into it.",
    "I asked the intern to fix the stock price.",
  ],
};
const T = (el, t) => {
  if (el && el._t !== t) {
    el._t = t;
    el.textContent = t;
  }
};
const H = (el, h) => {
  if (el && el._h !== h) {
    el._h = h;
    el.innerHTML = h;
  }
};
const CLS = (el, c) => {
  if (el && el._c !== c) {
    el._c = c;
    el.className = c;
  }
};
const flash = (el, up) => {
  if (el) {
    el._f = !el._f;
    el.dataset.fl = (up ? "u" : "d") + (el._f ? 1 : 0);
  }
};
const pick = (a) => a[Math.floor(Math.random() * a.length)];

const fmtN = (n, d = 2) =>
  Number(n).toLocaleString("en-US", {
    minimumFractionDigits: d,
    maximumFractionDigits: d,
  });
const money = (n, d = 2) => "Ꞩ" + fmtN(n, d);
const money0 = (n) => money(n, 0);
const sm = (n) => (n < 0 ? "-" : "+") + money(Math.abs(n));
const compact = (n) =>
  n >= 1e12
    ? "Ꞩ" + (n / 1e12).toFixed(2) + "T"
    : n >= 1e9
      ? "Ꞩ" + (n / 1e9).toFixed(1) + "B"
      : n >= 1e6
        ? "Ꞩ" + (n / 1e6).toFixed(0) + "M"
        : money0(n);
const pc = (n) => (n >= 0 ? "+" : "") + n.toFixed(2) + "%";
const esc = (s) =>
  String(s).replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const now = () => Date.now() + S.skew;
const ago = (t) => {
  const s = Math.max(0, ((now() - t) / 1000) | 0);
  return s < 5
    ? "just now"
    : s < 60
      ? s + "s ago"
      : s < 3600
        ? ((s / 60) | 0) + "m ago"
        : ((s / 3600) | 0) + "h ago";
};
const mmss = (ms) => {
  const s = Math.ceil(ms / 1000);
  return ((s / 60) | 0) + ":" + String(s % 60).padStart(2, "0");
};
const tier = (m) =>
  m < 15 ? "lo" : m < 38 ? "dn" : m < 62 ? "mid" : m < 85 ? "up" : "hi";
const moodLabel = (m) =>
  ({ lo: "Panic", dn: "Nervous", mid: "Calm", up: "Bullish", hi: "Euphoric" })[
    tier(m)
  ];
const chg = (c) => (c.price / c.open - 1) * 100;
const buzz = (ms) => {
  try {
    navigator.vibrate && navigator.vibrate(ms);
  } catch (e) {}
};
const main = () => $("#main");
const monoTxt = (c) => {
  const w = c.name.split(/\s+/).filter(Boolean);
  return esc(
    (w.length > 1 ? w[0][0] + w[1][0] : c.name.slice(0, 2)).toUpperCase(),
  );
};
const monoEl = (c) =>
  `<div class="mono" style="--h:${c.hue}">${monoTxt(c)}</div>`;
const sentOf = (n) => SENT[String(n.sent)] || SENT[0];

/* ---------- dismissed stories (swipe to remove) ---------- */

const disKey = () =>
  "br_dis:" + (S.realm ? S.realm.name : "") + ":" + (S.me ? S.me.name : "");
function loadDismissed() {
  try {
    S.dis = new Set(
      JSON.parse(localStorage.getItem(disKey()) || "[]").map(String),
    );
  } catch (e) {
    S.dis = new Set();
  }
}
function saveDismissed() {
  try {
    localStorage.setItem(disKey(), JSON.stringify([...S.dis].slice(-400)));
  } catch (e) {}
}
const shownNews = () => S.news.filter((n) => !S.dis.has(String(n.id)));
function dismissNews(id) {
  S.dis.add(String(id));
  saveDismissed();
  if (S.view === "news") paintWire();
  if (S.view === "market") paintWireTile();
}

(function initSwipe() {
  let st = null,
    lastSwipe = 0;
  const reset = (el) => {
    el.style.transition = "transform .18s ease, opacity .18s ease";
    el.style.transform = "";
    el.style.opacity = "";
    el.classList.remove("swiping");
    setTimeout(() => {
      el.style.transition = "";
    }, 200);
  };
  document.addEventListener("pointerdown", (e) => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    const el = e.target.closest("[data-nid]");
    if (!el || e.target.closest("[data-intel],[data-dismiss]")) return;
    st = { el, x: e.clientX, y: e.clientY, dx: 0, on: false, id: e.pointerId };
  });
  document.addEventListener("pointermove", (e) => {
    if (!st || e.pointerId !== st.id) return;
    const dx = e.clientX - st.x,
      dy = e.clientY - st.y;
    if (!st.on) {
      if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) {
        st = null;
        return;
      }
      if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy) * 1.2) {
        st.on = true;
        st.el.classList.add("swiping");
        try {
          st.el.setPointerCapture(e.pointerId);
        } catch (_) {}
      } else return;
    }
    st.dx = dx;
    st.el.style.transform = "translateX(" + dx + "px)";
    st.el.style.opacity = Math.max(
      0.25,
      1 - Math.abs(dx) / (st.el.offsetWidth * 0.9),
    );
  });
  const end = (e, cancelled) => {
    if (!st || e.pointerId !== st.id) return;
    const s = st;
    st = null;
    if (!s.on) return;
    lastSwipe = Date.now();
    const w = s.el.offsetWidth,
      far = Math.abs(s.dx) > Math.max(90, w * 0.35);
    if (far && !cancelled) {
      s.el.style.transition = "transform .2s ease, opacity .2s ease";
      s.el.style.transform =
        "translateX(" + (s.dx > 0 ? 1 : -1) * w * 1.1 + "px)";
      s.el.style.opacity = "0";
      buzz(15);
      setTimeout(() => dismissNews(s.el.dataset.nid), 190);
    } else reset(s.el);
  };
  document.addEventListener("pointerup", (e) => end(e, false));
  document.addEventListener("pointercancel", (e) => end(e, true));
  // a swipe must not also count as a tap on the story underneath
  document.addEventListener(
    "click",
    (e) => {
      if (Date.now() - lastSwipe < 350) {
        e.stopPropagation();
        e.preventDefault();
      }
    },
    true,
  );
})();

function calc() {
  let invested = 0,
    value = 0;
  S.me.hold.forEach((h) => {
    const c = S.co[h.id];
    invested += h.qty * h.avg;
    value += h.qty * (c ? c.price : h.avg);
  });
  return {
    invested,
    value,
    unreal: value - invested,
    worth: S.me.cash + value,
  };
}
const worth = () => calc().worth;

/* ---------- theme ---------- */

const themeMode = () => localStorage.getItem("br_theme") || "auto";
const isDark = () => {
  const m = themeMode();
  return (
    m === "dark" ||
    (m === "auto" && matchMedia("(prefers-color-scheme: dark)").matches)
  );
};
function applyTheme(mode) {
  if (mode) localStorage.setItem("br_theme", mode);
  document.documentElement.dataset.theme = isDark() ? "dark" : "light";
  const meta = $('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", isDark() ? "#131625" : "#e8ecf5");
  Chart._c = null;
  Object.keys(S.charts).forEach((k) => {
    const c = S.charts[k];
    if (c) {
      chartExtras(c, c.sid);
      c.draw();
    }
  });
  const b = $("#themebtn");
  if (b) b.innerHTML = ic(isDark() ? "sun" : "moon");
  if (S.view === "me" && main())
    $$("[data-tm]").forEach((x) =>
      x.classList.toggle("on", x.dataset.tm === themeMode()),
    );
}
try {
  matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (themeMode() === "auto") applyTheme();
  });
} catch (e) {}

async function api(path, body) {
  const r = await fetch("/api/" + path, {
    method: body ? "POST" : "GET",
    headers: { "Content-Type": "application/json", "X-Token": S.token || "" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) {
    if (r.status === 401 && S.token) logout();
    if (r.status === 423 && S.token) logout(j.error);
    throw new Error(j.error || "Something went wrong");
  }
  return j;
}

let ac;
function beep(f = 660, d = 0.09, type = "sine", v = 0.05, delay = 0) {
  if (!S.sound) return;
  try {
    ac = ac || new (window.AudioContext || window.webkitAudioContext)();
    const o = ac.createOscillator(),
      g = ac.createGain(),
      t = ac.currentTime + delay;
    o.type = type;
    o.frequency.value = f;
    g.gain.value = v;
    o.connect(g);
    g.connect(ac.destination);
    o.start(t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    o.stop(t + d);
  } catch (e) {}
}
const chime = () =>
  [523, 659, 784, 1046].forEach((f, i) =>
    beep(f, 0.14, "triangle", 0.05, i * 0.08),
  );
const ding = (up) => {
  beep(up ? 740 : 300, 0.1, "square", 0.03);
  beep(up ? 990 : 220, 0.12, "square", 0.03, 0.08);
};

function toast(msg, cls = "") {
  const t = document.createElement("div");
  t.className = "toast " + cls;
  t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2700);
}

function celebrate(n = 26) {
  const fx = $("#fx"),
    colors = ["#5b5fef", "#0a9c70", "#f2a007", "#e63a66", "#8a63f2", "#3fb8ff"];
  for (let i = 0; i < n; i++) {
    const s = document.createElement("span");
    s.style.left = Math.random() * 100 + "%";
    s.style.background = pick(colors);
    s.style.animationDuration = 1.6 + Math.random() * 1.6 + "s";
    s.style.animationDelay = Math.random() * 0.5 + "s";
    fx.appendChild(s);
    setTimeout(() => s.remove(), 4200);
  }
}

function handleEvents(list) {
  (list || []).forEach((e) => S.evq.push(e));
  if (!S.evBusy) nextEvent();
}
function nextEvent() {
  const e = S.evq.shift();
  if (!e) {
    S.evBusy = false;
    return;
  }
  S.evBusy = true;
  const p = document.createElement("div");
  p.className = "pop";
  p.innerHTML = `<span class="pi">${ic(EVENT_ICON[e.k] || "star")}</span><div><b>${esc(e.title)}</b><small>${esc(e.sub)}</small></div>`;
  document.body.appendChild(p);
  celebrate(e.k === "level" ? 40 : 22);
  buzz([40, 60, 40]);
  chime();
  setTimeout(() => p.remove(), 2600);
  setTimeout(nextEvent, 2700);
}

function spark(a, w = 90, h = 26) {
  const mn = Math.min(...a),
    mx = Math.max(...a),
    r = mx - mn || 1;
  const pts = a
    .map(
      (v, i) =>
        ((i * w) / (a.length - 1)).toFixed(1) +
        "," +
        (h - 2 - ((v - mn) / r) * (h - 4)).toFixed(1),
    )
    .join(" ");
  return `<svg class="sp ${a[a.length - 1] >= a[0] ? "u" : "d"}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none"><polyline points="${pts}"/></svg>`;
}

/* ---------- series and charts ---------- */

function ser(id) {
  return S.ser[id] || (S.ser[id] = { t: [], v: [], loaded: false });
}

function seed(id) {
  const s = ser(id);
  if (s.t.length) return s;
  const n = now() / 1000;
  if (id === "INDEX") {
    s.t.push(n);
    s.v.push(S.ix.v);
  } else if (S.co[id]) {
    const h = S.co[id].hist;
    h.forEach((v, i) => {
      s.t.push(n - (h.length - 1 - i));
      s.v.push(v);
    });
  }
  return s;
}

async function loadSeries(id) {
  const s = seed(id);
  if (s.loaded) return s;
  try {
    const r = await api("history?id=" + encodeURIComponent(id));
    if (r.t.length > 1) {
      s.t = r.t.slice();
      s.v = r.v.slice();
    }
    s.loaded = true;
    Object.values(S.charts).forEach((c) => {
      if (c && c.sid === id) c.appended();
    });
  } catch (e) {}
  return s;
}

function pushSer(id, t, v) {
  const s = S.ser[id];
  if (!s) return;
  const n = s.t.length;
  if (n && t <= s.t[n - 1]) return;
  s.t.push(t);
  s.v.push(v);
  if (n > 9000) {
    s.t.splice(0, 1500);
    s.v.splice(0, 1500);
  }
}

function chartExtras(ch, id) {
  const C = Chart.colors();
  if (id === "INDEX") {
    ch.o.base = () => S.ix.open;
    ch.lines = [
      { y: S.ix.open, color: C.mut, dash: [6, 4], label: "Session open" },
    ];
    ch.marks = [];
    return;
  }
  const c = S.co[id];
  if (!c) return;
  const lines = [
      { y: c.open, color: C.mut, dash: [6, 4], label: "Session open" },
    ],
    marks = [];
  ch.o.base = () => c.open;
  if (c.lim) {
    lines.push({
      y: c.lim.hi,
      color: C.up,
      dash: [2, 4],
      width: 1.5,
      label: "Upper limit",
    });
    lines.push({
      y: c.lim.lo,
      color: C.dn,
      dash: [2, 4],
      width: 1.5,
      label: "Lower limit",
    });
  }
  const h = S.me.hold.find((x) => x.id === id);
  if (h)
    h.lots.forEach((l) => {
      lines.push({
        y: l.p,
        color: C.pri,
        dash: [4, 4],
        width: 1,
        range: true,
        label: "Bought " + fmtN(l.q, 0) + " at " + money(l.p),
      });
      if (l.t) marks.push({ t: l.t, y: l.p, color: C.pri });
    });
  ch.lines = lines;
  ch.marks = marks;
}

function mount(key, host, id, o = {}) {
  if (S.charts[key]) {
    S.charts[key].destroy();
    S.charts[key] = null;
  }
  if (!host) return null;
  const s = seed(id);
  const ch = new Chart(
    host,
    Object.assign({ source: s, fmt: (v) => fmtN(v) }, o),
  );
  ch.sid = id;
  ch.key = key;
  S.charts[key] = ch;
  chartExtras(ch, id);
  ch.setSpan(S.spans[key] ?? 600);
  loadSeries(id);
  return ch;
}

function killCharts(keys) {
  keys.forEach((k) => {
    if (S.charts[k]) {
      S.charts[k].destroy();
      S.charts[k] = null;
    }
  });
}

const rangeChips = (key) =>
  RANGES.map(
    (r) =>
      `<button data-range="${r[1]}" data-ckey="${key}" class="${(S.spans[key] ?? 600) === r[1] ? "on" : ""}">${r[0]}</button>`,
  ).join("");

/* ---------- auth ---------- */

const devId = () => {
  try {
    let d = localStorage.getItem("br_dev");
    if (!d) {
      d = (crypto.randomUUID && crypto.randomUUID()) || Date.now() + "-" + Math.random().toString(36).slice(2);
      localStorage.setItem("br_dev", d);
    }
    return d;
  } catch (e) {
    return "";
  }
};
const acctGet = () => {
  try {
    const a = localStorage.getItem("br_acct");
    if (a) return a;
  } catch (e) {}
  const m = document.cookie.match(/(?:^|; )br_acct=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : "";
};
const acctSet = (name) => {
  try {
    localStorage.setItem("br_acct", name);
  } catch (e) {}
  document.cookie =
    "br_acct=" + encodeURIComponent(name) + "; max-age=31536000; path=/; SameSite=Lax";
};
const regLeft = (r) => (r.regClosesAt ? r.regClosesAt - Date.now() : 0);
const regState = (r) =>
  r.open === false
    ? "closed"
    : r.regOpen === false || (r.regClosesAt && regLeft(r) <= 0)
      ? "regclosed"
      : "ok";
function realmTile(r) {
  const st = regState(r),
    left = regLeft(r);
  const rc =
    st === "closed"
      ? "<b>Closed</b><small>for now</small>"
      : st === "regclosed"
        ? "<b>Full</b><small>joining closed</small>"
        : `<b>${r.players}</b><small>${left > 0 ? "closes in " + mmss(left) : "traders"}</small>`;
  return `<button class="realm ${r.id === S.realmPick ? "on" : ""}" data-realm="${r.id}" ${st !== "ok" ? "disabled" : ""}><div class="mono" style="--h:${REALM_HUE[r.id] ?? 200}">${esc(r.name[0])}</div>
    <div><b>${esc(r.name)}</b><small>${esc(r.blurb)}</small></div>
    <div class="rc" data-closes="${r.regClosesAt || 0}">${rc}</div></button>`;
}

async function showAuth(msg) {
  S.timers.forEach(clearInterval);
  S.timers = [];
  if (S.es) {
    S.es.close();
    S.es = null;
  }
  killCharts(Object.keys(S.charts));
  let realms = [];
  try {
    realms = await fetch("/api/realms").then((r) => r.json());
  } catch (e) {}
  const owned = acctGet(),
    anyOpen = realms.some((r) => regState(r) === "ok");
  app.className = "authmode";
  app.innerHTML = `
  <div class="auth">
    <div class="abrand">
      <div class="brand"><div class="mark">${LOGO}</div><span class="kick">Live market game</span><h1>Equity Rush</h1><p>Trade imaginary companies. Outsmart real people.</p></div>
      <div class="feats">
        <div class="feat"><span class="ficon">${ic("graph")}</span><b>Live prices</b><small>Every second, moved by news and by other players.</small></div>
        <div class="feat"><span class="ficon">${ic("news")}</span><b>Breaking news</b><small>Read fast. Rumors may be true, or not.</small></div>
        <div class="feat"><span class="ficon">${ic("ranks")}</span><b>Real rivals</b><small>Climb the leaderboard on your server.</small></div>
      </div>
    </div>
    <div class="aform t clay">
      <div class="seg" id="mode"><button data-m="reg" class="on">Create account</button><button data-m="login">Sign in</button></div>
      <div id="regonly" class="avs">${AVATARS.map((a) => `<button data-av="${a}" class="${a === S.avatar ? "on" : ""}">${a}</button>`).join("")}</div>
      <input class="field" id="nm" placeholder="Trader name" maxlength="16" autocomplete="username" autocapitalize="off">
      <input class="field" id="pw" type="password" placeholder="Password" autocomplete="current-password">
      <div class="realms" id="realms">${realms.map(realmTile).join("")}</div>
      <button class="btn lg" id="go">Enter the floor</button>
      <div class="err" id="err"></div>
    </div>
  </div>`;
  const firstOpen = realms.find((r) => regState(r) === "ok");
  if (
    firstOpen &&
    !realms.some((r) => r.id === S.realmPick && regState(r) === "ok")
  ) {
    S.realmPick = firstOpen.id;
    $$("#realms button").forEach((x) =>
      x.classList.toggle("on", x.dataset.realm === S.realmPick),
    );
  }
  $("#err").textContent = msg || "";
  const setMode = (m, keepMsg) => {
    S.authMode = m;
    $$("#mode button").forEach((b) =>
      b.classList.toggle("on", b.dataset.m === m),
    );
    $("#regonly").style.display = m === "reg" ? "grid" : "none";
    $("#realms").style.display = m === "reg" ? "grid" : "none";
    $("#go").textContent = m === "reg" ? "Enter the floor" : "Sign in";
    $("#pw").autocomplete = m === "reg" ? "new-password" : "current-password";
    if (m === "login" && owned && !$("#nm").value) $("#nm").value = owned;
    const note =
      m !== "reg"
        ? ""
        : owned
          ? "This device already has an account (" + owned + "). Sign in with it instead."
          : !anyOpen
            ? "Joining is closed right now. Sign in if you already have an account."
            : "";
    $("#go").disabled = m === "reg" && !!note;
    if (!keepMsg) $("#err").textContent = note;
  };
  $("#mode").onclick = (e) => {
    const b = e.target.closest("button");
    if (b) setMode(b.dataset.m);
  };
  S.timers.push(
    setInterval(() => {
      let expired = false;
      $$("#realms .rc[data-closes]").forEach((el) => {
        const t = +el.dataset.closes;
        if (!t) return;
        const left = t - Date.now();
        if (left <= 0) expired = true;
        else {
          const s = $("small", el);
          if (s) s.textContent = "closes in " + mmss(left);
        }
      });
      if (expired) showAuth($("#err") ? $("#err").textContent : "");
    }, 1000),
  );
  $("#regonly").onclick = (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    S.avatar = b.dataset.av;
    $$("#regonly button").forEach((x) => x.classList.toggle("on", x === b));
  };
  $("#realms").onclick = (e) => {
    const b = e.target.closest("button");
    if (!b || b.disabled) return;
    S.realmPick = b.dataset.realm;
    $$("#realms button").forEach((x) => x.classList.toggle("on", x === b));
  };
  const go = async () => {
    const name = $("#nm").value.trim(),
      password = $("#pw").value,
      btn = $("#go");
    if (S.authMode === "reg" && acctGet()) {
      $("#err").textContent =
        "This device already has an account (" + acctGet() + "). Sign in with it instead.";
      return;
    }
    btn.disabled = true;
    const btnText = btn.textContent;
    btn.textContent = "Opening the floor…";
    $("#err").textContent = "";
    try {
      const r =
        S.authMode === "reg"
          ? await api("register", {
              name,
              password,
              avatar: S.avatar,
              realm: S.realmPick,
              device: devId(),
            })
          : await api("login", { name, password });
      S.token = r.token;
      localStorage.setItem("br_token", r.token);
      if (S.authMode === "reg") acctSet(name);
      if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
      await enter();
      window.scrollTo(0, 0);
      introIn();
    } catch (e) {
      const el = $("#err");
      if (el) el.textContent = e.message;
      btn.disabled = false;
      btn.textContent = btnText;
    }
  };
  $("#go").onclick = go;
  $("#pw").onkeydown = (e) => {
    if (e.key === "Enter" && !$("#go").disabled) go();
  };
  setMode(owned || !anyOpen ? "login" : "reg", !!msg);
  if (window.gsap && !matchMedia("(prefers-reduced-motion: reduce)").matches) {
    gsap.from(".brand > *", { opacity: 0, y: 18, duration: 0.6, stagger: 0.07, ease: "power3.out", clearProps: "all" });
    gsap.from(".aform", { opacity: 0, y: 28, duration: 0.65, delay: 0.15, ease: "power3.out", clearProps: "all" });
  }
}

function logout(msg) {
  localStorage.removeItem("br_token");
  S.token = null;
  S.me = null;
  S.open = null;
  S.viewer = null;
  S.ser = {};
  document.body.classList.remove("modal");
  showAuth(typeof msg === "string" ? msg : "");
}

/* ---------- boot ---------- */
/* keep the layout glued to the screen after the on-screen keyboard closes */
(function () {
  const fix = () => {
    const t = ((document.activeElement || {}).tagName || "").toUpperCase();
    if (t !== "INPUT" && t !== "TEXTAREA") window.scrollTo(0, 0);
  };
  if (window.visualViewport) visualViewport.addEventListener("resize", fix);
  addEventListener("orientationchange", () => setTimeout(fix, 300));
  document.addEventListener("focusout", () => setTimeout(fix, 150));
})();

/* soft entrance for the app after sign-in */
function introIn() {
  if (!window.gsap || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  const st = $(".stage");
  if (st) gsap.fromTo(st, { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: 0.45, ease: "power3.out", clearProps: "all" });
  const nv = $("nav");
  if (nv) gsap.fromTo(nv, { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.5, delay: 0.1, ease: "back.out(1.4)", clearProps: "transform,opacity" });
}

async function enter() {
  /* the sign-in form may have scrolled #app to keep an input above the keyboard */
  app.scrollTop = 0;
  window.scrollTo(0, 0);
  const [me, mk, meta] = await Promise.all([
    api("me"),
    api("market"),
    fetch("/api/meta").then((r) => r.json()),
  ]);
  S.me = me;
  S.meta = meta;
  S.ser = {};
  loadMarket(mk);
  loadDismissed();
  S.view = "market";
  S.filter = "all";
  S.q = "";
  S.unseen = 0;
  S.lastW = worth();
  app.scrollTop = 0;
  shell();
  app.scrollTop = 0;
  render();
  try {
    connect();
  } catch (e) {}
  handleEvents(me.events);
  S.timers.forEach(clearInterval);
  S.timers = [
    setInterval(refreshMe, 12000),
    setInterval(() => {
      if (S.view === "ranks") renderRanks(true);
    }, 8000),
    setInterval(paintClock, 1000),
  ];
}

function loadMarket(mk) {
  S.skew = mk.now - Date.now();
  S.se = mk.se;
  S.on = mk.on;
  S.paused = mk.realm.paused;
  S.realm = mk.realm;
  S.co = {};
  S.order = [];
  S.sectors = mk.sectors;
  S.ix = mk.ix;
  mk.c.forEach((c) => {
    S.co[c.id] = c;
    S.order.push(c.id);
  });
  S.news = mk.news;
  S.chat = mk.chat;
}

async function resync() {
  try {
    loadMarket(await api("market"));
    S.me = await api("me");
    Object.keys(S.ser).forEach((k) => {
      S.ser[k].loaded = false;
      S.ser[k].t = [];
      S.ser[k].v = [];
      seed(k);
    });
    rebuildTape();
    paintTop();
    paintNav();
    if (S.open && !S.co[S.open]) closeCo();
    if (S.view !== "ranks") render();
    if (S.open) {
      paintDetail(true);
    }
    Object.values(S.charts).forEach((c) => {
      if (c) {
        chartExtras(c, c.sid);
        loadSeries(c.sid);
      }
    });
  } catch (e) {}
}

async function refreshMe() {
  if (!S.token) return;
  try {
    S.me = await api("me");
    handleEvents(S.me.events);
    paintTop();
    paintNav();
    if (S.view === "me") renderMe();
  } catch (e) {}
}

function shell() {
  app.className = "";
  app.innerHTML = `
  <div class="brk" id="brk"></div>
  <aside class="side">
    <div class="logo">${LOGO}<b>Equity Rush</b></div>
    <nav>${NAV.map((n) => `<button data-v="${n[0]}"><span class="ic">${ic(n[1])}</span><em>${n[2]}</em></button>`).join("")}</nav>
    <div class="pcard"><div class="av" id="sav"></div><div><b id="sn"></b><small><span class="live"></span><span id="sr"></span></small></div></div>
  </aside>
  <div class="stage">
    <header>
      <div class="who"><div class="av" id="hav"></div><div><b id="hn"></b><small><span class="live"></span><span id="hr"></span></small></div></div>
      <div class="ttl"><h1 id="pt"></h1><small id="ps"></small></div>
      <div class="hr"><div class="tops"><div class="sc"><small>Cash</small><b id="hc"></b></div><div class="sc"><small>Net worth</small><b id="hw"></b></div></div>
        <button class="themebtn" id="themebtn" data-act="theme" aria-label="Toggle dark mode"></button></div>
    </header>
    <div class="tape"><div class="track" id="tape"></div></div>
    <main id="main"></main>
  </div>
  <div class="sheet" id="sheet"></div>
  <div class="viewer" id="viewer"></div>`;
  $("#hn").textContent = $("#sn").textContent = S.me.name;
  $("#hr").textContent = $("#sr").textContent = S.realm.name;
  $("#sheet").addEventListener("click", (e) => {
    if (e.target.id === "sheet") closeCo();
  });
  $("#viewer").addEventListener("click", (e) => {
    if (e.target.id === "viewer") closeViewer();
  });
  rebuildTape();
  paintTop();
  paintNav();
  applyTheme();
}

function rebuildTape() {
  const items = S.order
    .map(
      (id) =>
        `<span class="ti" data-id="${id}"><b>${id}</b><i class="tp"></i><i class="tc"></i></span>`,
    )
    .join("");
  $("#tape").innerHTML = items + items;
  S.tapeEls = null;
  paintTape();
}

function setLive(on) {
  $$(".live").forEach((l) => l.classList.toggle("off", !on));
}

function connect() {
  if (S.es) S.es.close();
  const es = (S.es = new EventSource(
    "/api/stream?token=" + encodeURIComponent(S.token),
  ));
  es.onopen = () => {
    setLive(true);
    if (S.down) {
      S.down = false;
      resync();
    }
  };
  es.onerror = () => {
    S.down = true;
    setLive(false);
    if (es.readyState === 2) api("me").catch(() => {});
  };
  const handlers = {
    tick: onTick,
    news: onNews,
    companies: () => resync(),
    closed: () =>
      logout("This arena was closed by the admin. Try another one."),
    ceo: (m) => {
      const c = S.co[m.c.id];
      if (c) {
        c.ceo = m.c.ceo;
        if (S.open === c.id) paintDetail(true);
      }
    },
    chat: (m) => {
      S.chat.push(m.m);
      if (S.chat.length > 60) S.chat.shift();
      if (S.view === "news") paintFloor(true);
    },
    chatclear: () => {
      S.chat = [];
      if (S.view === "news") paintFloor();
    },
  };
  es.onmessage = (e) => {
    let m;
    try {
      m = JSON.parse(e.data);
    } catch (_) {
      return;
    }
    if (handlers[m.type]) handlers[m.type](m);
  };
}

/* ---------- live updates ---------- */

function onTick(m) {
  S.tickN++;
  const ts = m.t / 1000;
  let unknown = false;
  m.c.forEach(([id, p, o, hi, lo, mo, hit, limLo, limHi, av, fl, fw]) => {
    const c = S.co[id];
    if (!c) {
      unknown = true;
      return;
    }
    c.flash = p > c.price ? "fu" : p < c.price ? "fd" : "";
    c.price = p;
    c.open = o;
    c.high = hi;
    c.low = lo;
    c.mood = mo;
    c.avail = av;
    c.float = fl;
    c.flow = fw;
    if (c.lim && limLo > 0) {
      c.lim.hit = hit;
      c.lim.lo = limLo;
      c.lim.hi = limHi;
      c.lim.band = (limHi / o - 1) * 100;
    }
    c.hist.push(p);
    if (c.hist.length > 120) c.hist.shift();
    pushSer(id, ts, p);
  });
  if (unknown) {
    resync();
    return;
  }
  S.ix = { v: m.ix[0], open: m.ix[1], high: m.ix[2], low: m.ix[3] };
  pushSer("INDEX", ts, m.ix[0]);
  S.se = m.se;
  S.paused = m.paused;
  S.on = m.on;
  S.skew = m.t - Date.now();
  if (!S.raf) S.raf = requestAnimationFrame(flush);
}

function flush() {
  S.raf = 0;
  if (document.hidden) return;
  Object.keys(S.charts).forEach((k) => {
    const ch = S.charts[k];
    if (!ch) return;
    if (!ch.host.isConnected) {
      ch.destroy();
      S.charts[k] = null;
      return;
    }
    chartExtras(ch, ch.sid);
    ch.appended();
  });
  paintTop();
  if (S.tickN % 2 === 0) paintTape();
  const covered = !!(S.open || S.viewer);
  if (S.view === "market" && !covered) paintCards();
  else if (S.view === "portfolio" && !covered) paintPortfolio();
  if (S.open) paintDetail();
  if (S.viewer) paintViewer();
}

function onNews(m) {
  const n = m.n,
    i = S.news.findIndex((x) => x.id === n.id);
  if (i >= 0) {
    S.news[i] = n;
  } else {
    S.news.unshift(n);
    if (S.news.length > 60) S.news.pop();
    banner(n);
    if (S.view !== "news") {
      S.unseen++;
      paintNav();
    }
  }
  if (S.view === "news") paintWire();
  if (S.view === "market") paintWireTile();
}

let brkT;
function banner(n) {
  const el = $("#brk");
  if (!el) return;
  const c = n.tType === "company" ? S.co[n.tId] : null,
    sc = sentOf(n);
  const label =
    n.kind === "rumor" ? "Rumor" : n.kind === "update" ? "Update" : "Breaking";
  const badge = c
    ? monoEl(c)
    : `<div class="mono" style="--h:0">${n.tType === "sector" ? esc(n.tId.slice(0, 2).toUpperCase()) : "MK"}</div>`;
  el.className = "brk on " + sc[0];
  el.innerHTML = `${badge}<div><small>${label}, ${sc[1].toLowerCase()}</small><b>${esc(n.headline)}</b></div>`;
  el.onclick = () => {
    el.classList.remove("on");
    if (c) openCo(c.id);
    else go("news");
  };
  buzz([30, 40, 30]);
  ding(n.sent >= 0);
  clearTimeout(brkT);
  brkT = setTimeout(() => el.classList.remove("on"), 6500);
}

/* ---------- shell painting ---------- */

function paintTop() {
  const w = worth(),
    el = $("#hw");
  if (!el) return;
  const t = money(w);
  if (el._t !== t) {
    flash(el, w >= S.lastW);
    S.lastW = w;
  }
  T(el, t);
  T($("#hc"), money0(S.me.cash));
  paintWorthTile();
}

function paintNav() {
  $$("nav button").forEach((b) => {
    b.classList.toggle("on", b.dataset.v === S.view);
    const d = $(".dot", b);
    if (d) d.remove();
    const show = b.dataset.v === "news" && S.unseen > 0;
    if (show) b.insertAdjacentHTML("beforeend", '<i class="dot"></i>');
  });
  $("#hav").textContent = $("#sav").textContent = S.me.avatar;
  $("#pt").textContent = TITLES[S.view];
  $("#ps").textContent = S.realm.name;
}

function paintTape() {
  if (!S.tapeEls) S.tapeEls = {};
  S.order.forEach((id) => {
    const c = S.co[id],
      ch = chg(c);
    if (!S.tapeEls[id] || !S.tapeEls[id][0].isConnected)
      S.tapeEls[id] = $$(`.ti[data-id="${id}"]`).map((el) => [
        $(".tp", el),
        $(".tc", el),
      ]);
    const t = money(c.price),
      p = pc(ch),
      cl = "tc " + (ch >= 0 ? "u" : "d");
    S.tapeEls[id].forEach(([a, b]) => {
      T(a, t);
      T(b, p);
      CLS(b, cl);
    });
  });
}

function paintClock() {
  if (S.view === "market") paintIndex();
}

function go(v) {
  S.view = v;
  if (v === "news") S.unseen = 0;
  killCharts(["ix"]);
  closeViewer();
  closeCo();
  paintNav();
  render();
  main().scrollTop = 0;
}

function render() {
  if (!main()) return;
  if (S._lv !== S.view) {
    S._lv = S.view;
    const m = main();
    m.classList.remove("vin");
    void m.offsetWidth;
    m.classList.add("vin");
    clearTimeout(S._vt);
    S._vt = setTimeout(() => m.classList.remove("vin"), 700);
  }
  ({
    market: renderMarket,
    news: renderNews,
    portfolio: renderPortfolio,
    ranks: () => renderRanks(false),
    me: renderMe,
  })[S.view]();
}

/* ---------- market ---------- */

function visible() {
  let ids = S.order.slice();
  if (S.filter === "watch") ids = ids.filter((i) => S.me.watch.includes(i));
  else if (S.filter === "movers")
    ids.sort((a, b) => Math.abs(chg(S.co[b])) - Math.abs(chg(S.co[a])));
  else if (S.filter !== "all")
    ids = ids.filter((i) => S.co[i].sector === S.filter);
  const q = S.q.trim().toLowerCase();
  if (q)
    ids = ids.filter(
      (i) =>
        i.toLowerCase().includes(q) || S.co[i].name.toLowerCase().includes(q),
    );
  return ids.map((i) => S.co[i]);
}

function limTag(c) {
  if (c.avail === 0) return '<span class="tagc lim d">Sold out</span>';
  return c.lim && c.lim.hit
    ? `<span class="tagc lim ${c.lim.hit > 0 ? "u" : "d"}">${c.lim.hit > 0 ? "Upper limit" : "Lower limit"}</span>`
    : "";
}

function card(c) {
  const ch = chg(c);
  return `<button class="co" data-open="${c.id}" data-id="${c.id}" style="--h:${c.hue}">
    ${monoEl(c)}
    <div class="cm"><b>${esc(c.name)}</b><div class="tags"><span class="tagc h">${c.id}</span><span class="tagc">${esc(c.sector)}</span><span class="lt">${limTag(c)}</span></div></div>
    <div class="cp"><div class="px">${money(c.price)}</div><span class="chg ${ch >= 0 ? "u" : "d"}">${pc(ch)}</span></div>
    <div class="spw">${spark(c.hist)}</div>
    ${S.me.watch.includes(c.id) ? `<span class="star">${ic("star", "fill")}</span>` : ""}
  </button>`;
}

function listHtml() {
  return (
    visible().map(card).join("") ||
    `<div class="empty t clay">No companies match.<br><small>Try another filter, or star a company to watch it.</small></div>`
  );
}

function renderMarket() {
  const chips = [
    ["all", "All"],
    ["watch", "Watchlist"],
    ["movers", "Movers"],
    ...S.sectors.map((s) => [s, s]),
  ];
  S.mvSig = "";
  main().innerHTML = `
  <div class="bento">
    <section class="t clay s8 ctile">
      <div class="ch"><div><small>Total market index</small><div class="ixv"><b id="ixv"></b><span class="chg" id="ixc"></span></div></div>
        <div class="rchips">${rangeChips("ix")}</div></div>
      <div class="chartbox" id="ixchart" data-view="INDEX"></div>
      <div class="ctools"><small id="ixsub"></small><button class="ib" data-view="INDEX" aria-label="Expand chart">${ic("expand")}</button></div>
    </section>
    <section class="t clay s4 dsk worth">
      <small>Net worth</small><div class="big" id="wtw"></div>
      <div class="row"><span class="pill" id="wtg"></span><span class="pill n" id="wtr"></span></div>
      <div class="mini"><div class="well"><small>Cash</small><b id="wtc"></b></div><div class="well"><small>Invested</small><b id="wti"></b></div>
        <div class="well"><small>Holdings value</small><b id="wtv"></b></div><div class="well"><small>Unrealized P/L</small><b id="wtu"></b></div></div>
    </section>
    <section class="t clay s6 dsk wiretile"><div class="th"><b>Latest headlines</b><button class="lnk" data-go="news">See all</button></div><div id="wt"></div></section>
    <section class="t clay s6 dsk movers"><div class="th"><b>Top movers</b><small>this session</small></div>
      <div class="mvg"><div><small>Gainers</small><div id="mvu"></div></div><div><small>Losers</small><div id="mvd"></div></div></div></section>
  </div>
  <div class="toolbar">
    <div class="chips" id="fchips">${chips.map((c) => `<button data-filter="${c[0]}" class="${S.filter === c[0] ? "on" : ""}">${esc(c[1])}</button>`).join("")}</div>
    <label class="search">${ic("search")}<input id="q" placeholder="Search companies" value="${esc(S.q)}" autocomplete="off"></label>
  </div>
  <div class="list" id="list">${listHtml()}</div>`;
  mount("ix", $("#ixchart"), "INDEX");
  observeCards();
  paintIndex();
  paintWorthTile();
  paintWireTile();
  paintMovers();
}

function paintIndex() {
  const v = $("#ixv");
  if (!v) return;
  const x = S.ix,
    ch = x.open ? (x.v / x.open - 1) * 100 : 0;
  T(v, fmtN(x.v));
  const c = $("#ixc");
  T(c, pc(ch));
  CLS(c, "chg " + (ch >= 0 ? "u" : "d"));
  T(
    $("#ixsub"),
    `Open ${fmtN(x.open)}, high ${fmtN(x.high)}, low ${fmtN(x.low)}. ${S.paused ? "Trading is paused." : "Session closes in " + mmss(Math.max(0, S.se - now())) + "."} ${S.on} online.`,
  );
}

function paintWorthTile() {
  if (!$("#wtw")) return;
  const k = calc(),
    g = (k.worth / S.meta.start - 1) * 100,
    up = k.unreal >= 0;
  $("#wtw").textContent = money(k.worth);
  const pg = $("#wtg");
  pg.textContent = pc(g) + " since start";
  pg.className = "pill " + (g >= 0 ? "u" : "d");
  $("#wtr").textContent = "Rank " + S.me.rank + " of " + S.me.players;
  $("#wtc").textContent = money0(S.me.cash);
  $("#wti").textContent = money0(k.invested);
  $("#wtv").textContent = money0(k.value);
  const u = $("#wtu");
  u.textContent = sm(k.unreal);
  u.className = up ? "up" : "dn";
}

function wireRow(n) {
  const c = n.tType === "company" ? S.co[n.tId] : null,
    sc = sentOf(n);
  const badge = c
    ? monoEl(c)
    : `<div class="mono" style="--h:0">${n.tType === "sector" ? esc(n.tId.slice(0, 2).toUpperCase()) : "MK"}</div>`;
  return `<button class="wrow" data-nid="${n.id}" ${c ? `data-open="${c.id}"` : 'data-go="news"'}>${badge}
    <div><b>${esc(n.headline)}</b><small>${sc[1]}, ${ago(n.ts)}</small></div></button>`;
}

function paintWireTile() {
  const w = $("#wt");
  if (!w) return;
  w.innerHTML =
    shownNews().slice(0, 3).map(wireRow).join("") ||
    "<small>The wire is quiet for now.</small>";
}

function mrow(c) {
  return `<button class="mrow" data-open="${c.id}" data-id="${c.id}" style="--h:${c.hue}">${monoEl(c)}<b>${c.id}</b><span class="chg"></span></button>`;
}

function paintMovers() {
  if (!$("#mvu")) return;
  const a = S.order.map((i) => S.co[i]).sort((x, y) => chg(y) - chg(x));
  const up = a.slice(0, 3),
    dn = a.slice(-3).reverse(),
    sig = up
      .concat(dn)
      .map((c) => c.id)
      .join();
  if (sig !== S.mvSig) {
    S.mvSig = sig;
    $("#mvu").innerHTML = up.map(mrow).join("");
    $("#mvd").innerHTML = dn.map(mrow).join("");
  }
  $$(".mrow").forEach((el) => {
    const ch = chg(S.co[el.dataset.id]),
      e = $(".chg", el);
    e.textContent = pc(ch);
    e.className = "chg " + (ch >= 0 ? "u" : "d");
  });
}

const sparkPts = (a, w = 90, h = 26) => {
  const mn = Math.min(...a),
    mx = Math.max(...a),
    r = mx - mn || 1;
  return a
    .map(
      (v, i) =>
        ((i * w) / (a.length - 1)).toFixed(1) +
        "," +
        (h - 2 - ((v - mn) / r) * (h - 4)).toFixed(1),
    )
    .join(" ");
};

function paintCards() {
  const doSpark = S.tickN % 3 === 0;
  $$(".co", main()).forEach((el) => {
    const c = S.co[el.dataset.id];
    if (!c) return;
    const px = $(".px", el),
      t = money(c.price);
    if (px._t !== t) {
      if (c.flash) flash(px, c.flash === "fu");
      T(px, t);
    }
    const ch = chg(c);
    T($(".chg", el), pc(ch));
    CLS($(".chg", el), "chg " + (ch >= 0 ? "u" : "d"));
    H($(".lt", el), limTag(c));
    if (doSpark && el.dataset.vis !== "0") {
      const sp = $(".sp", el);
      if (sp) {
        $("polyline", sp).setAttribute("points", sparkPts(c.hist));
        sp.setAttribute(
          "class",
          "sp " + (c.hist[c.hist.length - 1] >= c.hist[0] ? "u" : "d"),
        );
      }
    }
  });
  paintIndex();
  paintMovers();
}

function observeCards() {
  if (!("IntersectionObserver" in window)) return;
  if (!S.io)
    S.io = new IntersectionObserver(
      (es) =>
        es.forEach((e) => {
          e.target.dataset.vis = e.isIntersecting ? "1" : "0";
        }),
      { rootMargin: "120px" },
    );
  S.io.disconnect();
  $$(".co", main()).forEach((el) => S.io.observe(el));
}

/* ---------- news & floor ---------- */

function newsRow(n) {
  const c = n.tType === "company" ? S.co[n.tId] : null,
    sc = sentOf(n);
  const chip = c
    ? `<span class="chip">${c.id}</span>`
    : n.tType === "sector"
      ? `<span class="chip">${esc(n.tId)}</span>`
      : '<span class="chip">Whole market</span>';
  const fresh = now() - n.ts < 45000;
  const done = n.status === "confirmed" ? "g" : "b";
  let tag = '<span class="tag n">Breaking</span>',
    extra = "";
  if (n.kind === "rumor") {
    tag = '<span class="tag r">Rumor</span>';
    const st = String(n.status || "pending");
    if (st === "pending") {
      const h = (S.me.intel || {})[String(n.id)],
        words = {
          likely: "probably true",
          "toss-up": "a coin flip",
          unlikely: "probably false",
        }[h];
      extra = words
        ? `<div class="hint">Insiders say: ${words}</div>`
        : `<button class="intel" data-intel="${n.id}">Buy intel for ${money0(S.meta.intel || 150)}</button>`;
    } else {
      const lbl =
        st === "confirmed"
          ? "Confirmed"
          : ["debunked", "false", "denied"].includes(st)
            ? "Debunked"
            : "Closed";
      extra = `<span class="tag ${st === "confirmed" ? "g" : "b"}">${lbl}</span>`;
    }
  } else if (n.kind === "update")
    tag = `<span class="tag ${done}">Update</span>`;
  else if (!fresh) tag = '<span class="tag b">News</span>';
  return `<article class="nw ${sc[0]} ${fresh ? "fresh" : ""}" data-nid="${n.id}" ${c ? `data-open="${c.id}"` : ""}>
    <button class="nx" data-dismiss="${n.id}" aria-label="Remove this story">${ic("close")}</button>
    <div class="meta">${tag}<span class="sent ${sc[0]}">${sc[1]}</span>${chip}<span>${ago(n.ts)}</span></div>
    <h4>${esc(n.headline)}</h4>${n.body ? `<p>${esc(n.body)}</p>` : ""}${extra}</article>`;
}

function renderNews() {
  main().innerHTML = `
  <div class="seg nseg" id="nseg"><button data-floor="wire" class="${S.floor === "wire" ? "on" : ""}">Wire</button><button data-floor="floor" class="${S.floor === "floor" ? "on" : ""}">Trading floor</button></div>
  <div class="bento">
    <div class="pane s7 ${S.floor === "wire" ? "" : "off"}" id="wire"></div>
    <section class="pane t clay s5 m12 ${S.floor === "floor" ? "" : "off"}" id="floor"></section>
  </div>`;
  paintWire();
  paintFloor(true);
}

function paintWire() {
  const b = $("#wire");
  if (!b) return;
  const l = shownNews();
  b.innerHTML =
    `<div class="th ph"><b>The wire</b><small>${l.length} ${l.length === 1 ? "story" : "stories"}. Swipe one sideways to remove it.</small></div>` +
    (l.length
      ? `<div class="list one">${l.map(newsRow).join("")}</div>`
      : `<div class="empty t clay">${S.news.length ? "You have cleared the wire. New stories will show up here." : "No headlines yet. The wire is quiet for now."}</div>`);
}

function chatRow(m) {
  if (m.sys)
    return `<div class="msg sys"><div class="bub">${esc(m.text)}</div></div>`;
  return `<div class="msg"><div class="a">${esc(m.avatar)}</div><div class="bub"><b>${esc(m.name)}</b>${esc(m.text)}</div></div>`;
}

function paintFloor(scroll) {
  const b = $("#floor");
  if (!b) return;
  if (!$("#msgs")) {
    b.innerHTML = `<div class="th"><b>Trading floor</b><small>${esc(S.realm.name)}</small></div><div class="msgs" id="msgs"></div>
      <div class="send"><input class="field" id="ct" maxlength="140" placeholder="Talk to the floor"><button class="btn" id="cs">Send</button></div>`;
    $("#ct").onkeydown = (e) => {
      if (e.key === "Enter") sendChat();
    };
    $("#cs").onclick = sendChat;
  }
  const el = $("#msgs");
  const atEnd = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
  el.innerHTML =
    S.chat.map(chatRow).join("") ||
    '<div class="empty">Nobody has spoken yet. Say hi.</div>';
  if (scroll || atEnd) el.scrollTop = el.scrollHeight;
}

async function sendChat() {
  const i = $("#ct"),
    text = i.value.trim();
  if (!text) return;
  i.value = "";
  try {
    await api("chat", { text });
  } catch (e) {
    toast(e.message, "err");
  }
}

async function buyIntel(id) {
  try {
    const r = await api("intel", { id: Number(id) });
    S.me = r.me;
    paintTop();
    handleEvents(S.me.events);
    toast("Intel purchased");
    if (S.view === "news") paintWire();
  } catch (e) {
    toast(e.message, "err");
  }
}

/* ---------- portfolio ---------- */

function holdRow(h) {
  const c = S.co[h.id];
  if (!c) return "";
  const v = h.qty * c.price,
    pl = v - h.qty * h.avg,
    pct = (c.price / h.avg - 1) * 100;
  return `<button class="hold" data-open="${c.id}" data-id="${c.id}" style="--h:${c.hue}">
    ${monoEl(c)}
    <div><b>${esc(c.name)}</b><br><small>${fmtN(h.qty, 0)} shares, invested ${money(h.qty * h.avg)}</small></div>
    <div class="r"><b class="hv">${money(v)}</b><br><small class="hp ${pl >= 0 ? "up" : "dn"}">${sm(pl)} (${pc(pct)})</small></div></button>`;
}

function allocBar() {
  const w = worth();
  if (w <= 0) return "";
  const segs = S.me.hold
    .filter((h) => S.co[h.id])
    .map((h) => ({
      n: h.id,
      v: h.qty * S.co[h.id].price,
      c: `hsl(${S.co[h.id].hue} 65% 58%)`,
    }));
  segs.push({ n: "Cash", v: S.me.cash, c: "#b7bed8" });
  return `<div class="alloc">${segs.map((s) => `<i style="width:${(s.v / w) * 100}%;background:${s.c}"></i>`).join("")}</div>
    <div class="legend">${segs
      .filter((s) => s.v > 0)
      .map(
        (s) =>
          `<em style="--c:${s.c}">${s.n} ${((s.v / w) * 100).toFixed(0)}%</em>`,
      )
      .join("")}</div>`;
}

function renderPortfolio() {
  const m = S.me;
  main().innerHTML = `
  <div class="bento">
    <section class="t clay s7 hero">
      <small>Net worth</small><div class="big" id="pw"></div>
      <div class="row"><span class="pill" id="pg"></span><span class="pill n">Rank ${m.rank} of ${m.players}</span></div>
      <div id="alloc"></div>
    </section>
    <section class="t clay s5 m12 stg2">
      <div class="stat"><small>Cash to invest</small><b id="pc"></b></div>
      <div class="stat"><small>Invested</small><b id="pi"></b></div>
      <div class="stat"><small>Current value</small><b id="pv"></b></div>
      <div class="stat"><small>Unrealized P/L</small><b id="pu"></b></div>
      <div class="stat"><small>Realized profit</small><b class="${m.realized >= 0 ? "up" : "dn"}">${sm(m.realized)}</b></div>
      <div class="stat"><small>Best sale</small><b>${pc(m.bestPct)}</b></div>
    </section>
  </div>
  ${
    m.hold.length
      ? `<div class="th ph"><b>Your holdings</b><small>${m.hold.length} companies</small></div><div class="list" id="holds">${m.hold.map(holdRow).join("")}</div>`
      : `<div class="empty t clay">You don't own anything yet.<br><small>Your first trade earns a badge.</small><br><button class="btn" data-go="market">Browse companies</button></div>`
  }`;
  paintPortfolio();
}

function paintPortfolio() {
  if (!$("#pw")) return;
  const k = calc(),
    g = (k.worth / S.meta.start - 1) * 100;
  $("#pw").textContent = money(k.worth);
  const pg = $("#pg");
  pg.textContent = pc(g) + " since you started";
  pg.className = "pill " + (g >= 0 ? "u" : "d");
  $("#pc").textContent = money(S.me.cash);
  $("#pi").textContent = money(k.invested);
  $("#pv").textContent = money(k.value);
  const pu = $("#pu");
  pu.textContent = `${sm(k.unreal)} (${pc(k.invested ? (k.unreal / k.invested) * 100 : 0)})`;
  pu.className = k.unreal >= 0 ? "up" : "dn";
  $("#alloc").innerHTML = allocBar();
  S.me.hold.forEach((h) => {
    const el = $(`.hold[data-id="${h.id}"]`),
      c = S.co[h.id];
    if (!el || !c) return;
    const v = h.qty * c.price,
      pl = v - h.qty * h.avg,
      pct = (c.price / h.avg - 1) * 100;
    $(".hv", el).textContent = money(v);
    const hp = $(".hp", el);
    hp.textContent = `${sm(pl)} (${pc(pct)})`;
    hp.className = "hp " + (pl >= 0 ? "up" : "dn");
  });
}

/* ---------- ranks ---------- */

async function renderRanks(quiet) {
  if (!quiet)
    main().innerHTML = '<div class="empty">Counting the money...</div>';
  try {
    const d = await api("leaders");
    if (S.view !== "ranks") return;
    const pod = [d.top[1], d.top[0], d.top[2]].filter(Boolean);
    const rest = d.top.slice(3);
    main().innerHTML = `
    <div><small>${esc(S.realm.name)}. You are ranked ${d.rank} of ${d.total}.</small></div>
    <div class="podium">${pod
      .map(
        (
          r,
        ) => `<div class="t clay pod p${r.rank} ${r.name === S.me.name ? "me" : ""}">
      <div class="rk1">${r.rank}</div><div class="a">${esc(r.avatar)}</div><b class="nm">${esc(r.name)}</b><small>Level ${r.level}</small>
      <div class="w">${money0(r.worth)}</div><span class="pill ${r.gain >= 0 ? "u" : "d"}">${pc(r.gain)}</span></div>`,
      )
      .join("")}</div>
    ${
      rest.length
        ? `<section class="t clay rows">${rest
            .map(
              (r) => `<div class="rk ${r.name === S.me.name ? "me" : ""}">
      <div class="n">${r.rank}</div><div class="a">${esc(r.avatar)}</div>
      <div><b>${esc(r.name)}</b><span class="lv">Lv ${r.level}</span><br><small>${esc(r.title)}</small></div>
      <div class="r"><b>${money0(r.worth)}</b><br><small class="${r.gain >= 0 ? "up" : "dn"}">${pc(r.gain)}</small></div></div>`,
            )
            .join("")}</section>`
        : ""
    }`;
  } catch (e) {}
}

/* ---------- profile ---------- */

function renderMe() {
  const m = S.me,
    pct = Math.min(100, ((m.xp - m.xpBase) / (m.xpNext - m.xpBase)) * 100);
  const scroll = main().scrollTop;
  main().innerHTML = `
  <div class="bento">
    <section class="t clay s6"><div class="prof"><div class="big">${m.avatar}</div>
      <div style="flex:1;min-width:0"><h2>${esc(m.name)}</h2><span class="pill n">${esc(m.title)}</span><span class="lv">Lv ${m.level}</span></div></div>
      <div class="xp"><i style="width:${pct}%"></i></div><small>${m.xp - m.xpBase} of ${m.xpNext - m.xpBase} XP to level ${m.level + 1}</small></section>
    <section class="t clay s6"><div class="th"><b>Today's missions</b><small>${m.missions.filter((x) => x.done).length} of ${m.missions.length} done</small></div>${m.missions
      .map(
        (x) => `
      <div class="mission ${x.done ? "done" : ""}"><div class="mi">${x.done ? ic("check") : x.progress + "/" + x.goal}</div>
      <div><b>${esc(x.title)}</b><br><small>${esc(x.desc)}</small><div class="bar"><i style="width:${(x.progress / x.goal) * 100}%"></i></div></div>
      <div style="text-align:right"><b>${money0(x.cash)}</b><br><small>${x.xp} XP</small></div></div>`,
      )
      .join("")}</section>
    <section class="t clay s12"><div class="th"><b>Badges</b><small>${m.badges.length} of ${S.meta.badges.length}</small></div><div class="badges">${S.meta.badges
      .map(
        (b) => `
      <div class="bd ${m.badges.includes(b.id) ? "on" : "off"}" title="${esc(b.desc)}"><span>${esc(b.tag)}</span>${esc(b.name)}</div>`,
      )
      .join("")}</div></section>
    <section class="t clay s12"><div class="th"><b>Settings</b></div>
      <div class="switch"><span>Appearance</span><div class="seg"><button data-tm="light" class="${themeMode() === "light" ? "on" : ""}">Light</button><button data-tm="dark" class="${themeMode() === "dark" ? "on" : ""}">Dark</button><button data-tm="auto" class="${themeMode() === "auto" ? "on" : ""}">System</button></div></div>
      <div class="switch"><span>Sound effects</span><button class="pill ${S.sound ? "u" : "n"}" data-act="sound">${S.sound ? "On" : "Off"}</button></div>
      <div class="h3" style="margin-top:12px">Profile picture</div>
      <div class="avs wide">${AVATARS.map((a) => `<button data-setav="${a}" class="${a === m.avatar ? "on" : ""}">${a}</button>`).join("")}</div>
      <button class="btn soft" data-act="logout" style="margin-top:14px">Sign out</button></section>
  </div>`;
  main().scrollTop = scroll;
}

/* ---------- company page ---------- */

function detailHtml(c) {
  return `<div class="dpanel" style="--h:${c.hue}">
  <div class="dbar"><button class="ib" data-act="back" aria-label="Close"><span class="mo">${ic("back")}</span><span class="de">${ic("close")}</span></button>
    <div class="dt"><b>${esc(c.name)}</b><div class="tags"><span class="tagc h">${c.id}</span><span class="tagc">${esc(c.sector)}</span></div></div>
    <button class="ib" data-act="star" id="star" aria-label="Watch"></button></div>
  <div class="dscroll">
    <div class="dgrid">
      <section class="t clay ctile sc2">
        <div class="ch"><div><small>Share price</small><div class="pricerow"><div class="bigpx" id="dpx"></div><span class="chg" id="dchg"></span></div></div>
          <div class="rchips">${rangeChips("co")}</div></div>
        <div class="chartbox tall" id="dchart" data-view="${c.id}"></div>
        <div class="ctools"><div class="leg" id="dleg"></div><button class="ib" data-view="${c.id}" aria-label="Expand chart">${ic("expand")}</button></div>
      </section>
      <section class="t clay sc2"><div class="h3">About</div><p style="margin:0 0 6px;font-weight:700">${esc(c.tagline)}</p><small>${esc(c.blurb)}</small></section>
      <section class="t clay sc2"><div class="h3">From the CEO</div><div class="ceo" id="dceo"></div></section>
      <section class="t clay sc2"><div class="stg" id="dstats"></div></section>
      <section class="t clay" id="dlim"></section>
      <section class="t clay" id="dsup"></section>
      <section class="t clay hype"><small>Crowd mood</small><b id="dml"></b><div class="track2"><i id="dmb"></i></div></section>
      <section class="t clay"><div class="h3">Board of investors</div><div id="dboard"><small>Loading...</small></div></section>
    </div>
    <div class="trade t clay">
      <div class="r1"><div class="seg seg2"><button data-side="buy" class="buy">Buy</button><button data-side="sell" class="sell">Sell</button></div>
        <div class="qty"><button data-step="-1" aria-label="Less">${ic("minus")}</button><input id="qty" inputmode="numeric" pattern="[0-9]*"><button data-step="1" aria-label="More">${ic("plus")}</button></div></div>
      <div class="qc"><button data-q="1">1</button><button data-q="10">10</button><button data-q="25%">25%</button><button data-q="50%">50%</button><button data-q="max">Max</button></div>
      <div class="est"><div><small>Total</small><b id="et"></b></div><div><small>Fee</small><b id="ef"></b></div><div><small>Impact</small><b id="ei"></b></div></div>
      <button class="btn lg up" id="act"></button>
    </div>
  </div></div>`;
}

function openCo(id) {
  const c = S.co[id];
  if (!c || S.open === id) return;
  S.open = id;
  S.side = "buy";
  S.qty = 1;
  S.detail = {};
  S.qTier = "";
  const sh = $("#sheet");
  sh.innerHTML = detailHtml(c);
  sh.classList.add("on");
  document.body.classList.add("modal");
  const q = $("#qty");
  q.value = 1;
  q.oninput = () => {
    S.qty = Math.max(1, parseInt(q.value, 10) || 1);
    paintTrade();
  };
  mount("co", $("#dchart"), id);
  paintDetail(true);
  api("company?id=" + id)
    .then((d) => {
      if (S.open === id) {
        S.detail = d;
        paintBoard();
        paintStats();
      }
    })
    .catch(() => {});
  api("visit", { id })
    .then(refreshMe)
    .catch(() => {});
}

function closeCo() {
  S.open = null;
  S.tickN = 0;
  killCharts(["co"]);
  document.body.classList.remove("modal");
  const sh = $("#sheet");
  if (sh) sh.classList.remove("on");
}

function maxQty(c, side) {
  if (side === "sell") {
    const h = S.me.hold.find((x) => x.id === c.id);
    return h ? h.qty : 0;
  }
  let q = Math.min(
    c.avail ?? Infinity,
    Math.floor(S.me.cash / (c.price * 1.003)),
  );
  while (q > 0) {
    const e = est(c, "buy", q);
    if (e.g + e.fee <= S.me.cash) break;
    q--;
  }
  return q;
}

function est(c, side, q) {
  const frac = c.float ? (c.avail ?? 0) / c.float : 1,
    depth = c.liq * (0.4 + 0.6 * frac);
  const imp = Math.min(0.06, (0.35 * c.price * q) / depth);
  let px =
    c.price *
    Math.max(0.5, 1 + (c.flow || 0) + (side === "buy" ? imp : -imp) / 2);
  if (c.lim) px = Math.max(c.lim.lo, Math.min(c.lim.hi, px));
  const g = px * q;
  return { imp: imp * 100, px, g, fee: g * (S.meta.fee || 0.002) };
}

function paintStats() {
  const c = S.co[S.open];
  if (!c || !$("#dstats")) return;
  const d = S.detail;
  H(
    $("#dstats"),
    [
      ["Market cap", compact(c.price * c.shares)],
      ["Session open", money(c.open)],
      ["Investors here", d.holders ?? "-"],
      ["Watching", d.watchers ?? "-"],
    ]
      .map(
        (s) => `<div class="stat"><small>${s[0]}</small><b>${s[1]}</b></div>`,
      )
      .join(""),
  );
}

function limitHtml(c) {
  if (!c.lim)
    return '<div class="h3">Price limits</div><small>The exchange has not disclosed price limits for this company.</small>';
  const L = c.lim,
    pos = Math.max(0, Math.min(100, ((c.price - L.lo) / (L.hi - L.lo)) * 100));
  const note =
    L.hit > 0
      ? "Upper limit reached. The price cannot rise further this session."
      : L.hit < 0
        ? "Lower limit reached. The price cannot fall further this session."
        : "Within limits. The band resets when the next session opens.";
  return `<div class="h3">Price limits</div>
    <div class="limrow"><span class="tagc">${esc(L.tier)}</span><b>${L.band.toFixed(1)}% each way</b></div>
    <div class="range"><span class="dn">${money(L.lo)}</span><div class="rb"><i style="left:${pos}%"></i></div><span class="up">${money(L.hi)}</span></div>
    <small class="limnote">${note}</small>`;
}

function legendHtml(id) {
  const c = S.co[id],
    parts = [];
  if (c) {
    if (S.me.hold.some((h) => h.id === id))
      parts.push("<span><i></i>Your buy prices</span>");
    if (c.lim)
      parts.push(
        '<span><i class="dot"></i>Upper limit</span><span><i class="dot d"></i>Lower limit</span>',
      );
  }
  return parts.join("");
}

function supplyHtml(c) {
  const fl = c.float || 0,
    av = c.avail || 0,
    pct = fl ? (av / fl) * 100 : 0;
  const note =
    av === 0
      ? "Sold out. Shares return to the market when holders sell."
      : pct < 10
        ? "Very few shares left. Buying moves the price more."
        : "Shares are limited. When they run out, nobody can buy more.";
  return `<div class="h3">Share supply</div><div class="limrow"><b>${fmtN(av, 0)} available</b><span class="tagc">of ${fmtN(fl, 0)}</span></div>
    <div class="track2"><i style="width:${pct}%;background:${av === 0 ? "var(--dn)" : pct < 10 ? "var(--gold)" : "var(--up)"}"></i></div><small class="limnote">${note}</small>`;
}

function paintDetail(force) {
  const c = S.co[S.open];
  if (!c) return;
  const ch = chg(c),
    px = $("#dpx"),
    t = money(c.price);
  if (px._t !== t) {
    if (c.flash && !force) flash(px, c.flash === "fu");
    T(px, t);
  }
  T($("#dchg"), pc(ch));
  CLS($("#dchg"), "chg " + (ch >= 0 ? "u" : "d"));
  H($("#dlim"), limitHtml(c));
  H($("#dsup"), supplyHtml(c));
  H($("#dleg"), legendHtml(c.id));
  const tr = tier(c.mood);
  T($("#dml"), moodLabel(c.mood));
  const mb = $("#dmb");
  mb.style.width = c.mood + "%";
  mb.style.background = `hsl(${c.mood * 1.3} 70% 50%)`;
  if (tr !== S.qTier || force || S.ceoShown !== c.ceo) {
    S.qTier = tr;
    S.ceoShown = c.ceo;
    $("#dceo")._h = null;
    H(
      $("#dceo"),
      `${monoEl({ hue: c.hue, name: c.ceo })}<p>${esc(pick(QUOTE[tr]))}<br><small>${esc(c.ceo)}, CEO</small></p>`,
    );
  }
  H($("#star"), ic("star", S.me.watch.includes(c.id) ? "fill" : ""));
  paintStats();
  paintTrade();
}

function paintBoard() {
  const b = $("#dboard");
  if (!b) return;
  const d = S.detail;
  b.innerHTML =
    d.board && d.board.length
      ? d.board
          .map(
            (r, i) =>
              `<div class="inv"><div class="a">${esc(r.avatar)}</div><b>${i + 1}. ${esc(r.name)}</b><span>${r.qty.toLocaleString()} shares</span></div>`,
          )
          .join("")
      : "<small>No investors yet. Be the first name on this board.</small>";
}

function paintTrade() {
  const c = S.co[S.open];
  if (!c || !$("#act")) return;
  const e = est(c, S.side, S.qty),
    buy = S.side === "buy";
  $$("[data-side]").forEach((b) =>
    b.classList.toggle("on", b.dataset.side === S.side),
  );
  const q = $("#qty");
  if (document.activeElement !== q) q.value = S.qty;
  T($("#et"), money(buy ? e.g + e.fee : e.g - e.fee));
  T($("#ef"), money(e.fee));
  T($("#ei"), (buy ? "+" : "-") + e.imp.toFixed(2) + "%");
  const own = maxQty(c, "sell"),
    bad =
      S.paused ||
      (buy
        ? e.g + e.fee > S.me.cash || S.qty > (c.avail ?? Infinity)
        : S.qty > own);
  const a = $("#act");
  a.className = "btn lg " + (buy ? "up" : "dn");
  a.disabled = bad;
  a.textContent = S.paused
    ? "Trading paused"
    : buy
      ? S.qty > (c.avail ?? Infinity)
        ? c.avail
          ? `Only ${c.avail} left`
          : "Sold out"
        : bad
          ? "Not enough cash"
          : `Buy ${S.qty} ${c.id}`
      : bad
        ? own
          ? `You only own ${own}`
          : "You own none"
        : `Sell ${S.qty} ${c.id}`;
}

function setQty(v) {
  const c = S.co[S.open],
    max = maxQty(c, S.side);
  let q =
    v === "max"
      ? max
      : String(v).endsWith("%")
        ? Math.floor((max * parseInt(v, 10)) / 100)
        : parseInt(v, 10);
  S.qty = Math.max(1, q || 1);
  paintTrade();
}

async function trade() {
  const c = S.co[S.open],
    q = S.qty,
    side = S.side,
    a = $("#act");
  a.disabled = true;
  try {
    const r = await api("trade", { id: c.id, side, qty: q });
    S.me = r.me;
    const won = r.pl != null && r.pl > 0;
    toast(
      `${side === "buy" ? "Bought" : "Sold"} ${q} ${c.id} at ${money(r.price)}${r.pl != null ? (r.pl >= 0 ? ", profit " : ", loss ") + money(Math.abs(r.pl)) : ""}`,
      r.pl == null ? "" : r.pl >= 0 ? "up" : "dn",
    );
    if (won) celebrate(18);
    buzz(35);
    ding(side === "buy" || won);
    handleEvents(S.me.events);
    S.qty = 1;
    paintTop();
    paintNav();
    paintDetail(true);
    Object.values(S.charts).forEach((ch) => {
      if (ch && ch.sid === c.id) {
        chartExtras(ch, c.id);
        ch.draw();
      }
    });
    if (S.viewer === c.id) $("#vleg").innerHTML = legendHtml(c.id);
    if (S.view === "market") paintCards();
  } catch (e) {
    toast(e.message, "err");
    paintTrade();
  }
}

async function toggleStar() {
  try {
    const r = await api("watch", { id: S.open });
    S.me.watch = r.watch;
    paintDetail(true);
    if (S.view === "market") {
      const l = $("#list");
      if (l) {
        l.innerHTML = listHtml();
        observeCards();
      }
    }
  } catch (e) {
    toast(e.message, "err");
  }
}

/* ---------- chart viewer ---------- */

function openViewer(id) {
  const c = id === "INDEX" ? null : S.co[id];
  if (id !== "INDEX" && !c) return;
  S.viewer = id;
  S.spans.vw = 0;
  const v = $("#viewer");
  v.innerHTML = `<div class="vp">
    <div class="vhead"><button class="ib" data-act="vclose" aria-label="Close chart">${ic("close")}</button>
      <div class="vt"><b>${c ? esc(c.name) : "Total market index"}</b><small>${c ? c.id + ", " + esc(c.sector) : "All listed companies"}</small></div>
      <div class="vpx"><b id="vpx"></b><span class="chg" id="vch"></span></div></div>
    <div class="vtools"><div class="rchips">${rangeChips("vw")}</div>
      <div class="zbtns"><button class="ib" data-vz="out" aria-label="Zoom out">${ic("minus")}</button><button class="ib" data-vz="in" aria-label="Zoom in">${ic("plus")}</button><button class="ib txt" data-vz="fit">Fit all</button></div></div>
    <div class="vchart"><div class="chartbox" id="vbox"></div></div>
    <div class="leg" id="vleg">${legendHtml(id)}</div>
    <small class="vhint">Drag to move. Pinch or scroll to zoom. Tap or hover for exact values.</small></div>`;
  v.classList.add("on");
  mount("vw", $("#vbox"), id, { interactive: true });
  paintViewer();
}

function paintViewer() {
  const id = S.viewer;
  if (!id || !$("#vpx")) return;
  const c = id === "INDEX" ? null : S.co[id];
  const v = c ? c.price : S.ix.v,
    base = c ? c.open : S.ix.open,
    ch = base ? (v / base - 1) * 100 : 0;
  $("#vpx").textContent = c ? money(v) : fmtN(v);
  const e = $("#vch");
  e.textContent = pc(ch);
  e.className = "chg " + (ch >= 0 ? "u" : "d");
}

function closeViewer() {
  if (!S.viewer && !$("#viewer.on")) return;
  S.viewer = null;
  killCharts(["vw"]);
  const v = $("#viewer");
  if (v) v.classList.remove("on");
}

/* ---------- input ---------- */

document.addEventListener("click", (e) => {
  const t = e.target;
  const intel = t.closest("[data-intel]");
  if (intel) {
    e.stopPropagation();
    buyIntel(intel.dataset.intel);
    return;
  }
  const dm = t.closest("[data-dismiss]");
  if (dm) {
    e.stopPropagation();
    dismissNews(dm.dataset.dismiss);
    return;
  }
  const nv = t.closest("nav button");
  if (nv) {
    go(nv.dataset.v);
    return;
  }
  const act = t.closest("[data-act]");
  if (act) {
    const a = act.dataset.act;
    if (a === "back") closeCo();
    else if (a === "vclose") closeViewer();
    else if (a === "star") toggleStar();
    else if (a === "logout") logout();
    else if (a === "theme") applyTheme(isDark() ? "light" : "dark");
    else if (a === "sound") {
      S.sound = !S.sound;
      localStorage.setItem("br_snd", S.sound ? "1" : "0");
      renderMe();
    }
    return;
  }
  const tm = t.closest("[data-tm]");
  if (tm) {
    applyTheme(tm.dataset.tm);
    return;
  }
  const rg = t.closest("[data-range]");
  if (rg) {
    const k = rg.dataset.ckey,
      sec = Number(rg.dataset.range);
    S.spans[k] = sec;
    $$(`[data-ckey="${k}"]`).forEach((b) =>
      b.classList.toggle("on", Number(b.dataset.range) === sec),
    );
    if (S.charts[k]) S.charts[k].setSpan(sec);
    return;
  }
  const vz = t.closest("[data-vz]");
  if (vz && S.charts.vw) {
    const a = vz.dataset.vz;
    if (a === "fit") {
      S.charts.vw.fit();
      $$('[data-ckey="vw"]').forEach((b) =>
        b.classList.toggle("on", Number(b.dataset.range) === 0),
      );
    } else S.charts.vw.zoomCenter(a === "in" ? 0.6 : 1 / 0.6);
    return;
  }
  const vw = t.closest("[data-view]");
  if (vw) {
    openViewer(vw.dataset.view);
    return;
  }
  const f = t.closest("[data-filter]");
  if (f) {
    S.filter = f.dataset.filter;
    $$("#fchips button").forEach((b) =>
      b.classList.toggle("on", b.dataset.filter === S.filter),
    );
    $("#list").innerHTML = listHtml();
    observeCards();
    return;
  }
  const fl = t.closest("[data-floor]");
  if (fl) {
    S.floor = fl.dataset.floor;
    renderNews();
    return;
  }
  const sd = t.closest("[data-side]");
  if (sd) {
    S.side = sd.dataset.side;
    S.qty = 1;
    paintTrade();
    return;
  }
  const st = t.closest("[data-step]");
  if (st) {
    S.qty = Math.max(1, S.qty + Number(st.dataset.step));
    paintTrade();
    return;
  }
  const q = t.closest("[data-q]");
  if (q) {
    setQty(q.dataset.q);
    return;
  }
  if (t.closest("#act")) {
    trade();
    return;
  }
  const sa = t.closest("[data-setav]");
  if (sa) {
    api("profile", { avatar: sa.dataset.setav, bio: "" })
      .then((m) => {
        S.me = m;
        paintNav();
        renderMe();
      })
      .catch(() => {});
    return;
  }
  const g = t.closest("[data-go]");
  if (g) {
    go(g.dataset.go);
    return;
  }
  const o = t.closest("[data-open]");
  if (o) openCo(o.dataset.open);
});

document.addEventListener("input", (e) => {
  if (e.target.id === "q") {
    S.q = e.target.value;
    const l = $("#list");
    if (l) {
      l.innerHTML = listHtml();
      observeCards();
    }
  }
});

document.addEventListener("keydown", (e) => {
  if (e.key !== "Escape") return;
  if (S.viewer) closeViewer();
  else if (S.open) closeCo();
});
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && S.token && S.me) resync();
});

(async function init() {
  applyTheme();
  const ready = () => {
    if (window.eqReady) requestAnimationFrame(() => requestAnimationFrame(window.eqReady));
  };
  if (S.token) {
    try {
      await enter();
      ready();
      return;
    } catch (e) {
      S.token = null;
      localStorage.removeItem("br_token");
    }
  }
  await showAuth();
  ready();
})();
