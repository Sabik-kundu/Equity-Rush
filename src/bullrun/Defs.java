package bullrun;

import java.util.List;
import java.util.Map;
import java.util.Random;

final class Defs {
    static final double START_CASH = 10000;
    static final double FEE = 0.002;
    static final long SESSION_MS = 15 * 60_000L;
    static final int INTEL_COST = 150;

    record Co(String id, String name, String sector, int hue, String tagline, String ceo, String blurb,
              double price, double vol, double beta, double liq, long shares, long flt) {
    }

    record RealmDef(String id, String name, String blurb, double volMul) {
    }

    record Badge(String id, String tag, String name, String desc, int xp) {
    }

    record Mission(String id, String title, String desc, int goal, int cash, int xp) {
    }

    static long floatFor(double price, double liq) {
        return Math.max(200, Math.round(liq * 1.5 / price / 50.0) * 50);
    }

    private static Co co(String id, String name, String sector, int hue, String tagline, String ceo, String blurb,
                         double price, double vol, double beta, double liq, long shares) {
        return new Co(id, name, sector, hue, tagline, ceo, blurb, price, vol, beta, liq, shares, floatFor(price, liq));
    }

    static final List<Co> COMPANIES = List.of(
            co("NVX", "Novatek Labs", "Tech", 265, "Teaching machines to daydream", "Ada Voss",
                    "Neural chips, chatbots and a suspiciously large GPU farm.", 240, 0.0036, 1.4, 200000, 900_000_000L),
            co("CLD", "CloudNine Systems", "Tech", 200, "Your data, somewhere fluffy", "Priya Nair",
                    "Enterprise cloud with legendary uptime and a terrible logo.", 310, 0.0022, 1.0, 320000, 1_400_000_000L),
            co("PXL", "Pixelforge Studios", "Tech", 320, "Games you can't put down", "Yuki Tanaka",
                    "Blockbuster games, loot boxes and one very loyal fanbase.", 72, 0.0032, 1.3, 150000, 400_000_000L),
            co("ORB", "Orbitron Space", "Industry", 235, "Rent-a-rocket for everyone", "Rex Calloway",
                    "Reusable rockets, moon tourism and a lot of confidence.", 88, 0.0044, 1.6, 140000, 600_000_000L),
            co("VLT", "Voltara Motors", "Industry", 52, "EVs that go brrr", "Dmitri Kane",
                    "Electric cars, battery swaps and a cult of early adopters.", 156, 0.0034, 1.4, 220000, 800_000_000L),
            co("SOL", "Solstice Energy", "Industry", 30, "Bottling sunlight", "Amara Osei",
                    "Solar farms on three continents and a very tanned workforce.", 51, 0.0022, 0.9, 160000, 700_000_000L),
            co("BRW", "Brewtopia Coffee", "Consumer", 24, "Caffeine as a service", "Mina Park",
                    "Ten thousand cafés and one secret oat-milk recipe.", 34, 0.0018, 0.7, 120000, 500_000_000L),
            co("TAC", "TacoTron Foods", "Consumer", 12, "Robots make the tacos", "Carlos Mendez",
                    "Automated taco kitchens with permanent Tuesday energy.", 21, 0.0038, 1.1, 90000, 350_000_000L),
            co("FSH", "FashionFwd Retail", "Consumer", 340, "Trends before they trend", "Sofia Rossi",
                    "Fast fashion, faster drops and influencers everywhere.", 44, 0.0026, 1.0, 100000, 450_000_000L),
            co("MDX", "Medivance Pharma", "Health", 165, "Cures on the horizon", "Lena Fischer",
                    "Late-stage trials, big patents and nervous analysts.", 118, 0.0030, 1.1, 180000, 550_000_000L),
            co("ZEN", "Zenith Wellness", "Health", 140, "Stress less, earn more", "Arjun Mehta",
                    "Meditation apps, smart mats and an extremely calm CEO.", 63, 0.0020, 0.8, 100000, 300_000_000L),
            co("GLD", "Goldvault Bank", "Finance", 46, "Boring money, exciting returns", "Harold Finch",
                    "Century-old bank with vault doors thicker than its dividends.", 96, 0.0015, 0.8, 300000, 1_000_000_000L),
            co("CRY", "CryptoKitten Exchange", "Finance", 290, "Meow to the moon", "Kit Nakamura",
                    "Trade coins, cats and questionable decisions around the clock.", 15, 0.0062, 1.8, 80000, 250_000_000L),
            co("QNT", "Quantum Nest Computing", "Tech", 250, "Qubits that mostly work", "Ivan Petrov",
                    "Quantum hardware that is both fast and slow until observed.", 128, 0.0042, 1.5, 130000, 350_000_000L),
            co("SEC", "SentinelSec Cyber", "Tech", 215, "Hackers hate us", "Mara Lindqvist",
                    "Security software with a suspiciously good track record.", 84, 0.0028, 1.1, 150000, 420_000_000L),
            co("RBT", "Ironclad Robotics", "Industry", 205, "Robots that do the heavy lifting", "Tomas Herrera",
                    "Warehouse robots and one very dramatic demo video.", 67, 0.0035, 1.3, 130000, 380_000_000L),
            co("OIL", "BlackGold Petroleum", "Energy", 35, "Drill baby, drill", "Walter Boone",
                    "Old-school oil major with new-school PR.", 74, 0.0030, 1.0, 260000, 1_200_000_000L),
            co("HYD", "HydroGen Fuel", "Energy", 185, "Fueling the future one molecule at a time", "Freya Andersen",
                    "Hydrogen cells, pipelines and endless optimism.", 39, 0.0040, 1.4, 110000, 500_000_000L),
            co("WND", "WindRider Power", "Energy", 170, "Full of hot air, productively", "Kofi Mensah",
                    "Offshore wind farms and a very windy boardroom.", 46, 0.0024, 0.9, 140000, 650_000_000L),
            co("STR", "StreamCast Media", "Media", 300, "One more episode", "Leah Kowalski",
                    "Streaming giant that cancels shows you love.", 92, 0.0034, 1.2, 190000, 700_000_000L),
            co("MUS", "Melodia Music", "Media", 330, "Every song, everywhere", "Diego Alvarez",
                    "Music streaming with a catalogue and a playlist problem.", 37, 0.0030, 1.0, 100000, 450_000_000L),
            co("TEL", "Telestar Networks", "Telecom", 225, "Five bars, everywhere", "Grace Okafor",
                    "National carrier with dependable towers and slow customer service.", 61, 0.0018, 0.7, 250000, 900_000_000L),
            co("SAT", "Skyline Satellite", "Telecom", 195, "Coverage from above", "Owen Sinclair",
                    "Low-orbit internet and lots of debris insurance.", 33, 0.0032, 1.1, 120000, 520_000_000L),
            co("HOM", "HomeNest Realty", "Real Estate", 20, "Home is where the mortgage is", "Beatrice Moreau",
                    "Suburban housing developer with a lot of cul-de-sacs.", 105, 0.0020, 0.8, 200000, 480_000_000L),
            co("TWR", "SkyTower Properties", "Real Estate", 260, "Vertical living, horizontal profits", "Hiro Sato",
                    "Luxury towers and a rooftop pool nobody can afford.", 148, 0.0022, 0.9, 210000, 350_000_000L));

    static final List<RealmDef> REALMS = List.of(
            new RealmDef("alpha", "Alpha Exchange", "The classic floor. Balanced swings.", 1.0),
            new RealmDef("blitz", "Blitz Arena", "Wild swings. Fast money, faster losses.", 1.9),
            new RealmDef("zen", "Zen Garden", "Calm prices for patient investors.", 0.55));

    static final List<Mission> MISSIONS = List.of(
            new Mission("trader", "Warm-up", "Make 3 trades", 3, 500, 40),
            new Mission("scout", "Scout the market", "Visit 3 different companies", 3, 300, 30),
            new Mission("profit", "Take profit", "Sell shares for a gain", 1, 750, 60),
            new Mission("hound", "News hound", "Trade a company within 40s of its news", 1, 1000, 80));

    static final List<Badge> BADGES = List.of(
            new Badge("first_trade", "1st", "First blood", "Make your first trade", 25),
            new Badge("whale", "5K", "Whale", "Place a trade worth \uA7A85,000 or more", 60),
            new Badge("diamond", "+25%", "Diamond hands", "Sell a position for 25% profit or more", 80),
            new Badge("paper", "-15%", "Paper hands", "Sell a position at a 15% loss or worse", 20),
            new Badge("sniper", "40s", "News sniper", "Trade within 40s of company news", 50),
            new Badge("diverse", "x5", "Diversified", "Hold 5 different companies", 40),
            new Badge("rich1", "20K", "Riser", "Reach \uA7A820,000 net worth", 50),
            new Badge("rich2", "50K", "Tycoon", "Reach \uA7A850,000 net worth", 120),
            new Badge("rich3", "100K", "Mogul", "Reach \uA7A8100,000 net worth", 300),
            new Badge("streak7", "7d", "On fire", "Claim the daily bonus 7 days in a row", 100),
            new Badge("scout", "All", "Explorer", "Visit every company", 60),
            new Badge("insider", "Tip", "Insider", "Buy your first rumor intel", 20),
            new Badge("chatty", "x10", "Floor talker", "Send 10 messages on the floor", 15));

    static final String[] FIRST = {"Marcus", "Elena", "Tobias", "Nadia", "Felix", "Ingrid", "Rafael", "Mei", "Callum", "Zara", "Viktor",
            "Amelie", "Hassan", "Portia", "Leo", "Sunita", "Bruno", "Katya", "Idris", "Clara", "Anders", "Yara", "Silas", "Noor",
            "Emeka", "Livia", "Dario", "Hana", "Gideon", "Thea"};
    static final String[] LAST = {"Blackwood", "Okonkwo", "Lindgren", "Marchetti", "Van Der Berg", "Castellano", "Nakagawa", "Hargrove",
            "Devereux", "Abernathy", "Kowalczyk", "Ferreira", "Whitlock", "Halvorsen", "Chaudhry", "Montague", "Ostrowski",
            "Delacroix", "Yamamoto", "Rasmussen", "Underwood", "Bellamy", "Achterberg", "Sandoval", "Pemberton", "Vasquez",
            "Thorne", "Kaplan", "Novak", "Lockhart"};
    static final String[] CEO_OUT = {"%c CEO %o steps down effective immediately", "%o resigns as CEO of %c amid board clash",
            "Shock exit: %o quits %c"};
    static final String[] CEO_IN = {"%n takes the helm at %c", "New CEO %n lays out a vision for %c"};
    static final String[] BUYBACK = {"%c launches a share buyback programme"};
    static final String[] OFFERING = {"%c announces a new share offering"};

    static String fakeName(Random r, String not) {
        String n;
        do {
            n = FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
        } while (n.equals(not));
        return n;
    }

    static final String[] UP = {"%c posts record profit as demand surges", "%c raises full-year guidance",
            "%c strikes a partnership with an industry giant", "%c's new product sells out within hours", "%c smashes quarterly estimates", "%c lands a mega-contract",
            "%c unveils a surprise product", "Analysts upgrade %c to Strong Buy", "%o teases 'something huge' at %c"};
    static final String[] DOWN = {"%c cuts guidance as costs balloon", "Whistleblower alleges accounting issues at %c",
            "%c faces a class-action lawsuit", "Factory fire halts production at %c", "%c hit by supply chain meltdown", "Regulators open probe into %c",
            "%c misses earnings as CFO resigns", "Data leak rattles %c customers", "%o spotted selling shares of %c"};
    static final String[] SECTOR_UP = {"%s stocks surge on booming demand", "Investors pile into %s"};
    static final String[] SECTOR_DOWN = {"New tariffs hammer %s", "%s stocks wobble as investors flee"};
    static final String[] MARKET_UP = {"Central bank cuts rates and markets cheer", "Jobs report crushes expectations"};
    static final String[] MARKET_DOWN = {"Inflation shock spooks investors", "Global sell-off rattles the floor"};
    static final String BODY_UP = "Traders are scrambling to reposition as the news spreads.";
    static final String BODY_DOWN = "Sellers are rushing for the exits as details emerge.";

    static Mission mission(String id) {
        return MISSIONS.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    static Badge badge(String id) {
        return BADGES.stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    static Co make(String id, String name, String sector, double price, double sharesM, double vol, String ceo,
                   String tagline, String blurb, long flt) {
        long shares = (long) (sharesM * 1_000_000);
        double capB = price * shares / 1e9;
        double beta = Math.max(0.6, Math.min(1.9, vol * 300));
        int hue = Math.floorMod(id.hashCode() * 37, 360);
        double liq = Math.round(70000 + 550 * capB);
        return new Co(id, name, sector, hue, tagline, ceo, blurb, price, vol, beta, liq, shares, flt > 0 ? flt : floatFor(price, liq));
    }

    static Map<String, Object> coMap(Co c) {
        return Json.of("id", c.id(), "name", c.name(), "sector", c.sector(), "hue", c.hue(), "tagline", c.tagline(),
                "ceo", c.ceo(), "blurb", c.blurb(), "price", c.price(), "vol", c.vol(), "beta", c.beta(), "liq", c.liq(),
                "shares", c.shares(), "flt", c.flt());
    }

    static Co coFrom(Map<String, Object> m) {
        return new Co(Json.str(m.get("id"), "?"), Json.str(m.get("name"), "?"), Json.str(m.get("sector"), "Other"),
                (int) Json.lng(m.get("hue"), 200), Json.str(m.get("tagline"), ""), Json.str(m.get("ceo"), ""),
                Json.str(m.get("blurb"), ""), Json.num(m.get("price"), 10), Json.num(m.get("vol"), 0.003),
                Json.num(m.get("beta"), 1), Json.num(m.get("liq"), 100000), Json.lng(m.get("shares"), 100_000_000L),
                Json.lng(m.get("flt"), floatFor(Json.num(m.get("price"), 10), Json.num(m.get("liq"), 100000))));
    }
}
