package bullrun;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

final class Company {
    final Defs.Co d;
    final Series s = new Series();
    private final Random rnd = new Random();
    private final double[] sched = new double[16];
    String ceo;
    double price, open, high, low, fair, buzz, mood = 50, band, bandOverride, flow, trendPer;
    boolean showLimit = true;
    int hit, trendLeft;
    long floatShares, held, flood;

    Company(Defs.Co d, double jitter) {
        this.d = d;
        ceo = d.ceo();
        floatShares = d.flt();
        price = open = high = low = fair = r2(d.price() * jitter);
        band = autoBand(cap());
        s.add(System.currentTimeMillis() / 1000, price);
    }

    static double autoBand(double cap) {
        double b = 40 - 11 * Math.log10(Math.max(cap / 1e9, 0.05));
        return Math.round(Math.max(6, Math.min(35, b)) * 2) / 2.0;
    }

    double cap() {
        return price * d.shares();
    }

    String tier() {
        double b = cap() / 1e9;
        return b >= 100 ? "Mega cap" : b >= 25 ? "Large cap" : b >= 5 ? "Mid cap" : "Small cap";
    }

    double lo() {
        return open * (1 - band / 100);
    }

    double hi() {
        return open * (1 + band / 100);
    }

    double clampPx(double px) {
        return Math.max(1, Math.max(lo(), Math.min(hi(), px)));
    }

    long avail() {
        return Math.max(0, floatShares - held - flood);
    }

    double availFrac() {
        return floatShares <= 0 ? 0 : (double) avail() / floatShares;
    }

    double depth() {
        return d.liq() * (0.4 + 0.6 * availFrac());
    }

    private static double bound(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private void clamp() {
        hit = 0;
        if (price >= hi()) {
            price = hi();
            hit = 1;
        } else if (price <= lo()) {
            price = Math.max(1, lo());
            hit = -1;
        }
        fair = Math.max(1, Math.max(lo() * 0.95, Math.min(hi() * 1.05, fair)));
    }

    void tick(double market, double sector, double volMul, long nowSec) {
        double news = sched[0];
        System.arraycopy(sched, 1, sched, 0, sched.length - 1);
        sched[sched.length - 1] = 0;
        double trend = 0;
        if (trendLeft > 0) {
            trend = trendPer;
            trendLeft--;
        }
        double push = news + trend;
        double fl = flow * 0.30;
        flow -= fl;
        double conflict = push * fl < 0 ? Math.min(Math.abs(push), Math.abs(fl)) : 0;
        double thin = 1 + 0.6 * Math.pow(1 - availFrac(), 3);
        double vm = volMul * thin * (1 + Math.min(2.0, 40 * conflict));
        double noise = rnd.nextGaussian() * d.vol() * vm;
        double revert = -0.004 * Math.log(price / fair);
        double ret = bound(noise + revert + push + fl + (market + sector) * d.beta(), -0.08, 0.08);
        price = Math.max(1, price * Math.exp(ret));
        fair = Math.max(1, fair * Math.exp(news * 0.7 + trend * 0.6 + fl * 0.35 + rnd.nextGaussian() * d.vol() * 0.12));
        clamp();
        buzz *= 0.985;
        high = Math.max(high, price);
        low = Math.min(low, price);
        s.add(nowSec, r2(price));
        double b = s.back(30);
        double tr = b > 0 ? Math.log(price / b) : 0;
        double target = Math.max(3, Math.min(97, 50 + tr * 1400 + buzz));
        mood += (target - mood) * 0.12;
    }

    void pulse(double pct) {
        double f = 1 + pct / 100 * (0.85 + 0.30 * rnd.nextDouble());
        if (f <= 0.05) f = 0.05;
        double total = Math.log(f);
        int start = rnd.nextInt(2), n = 4 + rnd.nextInt(3);
        double[] w = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            w[i] = 0.35 + rnd.nextDouble();
            sum += w[i];
        }
        for (int i = 0; i < n; i++) sched[start + i] += total * w[i] / sum;
        buzz = bound(buzz + pct * 2.5, -60, 60);
    }

    void addFlow(double signed) {
        flow = bound(flow + signed, -0.2, 0.2);
        buzz = bound(buzz + signed * 120, -60, 60);
    }

    double pendingPct() {
        double t = 0;
        for (double x : sched) t += x;
        if (trendLeft > 0) t += trendPer * trendLeft;
        return (Math.exp(t) - 1) * 100;
    }

    void startTrend(double pct, double minutes) {
        pct = bound(pct, -60, 200);
        trendLeft = (int) Math.max(1, Math.round(minutes * 60));
        trendPer = Math.log(1 + pct / 100) / trendLeft;
    }

    void stopTrend() {
        trendLeft = 0;
        trendPer = 0;
    }

    void setPrice(double p) {
        price = fair = Math.max(1, p);
        Arrays.fill(sched, 0);
        flow = 0;
        stopTrend();
        resetSession();
    }

    void setBand(double pct) {
        bandOverride = pct > 0 ? Math.max(1, Math.min(90, pct)) : 0;
        band = bandOverride > 0 ? bandOverride : autoBand(cap());
        high = Math.min(high, hi());
        low = Math.max(low, lo());
        clamp();
    }

    void resetSession() {
        open = high = low = price;
        band = bandOverride > 0 ? bandOverride : autoBand(cap());
        hit = 0;
    }

    Map<String, Object> lim() {
        return Json.of("band", band, "tier", tier(), "lo", r2(lo()), "hi", r2(hi()), "hit", hit);
    }

    Map<String, Object> snap() {
        return Json.of("id", d.id(), "name", d.name(), "sector", d.sector(), "hue", d.hue(),
                "tagline", d.tagline(), "ceo", ceo, "blurb", d.blurb(), "shares", d.shares(), "liq", d.liq(),
                "float", floatShares, "avail", avail(), "flow", Math.round(flow * 1e4) / 1e4,
                "price", r2(price), "open", r2(open), "high", r2(high), "low", r2(low), "mood", Math.round(mood),
                "lim", showLimit ? lim() : null, "hist", s.tail(120));
    }

    Map<String, Object> dump() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price", price);
        m.put("fair", fair);
        m.put("open", open);
        m.put("high", high);
        m.put("low", low);
        m.put("buzz", buzz);
        m.put("mood", mood);
        m.put("bandOverride", bandOverride);
        m.put("showLimit", showLimit);
        m.put("ceo", ceo);
        m.put("float", floatShares);
        m.put("flood", flood);
        m.put("trendPer", trendPer);
        m.put("trendLeft", trendLeft);
        m.put("hist", s.dumpCoarse());
        return m;
    }

    void restore(Map<String, Object> m) {
        price = Json.num(m.get("price"), price);
        fair = Json.num(m.get("fair"), fair);
        open = Json.num(m.get("open"), open);
        high = Json.num(m.get("high"), high);
        low = Json.num(m.get("low"), low);
        buzz = Json.num(m.get("buzz"), 0);
        mood = Json.num(m.get("mood"), 50);
        bandOverride = Json.num(m.get("bandOverride"), 0);
        showLimit = Json.bool(m.get("showLimit"), true);
        ceo = Json.str(m.get("ceo"), d.ceo());
        floatShares = Json.lng(m.get("float"), d.flt());
        flood = Json.lng(m.get("flood"), 0);
        trendPer = Json.num(m.get("trendPer"), 0);
        trendLeft = (int) Json.lng(m.get("trendLeft"), 0);
        band = bandOverride > 0 ? bandOverride : autoBand(cap());
        Map<String, Object> h = Json.map(m.get("hist"));
        s.reset();
        s.restoreCoarse(Json.list(h.get("t")), Json.list(h.get("v")));
        s.add(System.currentTimeMillis() / 1000, r2(price));
    }

    static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
