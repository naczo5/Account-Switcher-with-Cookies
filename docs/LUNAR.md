# Lunar Client Setup

This fork is scoped for **Lunar Client** and **Localts** cookie alt import. Standard Forge/Fabric installs work the same way.

## Supported Minecraft versions

| Minecraft | Loader | Build (PowerShell) | Output jar |
|-----------|--------|-------------------|------------|
| **26.1.2** (26.1) | Fabric | `.\gradlew.bat :26.1.2-fabric:jar` | `build\libs\IAS-*+26.1.2-fabric.jar` |
| **1.21.11** | Fabric | `.\gradlew.bat :1.21.11-fabric:remapJar` | `build\libs\IAS-*+1.21.11-fabric.jar` |

## Install

### 26.1.2 / 1.21.11 Fabric

1. Use a Lunar **Fabric** profile for **26.1.2** or **1.21.11**.
2. Open version **Settings** → **Mods**, drag in the matching jar (or use the folder button).
3. Ensure **Fabric API** is present for that Minecraft version.

## Accessing the mod on Lunar

Lunar replaces the vanilla title screen, so the mod uses multiple entry points:

1. **Keybind (default: `O`, changeable under Controls → In-Game Account Switcher)** — works on the main menu and multiplayer list (not while in a world or when a text field is focused).
2. **Mod Menu** (Fabric profiles only) — **In-Game Account Switcher** in the Mod Menu list.
3. **Title / multiplayer button** — on compatible `GuiScreen`s; may not appear on Lunar's WebOSR home — use **`O`** instead.

## Bypassing Lunar's signed-in account check (Direct Play)

Lunar's launcher home screen prevents opening Singleplayer or Multiplayer if you are not signed into an account through the Lunar launcher.

This mod provides **Direct Play** buttons to enter the game menus without passing through Lunar's account gate:
- **Inside Account Switcher (`O`):** **Singleplayer** and **Multiplayer** buttons in the top-right corner. Select or import your alt account, then immediately jump in.
- **Singleplayer** opens the vanilla world list directly.
- **Multiplayer** opens an IAS-owned **direct-connect** screen (type a server IP and join, Enter works too). It deliberately avoids all vanilla multiplayer screens: on gated setups Lunar hooks those and their buttons silently do nothing. The last used IP is pre-filled automatically.
- **On the direct-connect screen:** a **Server List** button offers the vanilla server list as a fallback for setups without the gate.
- **On compatible title screens:** Direct **Singleplayer** and **Multiplayer** buttons are placed at the top-left.
- **1.8.9 Forge parity:** Direct-play buttons are also available on both the main menu (non-vanilla menus only, so they don't duplicate the built-in buttons) and the 1.8 account selector GUI. (Note: 1.8.9 still opens the server list, as that version has no direct-connect screen.)
- Can be toggled in the mod settings (**Config → Direct Play Buttons**).

Online-mode servers still need a valid Microsoft session — log in via IAS first. Offline servers work without one.

## Localts / cookie import on Lunar

1. Open the account switcher with the `O` keybind or from Mod Menu.
2. **Add** → **Import Cookie**.
3. **File Path** or **Paste** — Localts token or Netscape cookie text.

Cookie imports are stored **without encryption** in this fork.

## If account switching fails

1. Open `latest.log` in the instance folder (Lunar launcher → folder icon for the profile, or the path shown in version settings).
2. Search for `IAS`, `In-Game Account Switcher`, or authentication errors.
3. Common causes:
   - Jar missing from the profile **Mods** folder
   - Expired or revoked Localts token / session cookies
   - Account storage folder not writable: `{gameDir}/_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE`

## Remote disable check

Upstream IAS may remotely disable specific versions. If the mod shows as disabled on launch, add JVM arg:

`-Dias.skipDisableScanning=true`
