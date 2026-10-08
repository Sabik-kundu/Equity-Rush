# Equity Rush

A multiplayer stock-market game. Java 17+ server, responsive web client (phone, tablet and desktop) with light and dark claymorphism themes, browser admin panel. No dependencies, no build tool.

## Run

    ./run.sh            (Windows: run.bat)

- Game: http://localhost:8080
- Admin: http://localhost:8080/admin (sign in with a personal access code)
- Graphs: http://localhost:8080/graph (needs the graph key)

Environment variables: `PORT` (8080), `ADMIN_CODES` (admin handles and access codes, see below), `BULLRUN_GRAPH` (graph page key, default `graph`; the admin can change it in Admin > Access), `BULLRUN_DATA` (save folder, default `data`).

To play from phones on the same Wi-Fi, open `http://<your-PC-IP>:8080` and use "Add to Home Screen".

## Layout

- Phone: single column with a floating bottom nav and a full-screen company page with a docked trade bar.
- Tablet: two-column bento tiles.
- Desktop (1024px and up): sidebar, 12-column bento dashboard, side-by-side news and trading floor, and companies open in a modal with the trade panel docked on the right. Esc closes it.
- Light or dark theme, switchable from the header icon or Settings, and remembered per device.

## What is in it

- Three servers (Alpha Exchange, Blitz Arena, Zen Garden), each with its own market, players, chat and news.
- 13 fictional companies to start, plus any the admin adds. Prices tick every second. Player trades move prices (larger trades move them more, and you pay slippage).
- Each company has a session price limit (a percent band around the session's opening price) sized by its market cap: bigger companies get tighter limits. The admin can override a company's limit, or hide the limit details from players, per company or for the whole server.
- Admins can list a new company at any time (ticker, name, sector, starting price, shares outstanding, volatility, CEO and tagline), on one server or all of them, and delist one (holders are cashed out at the last price).
- Admin news moves prices in real time. Rumors move the price a little, then resolve 25 seconds later as confirmed or debunked based on the credibility the admin set. Every story carries a sentiment (very/mildly positive, neutral, mildly/very negative), and the breaking-news popup and news cards are coloured to match.
- A total market index (weighted by every company's market cap) tracks the whole market. It's shown on the Market tile and has its own full **Graph** page with a draggable, pinch-zoomable timeline and a company-news feed filterable by ticker.
- Any chart (the index or a single company) can be tapped to open a larger view: drag to pan, pinch or scroll to zoom, double-click or "Fit all" to reset. A company's chart also plots a thin dashed line at each price you bought in at.
- Net worth reflects live prices, but your invested amount (cost basis) only changes when you actually buy or sell — it doesn't drift with the market, so unrealized profit/loss is easy to read at a glance.
- Players can buy intel on a rumor, complete daily missions, claim a streak bonus, earn XP, levels and badges, chat on the trading floor, and climb the leaderboard.

## New in this version

- **Speed**: painting is batched into one frame per tick, DOM writes only happen when a value changed, no forced reflows, off-screen cards are skipped, cheaper shadows, no backdrop blur, gzip + ETag for static files.
- **Admin tabs**: Overview, News, Companies (with a Manage dialog per company), Listings, Players, Chat, Arenas, Access. The tab is kept in the URL hash.
- **Arenas**: the admin can open or close any arena. A closed arena is offline: no sign-ups or logins, players inside are signed out immediately, and the market freezes until it is reopened.
- **Graph page** (`/graph`): key protected, shows only the total market graph and one graph per sector, for any arena.
- **Charts** start at the left edge and fill rightwards; they resize cleanly on phones and vertical scrolling still works over them.
- **Realistic news**: an impact is delivered over 4 to 6 ticks (after a 0 to 1 tick delay) with random weights and a small random size variation.
- **More companies**: 25 in total, with new Energy, Media, Telecom and Real Estate sectors. Events include CEO resignations (a fake successor is named, followed by a "new CEO" story), share buybacks and new share offerings.
- **Limited shares**: every company has a float (the most shares players can own in total). The admin sets it when listing and can change it any time. Sold-out stocks can't be bought. Trades cost more price impact when little is left.
- **Bans and resets**: the player's shares are taken and released back to the market slowly, with gentle selling pressure on the price.
- **Competing forces**: news, admin trends and player order flow are added together each tick. When they pull opposite ways the price is dampened but gets more volatile. The admin can start a **trend** (for example -12% over 10 minutes) and buyers push against it.
- Chat no longer announces who bought or sold how many shares.

## Files

    src/bullrun/Game.java      accounts, tokens, saving, live broadcast
    src/bullrun/Realm.java     one server's market: pricing, news, trading, missions, chat, companies
    src/bullrun/Company.java   price model and session price limits
    src/bullrun/Series.java    time-series storage behind every chart
    src/bullrun/Player.java    profile, portfolio and buy-lot history
    src/bullrun/Http.java      REST routes, live stream (SSE), static files
    src/bullrun/Defs.java      starting companies, servers, missions, badges, news templates
    web/index.html, app.js, app.css, theme.css, chart.js, icons.js   player app
    web/admin.html                                                  admin panel (tabbed)
    web/graph.html                                                  key-protected market and sector graphs

State saves to `data/save.json` every 20 seconds and on shutdown.

## Admin access codes

Each admin has a handle and a personal access code (not case sensitive). Four are built in (see `src/bullrun/Main.java`, handed out on the separate admin sheet). To use your own, set `ADMIN_CODES` to `Handle=code;Handle=code`, for example `ADMIN_CODES="Admin Alpha=lotus-river-31;Admin Bravo=coral-maple-52"`. When it is set, the built-in codes are ignored. Ten wrong codes from one address lock that address out for 10 minutes.

## Deploy on Railway

1. Push this folder to a GitHub repo (the `.gitignore` keeps `data/` and the admin sheet out).
2. In Railway: New Project, Deploy from GitHub repo. The `Dockerfile` and `railway.json` are picked up automatically; nothing else to configure for the build.
3. Add a Volume to the service and mount it at `/data`. This keeps accounts and market state across redeploys.
4. Variables: `ADMIN_CODES` (recommended, so codes are not in the public repo) and optionally `BULLRUN_GRAPH`.
5. Service > Settings > Networking > Generate Domain. Players open the domain; admins open `/admin`.

Railway sets `PORT` itself. Keep the service at one replica, because the market lives in memory.
