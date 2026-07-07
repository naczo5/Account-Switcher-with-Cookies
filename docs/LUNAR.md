# Lunar Client Setup

This fork is scoped for **Lunar Client** and **Localts** cookie alt import. Standard Forge/Fabric installs work the same way.

## Supported Minecraft versions

| Minecraft | Loader | Build (PowerShell) | Output jar |
|-----------|--------|-------------------|------------|
| **1.8.9** | Forge (Lunar) | `cd forge-1.8; .\gradlew.bat build` (requires **JDK 8**) | `forge-1.8\build\libs\IAS-9.0.7+1.8.9-forge.jar` |
| **26.1.2** (26.1) | Fabric | `.\gradlew.bat :26.1.2-fabric:jar` | `build\libs\IAS-*+26.1.2-fabric.jar` |
| **1.21.11** | Fabric | `.\gradlew.bat :1.21.11-fabric:remapJar` | `build\libs\IAS-*+1.21.11-fabric.jar` |

## Install

### 1.8.9 Forge (Lunar)

1. Open the Lunar launcher, select **1.8.9**, and enable the **Forge** module (Forge icon in the version selector).
2. Open version **Settings** (gear) → **Mods**.
3. Click the **folder** button to open the profile mods directory, then copy in:

   `forge-1.8\build\libs\IAS-9.0.7+1.8.9-forge.jar`

4. Launch the profile. On the Lunar home screen you should briefly see **`IAS loaded - press O`** in the top-left.
5. Press **`O`** to open the account switcher.

If the overlay never appears, the jar is not loading — check `latest.log` for `IAS` or Forge errors.

### 26.1.2 / 1.21.11 Fabric

1. Use a Lunar **Fabric** profile for **26.1.2** or **1.21.11**.
2. Open version **Settings** → **Mods**, drag in the matching jar (or use the folder button).
3. Ensure **Fabric API** is present for that Minecraft version.

## Accessing the mod on Lunar

Lunar replaces the vanilla title screen, so the mod uses multiple entry points:

1. **Keybind (default: `O`)** — works on the main menu and multiplayer list (not while in a world or when a text field is focused). On Lunar's home UI, `O` is captured at the keyboard level because Lunar does not use the vanilla title screen.
2. **Mod Menu** (Fabric profiles only) — **In-Game Account Switcher** in the Mod Menu list.
3. **Title / multiplayer button** — on compatible `GuiScreen`s; may not appear on Lunar's WebOSR home — use **`O`** instead.

## Localts / cookie import on Lunar

1. Open the account switcher (`O` on 1.8.9 Lunar home screen, or Mod Menu on Fabric).
2. **Add** → **Import Cookie**.
3. **File Path** or **Paste** — Localts token or Netscape cookie text.

Cookie imports are stored **without encryption** in this fork.

## If account switching fails

1. Open `latest.log` in the instance folder (Lunar launcher → folder icon for the profile, or the path shown in version settings).
2. Search for `IAS`, `In-Game Account Switcher`, or authentication errors.
3. Common causes:
   - Jar missing from the Forge **Mods** folder for that 1.8.9 profile
   - Expired or revoked Localts token / session cookies
   - Account storage folder not writable: `{gameDir}/_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE`

## Remote disable check

Upstream IAS may remotely disable specific versions. If the mod shows as disabled on launch, add JVM arg:

`-Dias.skipDisableScanning=true`
