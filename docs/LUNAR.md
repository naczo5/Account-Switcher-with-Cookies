# Lunar Client Setup

This fork is scoped for **Lunar Client Fabric profiles** and **Localts** cookie alt import. Standard Fabric installs work the same way.

## Supported Minecraft versions

| Minecraft | Fabric build (PowerShell) | Output jar |
|-----------|--------------------------|------------|
| **26.1.2** (26.1) | `.\gradlew.bat :26.1.2-fabric:jar` | `build\libs\IAS-*+26.1.2-fabric.jar` |
| **1.21.11** | `.\gradlew.bat :1.21.11-fabric:remapJar` | `build\libs\IAS-*+1.21.11-fabric.jar` |

Use the jar whose version **exactly matches** your Lunar Fabric profile. **26.1.2** is the recommended target for current Lunar releases.

## Install

1. Use a Lunar **Fabric** profile for **26.1.2** or **1.21.11**.
2. Ensure **Fabric API** is present for that Minecraft version (included in Lunar's Fabric add-on; verify the version matches).
3. Copy the matching jar from `build\libs\` into the profile **mods** folder.

## Accessing the mod on Lunar

Lunar replaces the vanilla title screen, so the mod uses multiple entry points:

1. **Keybind (default: `O`)** — works on the main menu, pause menu, and multiplayer list. On Lunar's custom main menu, `O` is captured at the keyboard level because Lunar does not use Minecraft screens there.
2. **Mod Menu** — **In-Game Account Switcher** in the Mod Menu list (bottom-left Lunar logo → Mods).
3. **Title button** — appears on vanilla `TitleScreen` only; **not** on Lunar's WebOSR main menu.

The multiplayer screen also has the account-switcher button (bottom-right by default).

## Localts / cookie import on Lunar

1. Open the account switcher (`O` or Mod Menu).
2. **Add** → **Import Cookie**.
3. **File Path** — e.g. `C:\alts\account.txt`  
   **Paste** — paste Localts token or Netscape cookie text, or use clipboard **Import**.

### Localts format

Localts exports a single Microsoft refresh token per file, typically one line starting with `M.C` and containing `MsaArtifacts`. The mod exchanges it for Minecraft tokens automatically — no browser login required.

### Netscape format

Tab-separated cookie dumps with `__Host-MSAAUTH` or `__Host-MSAAUTHP` from `.login.live.com` are also supported via Xbox SISU.

Cookie imports are stored **without encryption** in this fork. Keep alt files private; use a local `cookies/` folder (gitignored) for storage.

## If account switching fails

1. Open `latest.log` in the instance folder.
2. Search for `IAS`, `mixin`, or authentication errors.
3. Common causes on Lunar:
   - Mixin conflict with Lunar's patched `Minecraft` class
   - Expired or revoked Localts token / session cookies — re-export from Localts
   - Account storage folder not writable: `{gameDir}/_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE`

## Remote disable check

Upstream IAS may remotely disable specific versions. If the mod shows as disabled on launch, add JVM arg:

`-Dias.skipDisableScanning=true`
