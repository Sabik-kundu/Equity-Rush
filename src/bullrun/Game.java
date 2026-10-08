package bullrun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Game {
    static final class Sub {
        final String realm;
        final BlockingQueue<String> q = new ArrayBlockingQueue<>(64);
        volatile boolean alive = true;

        Sub(String realm) {
            this.realm = realm;
        }
    }

    static final class Session {
        final String key;
        volatile long seen;

        Session(String key, long seen) {
            this.key = key;
            this.seen = seen;
        }
    }

    private static final long TOKEN_TTL_MS = 30L * 24 * 3600 * 1000;
    private static final int MAX_TOKENS_PER_PLAYER = 6;
    private static final String DUMMY_SALT = Base64.getEncoder().encodeToString(new byte[16]);
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
    private final SecureRandom rng = new SecureRandom();
    final Map<String, Realm> realms = new LinkedHashMap<>();
    final Map<String, Player> players = new ConcurrentHashMap<>();
    final Map<String, Session> tokens = new ConcurrentHashMap<>();
    final List<Sub> subs = new CopyOnWriteArrayList<>();
    /** access code (lower-case) -> admin handle */
    final Map<String, String> admins;
    private final Map<String, long[]> adminFails = new ConcurrentHashMap<>();
    volatile String graphKey = System.getenv().getOrDefault("BULLRUN_GRAPH", "graph");
    private final Path file;

    Game(Path dir, Map<String, String> admins) throws IOException {
        Files.createDirectories(dir);
        this.file = dir.resolve("save.json");
        this.admins = admins;
        for (Defs.RealmDef d : Defs.REALMS) realms.put(d.id(), new Realm(this, d));
    }

    static long nextId() {
        return IDS.incrementAndGet();
    }

    Realm realm(String id) {
        Realm r = realms.get(id);
        if (r == null) throw new ApiError(404, "Unknown server");
        return r;
    }

    Realm realm(Player p) {
        return realm(p.realm);
    }

    int online(Realm r) {
        int n = 0;
        for (Sub s : subs) if (s.realm.equals(r.def.id())) n++;
        return n;
    }

    void push(String realm, String json) {
        String frame = "data: " + json + "\n\n";
        for (Sub s : subs) if (s.realm.equals(realm) && !s.q.offer(frame)) s.alive = false;
    }

    private static String hash(String pw, String salt) {
        PBEKeySpec spec = null;
        try {
            byte[] raw = Base64.getDecoder().decode(salt);
            if (raw.length == 0) raw = new byte[16];
            spec = new PBEKeySpec(pw.toCharArray(), raw, 20000, 256);
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            if (spec != null) spec.clearPassword();
        }
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String randomB64(int n) {
        byte[] b = new byte[n];
        rng.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    Player register(String name, String pw, String avatar, String realmId) {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) throw new ApiError(400, "Name must be 3-16 letters, numbers or _");
        if (pw.length() < 4) throw new ApiError(400, "Password needs at least 4 characters");
        if (pw.length() > 128) throw new ApiError(400, "Password can be at most 128 characters");
        Realm r = realm(realmId);
        if (!r.open) throw new ApiError(423, "That arena is closed right now");
        Player p = new Player(name);
        byte[] salt = new byte[16];
        rng.nextBytes(salt);
        p.salt = Base64.getEncoder().encodeToString(salt);
        p.hash = hash(pw, p.salt);
        p.avatar = Player.cleanAvatar(avatar, "🦊");
        p.realm = realmId;
        if (players.putIfAbsent(p.key(), p) != null) throw new ApiError(409, "That name is taken");
        r.join(p);
        return p;
    }

    Player login(String name, String pw) {
        Player p = players.get(Player.key(name));
        String computed = hash(pw, p == null ? DUMMY_SALT : p.salt);
        if (p == null || !same(computed, p.hash)) throw new ApiError(401, "Wrong name or password");
        if (p.banned) throw new ApiError(403, "This account is banned");
        if (!realm(p).open) throw new ApiError(423, "Your arena is closed right now");
        return p;
    }

    String token(Player p) {
        String key = p.key();
        List<Map.Entry<String, Session>> mine = new ArrayList<>();
        for (Map.Entry<String, Session> e : tokens.entrySet()) if (e.getValue().key.equals(key)) mine.add(e);
        if (mine.size() >= MAX_TOKENS_PER_PLAYER) {
            mine.sort(Comparator.comparingLong(e -> e.getValue().seen));
            for (int i = 0; i <= mine.size() - MAX_TOKENS_PER_PLAYER; i++) tokens.remove(mine.get(i).getKey());
        }
        String t = randomB64(24);
        tokens.put(t, new Session(key, System.currentTimeMillis()));
        return t;
    }

    Player auth(String token) {
        long now = System.currentTimeMillis();
        Session s = token == null ? null : tokens.get(token);
        if (s != null && now - s.seen > TOKEN_TTL_MS) {
            tokens.remove(token);
            s = null;
        }
        Player p = s == null ? null : players.get(s.key);
        if (p == null) throw new ApiError(401, "Please sign in again");
        s.seen = now;
        if (p.banned) throw new ApiError(403, "This account is banned");
        if (!realm(p).open) throw new ApiError(423, "This arena was closed by the admin");
        p.lastSeen = now;
        return p;
    }

    private static String norm(String k) {
        return k == null ? "" : k.strip().toLowerCase(Locale.ROOT);
    }

    /** Handle of the admin who owns this code, or null if the code is wrong. */
    String adminName(String key) {
        String k = norm(key);
        String found = null;
        for (Map.Entry<String, String> e : admins.entrySet()) if (same(k, e.getKey())) found = e.getValue();
        return found;
    }

    boolean isAdmin(String key) {
        return adminName(key) != null;
    }

    /** Checks an admin code with a gentle brute-force lock per client address. Returns the admin handle. */
    String adminCheck(String ip, String key) {
        long now = System.currentTimeMillis();
        long[] f = adminFails.get(ip);
        if (f != null && now - f[1] > 10 * 60_000L) {
            adminFails.remove(ip);
            f = null;
        }
        if (f != null && f[0] >= 10) throw new ApiError(429, "Too many wrong codes. Please wait 10 minutes and try again.");
        String who = adminName(key);
        if (who == null) {
            adminFails.compute(ip, (k, v) -> v == null ? new long[]{1, now} : new long[]{v[0] + 1, v[1]});
            throw new ApiError(401, "That code is not right. Check it and try again.");
        }
        adminFails.remove(ip);
        return who;
    }

    boolean isGraph(String key) {
        return key != null && (same(key, graphKey) || isAdmin(key));
    }

    List<Object> realmList() {
        List<Object> out = new ArrayList<>();
        for (Realm r : realms.values())
            out.add(Json.of("id", r.def.id(), "name", r.def.name(), "blurb", r.def.blurb(),
                    "open", r.open, "players", r.players.size(), "online", online(r)));
        return out;
    }

    List<Object> adminRealms() {
        List<Object> out = new ArrayList<>();
        for (Realm r : realms.values())
            out.add(Json.of("id", r.def.id(), "name", r.def.name(), "paused", r.paused, "open", r.open,
                    "players", r.players.size(), "online", online(r)));
        return out;
    }

    void start() {
        ScheduledExecutorService ex = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "game");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleAtFixedRate(() -> {
            for (Realm r : realms.values()) {
                if (!r.open) continue;
                try {
                    r.tick();
                    int on = online(r);
                    if (on > 0) push(r.def.id(), r.frame(on));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        ex.scheduleAtFixedRate(this::save, 20, 20, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
    }

    synchronized void save() {
        try {
            Map<String, Object> rs = new LinkedHashMap<>();
            List<Object> ps = new ArrayList<>();
            for (Realm r : realms.values()) {
                synchronized (r) {
                    rs.put(r.def.id(), r.dump());
                    for (Player p : r.players.values()) ps.add(p.toMap());
                }
            }
            long now = System.currentTimeMillis();
            Map<String, Object> ts = new LinkedHashMap<>();
            for (Map.Entry<String, Session> e : tokens.entrySet()) {
                Session s = e.getValue();
                if (now - s.seen > TOKEN_TTL_MS) tokens.remove(e.getKey());
                else ts.put(e.getKey(), Json.of("n", s.key, "t", s.seen));
            }
            String json = Json.write(Json.of("realms", rs, "players", ps, "tokens", ts, "graphKey", graphKey));
            Path tmp = file.resolveSibling("save.tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("save failed: " + e);
        }
    }

    void load() throws IOException {
        if (!Files.exists(file)) return;
        String text = Files.readString(file);
        Map<String, Object> root = Json.obj(text);
        if (root.isEmpty() && !text.isBlank()) {
            Path bad = file.resolveSibling("save.corrupt-" + System.currentTimeMillis() + ".json");
            Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("save.json could not be parsed. It was kept as " + bad.getFileName() + " and a fresh game was started");
            return;
        }
        Json.map(root.get("realms")).forEach((k, v) -> {
            Realm r = realms.get(k);
            if (r != null) r.restore(Json.map(v));
        });
        for (Object o : Json.list(root.get("players"))) {
            Player p = Player.from(Json.map(o));
            Realm r = realms.get(p.realm);
            if (r == null) continue;
            players.put(p.key(), p);
            r.players.put(p.key(), p);
        }
        long now = System.currentTimeMillis();
        Json.map(root.get("tokens")).forEach((k, v) -> {
            String key;
            long seen;
            if (v instanceof Map) {
                Map<String, Object> m = Json.map(v);
                key = Json.str(m.get("n"), "");
                seen = Json.lng(m.get("t"), now);
            } else {
                key = String.valueOf(v);
                seen = now;
            }
            if (players.containsKey(key) && now - seen <= TOKEN_TTL_MS) tokens.put(k, new Session(key, seen));
        });
        String gk = Json.str(root.get("graphKey"), "");
        if (!gk.isEmpty()) graphKey = gk;
        realms.values().forEach(Realm::reconcile);
    }
}
