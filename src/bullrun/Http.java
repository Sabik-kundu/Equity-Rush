package bullrun;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class Http {
    private static final int MAX_BODY = 65536;

    interface Handler {
        Object run(Req r) throws Exception;
    }

    final class Req {
        final HttpExchange ex;
        final Map<String, String> q = new HashMap<>();
        final Map<String, Object> body;

        Req(HttpExchange ex) throws IOException {
            this.ex = ex;
            String raw = ex.getRequestURI().getRawQuery();
            if (raw != null) {
                for (String kv : raw.split("&")) {
                    int i = kv.indexOf('=');
                    if (i > 0) q.put(decode(kv.substring(0, i)), decode(kv.substring(i + 1)));
                }
            }
            byte[] b = ex.getRequestBody().readNBytes(MAX_BODY + 1);
            if (b.length > MAX_BODY) throw new ApiError(413, "Request too large");
            body = b.length == 0 ? new LinkedHashMap<>() : Json.obj(new String(b, StandardCharsets.UTF_8));
        }

        String s(String k) {
            return Json.str(body.get(k), "");
        }

        double d(String k) {
            return Json.num(body.get(k), 0);
        }

        long l(String k) {
            return Json.lng(body.get(k), 0);
        }

        Player me() {
            String t = ex.getRequestHeaders().getFirst("X-Token");
            return game.auth(t != null ? t : q.get("token"));
        }

        void graph() {
            if (!game.isGraph(ex.getRequestHeaders().getFirst("X-Graph"))) throw new ApiError(401, "Wrong graph key");
        }

        String ip() {
            String xf = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            if (xf != null && !xf.isBlank()) return xf.split(",")[0].strip();
            return ex.getRemoteAddress().getAddress().getHostAddress();
        }

        String admin() {
            return game.adminCheck(ip(), ex.getRequestHeaders().getFirst("X-Admin"));
        }
    }

    private final Game game;
    private record Cached(long mtime, byte[] raw, byte[] gz, String etag) {
    }

    private final Map<Path, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private HttpServer server;
    private final Path web = Path.of(System.getProperty("bullrun.web", "web")).toAbsolutePath().normalize();

    Http(Game game) {
        this.game = game;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ApiError(400, "Bad request");
        }
    }

    void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 256);
        server.setExecutor(Executors.newCachedThreadPool());
        routes();
        server.createContext("/api/stream", this::stream);
        server.createContext("/", this::files);
        server.start();
    }

    private void route(String method, String path, Handler h) {
        server.createContext(path, ex -> {
            try {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Token, X-Admin, X-Graph");
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                if (ex.getRequestMethod().equals("OPTIONS")) {
                    ex.sendResponseHeaders(204, -1);
                    ex.close();
                    return;
                }
                if (!ex.getRequestURI().getPath().equals(path)) throw new ApiError(404, "Not found");
                if (!ex.getRequestMethod().equals(method)) throw new ApiError(405, "Method not allowed");
                send(ex, 200, Json.write(h.run(new Req(ex))));
            } catch (ApiError e) {
                send(ex, e.code, Json.write(Json.of("error", e.getMessage())));
            } catch (Throwable t) {
                t.printStackTrace();
                send(ex, 500, Json.write(Json.of("error", "Server error")));
            }
        });
    }

    private void get(String path, Handler h) {
        route("GET", path, h);
    }

    private void post(String path, Handler h) {
        route("POST", path, h);
    }

    private static void send(HttpExchange ex, int code, String json) {
        try {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
        } catch (IOException ignored) {
        } finally {
            ex.close();
        }
    }

    private void routes() {
        get("/api/realms", r -> game.realmList());

        get("/api/meta", r -> {
            List<Object> badges = new ArrayList<>();
            for (Defs.Badge b : Defs.BADGES)
                badges.add(Json.of("id", b.id(), "tag", b.tag(), "name", b.name(), "desc", b.desc(), "xp", b.xp()));
            return Json.of("badges", badges, "start", Defs.START_CASH, "fee", Defs.FEE,
                    "intel", Defs.INTEL_COST);
        });

        post("/api/register", r -> {
            Player p = game.register(r.s("name"), r.s("password"), r.s("avatar"), r.s("realm"));
            return Json.of("token", game.token(p));
        });

        post("/api/login", r -> Json.of("token", game.token(game.login(r.s("name"), r.s("password")))));

        get("/api/me", r -> {
            Player p = r.me();
            return game.realm(p).me(p);
        });

        get("/api/market", r -> {
            Realm rl = game.realm(r.me());
            return rl.market(game.online(rl));
        });

        get("/api/company", r -> game.realm(r.me()).company(r.q.getOrDefault("id", "")));

        get("/api/history", r -> game.realm(r.me()).history(r.q.getOrDefault("id", "")));

        get("/api/leaders", r -> {
            Player p = r.me();
            return game.realm(p).leaders(p);
        });

        post("/api/trade", r -> {
            Player p = r.me();
            Realm rl = game.realm(p);
            Map<String, Object> res = rl.trade(p, r.s("id"), r.s("side"), r.l("qty"));
            res.put("me", rl.me(p));
            return res;
        });

        post("/api/daily", r -> {
            Player p = r.me();
            Realm rl = game.realm(p);
            Map<String, Object> res = rl.daily(p);
            res.put("me", rl.me(p));
            return res;
        });

        post("/api/visit", r -> {
            Player p = r.me();
            game.realm(p).visit(p, r.s("id"));
            return Json.of("ok", true);
        });

        post("/api/watch", r -> {
            Player p = r.me();
            Realm rl = game.realm(p);
            rl.watch(p, r.s("id"));
            return Json.of("watch", new ArrayList<>(p.watch));
        });

        post("/api/intel", r -> {
            Player p = r.me();
            Realm rl = game.realm(p);
            Map<String, Object> res = rl.intel(p, r.l("id"));
            res.put("me", rl.me(p));
            return res;
        });

        post("/api/chat", r -> {
            Player p = r.me();
            game.realm(p).say(p, r.s("text"));
            return Json.of("ok", true);
        });

        post("/api/profile", r -> {
            Player p = r.me();
            p.avatar = Player.cleanAvatar(r.s("avatar"), p.avatar);
            String bio = r.s("bio").strip();
            p.bio = bio.length() > 80 ? bio.substring(0, 80) : bio;
            return game.realm(p).me(p);
        });

        get("/api/admin/overview", r -> {
            String who = r.admin();
            Realm rl = game.realm(r.q.getOrDefault("realm", "alpha"));
            Set<String> sec = new HashSet<>(Arrays.asList(r.q.getOrDefault("sections", "").split(",")));
            Map<String, Object> out = Json.of("realms", game.adminRealms(), "realm", rl.admin(game.online(rl), sec));
            out.put("graphKey", game.graphKey);
            out.put("admin", who);
            return out;
        });

        post("/api/admin/news", r -> {
            r.admin();
            String target = r.s("realm");
            List<Realm> rs = target.equals("all") ? new ArrayList<>(game.realms.values()) : List.of(game.realm(target));
            String kind = r.s("kind").isEmpty() ? "news" : r.s("kind");
            double cred = r.body.containsKey("cred") ? r.d("cred") : kind.equals("rumor") ? 0.5 : 1;
            for (Realm rl : rs)
                rl.publish(kind, r.s("tType"), r.s("tId"), r.s("headline"), r.s("body"), r.d("impact"), cred);
            return Json.of("ok", true);
        });

        post("/api/admin/realm", r -> {
            r.admin();
            Realm rl = game.realm(r.s("realm"));
            synchronized (rl) {
                if (r.body.containsKey("open")) rl.setOpen(Json.bool(r.body.get("open"), true));
                if (r.body.containsKey("paused")) rl.setPaused(Json.bool(r.body.get("paused"), false));
                if (r.body.containsKey("auto")) rl.auto = Json.bool(r.body.get("auto"), true);
                if (r.body.containsKey("volMul")) rl.volMul = Math.max(0.1, Math.min(5, r.d("volMul")));
                if (r.body.containsKey("showLimits")) rl.showLimits(Json.bool(r.body.get("showLimits"), true));
                if (r.s("action").equals("session")) rl.resetSession();
                if (r.s("action").equals("clearChat")) rl.clearChat();
                if (r.s("action").equals("announce") && !r.s("text").isBlank()) rl.system("Announcement: " + r.s("text"));
            }
            return Json.of("ok", true);
        });

        post("/api/admin/company", r -> {
            r.admin();
            Realm rl = game.realm(r.s("realm"));
            if (r.s("action").equals("setPrice")) rl.setPrice(r.s("id"), r.d("value"));
            else if (r.s("action").equals("shock")) rl.shock(r.s("id"), r.d("value"));
            else if (r.s("action").equals("band")) rl.setBand(r.s("id"), r.d("value"));
            else if (r.s("action").equals("showLimit")) rl.showLimit(r.s("id"), Json.bool(r.body.get("value"), true));
            else if (r.s("action").equals("float")) rl.setFloat(r.s("id"), (long) r.d("value"));
            else if (r.s("action").equals("trend")) rl.trend(r.s("id"), r.d("value"), r.d("minutes"));
            else if (r.s("action").equals("trendStop")) rl.stopTrend(r.s("id"));
            else if (r.s("action").equals("ceo")) rl.ceoResign(r.s("id"), r.s("name"));
            else throw new ApiError(400, "Unknown action");
            return Json.of("ok", true);
        });

        post("/api/admin/addcompany", r -> {
            r.admin();
            String id = r.s("id").trim().toUpperCase();
            String name = r.s("name").trim(), sector = r.s("sector").trim();
            if (!id.matches("[A-Z0-9]{2,5}")) throw new ApiError(400, "Ticker must be 2-5 letters or digits");
            if (name.length() < 2 || name.length() > 32) throw new ApiError(400, "Name must be 2-32 characters");
            if (!sector.matches("[A-Za-z ]{2,16}")) throw new ApiError(400, "Sector must be 2-16 letters");
            double price = r.d("price"), sharesM = r.d("sharesM"), vol = r.d("vol");
            if (price < 1 || price > 5000) throw new ApiError(400, "Price must be between 1 and 5000");
            if (sharesM < 10 || sharesM > 50000) throw new ApiError(400, "Shares must be between 10M and 50,000M");
            if (vol < 0.001 || vol > 0.008) throw new ApiError(400, "Volatility must be between 0.001 and 0.008");
            long fl = (long) r.d("floatShares");
            if (fl != 0 && (fl < 100 || fl > 1_000_000_000L)) throw new ApiError(400, "Float must be between 100 and 1,000,000,000 shares");
            String ceo = r.s("ceo").trim();
            if (ceo.length() > 30) ceo = ceo.substring(0, 30);
            String tagline = r.s("tagline").trim(), blurb = r.s("blurb").trim();
            Defs.Co d = Defs.make(id, name, sector, price, sharesM, vol, ceo.isEmpty() ? Defs.fakeName(new Random(), "") : ceo,
                    tagline.length() > 60 ? tagline.substring(0, 60) : tagline, blurb.length() > 160 ? blurb.substring(0, 160) : blurb, fl);
            String target = r.s("realm");
            List<Realm> rs = target.equals("all") ? new ArrayList<>(game.realms.values()) : List.of(game.realm(target));
            for (Realm rl : rs) if (rl.cos.containsKey(id)) throw new ApiError(409, id + " already exists on " + rl.def.name());
            for (Realm rl : rs) rl.addCompany(d);
            return Json.of("ok", true);
        });

        post("/api/admin/delist", r -> {
            r.admin();
            game.realm(r.s("realm")).delist(r.s("id"));
            return Json.of("ok", true);
        });

        post("/api/admin/graphkey", r -> {
            r.admin();
            String k = r.s("key").trim();
            if (k.length() < 3 || k.length() > 32) throw new ApiError(400, "Key must be 3-32 characters");
            game.graphKey = k;
            return Json.of("ok", true);
        });

        get("/api/graph/overview", r -> {
            r.graph();
            Realm rl = game.realm(r.q.getOrDefault("realm", "alpha"));
            Map<String, Object> o = rl.graphOverview();
            o.put("realms", game.adminRealms());
            return o;
        });

        get("/api/graph/live", r -> {
            r.graph();
            return game.realm(r.q.getOrDefault("realm", "alpha")).graphLive();
        });

        get("/api/graph/history", r -> {
            r.graph();
            return game.realm(r.q.getOrDefault("realm", "alpha")).graphHistory(r.q.getOrDefault("id", "INDEX"));
        });

        post("/api/admin/player", r -> {
            r.admin();
            Player p = game.players.get(Player.key(r.s("name")));
            if (p == null) throw new ApiError(404, "No such player");
            Realm rl = game.realm(p);
            synchronized (rl) {
                switch (r.s("action")) {
                    case "give" -> p.cash = Math.max(0, Math.round((p.cash + r.d("amount")) * 100) / 100.0);
                    case "ban" -> rl.ban(p, true);
                    case "unban" -> rl.ban(p, false);
                    case "mute" -> p.muted = true;
                    case "unmute" -> p.muted = false;
                    case "reset" -> rl.resetPlayer(p);
                    default -> throw new ApiError(400, "Unknown action");
                }
            }
            return Json.of("ok", true);
        });
    }

    private void stream(HttpExchange ex) throws IOException {
        Game.Sub sub;
        try {
            Player p = new Req(ex).me();
            sub = new Game.Sub(p.realm);
        } catch (ApiError e) {
            send(ex, e.code, Json.write(Json.of("error", e.getMessage())));
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("X-Accel-Buffering", "no");
        ex.sendResponseHeaders(200, 0);
        game.subs.add(sub);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(("data: {\"type\":\"hello\"}\n\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
            while (sub.alive) {
                String f = sub.q.poll(15, TimeUnit.SECONDS);
                o.write((f == null ? ": ping\n\n" : f).getBytes(StandardCharsets.UTF_8));
                o.flush();
            }
        } catch (IOException | InterruptedException ignored) {
        } finally {
            sub.alive = false;
            game.subs.remove(sub);
            ex.close();
        }
    }

    private static void plain(HttpExchange ex, int code, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    private static String mime(String n) {
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".webmanifest")) return "application/manifest+json";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private void files(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) {
            plain(ex, 405, "Method not allowed");
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        else if (path.equals("/admin")) path = "/admin.html";
        else if (path.equals("/graph")) path = "/graph.html";
        Path f;
        try {
            f = web.resolve(path.substring(1)).normalize();
        } catch (InvalidPathException e) {
            f = null;
        }
        if (f == null || !f.startsWith(web) || !Files.isRegularFile(f)) {
            plain(ex, 404, "Not found");
            return;
        }
        long mt = Files.getLastModifiedTime(f).toMillis();
        Cached c = cache.get(f);
        if (c == null || c.mtime != mt) {
            byte[] raw = Files.readAllBytes(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bo)) {
                gz.write(raw);
            }
            c = new Cached(mt, raw, bo.toByteArray(), mt + "-" + raw.length);
            cache.put(f, c);
        }
        String ae = ex.getRequestHeaders().getFirst("Accept-Encoding");
        boolean gz = ae != null && ae.contains("gzip") && c.raw.length > 1024;
        String etag = "\"" + c.etag + (gz ? "-gz" : "") + "\"";
        ex.getResponseHeaders().set("Content-Type", mime(f.getFileName().toString()));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("ETag", etag);
        ex.getResponseHeaders().set("Vary", "Accept-Encoding");
        if (etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
            ex.sendResponseHeaders(304, -1);
            ex.close();
            return;
        }
        byte[] b = gz ? c.gz : c.raw;
        if (gz) ex.getResponseHeaders().set("Content-Encoding", "gzip");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }
}
