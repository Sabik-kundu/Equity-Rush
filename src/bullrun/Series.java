package bullrun;

import java.util.List;
import java.util.Map;

final class Series {
    private static final int FN = 600, CN = 2880, STEP = 10;
    private final long[] ft = new long[FN], ct = new long[CN];
    private final double[] fv = new double[FN], cv = new double[CN];
    private int fh, fc, ch, cc;
    private long n;

    synchronized void add(long t, double v) {
        ft[fh] = t;
        fv[fh] = v;
        fh = (fh + 1) % FN;
        if (fc < FN) fc++;
        if (++n % STEP == 0) pushCoarse(t, v);
    }

    private void pushCoarse(long t, double v) {
        ct[ch] = t;
        cv[ch] = v;
        ch = (ch + 1) % CN;
        if (cc < CN) cc++;
    }

    synchronized void reset() {
        fh = fc = ch = cc = 0;
        n = 0;
    }

    synchronized double back(int k) {
        if (fc == 0) return 0;
        return fv[((fh - 1 - Math.min(k, fc - 1)) % FN + FN) % FN];
    }

    synchronized double[] tail(int len) {
        double[] o = new double[Math.max(0, len)];
        if (fc == 0) return o;
        for (int i = 0; i < o.length; i++) o[i] = fv[((fh - 1 - Math.min(o.length - 1 - i, fc - 1)) % FN + FN) % FN];
        return o;
    }

    synchronized Map<String, Object> merged() {
        int fs = (fh - fc + FN) % FN, cs = (ch - cc + CN) % CN;
        long first = fc == 0 ? Long.MAX_VALUE : ft[fs];
        int cn = 0;
        while (cn < cc && ct[(cs + cn) % CN] < first) cn++;
        long[] t = new long[cn + fc];
        double[] v = new double[cn + fc];
        for (int i = 0; i < cn; i++) {
            t[i] = ct[(cs + i) % CN];
            v[i] = cv[(cs + i) % CN];
        }
        for (int i = 0; i < fc; i++) {
            t[cn + i] = ft[(fs + i) % FN];
            v[cn + i] = fv[(fs + i) % FN];
        }
        return Json.of("t", t, "v", v);
    }

    synchronized Map<String, Object> dumpCoarse() {
        int cs = (ch - cc + CN) % CN;
        long[] t = new long[cc];
        double[] v = new double[cc];
        for (int i = 0; i < cc; i++) {
            t[i] = ct[(cs + i) % CN];
            v[i] = cv[(cs + i) % CN];
        }
        return Json.of("t", t, "v", v);
    }

    synchronized void restoreCoarse(List<Object> t, List<Object> v) {
        cc = 0;
        ch = 0;
        for (int i = 0; i < Math.min(t.size(), v.size()); i++) pushCoarse(Json.lng(t.get(i), 0), Json.num(v.get(i), 0));
    }
}
