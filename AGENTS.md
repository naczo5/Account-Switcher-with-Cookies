# AGENTS.md — Account-Switcher-with-Cookies

This is the operating guide for agents and contributors working in this repository. It is not user-facing documentation.

## Project overview

- Fork of **In-Game Account Switcher (IAS)** for Minecraft Java Edition.
- Adds cookie-alt / `M.C...` refresh-token / Minecraft access-token (MCA JWT) importing, Localts marketplace integration, batch importing, and multi-version builds.
- License: LGPL-3.0. Keep existing copyright headers intact.

## Supported targets

- **Active modern targets** (built by `./gradlew assemble`): **26.3**, **26.2**, **1.21.11** (Fabric).
- **Forge 1.8.9** is a standalone project in `forge-1.8/` and is built separately.
- **Legacy targets** (1.18.2 through 26.1.2) may provide overrides under `src/_legacy/<mcver>/...`; some newer legacy versions build directly from `src/main` + `src/_legacy/_shared`. They are only built with `-Dru.vidtu.ias.legacy=true` (`./compileall legacy`).
- The active Stonecutter version is `26.3-fabric` in `stonecutter.gradle.kts`. `versions/` contains generated virtual projects; never edit `versions/*/build`.

## Source map

| Path | Purpose |
| :--- | :--- |
| `src/main/java/ru/vidtu/ias/screen/` | Modern UI screens (26.3 / 26.2 / 1.21.11). |
| `src/main/java/ru/vidtu/ias/screen/CookiePopupScreen.java` | Modern cookie/token/Localts batch import: live status, type/username hints, skip, timeouts. |
| `src/_legacy/_shared/ru/vidtu/ias/` | Auth, config, storage and utilities shared with modern builds. |
| `src/_legacy/_shared/ru/vidtu/ias/auth/cookie/` | Modern `CookieParser` (Netscape, Localts, cookie-header formats). |
| `src/_legacy/_shared/ru/vidtu/ias/auth/TokenImporter.java` | Modern bare-token import and OAuth-client fallback. |
| `src/_legacy/_shared/ru/vidtu/ias/auth/microsoft/` | Modern Microsoft/OAuth/SISU pipeline. |
| `src/_legacy/_shared/ru/vidtu/ias/auth/localts/` | Localts API client and order parsing. |
| `src/_legacy/<mcver>/{root,fabric,forge,neoforge}/` | Version-specific overrides for older legacy targets (some newer legacy versions build from `src/main` + `_shared`). |
| `forge-1.8/src/main/java/` | Standalone 1.8.9 implementation (Forge). |
| `forge-1.8/src/main/java/the_fireplace/ias/gui/GuiCookieImport.java` | **1.8.9 batch import UI** — separate from the modern screen. |
| `forge-1.8/src/main/java/ru/vidtu/iasfork/cookie/` | 1.8.9 `CookieParser`, `TokenImporter`, `CookieAuth` (private `MsaTokens`, public `MinecraftProfile`). |
| `src/main/resources/assets/ias/lang/*.json` | Modern translations; `en_us.json` is the fallback. |
| `forge-1.8/src/main/resources/assets/ias/lang/en_us.lang` | 1.8.9 translations (`.lang` format). |
| `.agents/skills/refresh-and-cookie-alts/` | Token/cookie format reference, synthetic examples, troubleshooting. |
| `tests/run-parser-tests` | Standalone parser tests (no JUnit/Minecraft classes). |
| `.github/workflows/` | Build (main/branches/legacy) and release automation. |

## 1.8.9 parity rule

**Modern changes are not automatically included in 1.8.9.**

- `forge-1.8` only copies `auth/hypixel/**` and `auth/localts/**` from `src/_legacy/_shared` into its build (`forge-1.8/build.gradle`, task `copyHypixelSources`).
- The 1.8.9 importer uses its own `GuiCookieImport`, `ru.vidtu.iasfork.cookie.CookieParser`, `TokenImporter`, `CookieAuth`, and `MsaTokens`/`GetRequest`/`PostRequest`.
- When changing import UX or batch behavior, update **both** `CookiePopupScreen.java` and `forge-1.8/.../GuiCookieImport.java`, unless the change is explicitly modern-only.
- Modern translation keys go in `src/main/resources/assets/ias/lang/en_us.json`; 1.8.9 keys go in `forge-1.8/src/main/resources/assets/ias/lang/en_us.lang`.

## Build and test

Local toolchains:
- JDK **25** for modern targets (Gradle auto-download cache: `~/.gradle/jdks/`).
- JDK **8** for Forge 1.8.9 (`~/.jdks/temurin-8`).

Commands:

```bash
# Compile all active modern targets (fast sanity check)
./gradlew :26.3-fabric:compileJava :26.2-fabric:compileJava :1.21.11-fabric:compileJava

# Full modern build (jars in build/libs/)
./gradlew assemble

# Parser tests (expect 10/10 CookieParser + 11/11 LocaltsClient)
./tests/run-parser-tests

# Standalone Forge 1.8.9 build (jar in forge-1.8/build/libs/)
cd forge-1.8
./gradlew build -Dorg.gradle.java.home="$HOME/.jdks/temurin-8"

# One target
./compileone 1.21.11-fabric

# All active targets / including legacy sources
./compileall normal
./compileall legacy
```

Always compile **26.3, 26.2, and 1.21.11** after touching shared/modern code; the Stonecutter preprocessor must produce valid code for every active target.

## Stonecutter conventions

- Code is preprocessed with Stonecutter conditional comments. Keep the `//? if <condition> {` / `//?} else` / `/*...*/` structure intact.
- `CookiePopupScreen` uses `GuiGraphicsExtractor` on 26.1+ and `GuiGraphics` on older versions; `renderBatchStatus` and `renderFailures` follow the same dual signature pattern.
- Do not edit generated `versions/**/build` files.
- After changing `stonecutter.gradle.kts`, `settings.gradle.kts`, or version files in `dev/versions/`, run the full `assemble`.

## Import behavior and invariants

- Batch spacing: **6 s** between accounts, **30 s** wait on rate-limit (2 retries), **120 s** per-record timeout in the modern importer.
- Account types: cookie alt, `M.C...` / refresh token, Minecraft access token (`eyJ...` JWT).
- Never log or display full cookies, refresh tokens, access tokens, or passwords. Metadata usernames must be truncated at `:` and never include a `password` suffix.
- Modern batch state lives in `CookiePopupScreen` (`batch*` volatile fields, `skipRequestedIndex`, `pendingJobIndex`, `activeRecordFuture`/`activeRecordCancelled`).
- 1.8.9 batch state lives in `GuiCookieImport` (`batch*` volatile fields, `skipRequested`, `importThread`).
- A skipped, timed-out, failed, or closed import must never leave `importing` true or a batch future pending. Reset batch state in every finish/error path.
- Reject non-regular files (`Files.isRegularFile`) before reading; FIFOs/devices can block the import thread forever.
- Modern HTTP uses `IAS.executor()` and `IAS.TIMEOUT` (15 s); cookie/SISU stages check cancellation between requests. 1.8.9 uses blocking `HttpURLConnection` with 15 s connect/read timeouts and interrupts worker sleeps on skip/close.
- 1.8.9 file imports are file-level jobs (a file can contain several records/accounts); Localts payload imports are account-level jobs. Progress totals count jobs, not necessarily accounts.

## Versioning and release

- Version: `gradle.properties` → `version=3.4.4`.
- Also update when bumping:
  - `README.md` and `README.zh-CN.md` artifact names.
  - `forge-1.8/build.gradle` (`version = '<version>+1.8.9-forge'`).
  - `src/_legacy/_shared/ru/vidtu/ias/auth/localts/LocaltsClient.java` (`USER_AGENT`).
  - Any `CookieIAS-<version>` docs.
- CI:
  - `.github/workflows/main.yml` builds modern + parser tests + Forge 1.8.9 on pushes to `main`.
  - `.github/workflows/release.yml` builds and publishes on tag pushes (`v*`, `*-pre*`, `*.*.*`) or via `workflow_dispatch` with `tag_name`, `prerelease`, and `draft` inputs.
- A draft release can be created with the version tag and all four jars:
  - modern: `build/libs/CookieIAS-<version>+{26.3,26.2,1.21.11}-fabric.jar`
  - legacy: `forge-1.8/build/libs/CookieIAS-<version>+1.8.9-forge.jar`

## Code style

- Java source follows the existing style: 4-space indentation, explicit imports, `@NotNull`/`@Contract` where the surrounding code uses them, final utility classes with private constructors.
- Keep user-visible strings in translation files, not hardcoded English, except internal log messages.
- Use `AuthLog.expected(...)` for expected auth failures and `LOGGER.error(...)` only for unexpected failures. These are modern-stack utilities; 1.8.9 uses `System.err`/`printStackTrace`.
- Comment only non-obvious behavior (for example, why nested batches are forbidden or why a token prefix must be preserved).

## Environment / safety

- This workspace runs on a 24/7 production home server. Follow the global instructions in `~/.config/opencode/AGENTS.md`:
  - Do not reboot, stop/restart VPN/firewall services, open router ports, or kill unidentified processes.
  - Do not touch `~/.config/wol-server/metrics.db*` or its `config.json`.
  - Use `/tmp/opencode` for scratch files.
- Do not commit generated jars, build outputs, `.ias` runtime data, account storage, cookies, or tokens.

## Known limitations

- Modern skip/cancellation stops auth between stages, but one HTTP request already in flight may finish (bounded by the 15 s timeout).
- 1.8.9 skip interrupts worker sleeps and is checked between dump records, but an in-flight blocking HTTP request finishes before the account is discarded (bounded by 15 s per request).
- File imports are file-level jobs: a dump containing several accounts counts as one job and can produce several imported accounts. Use Localts payloads for per-account progress.
