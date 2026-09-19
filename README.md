<img src="ias.png" alt="In-Game Account Switcher Icon" width=128 height=128/>

# Account Switcher with Cookies (Forge / Fabric / Lunar / Localts)

[![Release](https://img.shields.io/github/v/release/naczo5/Account-Switcher-with-Cookies?include_prereleases&color=238636&logo=github)](https://github.com/naczo5/Account-Switcher-with-Cookies/releases)
[![Downloads](https://img.shields.io/github/downloads/naczo5/Account-Switcher-with-Cookies/total?color=1f6feb&logo=github)](https://github.com/naczo5/Account-Switcher-with-Cookies/releases)
[![Stars](https://img.shields.io/github/stars/naczo5/Account-Switcher-with-Cookies?color=e3b341&logo=github)](https://github.com/naczo5/Account-Switcher-with-Cookies/stargazers)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9%20%7C%201.21.11%20%7C%2026.2-brightgreen)](#supported-versions)

A fork of [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher) with support for **Forge**, **Fabric**, **Lunar Fabric**, and **Localts** cookie alt files. Switch Minecraft accounts in-game without restarting, and add Microsoft accounts by importing cookie files exported from Localts or standard Netscape cookie dumps.

## What this fork adds

- **Localts import** — single-line `M.C…` Microsoft refresh tokens (Localts export format) are detected and exchanged for a full Minecraft session.
- **Netscape cookie import** — tab-separated browser cookie exports with `__Host-MSAAUTH` / `__Host-MSAAUTHP` still work via Xbox SISU.
- **Token login** — import Minecraft access tokens from files or paste.
- **Multi-file cookie import** — the file picker can select several alt files at once.
- **Lunar Client support** — keyboard shortcut (`O` by default) and Mod Menu entry work on Lunar's custom main menu where the vanilla title button does not appear.
- **No encryption prompt on cookie import** — cookie imports skip the Crypt selection screen and are stored unencrypted.

## Supported versions

| Minecraft | Loader | Build (PowerShell) | Output jar |
|-----------|--------|-------------------|------------|
| **26.2** | Fabric / Lunar Fabric | `.\gradlew.bat :26.2-fabric:jar` | `build\libs\CookieIAS-*+26.2-fabric.jar` |
| **1.21.11** | Fabric / Lunar Fabric | `.\gradlew.bat :1.21.11-fabric:remapJar` | `build\libs\CookieIAS-*+1.21.11-fabric.jar` |
| **1.8.9** | Forge | `cd forge-1.8; .\gradlew.bat build` (requires **JDK 8**) | `forge-1.8\build\libs\CookieIAS-3.2+1.8.9-forge.jar` |

**26.2** supports both standard Fabric and Lunar Fabric profiles. **1.8.9 Forge** is a standard Forge build and can be opened from the title-screen button or the `O` keybind.

## Dependencies

**Fabric (Lunar):** [Fabric API](https://modrinth.com/mod/fabric-api) (required), [Mod Menu](https://modrinth.com/mod/modmenu) (recommended)

## Install

### 1.8.9 (Forge)

1. Install Forge for **1.8.9**.
2. Copy `forge-1.8\build\libs\CookieIAS-3.2+1.8.9-forge.jar` into the instance's **mods** folder.
3. Open the account switcher from its title-screen button or press **`O`**.

### 26.2 / 1.21.11 (Fabric or Lunar Fabric)

1. Use a **Fabric** or Lunar **Fabric** profile matching one of the supported versions above.
2. Copy the matching jar from `build\libs\` into the profile **mods** folder, or install via the Lunar launcher mod browser.
3. Ensure **Fabric API** is present for that Minecraft version.

See [docs/LUNAR.md](docs/LUNAR.md) for Lunar Fabric setup and troubleshooting.

## Import a Localts or cookie alt file

1. Open the account switcher — press **`O`** (default keybind, changeable under **Controls → In-Game Account Switcher**) on the main menu, or open **Mod Menu → In-Game Account Switcher**.
2. **Add** → **Import Cookie**.
3. Choose how to supply the file:
   - **File Path** — path to your `.txt` alt file, e.g. `C:\alts\myaccount.txt`, or click **...** to open the OS file picker (multiple files are allowed)
   - **Paste** — paste the full cookie file into the multi-line text box, or leave it empty and click **Import** to use the clipboard

If import fails, check `latest.log` in your instance folder and search for `IAS/Cookie`.

### Supported file formats

| Format | What it looks like | Notes |
|--------|-------------------|-------|
| **Localts** | One line starting with `M.C`, often ending in `MsaArtifacts` | Primary format this fork targets |
| **Netscape** | Tab-separated lines with `.login.live.com` domains | Full browser cookie jar |
| **Cookie header** | Semicolon-separated `name=value` pairs on one or more lines | Pasted from devtools |

Place personal alt files in a local `cookies/` folder (gitignored) — **never** commit them.

## FAQ

**Q:** The mod button doesn't show on the main menu.
**A:** Press **`O`** or use Mod Menu (Fabric profiles). On 1.8.9, use the title-screen button or **`O`**.

**Q:** Cookie import says expired or invalid.  
**A:** Localts tokens and session cookies expire or get revoked. Export a fresh alt from Localts and import again.

**Q:** Can I use normal Microsoft login instead of cookies?  
**A:** Yes. **Add → Microsoft** still works and lets you choose password or hardware encryption.

**Q:** Where is this fork hosted?  
**A:** [GitHub — naczo5/Account-Switcher-with-Cookies](https://github.com/naczo5/Account-Switcher-with-Cookies). Upstream IAS: [Modrinth](https://modrinth.com/mod/in-game-account-switcher), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/in-game-account-switcher).

**Q:** Is this mod open source?  
**A:** Yes, under [GNU LGPLv3](LICENSE), same as upstream IAS.

## Building

```powershell
# 26.2 Fabric / Lunar Fabric
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=26.2-fabric"
.\gradlew.bat :26.2-fabric:jar

# 1.21.11 Fabric
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=1.21.11-fabric"
.\gradlew.bat :1.21.11-fabric:remapJar

# 1.8.9 Forge (standalone module — requires JDK 8)
cd forge-1.8
.\gradlew.bat build
cd ..
```

Built jars appear in `build\libs\`.

## Credits

- Originally by **VidTu**, **The_Fireplace**, and IAS contributors.
- Cookie authentication fork, Localts integration, Hypixel checks, and 1.8.9 Forge backports by **naczo5**.
- Profile management, skin updating, and multi-file selection features by [**Articuling / xCheezie**](https://github.com/xCheezie).
- Hypixel ban-check logic adapted from [mcchecker](https://github.com/cooldood-dev/mcchecker) by cooldood-dev.

Microsoft authentication flow references: [minecraft.wiki/Microsoft_authentication](https://minecraft.wiki/Microsoft_authentication).
