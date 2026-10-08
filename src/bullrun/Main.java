package bullrun;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Main {
    /** Built-in admin handles and access codes. Override with the ADMIN_CODES variable (see README). */
    private static final String DEFAULT_ADMINS = "Admin Alpha=pebble-thistle-75;Admin Bravo=summit-sparrow-29;Admin Charlie=olive-ember-45;Admin Delta=maple-amber-78";

    static Map<String, String> admins() {
        String env = System.getenv("ADMIN_CODES");
        String legacy = System.getenv("BULLRUN_ADMIN");
        String spec = env != null && !env.isBlank() ? env : DEFAULT_ADMINS;
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : spec.split(";")) {
            int i = part.indexOf('=');
            if (i < 1 || i == part.length() - 1) continue;
            m.put(part.substring(i + 1).strip().toLowerCase(Locale.ROOT), part.substring(0, i).strip());
        }
        if (legacy != null && !legacy.isBlank()) m.put(legacy.strip().toLowerCase(Locale.ROOT), "Admin");
        return m;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Map<String, String> admins = admins();
        Game game = new Game(Path.of(System.getenv().getOrDefault("BULLRUN_DATA", "data")), admins);
        game.load();
        game.start();
        new Http(game).start(port);
        System.out.println("Equity Rush is live on port " + port);
        System.out.println("Admin panel: /admin  (" + admins.size() + " admin codes active)");
    }
}
