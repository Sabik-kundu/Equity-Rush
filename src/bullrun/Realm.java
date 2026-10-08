package bullrun;

import java.util.*;

final class Realm {
    static final class News {
        long id, ts;
        String kind, status = "", tType, tId, headline, body;
        double impact, cred;

        Map<String, Object> view(boolean admin) {
            Map<String, Object> m = Json.of("id", id, "ts", ts, "kind", kind, "status", status, "tType", tType,
                    "tId", tId, "headline", headline, "body", body, "sent", sent());
            if (admin) {
                m.put("impact", impact);
                m.put("cred", cred);
            }
            return m;
        }

        String hint() {
            return cred >= 0.62 ? "likely" : cred > 0.38 ? "toss-up" : "unlikely";
        }

        int sent() {
            double a = Math.abs(impact);
            int level = a < 1 ? 0 : a < 5 ? 1 : 2;
            return impact < 0 ? -level : level;
        }

        static News from(Map<String, Object> m) {
            News n = new News();
            n.id = Json.lng(m.get("id"), 0);
            n.ts = Json.lng(m.get("ts"), 0);
            n.kind = Json.str(m.get("kind"), "news");
            n.status = Json.str(m.get("status"), "");
            n.tType = Json.str(m.get("tType"), "market");
            n.tId = Json.str(m.get("tId"), "");
            n.headline = Json.str(m.get("headline"), "");
            n.body = Json.str(m.get("body"), "");
            n.impact = Json.num(m.get("impact"), 0);
            n.cred = Json.num(m.get("cred"), 1);
            return n;
        }
    }

    private record Pending(long at, Runnable run) {
    }

    private record Row(Player p, double worth) {
    }

    private static final long RUMOR_MIN_MS = 20_000;
    private static final long RUMOR_SPREAD_MS = 15_000;
    private static final long HOT_NEWS_MS = 40_000;

    final Game game;
    final Defs.RealmDef def;
    final Map<String, Company> cos = new LinkedHashMap<>();
    final Map<String, Player> players = new HashMap<>();
    final LinkedList<News> news = new LinkedList<>();
    final LinkedList<Map<String, Object>> chat = new LinkedList<>();
    private final Map<String, Double> sectorTrend = new HashMap<>();
    private final List<Defs.Co> extras = new ArrayList<>();
    private final Set<String> removed = new LinkedHashSet<>();
    final Series ix = new Series();
    double divisor = 1, ixOpen, ixHigh, ixLow;
    private final List<Pending> pending = new ArrayList<>();
    private final Random rnd = new Random();
    double volMul, marketTrend;
    volatile boolean paused, auto = true, open = true;
    long sessionEnd, nextAuto, ticks, frozenAt;
    final Map<String, Series> sx = new LinkedHashMap<>();
    private final Map<String, Double> sdiv = new HashMap<>();
    private final Map<String, double[]> sstat = new HashMap<>();

    Realm(Game game, Defs.RealmDef def) {
        this.game = game;
        this.def = def;
        volMul = def.volMul();
        for (Defs.Co d : Defs.COMPANIES) {
            cos.put(d.id(), new Company(d, 0.92 + rnd.nextDouble() * 0.16));
            sectorTrend.putIfAbsent(d.sector(), 0.0);
        }
        long now = System.currentTimeMillis();
        sessionEnd = now + Defs.SESSION_MS;
        nextAuto = now + 15_000;
        divisor = totalCap() / 1000;
        ixOpen = ixHigh = ixLow = index();
        ix.add(now / 1000, r2(index()));
        ensureSectors();
    }

    private double sectorCap(String sec) {
        double t = 0;
        for (Company c : cos.values()) if (c.d.sector().equals(sec)) t += c.cap();
        return t;
    }

    private double sectorIdx(String sec) {
        Double dv = sdiv.get(sec);
        return dv == null || dv <= 0 ? 0 : sectorCap(sec) / dv;
    }

    private void ensureSectors() {
        long now = System.currentTimeMillis() / 1000;
        for (String sec : sectors()) {
            if (sdiv.containsKey(sec)) continue;
            sdiv.put(sec, sectorCap(sec) / 1000);
            Series se = new Series();
            se.add(now, r2(sectorIdx(sec)));
            sx.put(sec, se);
            double v = sectorIdx(sec);
            sstat.put(sec, new double[]{v, v, v});
        }
        for (String sec : new ArrayList<>(sdiv.keySet())) {
            if (!sectors().contains(sec)) {
                sdiv.remove(sec);
                sx.remove(sec);
                sstat.remove(sec);
            }
        }
    }

    private Map<String, Double> sectorSnapshot() {
        Map<String, Double> m = new HashMap<>();
        for (String sec : sdiv.keySet()) m.put(sec, sectorIdx(sec));
        return m;
    }

    private void rebase(Map<String, Double> before) {
        for (String sec : sectors()) {
            Double b = before.get(sec);
            if (b != null && b > 0 && sdiv.containsKey(sec)) sdiv.put(sec, sectorCap(sec) / b);
        }
        ensureSectors();
    }

    void recount() {
        for (Company c : cos.values()) c.held = 0;
        for (Player p : players.values())
            for (Map.Entry<String, Player.Holding> e : p.hold.entrySet()) {
                Company c = cos.get(e.getKey());
                if (c != null) c.held += e.getValue().qty;
            }
    }

    synchronized void reconcile() {
        for (Player p : players.values()) {
            if (p.banned) {
                p.hold.clear();
                continue;
            }
            p.hold.entrySet().removeIf(e -> {
                if (cos.containsKey(e.getKey())) return false;
                p.cash = r2(p.cash + e.getValue().qty * e.getValue().avg);
                return true;
            });
        }
        recount();
        for (Company c : cos.values()) {
            long need = c.held + c.flood;
            if (c.floatShares < need) c.floatShares = (long) Math.ceil(need * 1.3);
        }
    }

    double totalCap() {
        double t = 0;
        for (Company c : cos.values()) t += c.cap();
        return t;
    }

    double index() {
        return totalCap() / divisor;
    }

    List<String> sectors() {
        Set<String> out = new LinkedHashSet<>();
        for (Company c : cos.values()) out.add(c.d.sector());
        return new ArrayList<>(out);
    }

    synchronized void join(Player p) {
        players.put(p.key(), p);
        system(p.name + " joined the floor");
    }

    synchronized void tick() {
        if (!open || paused) return;
        long now = System.currentTimeMillis();
        ticks++;
        recount();
        for (Company c : cos.values()) {
            if (c.flood <= 0) continue;
            long chunk = Math.min(c.flood, Math.max(1, Math.max((long) Math.ceil(c.flood * 0.03), c.floatShares / 2000)));
            c.flood -= chunk;
            c.addFlow(-Math.min(0.02, 0.35 * c.price * chunk / c.depth() * 0.5));
        }
        marketTrend = marketTrend * 0.995 + rnd.nextGaussian() * 0.00003;
        sectorTrend.replaceAll((k, v) -> v * 0.99 + rnd.nextGaussian() * 0.00005);
        long nowSec = now / 1000;
        for (Company c : cos.values()) c.tick(marketTrend, sectorTrend.getOrDefault(c.d.sector(), 0.0), volMul, nowSec);
        double iv = index();
        ix.add(nowSec, r2(iv));
        ixHigh = Math.max(ixHigh, iv);
        ixLow = Math.min(ixLow, iv);
        for (String sec : sdiv.keySet()) {
            double v = sectorIdx(sec);
            sx.get(sec).add(nowSec, r2(v));
            double[] st = sstat.get(sec);
            st[1] = Math.max(st[1], v);
            st[2] = Math.min(st[2], v);
        }
        for (Pending p : new ArrayList<>(pending)) {
            if (p.at() > now) continue;
            pending.remove(p);
            try {
                p.run().run();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        if (now >= sessionEnd) {
            resetSession();
            system("New session started. Opening prices have been reset");
        }
        if (auto && now >= nextAuto) {
            autoNews();
            nextAuto = now + 40_000 + rnd.nextInt(50_000);
        }
        if (ticks % 5 == 0) for (Player p : players.values()) milestones(p);
    }

    synchronized void resetSession() {
        for (Company c : cos.values()) c.resetSession();
        ixOpen = ixHigh = ixLow = index();
        for (String sec : sdiv.keySet()) {
            double v = sectorIdx(sec);
            sstat.put(sec, new double[]{v, v, v});
        }
        sessionEnd = System.currentTimeMillis() + Defs.SESSION_MS;
    }

    synchronized String frame(int online) {
        List<Object> rows = new ArrayList<>();
        for (Company c : cos.values())
            rows.add(Arrays.asList(c.d.id(), r2(c.price), r2(c.open), r2(c.high), r2(c.low), Math.round(c.mood),
                    c.showLimit ? c.hit : 0, c.showLimit ? r2(c.lo()) : 0, c.showLimit ? r2(c.hi()) : 0,
                    c.avail(), c.floatShares, Math.round(c.flow * 1e4) / 1e4));
        return Json.write(Json.of("type", "tick", "t", System.currentTimeMillis(), "se", sessionEnd, "paused", paused,
                "on", online, "ix", Arrays.asList(r2(index()), r2(ixOpen), r2(ixHigh), r2(ixLow)), "c", rows));
    }

    private void emit(String type, String key, Object value) {
        game.push(def.id(), Json.write(Json.of("type", type, key, value)));
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private double px(String id) {
        Company c = cos.get(id);
        return c == null ? 0 : c.price;
    }

    double worth(Player p) {
        double w = p.cash;
        for (Map.Entry<String, Player.Holding> e : p.hold.entrySet()) w += e.getValue().qty * px(e.getKey());
        return w;
    }

    private void note(Player p, String kind, String title, String sub) {
        p.pending.add(Json.of("k", kind, "title", title, "sub", sub));
    }

    private void addXp(Player p, long x) {
        int before = p.level();
        p.xp += x;
        for (int l = before + 1; l <= p.level(); l++) {
            int bonus = 150 * l;
            p.cash += bonus;
            note(p, "level", "Level " + l, "+\uA7A8" + bonus + " bonus. You are now a " + p.title());
        }
    }

    private void mission(Player p, String id, int inc) {
        p.rollDay();
        if (p.mdone.contains(id)) return;
        Defs.Mission m = Defs.mission(id);
        int v = p.mprog.merge(id, inc, Integer::sum);
        if (v < m.goal()) return;
        p.mdone.add(id);
        p.cash += m.cash();
        addXp(p, m.xp());
        note(p, "mission", "Mission complete: " + m.title(), "+\uA7A8" + m.cash() + " and +" + m.xp() + " XP");
    }

    private void badge(Player p, String id) {
        if (!p.badges.add(id)) return;
        Defs.Badge b = Defs.badge(id);
        addXp(p, b.xp());
        note(p, "badge", "Badge unlocked: " + b.name(), b.desc() + ". +" + b.xp() + " XP");
    }

    private void milestones(Player p) {
        double w = worth(p);
        p.peak = Math.max(p.peak, w);
        if (w >= 20000) badge(p, "rich1");
        if (w >= 50000) badge(p, "rich2");
        if (w >= 100000) badge(p, "rich3");
        if (p.hold.size() >= 5) badge(p, "diverse");
        if (p.streak >= 7) badge(p, "streak7");
        if (p.seen.containsAll(cos.keySet())) badge(p, "scout");
    }

    private boolean hotNews(Company c) {
        long now = System.currentTimeMillis();
        for (News n : news) {
            if (now - n.ts > HOT_NEWS_MS) break;
            if (n.kind.equals("update") || n.status.equals("debunked")) continue;
            if (n.tType.equals("company") && n.tId.equals(c.d.id())) return true;
            if (n.tType.equals("sector") && n.tId.equals(c.d.sector())) return true;
        }
        return false;
    }

    synchronized Map<String, Object> trade(Player p, String id, String side, long qty) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        if (qty < 1 || qty > 10_000_000) throw new ApiError(400, "Invalid quantity");
        if (paused) throw new ApiError(409, "Trading is paused right now");
        boolean buy = side.equals("buy");
        if (!buy && !side.equals("sell")) throw new ApiError(400, "Invalid side");
        if (buy && qty > c.avail())
            throw new ApiError(409, c.avail() == 0 ? "Sold out. No " + id + " shares are available right now"
                    : "Only " + c.avail() + " " + id + " shares are available");
        double impact = Math.min(0.06, 0.35 * c.price * qty / c.depth());
        double px = r2(c.clampPx(c.price * Math.max(0.5, 1 + c.flow + (buy ? impact : -impact) / 2)));
        double gross = px * qty, fee = gross * Defs.FEE;
        Player.Holding h = p.hold.get(id);
        Double pl = null;
        double pct = 0;
        if (buy) {
            if (p.cash < gross + fee) throw new ApiError(400, "Not enough cash");
            if (h == null) {
                h = new Player.Holding();
                p.hold.put(id, h);
            }
            h.avg = (h.avg * h.qty + gross + fee) / (h.qty + qty);
            h.qty += qty;
            h.addLot(System.currentTimeMillis() / 1000, px, qty);
            p.cash -= gross + fee;
        } else {
            if (h == null || h.qty < qty) throw new ApiError(400, "You don't own that many shares");
            double cost = h.avg * qty;
            pl = gross - fee - cost;
            pct = cost > 0 ? pl / cost * 100 : 0;
            p.realized += pl;
            p.bestPct = Math.max(p.bestPct, pct);
            h.qty -= qty;
            h.takeLots(qty);
            if (h.qty == 0) p.hold.remove(id);
            p.cash += gross - fee;
        }
        p.cash = r2(p.cash);
        c.held += buy ? qty : -qty;
        c.addFlow(buy ? impact : -impact);
        p.trades++;
        p.rollDay();
        long xp = 8 + Math.min(30, (long) (gross / 400));
        if (pl != null && pl > 0) xp += Math.min(60, (long) (pct * 2));
        addXp(p, xp);
        mission(p, "trader", 1);
        badge(p, "first_trade");
        if (gross >= 5000) badge(p, "whale");
        if (pl != null && pl > 0) mission(p, "profit", 1);
        if (pl != null && pct >= 25) badge(p, "diamond");
        if (pl != null && pct <= -15) badge(p, "paper");
        if (hotNews(c)) {
            mission(p, "hound", 1);
            badge(p, "sniper");
        }
        milestones(p);
        return Json.of("side", side, "id", id, "qty", qty, "price", px, "fee", r2(fee),
                "pl", pl == null ? null : r2(pl), "impact", r2(impact * 100));
    }

    synchronized Map<String, Object> daily(Player p) {
        long today = Player.today();
        if (p.lastDaily == today) throw new ApiError(409, "Already claimed today");
        p.streak = p.lastDaily == today - 1 ? p.streak + 1 : 1;
        p.lastDaily = today;
        int reward = 400 + 150 * Math.min(p.streak, 7);
        p.cash += reward;
        addXp(p, 25);
        note(p, "daily", "Daily bonus: day " + p.streak, "+\uA7A8" + reward + " and +25 XP");
        milestones(p);
        return Json.of("reward", reward, "streak", p.streak);
    }

    synchronized void visit(Player p, String id) {
        if (!cos.containsKey(id)) throw new ApiError(404, "Unknown company");
        p.rollDay();
        p.seen.add(id);
        if (p.dayVisits.add(id)) mission(p, "scout", 1);
        milestones(p);
    }

    synchronized void watch(Player p, String id) {
        if (!cos.containsKey(id)) throw new ApiError(404, "Unknown company");
        if (!p.watch.remove(id)) p.watch.add(id);
    }

    synchronized Map<String, Object> intel(Player p, long newsId) {
        News n = news.stream().filter(x -> x.id == newsId).findFirst().orElseThrow(() -> new ApiError(404, "News not found"));
        if (!n.kind.equals("rumor") || !n.status.equals("pending")) throw new ApiError(409, "Nothing left to verify");
        String key = String.valueOf(newsId);
        if (!p.intel.containsKey(key)) {
            if (p.cash < Defs.INTEL_COST) throw new ApiError(400, "Not enough cash for intel");
            p.cash = r2(p.cash - Defs.INTEL_COST);
            p.intel.put(key, n.hint());
            if (p.intel.size() > 60) p.intel.remove(p.intel.keySet().iterator().next());
            addXp(p, 5);
            badge(p, "insider");
        }
        return Json.of("hint", p.intel.get(key));
    }

    synchronized void say(Player p, String text) {
        if (p.muted) throw new ApiError(403, "You are muted");
        text = text.trim();
        if (text.isEmpty()) throw new ApiError(400, "Say something first");
        if (text.length() > 140) text = text.substring(0, 140);
        long now = System.currentTimeMillis();
        if (now - p.lastChat < 1500) throw new ApiError(429, "Slow down a little");
        p.lastChat = now;
        if (++p.chats >= 10) badge(p, "chatty");
        post(Json.of("id", Game.nextId(), "ts", now, "name", p.name, "avatar", p.avatar, "level", p.level(),
                "text", text, "sys", false));
    }

    synchronized void system(String text) {
        post(Json.of("id", Game.nextId(), "ts", System.currentTimeMillis(), "name", "", "avatar", "", "level", 0,
                "text", text, "sys", true));
    }

    private void post(Map<String, Object> m) {
        chat.addLast(m);
        while (chat.size() > 60) chat.removeFirst();
        emit("chat", "m", m);
    }

    synchronized void clearChat() {
        chat.clear();
        emit("chatclear", "ok", true);
    }

    private List<Company> targets(News n) {
        List<Company> out = new ArrayList<>();
        for (Company c : cos.values()) {
            if (n.tType.equals("market") || (n.tType.equals("sector") && c.d.sector().equals(n.tId))
                    || (n.tType.equals("company") && c.d.id().equals(n.tId))) out.add(c);
        }
        return out;
    }

    private static double scale(News n, Company c) {
        return n.tType.equals("company") ? 1 : c.d.beta() * 0.8;
    }

    private void apply(News n, double pct) {
        for (Company c : targets(n)) c.pulse(pct * scale(n, c));
    }

    synchronized News publish(String kind, String tType, String tId, String headline, String body, double impact, double cred) {
        if (!kind.equals("news") && !kind.equals("rumor")) throw new ApiError(400, "Invalid news type");
        if (tType.equals("company") && !cos.containsKey(tId)) throw new ApiError(400, "Unknown company");
        if (tType.equals("sector") && !sectors().contains(tId)) throw new ApiError(400, "Unknown sector");
        if (!tType.equals("company") && !tType.equals("sector") && !tType.equals("market")) throw new ApiError(400, "Invalid target");
        headline = headline.trim();
        if (headline.isEmpty()) throw new ApiError(400, "Headline required");
        if (headline.length() > 140) headline = headline.substring(0, 140);
        if (body.length() > 400) body = body.substring(0, 400);
        News n = new News();
        n.id = Game.nextId();
        n.ts = System.currentTimeMillis();
        n.kind = kind;
        n.tType = tType;
        n.tId = tType.equals("market") ? "" : tId;
        n.headline = headline;
        n.body = body.trim();
        n.impact = Math.max(-40, Math.min(40, impact));
        if (kind.equals("rumor")) {
            n.cred = Math.max(0, Math.min(1, cred));
            n.status = "pending";
            queueResolve(n, n.ts + RUMOR_MIN_MS + (long) (rnd.nextDouble() * RUMOR_SPREAD_MS));
        } else {
            n.cred = 1;
            apply(n, n.impact);
        }
        add(n);
        return n;
    }

    private void queueResolve(News n, long at) {
        pending.add(new Pending(at, () -> resolve(n)));
    }

    private void add(News n) {
        news.addFirst(n);
        while (news.size() > 150) news.removeLast();
        emit("news", "n", n.view(false));
    }

    private void resolve(News n) {
        if (!n.status.equals("pending")) return;
        boolean ok = rnd.nextDouble() < n.cred;
        n.status = ok ? "confirmed" : "debunked";
        if (ok) apply(n, n.impact);
        emit("news", "n", n.view(false));
        News u = new News();
        u.id = Game.nextId();
        u.ts = System.currentTimeMillis();
        u.kind = "update";
        u.status = n.status;
        u.tType = n.tType;
        u.tId = n.tId;
        u.impact = ok ? n.impact : 0;
        u.cred = 1;
        u.headline = (ok ? "Confirmed: " : "Debunked: ") + n.headline;
        u.body = ok ? "The rumor was true. Markets are repricing." : "The rumor was false. Markets are unaffected.";
        add(u);
    }

    private static String fill(String t, Company c, String sector) {
        return t.replace("%c", c == null ? "" : c.d.name()).replace("%o", c == null ? "" : c.ceo).replace("%s", sector == null ? "" : sector);
    }

    private <T> T pick(List<T> l) {
        return l.get(rnd.nextInt(l.size()));
    }

    private String pick(String[] a) {
        return a[rnd.nextInt(a.length)];
    }

    private void autoNews() {
        int r = rnd.nextInt(100);
        if (r < 8) {
            ceoResign(pick(new ArrayList<>(cos.values())).d.id(), null);
            return;
        }
        if (r < 13) {
            supplyEvent();
            return;
        }
        boolean up = rnd.nextBoolean();
        double mag = 1.5 + rnd.nextDouble() * 4.5;
        double impact = up ? mag : -mag;
        String kind = rnd.nextInt(4) == 0 ? "rumor" : "news";
        double cred = kind.equals("rumor") ? 0.15 + rnd.nextDouble() * 0.70 : 1;
        String body = up ? Defs.BODY_UP : Defs.BODY_DOWN;
        if (r < 68) {
            Company c = pick(new ArrayList<>(cos.values()));
            publish(kind, "company", c.d.id(), fill(pick(up ? Defs.UP : Defs.DOWN), c, null), body, impact, cred);
        } else if (r < 88) {
            String s = pick(sectors());
            publish(kind, "sector", s, fill(pick(up ? Defs.SECTOR_UP : Defs.SECTOR_DOWN), null, s), body, impact * 0.7, cred);
        } else {
            publish(kind, "market", "", pick(up ? Defs.MARKET_UP : Defs.MARKET_DOWN), body, impact * 0.5, cred);
        }
    }

    synchronized News ceoResign(String id, String successor) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        String old = c.ceo;
        String next = successor == null || successor.isBlank() ? Defs.fakeName(rnd, old)
                : successor.trim().substring(0, Math.min(30, successor.trim().length()));
        String head = fill(pick(Defs.CEO_OUT), c, null);
        c.ceo = next;
        News n = publish("news", "company", id, head,
                next + " has been named the new CEO effective immediately. Investors are weighing what it means.",
                -(4 + rnd.nextDouble() * 5), 1);
        emit("ceo", "c", Json.of("id", id, "ceo", next));
        pending.add(new Pending(System.currentTimeMillis() + 25_000 + rnd.nextInt(20_000), () -> {
            Company cc = cos.get(id);
            if (cc == null) return;
            publish("news", "company", id, pick(Defs.CEO_IN).replace("%n", next).replace("%c", cc.d.name()),
                    "The new chief executive promises continuity and a fresh start.", 0.5 + rnd.nextDouble() * 2.5, 1);
        }));
        return n;
    }

    private void supplyEvent() {
        Company c = pick(new ArrayList<>(cos.values()));
        if (rnd.nextBoolean()) {
            long cut = (long) (c.avail() * (0.05 + rnd.nextDouble() * 0.15));
            if (cut < 1) return;
            c.floatShares -= cut;
            publish("news", "company", c.d.id(), fill(pick(Defs.BUYBACK), c, null),
                    cut + " shares are being retired, so fewer are left to trade.", 2 + rnd.nextDouble() * 2, 1);
        } else {
            long add = Math.max(1, (long) (c.floatShares * (0.08 + rnd.nextDouble() * 0.10)));
            c.floatShares += add;
            publish("news", "company", c.d.id(), fill(pick(Defs.OFFERING), c, null),
                    add + " new shares are coming to the market, diluting existing holders.", -(2 + rnd.nextDouble() * 2), 1);
        }
    }

    private List<Row> ranked() {
        List<Row> rows = new ArrayList<>();
        for (Player p : players.values()) if (!p.banned) rows.add(new Row(p, worth(p)));
        rows.sort((a, b) -> Double.compare(b.worth(), a.worth()));
        return rows;
    }

    synchronized Map<String, Object> leaders(Player me) {
        List<Row> rows = ranked();
        List<Object> top = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Player p = rows.get(i).p();
            if (p == me) rank = i + 1;
            if (i < 25)
                top.add(Json.of("rank", i + 1, "name", p.name, "avatar", p.avatar, "level", p.level(), "title", p.title(),
                        "worth", r2(rows.get(i).worth()), "gain", r2((rows.get(i).worth() / Defs.START_CASH - 1) * 100)));
        }
        return Json.of("top", top, "rank", rank, "total", rows.size());
    }

    synchronized Map<String, Object> me(Player p) {
        p.rollDay();
        long today = Player.today();
        List<Object> hold = new ArrayList<>();
        double invested = 0, value = 0;
        for (Map.Entry<String, Player.Holding> e : p.hold.entrySet()) {
            Player.Holding h = e.getValue();
            invested += h.qty * h.avg;
            value += h.qty * px(e.getKey());
            List<Object> lots = new ArrayList<>();
            for (int i = Math.max(0, h.lots.size() - 12); i < h.lots.size(); i++) {
                double[] l = h.lots.get(i);
                lots.add(Json.of("t", (long) l[0], "p", r2(l[1]), "q", (long) l[2]));
            }
            hold.add(Json.of("id", e.getKey(), "qty", h.qty, "avg", r2(h.avg), "lots", lots));
        }
        List<Object> ms = new ArrayList<>();
        for (Defs.Mission m : Defs.MISSIONS)
            ms.add(Json.of("id", m.id(), "title", m.title(), "desc", m.desc(), "goal", m.goal(),
                    "progress", Math.min(m.goal(), p.mprog.getOrDefault(m.id(), 0)), "cash", m.cash(), "xp", m.xp(),
                    "done", p.mdone.contains(m.id())));
        List<Object> events = new ArrayList<>(p.pending);
        p.pending.clear();
        int lv = p.level();
        int rank = 1;
        double mine = worth(p);
        for (Player o : players.values()) if (!o.banned && o != p && worth(o) > mine) rank++;
        return Json.of("name", p.name, "avatar", p.avatar, "bio", p.bio, "realm", def.id(), "level", lv,
                "title", p.title(), "xp", p.xp, "xpBase", Player.xpFor(lv), "xpNext", Player.xpFor(lv + 1),
                "cash", r2(p.cash), "worth", r2(mine), "invested", r2(invested), "value", r2(value), "hold", hold, "badges", new ArrayList<>(p.badges),
                "watch", new ArrayList<>(p.watch), "streak", p.lastDaily >= today - 1 ? p.streak : 0,
                "canClaim", p.lastDaily != today, "missions", ms, "trades", p.trades, "realized", r2(p.realized),
                "bestPct", r2(p.bestPct), "joined", p.joined, "intel", p.intel, "events", events, "rank", rank,
                "players", players.size());
    }

    synchronized Map<String, Object> market(int online) {
        List<Object> cs = new ArrayList<>();
        for (Company c : cos.values()) cs.add(c.snap());
        List<Object> ns = new ArrayList<>();
        for (News n : news) {
            if (ns.size() >= 40) break;
            ns.add(n.view(false));
        }
        return Json.of("realm", Json.of("id", def.id(), "name", def.name(), "paused", paused, "open", open),
                "now", System.currentTimeMillis(), "se", sessionEnd, "on", online, "c", cs, "news", ns,
                "chat", new ArrayList<>(chat), "sectors", sectors(),
                "ix", Json.of("v", r2(index()), "open", r2(ixOpen), "high", r2(ixHigh), "low", r2(ixLow)));
    }

    synchronized Map<String, Object> company(String id) {
        if (!cos.containsKey(id)) throw new ApiError(404, "Unknown company");
        long held = 0;
        List<Object[]> hs = new ArrayList<>();
        for (Player p : players.values()) {
            Player.Holding h = p.hold.get(id);
            if (h != null && h.qty > 0 && !p.banned) {
                held += h.qty;
                hs.add(new Object[]{p, h.qty});
            }
        }
        hs.sort((a, b) -> Long.compare((Long) b[1], (Long) a[1]));
        List<Object> board = new ArrayList<>();
        for (int i = 0; i < Math.min(3, hs.size()); i++) {
            Player p = (Player) hs.get(i)[0];
            board.add(Json.of("name", p.name, "avatar", p.avatar, "qty", hs.get(i)[1]));
        }
        int watchers = 0;
        for (Player p : players.values()) if (p.watch.contains(id)) watchers++;
        return Json.of("id", id, "holders", hs.size(), "held", held, "board", board, "watchers", watchers);
    }

    synchronized Map<String, Object> admin(int online, Set<String> sec) {
        Map<String, Object> out = Json.of("id", def.id(), "name", def.name(), "paused", paused, "open", open, "volMul", volMul,
                "auto", auto, "online", online, "playerCount", players.size(), "sessionEnd", sessionEnd,
                "sectors", sectors(), "index", r2(index()));
        if (sec.contains("companies")) {
            List<Object> cs = new ArrayList<>();
            for (Company c : cos.values()) {
                int holders = 0;
                for (Player p : players.values()) if (p.hold.containsKey(c.d.id())) holders++;
                cs.add(Json.of("id", c.d.id(), "name", c.d.name(), "sector", c.d.sector(), "ceo", c.ceo,
                        "price", r2(c.price), "open", r2(c.open), "mood", Math.round(c.mood), "holders", holders,
                        "shock", r2(c.pendingPct()), "cap", c.cap(), "tier", c.tier(), "band", c.band,
                        "auto", Company.autoBand(c.cap()), "override", c.bandOverride > 0, "lo", r2(c.lo()), "hi", r2(c.hi()),
                        "hit", c.hit, "showLimit", c.showLimit, "float", c.floatShares, "held", c.held, "flood", c.flood,
                        "avail", c.avail(), "trendLeft", c.trendLeft,
                        "trendPct", r2((Math.exp(c.trendPer * c.trendLeft) - 1) * 100)));
            }
            out.put("companies", cs);
        }
        if (sec.contains("players")) {
            List<Object> ps = new ArrayList<>();
            for (Row r : ranked()) {
                if (ps.size() >= 100) break;
                Player p = r.p();
                ps.add(Json.of("name", p.name, "avatar", p.avatar, "level", p.level(), "worth", r2(r.worth()),
                        "cash", r2(p.cash), "trades", p.trades, "muted", p.muted, "banned", false, "lastSeen", p.lastSeen));
            }
            for (Player p : players.values())
                if (p.banned)
                    ps.add(Json.of("name", p.name, "avatar", p.avatar, "level", p.level(), "worth", r2(worth(p)),
                            "cash", r2(p.cash), "trades", p.trades, "muted", p.muted, "banned", true, "lastSeen", p.lastSeen));
            out.put("players", ps);
        }
        if (sec.contains("news")) {
            List<Object> ns = new ArrayList<>();
            for (News n : news) {
                if (ns.size() >= 40) break;
                ns.add(n.view(true));
            }
            out.put("news", ns);
        }
        if (sec.contains("chat")) out.put("chat", new ArrayList<>(chat));
        return out;
    }

    synchronized void ban(Player p, boolean on) {
        p.banned = on;
        if (on) confiscate(p);
    }

    synchronized void resetPlayer(Player p) {
        p.cash = Defs.START_CASH;
        confiscate(p);
    }

    private void confiscate(Player p) {
        for (Map.Entry<String, Player.Holding> e : p.hold.entrySet()) {
            Company c = cos.get(e.getKey());
            if (c != null) c.flood += e.getValue().qty;
        }
        p.hold.clear();
        recount();
    }

    private boolean active() {
        return open && !paused;
    }

    private void shiftTimers(long dt) {
        if (dt <= 0) return;
        sessionEnd += dt;
        nextAuto += dt;
        List<Pending> old = new ArrayList<>(pending);
        pending.clear();
        for (Pending p : old) pending.add(new Pending(p.at() + dt, p.run()));
    }

    private void transition(boolean wasActive) {
        boolean now = active();
        long t = System.currentTimeMillis();
        if (wasActive && !now) frozenAt = t;
        else if (!wasActive && now) shiftTimers(t - frozenAt);
    }

    synchronized void setOpen(boolean on) {
        if (on == open) return;
        boolean was = active();
        open = on;
        transition(was);
        if (!on) emit("closed", "ok", true);
    }

    synchronized void setPaused(boolean on) {
        if (on == paused) return;
        boolean was = active();
        paused = on;
        transition(was);
    }

    synchronized void setFloat(String id, long n) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        recount();
        if (n < 100 || n > 1_000_000_000L) throw new ApiError(400, "Float must be between 100 and 1,000,000,000 shares");
        if (n < c.held + c.flood)
            throw new ApiError(409, "Players hold " + c.held + " shares and " + c.flood + " are still being released. Float can't go below " + (c.held + c.flood));
        c.floatShares = n;
    }

    synchronized void trend(String id, double pct, double minutes) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        if (minutes < 0.5 || minutes > 240) throw new ApiError(400, "Duration must be between 0.5 and 240 minutes");
        c.startTrend(pct, minutes);
    }

    synchronized void stopTrend(String id) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        c.stopTrend();
    }

    synchronized Map<String, Object> graphOverview() {
        List<Object> ss = new ArrayList<>();
        for (String sec : sectors()) {
            double[] st = sstat.get(sec);
            int n = 0, adv = 0;
            for (Company c : cos.values()) {
                if (!c.d.sector().equals(sec)) continue;
                n++;
                if (c.price >= c.open) adv++;
            }
            ss.add(Json.of("name", sec, "v", r2(sectorIdx(sec)), "open", r2(st[0]), "high", r2(st[1]), "low", r2(st[2]),
                    "cap", sectorCap(sec), "n", n, "adv", adv));
        }
        return Json.of("id", def.id(), "name", def.name(), "open", open, "paused", paused, "se", sessionEnd,
                "now", System.currentTimeMillis(), "ix", Json.of("v", r2(index()), "open", r2(ixOpen), "high", r2(ixHigh), "low", r2(ixLow)),
                "sectors", ss);
    }

    synchronized Map<String, Object> graphLive() {
        Map<String, Object> sv = new LinkedHashMap<>();
        for (String sec : sectors()) {
            double[] st = sstat.get(sec);
            sv.put(sec, Arrays.asList(r2(sectorIdx(sec)), r2(st[0]), r2(st[1]), r2(st[2])));
        }
        return Json.of("t", System.currentTimeMillis() / 1000, "open", open, "paused", paused, "se", sessionEnd,
                "ix", Arrays.asList(r2(index()), r2(ixOpen), r2(ixHigh), r2(ixLow)), "s", sv, "n", sectors());
    }

    synchronized Map<String, Object> graphHistory(String id) {
        if (id.equals("INDEX")) return ix.merged();
        Series se = sx.get(id);
        if (se == null) throw new ApiError(404, "Unknown sector");
        return se.merged();
    }

    synchronized void setPrice(String id, double price) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        if (price < 1 || price > 100000) throw new ApiError(400, "Price must be between 1 and 100000");
        c.setPrice(price);
    }

    synchronized void shock(String id, double pct) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        c.pulse(Math.max(-40, Math.min(40, pct)));
    }

    private void put(Defs.Co d) {
        cos.put(d.id(), new Company(d, 1.0));
        sectorTrend.putIfAbsent(d.sector(), 0.0);
    }

    synchronized void addCompany(Defs.Co d) {
        if (cos.containsKey(d.id())) throw new ApiError(409, d.id() + " already exists on " + def.name());
        double before = index();
        Map<String, Double> sb = sectorSnapshot();
        put(d);
        extras.add(d);
        removed.remove(d.id());
        divisor = totalCap() / before;
        rebase(sb);
        system("New listing: " + d.name() + " (" + d.id() + ")");
        emit("companies", "ok", true);
    }

    synchronized void delist(String id) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        if (cos.size() <= 2) throw new ApiError(409, "Keep at least two companies listed");
        double before = index();
        Map<String, Double> sb = sectorSnapshot();
        for (Player p : players.values()) {
            Player.Holding h = p.hold.remove(id);
            if (h != null) p.cash = r2(p.cash + h.qty * c.price);
            p.watch.remove(id);
        }
        cos.remove(id);
        extras.removeIf(x -> x.id().equals(id));
        if (Defs.COMPANIES.stream().anyMatch(x -> x.id().equals(id))) removed.add(id);
        divisor = totalCap() / before;
        rebase(sb);
        system(c.d.name() + " (" + id + ") was delisted. Holdings were cashed out at the last price");
        emit("companies", "ok", true);
    }

    synchronized Map<String, Object> history(String id) {
        if (id.equals("INDEX")) return ix.merged();
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        return c.s.merged();
    }

    synchronized void setBand(String id, double pct) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        c.setBand(pct);
    }

    synchronized void showLimit(String id, boolean on) {
        Company c = cos.get(id);
        if (c == null) throw new ApiError(404, "Unknown company");
        c.showLimit = on;
        emit("companies", "ok", true);
    }

    synchronized void showLimits(boolean on) {
        for (Company c : cos.values()) c.showLimit = on;
        emit("companies", "ok", true);
    }

    synchronized Map<String, Object> dump() {
        Map<String, Object> cm = new LinkedHashMap<>();
        cos.forEach((k, v) -> cm.put(k, v.dump()));
        List<Object> ns = new ArrayList<>();
        for (News n : news) ns.add(n.view(true));
        List<Object> ex = new ArrayList<>();
        for (Defs.Co d : extras) ex.add(Defs.coMap(d));
        return Json.of("volMul", volMul, "paused", paused, "auto", auto, "companies", cm, "news", ns,
                "extras", ex, "removed", new ArrayList<>(removed), "divisor", divisor, "index", ix.dumpCoarse(),
                "open", open, "sectors", sdump());
    }

    private Map<String, Object> sdump() {
        Map<String, Object> o = new LinkedHashMap<>();
        for (String sec : sdiv.keySet()) o.put(sec, Json.of("div", sdiv.get(sec), "hist", sx.get(sec).dumpCoarse()));
        return o;
    }

    synchronized void restore(Map<String, Object> m) {
        volMul = Json.num(m.get("volMul"), volMul);
        paused = Json.bool(m.get("paused"), false);
        auto = Json.bool(m.get("auto"), true);
        for (Object r : Json.list(m.get("removed"))) {
            removed.add(String.valueOf(r));
            cos.remove(String.valueOf(r));
        }
        for (Object o : Json.list(m.get("extras"))) {
            Defs.Co d = Defs.coFrom(Json.map(o));
            put(d);
            extras.add(d);
        }
        open = Json.bool(m.get("open"), true);
        if (!active()) frozenAt = System.currentTimeMillis();
        Set<String> saved = new HashSet<>();
        Json.map(m.get("companies")).forEach((k, v) -> {
            Company c = cos.get(k);
            if (c != null) {
                c.restore(Json.map(v));
                saved.add(k);
            }
        });
        double capSaved = 0;
        for (Company c : cos.values()) if (saved.contains(c.d.id())) capSaved += c.cap();
        divisor = Json.num(m.get("divisor"), 0);
        divisor = divisor <= 0 || capSaved <= 0 ? totalCap() / 1000 : divisor * totalCap() / capSaved;
        sdiv.clear();
        sx.clear();
        sstat.clear();
        ensureSectors();
        Map<String, Object> sm = Json.map(m.get("sectors"));
        for (String sec : sectors()) {
            Map<String, Object> e = Json.map(sm.get(sec));
            double dv = Json.num(e.get("div"), 0), cs = 0;
            for (Company c : cos.values()) if (c.d.sector().equals(sec) && saved.contains(c.d.id())) cs += c.cap();
            if (dv <= 0 || cs <= 0) continue;
            sdiv.put(sec, dv * sectorCap(sec) / cs);
            Map<String, Object> h = Json.map(e.get("hist"));
            Series se = sx.get(sec);
            se.reset();
            se.restoreCoarse(Json.list(h.get("t")), Json.list(h.get("v")));
            se.add(System.currentTimeMillis() / 1000, r2(sectorIdx(sec)));
            double v = sectorIdx(sec);
            sstat.put(sec, new double[]{v, v, v});
        }
        ixOpen = ixHigh = ixLow = index();
        Map<String, Object> hx = Json.map(m.get("index"));
        ix.reset();
        ix.restoreCoarse(Json.list(hx.get("t")), Json.list(hx.get("v")));
        ix.add(System.currentTimeMillis() / 1000, r2(index()));
        for (Object o : Json.list(m.get("news"))) news.addLast(News.from(Json.map(o)));
        long now = System.currentTimeMillis();
        for (News n : news)
            if (n.kind.equals("rumor") && n.status.equals("pending")) queueResolve(n, now + 3_000 + rnd.nextInt(7_000));
    }
}