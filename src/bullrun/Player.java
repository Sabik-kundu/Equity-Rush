package bullrun;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

final class Player {
    static final class Holding {
        long qty;
        double avg;
        final List<double[]> lots = new ArrayList<>();

        void addLot(long ts, double price, long q) {
            lots.add(new double[]{ts, price, q});
            if (lots.size() > 40) {
                double[] a = lots.remove(0), b = lots.get(0);
                b[1] = (a[1] * a[2] + b[1] * b[2]) / (a[2] + b[2]);
                b[2] += a[2];
                b[0] = a[0];
            }
        }

        void takeLots(long q) {
            long left = q;
            while (left > 0 && !lots.isEmpty()) {
                double[] l = lots.get(0);
                long take = Math.min(left, (long) l[2]);
                l[2] -= take;
                left -= take;
                if (l[2] < 1) lots.remove(0);
            }
        }
    }

    final String name;
    String salt = "", hash = "", avatar = "🦊", realm = "alpha", bio = "";
    double cash = Defs.START_CASH, realized, bestPct, peak = Defs.START_CASH;
    long xp, joined = System.currentTimeMillis(), lastSeen, lastChat, lastDaily = -1, missionDay;
    int streak, trades, chats;
    boolean banned, muted;
    final Map<String, Holding> hold = new LinkedHashMap<>();
    final Set<String> badges = new LinkedHashSet<>(), watch = new LinkedHashSet<>(), seen = new LinkedHashSet<>(),
            dayVisits = new LinkedHashSet<>(), mdone = new LinkedHashSet<>();
    final Map<String, Integer> mprog = new LinkedHashMap<>();
    final Map<String, String> intel = new LinkedHashMap<>();
    final List<Map<String, Object>> pending = new ArrayList<>();

    Player(String name) {
        this.name = name;
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    String key() {
        return key(name);
    }

    static String cleanAvatar(String a, String fallback) {
        if (a == null) return fallback;
        a = a.strip();
        if (a.isEmpty() || a.length() > 8) return fallback;
        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);
            if (c < 0x20 || "<>&\"'`".indexOf(c) >= 0) return fallback;
        }
        return a;
    }

    static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    void rollDay() {
        long t = today();
        if (missionDay == t) return;
        missionDay = t;
        mprog.clear();
        mdone.clear();
        dayVisits.clear();
    }

    static long xpFor(int level) {
        return 60L * (level - 1) * (level - 1);
    }

    int level() {
        return (int) Math.floor(Math.sqrt(xp / 60.0)) + 1;
    }

    String title() {
        int l = level();
        if (l >= 25) return "Market titan";
        if (l >= 18) return "Market legend";
        if (l >= 12) return "Floor wolf";
        if (l >= 8) return "Bull whisperer";
        if (l >= 5) return "Chart wizard";
        if (l >= 3) return "Day trader";
        return "Penny picker";
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = Json.of("name", name, "salt", salt, "hash", hash, "avatar", avatar, "realm", realm,
                "bio", bio, "cash", cash, "realized", realized, "bestPct", bestPct, "peak", peak, "xp", xp,
                "joined", joined, "lastSeen", lastSeen, "lastDaily", lastDaily, "streak", streak, "trades", trades,
                "chats", chats, "banned", banned, "muted", muted, "missionDay", missionDay);
        Map<String, Object> h = new LinkedHashMap<>();
        hold.forEach((k, v) -> {
            List<Object> lots = new ArrayList<>();
            for (double[] l : v.lots) lots.add(Arrays.asList((long) l[0], l[1], (long) l[2]));
            h.put(k, Arrays.asList(v.qty, v.avg, lots));
        });
        m.put("hold", h);
        m.put("badges", new ArrayList<>(badges));
        m.put("watch", new ArrayList<>(watch));
        m.put("seen", new ArrayList<>(seen));
        m.put("dayVisits", new ArrayList<>(dayVisits));
        m.put("mdone", new ArrayList<>(mdone));
        m.put("mprog", mprog);
        m.put("intel", intel);
        return m;
    }

    static Player from(Map<String, Object> m) {
        Player p = new Player(Json.str(m.get("name"), "?"));
        p.salt = Json.str(m.get("salt"), "");
        p.hash = Json.str(m.get("hash"), "");
        p.avatar = cleanAvatar(Json.str(m.get("avatar"), "🦊"), "🦊");
        p.realm = Json.str(m.get("realm"), "alpha");
        p.bio = Json.str(m.get("bio"), "");
        p.cash = Json.num(m.get("cash"), Defs.START_CASH);
        p.realized = Json.num(m.get("realized"), 0);
        p.bestPct = Json.num(m.get("bestPct"), 0);
        p.peak = Json.num(m.get("peak"), Defs.START_CASH);
        p.xp = Json.lng(m.get("xp"), 0);
        p.joined = Json.lng(m.get("joined"), p.joined);
        p.lastSeen = Json.lng(m.get("lastSeen"), 0);
        p.lastDaily = Json.lng(m.get("lastDaily"), -1);
        p.streak = (int) Json.lng(m.get("streak"), 0);
        p.trades = (int) Json.lng(m.get("trades"), 0);
        p.chats = (int) Json.lng(m.get("chats"), 0);
        p.banned = Json.bool(m.get("banned"), false);
        p.muted = Json.bool(m.get("muted"), false);
        p.missionDay = Json.lng(m.get("missionDay"), 0);
        Json.map(m.get("hold")).forEach((k, v) -> {
            List<Object> l = Json.list(v);
            if (l.size() < 2) return;
            Holding h = new Holding();
            h.qty = Json.lng(l.get(0), 0);
            h.avg = Json.num(l.get(1), 0);
            if (l.size() > 2) {
                for (Object lo : Json.list(l.get(2))) {
                    List<Object> x = Json.list(lo);
                    if (x.size() == 3) h.lots.add(new double[]{Json.num(x.get(0), 0), Json.num(x.get(1), 0), Json.num(x.get(2), 0)});
                }
            }
            if (h.lots.isEmpty()) h.lots.add(new double[]{0, h.avg, h.qty});
            if (h.qty > 0) p.hold.put(k, h);
        });
        Json.list(m.get("badges")).forEach(x -> p.badges.add(String.valueOf(x)));
        Json.list(m.get("watch")).forEach(x -> p.watch.add(String.valueOf(x)));
        Json.list(m.get("seen")).forEach(x -> p.seen.add(String.valueOf(x)));
        Json.list(m.get("dayVisits")).forEach(x -> p.dayVisits.add(String.valueOf(x)));
        Json.list(m.get("mdone")).forEach(x -> p.mdone.add(String.valueOf(x)));
        Json.map(m.get("mprog")).forEach((k, v) -> p.mprog.put(k, (int) Json.lng(v, 0)));
        Json.map(m.get("intel")).forEach((k, v) -> p.intel.put(k, String.valueOf(v)));
        return p;
    }
}
