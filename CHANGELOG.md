# Changelog

All notable changes in **this** repository are documented here.

This project started from [CookieIAS](https://github.com/naczo5/Account-Switcher-with-Cookies) / [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher). Entries from **3.2.1** onward are the work in `antlmao1337/Account-Switcher-with-Cookies`.

## [3.2.4] - 2026-09-27

Repo cleanup: no live credentials in source.

- Sample cookie/token files are placeholders only.
- LiquidProxy docs use `dedicated.REGION.liquidproxy.net`, empty user/pass/route.
- `ias.json`, `accounts.json`, and `nfaswitcher/import` dumps are gitignored.

## [3.2.3] - 2026-09-27

LiquidProxy support.

### Added

- `liquidProxyHost` / `liquidProxyPort` / `liquidProxyUsername` / `liquidProxyPassword` / `liquidProxyType` in `config/nfaswitcher/ias.json` — Proxy Manager credentials (`dedicated.REGION.liquidproxy.net:1080` + separate user/pass).
- `liquidProxyRoute` — dashboard route hostname for vanilla-style joins.
- Hypixel ban checks use LiquidProxy SOCKS (or the route if that's all that's set) instead of your IP.
- Direct Play **Fill LiquidProxy Route** and Hypixel address rewrite onto the route.

## [3.2.2] - 2026-09-27

Hypixel ban-check IP-ban protection.

### Changed

- `ias.json` is stored in `config/nfaswitcher/` (next to `accounts.json` and `import/`), pretty-printed, and created on launch.
- Hypixel checker popups use short labels (`12/195  Name` + one status line) instead of overflowing the panel.
- Check Hypixel no longer logs **no-rank / unknown-rank** alts into `mc.hypixel.net` from your IP. Those joins are what IP-ban the connection when the alt is banned and has no rank.
- Ranked (VIP+) alts still join locally by default.
- If Hypixel kicks with an IP/network block, the rest of the queue is **not** joined.

### Added

- `hypixelCheckProxy` in `config/ias.json` — SOCKS5 or HTTP CONNECT proxy for ban-check joins (`socks5://user:pass@host:1080` or `host:port`).
- `hypixelApiKey` — optional key from [developer.hypixel.net](https://developer.hypixel.net) so rank can be read *before* any Hypixel login.
- Skip marker `H-` on accounts that were not joined (tooltip explains why).
- Config flags: `hypixelCheckAllowRankedDirect` (default true), `hypixelCheckAllowUnrankedDirect` (false), `hypixelCheckAllowUnknownDirect` (false), `hypixelCheckAllowNeverJoinedDirect` (true).

## [3.2.1] - 2026-09-27

Checker-dump import, live session swap, and bulk cookie-alt loading.

### Added

- **Bulk Import** button on the account manager. Reads every cookie/token file in `.minecraft/config/nfaswitcher/import`.
- Auto-created `config/nfaswitcher/import` drop folder plus a `README.txt` explaining supported files.
- `CookieParser.splitRecords()` — splits checker exports into one account per block using:
  - `Source:` / `Email:` / `Username:` metadata headers
  - `# Netscape HTTP Cookie File`
  - `--------` separators
- `AuthLog` — compact one-line logging for expected auth failures (dead cookies, expired tokens, 429s). Full stack traces stay at debug.
- Mixin overrides for `Minecraft.getUser()` and `Minecraft.getGameProfile()` so a swapped alt is the live session, not only an entry in the list.
- Rate-limit delay on bulk cookie login (~6s between accounts, 30s retry on HTTP 429).

### Changed

- Netscape dumps keep `#HttpOnly_` cookies instead of skipping them.
- Auth cookies recognized: `__Host-MSAAUTH`, `__Host-MSAAUTHP`, `MSPAuth`, `MSPProf`, `WLSSID`.
- Cookie files are decoded as UTF-8, UTF-16 LE/BE, or Windows ANSI instead of assuming UTF-8.
- Token importer only accepts real tokens (`M.C…`, `M.…`, JWTs, `email:token`). Netscape TSV rows are no longer treated as refresh tokens.
- Microsoft HTTP error bodies are truncated in exceptions so `latest.log` is readable.
- Title-bar nick uses the swapped account when one is active.
- Single-account cookie/token add auto-applies the session after the list save.

### Fixed

- `.txt` import reporting success while the game stayed on the launcher account (`Minecraft.user` is `final`; the mixin is the actual swap).
- Multi-account checker files (`good.txt` style) importing as one mashed account.
- Invalid/expired alts flooding logs with full HTML pages and stack traces.
- Bad TSV lines aborting parse of the rest of a dump.

### Notes

- Bulk import fills the account list. It does not auto-login every alt. Pick one and press **Login**.
- Dead cookies in a mixed dump fail individually and the rest continue.

## [3.2] - 2026-09-19

CookieIAS 3.2 (upstream).

- Fixed keybind customization and handling on Fabric 1.21.11, Fabric 26.2, and Forge 1.8.9.
- Direct-play buttons to bypass Lunar's account-check gate (world list / server connect from the switcher).
- Fixed incorrect button names on 1.8.9.

## [3.1] - 2026-09-10

CookieIAS 3.1 (upstream).

- Error handling shows the real in-game error instead of pointing at missing logs.
- Mod metadata/naming change — delete the old IAS jar when replacing.

## [3.0] - 2026-09-06

CookieIAS 3.0 (upstream).

- Hypixel ban checker in the account list.
- Extract and save OAuth refresh tokens from cookie accounts.
- Unified Localts, cookie, and token import dialogs.
- Profile manager, skin preview/change, file dialogs, and Hypixel check on 1.8.9.
- Modern versions trimmed to Fabric.
- In-game skin changer, skin previews, multi-file file-dialog selection (xCheezie).
