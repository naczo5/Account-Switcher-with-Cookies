<img src="ias.png" alt="In-Game Account Switcher Icon" width=128 height=128/>

# Account Switcher with Cookies (Lunar / Localts)

A fork of [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher) scoped for **Lunar Client** and **Localts** cookie alt files. Switch Minecraft accounts in-game without restarting, and add Microsoft accounts by importing cookie files exported from Localts or standard Netscape cookie dumps.

> **Security:** Cookie and Localts files are full account credentials. Never share them, never commit them to git, and treat imported accounts like passwords. This fork stores cookie-imported accounts **without encryption** by default so alt files can be re-imported easily — use Microsoft OAuth login instead if you want password or hardware encryption.

## What this fork adds

- **Localts import** — single-line `M.C…` Microsoft refresh tokens (Localts export format) are detected and exchanged for a full Minecraft session.
- **Netscape cookie import** — tab-separated browser cookie exports with `__Host-MSAAUTH` / `__Host-MSAAUTHP` still work via Xbox SISU.
- **Lunar Client support** — keyboard shortcut (`O` by default) and Mod Menu entry work on Lunar's custom main menu where the vanilla title button does not appear.
- **No encryption prompt on cookie import** — cookie imports skip the Crypt selection screen and are stored unencrypted.

## Supported versions

| Minecraft | Loader | Build (PowerShell) | Output jar |
|-----------|--------|-------------------|------------|
| **26.1.2** | Fabric | `.\gradlew.bat :26.1.2-fabric:jar` | `build\libs\IAS-*+26.1.2-fabric.jar` |
| **1.21.11** | Fabric | `.\gradlew.bat :1.21.11-fabric:remapJar` | `build\libs\IAS-*+1.21.11-fabric.jar` |
| 1.20.1 | Fabric / Forge / NeoForge | see `dev/versions/versions_active.txt` | `build\libs\IAS-*+<version>-<loader>.jar` |

**26.1.2** is the primary target for current Lunar Fabric profiles. **1.21.1** is deprecated in this fork (moved to legacy builds). See [docs/LUNAR.md](docs/LUNAR.md) for Lunar-specific setup.

## Dependencies

**Fabric (Lunar):** [Fabric API](https://modrinth.com/mod/fabric-api) (required), [Mod Menu](https://modrinth.com/mod/modmenu) (recommended)

## Install on Lunar Client

1. Use a Lunar **Fabric** profile matching one of the supported versions above (26.1.2 recommended).
2. Copy the matching jar from `build\libs\` into the profile **mods** folder, or install via the Lunar launcher mod browser.
3. Ensure **Fabric API** is present for that Minecraft version.

See [docs/LUNAR.md](docs/LUNAR.md) for opening the mod on Lunar, troubleshooting, and the remote-disable JVM flag.

## Import a Localts or cookie alt file

1. Open the account switcher — press **`O`** (default keybind) on the main menu, or open **Mod Menu → In-Game Account Switcher**.
2. **Add** → **Import Cookie**.
3. Choose how to supply the file:
   - **File Path** — path to your `.txt` alt file, e.g. `C:\alts\myaccount.txt`
   - **Paste** — paste file contents into the text box, or copy to clipboard and click **Import**

### Supported file formats

| Format | What it looks like | Notes |
|--------|-------------------|-------|
| **Localts** | One line starting with `M.C`, often ending in `MsaArtifacts` | Primary format this fork targets |
| **Netscape** | Tab-separated lines with `.login.live.com` domains | Full browser cookie jar |
| **Cookie header** | Semicolon-separated `name=value` pairs on one or more lines | Pasted from devtools |

Place personal alt files in a local `cookies/` folder (gitignored) — **never** commit them.

## FAQ

**Q:** The mod button doesn't show on Lunar's main menu.  
**A:** Press **`O`** or use Mod Menu. Lunar replaces the vanilla title screen; the keybind is captured at the keyboard level. Details in [docs/LUNAR.md](docs/LUNAR.md).

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
# Primary Lunar target (26.1.2 Fabric)
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=26.1.2-fabric"
.\gradlew.bat :26.1.2-fabric:jar

# 1.21.11 Fabric
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=1.21.11-fabric"
.\gradlew.bat :1.21.11-fabric:remapJar
```

Built jars appear in `build\libs\`.

## Credits

Based on [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher) by VidTu and contributors.

Microsoft authentication flow references: [minecraft.wiki/Microsoft_authentication](https://minecraft.wiki/Microsoft_authentication).
