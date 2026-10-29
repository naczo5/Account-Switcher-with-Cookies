<img src="ias.png" alt="In-Game Account Switcher Icon" width=128 height=128/>

# 带 Cookie 的账号切换器（Forge / Fabric / Lunar / Localts）

[English](README.md) | **简体中文**

[![Release](https://img.shields.io/github/v/release/naczo5/Account-Switcher-with-Cookies?include_prereleases&color=238636&logo=github)](https://github.com/naczo5/Account-Switcher-with-Cookies/releases)
[![Downloads](https://img.shields.io/github/downloads/naczo5/Account-Switcher-with-Cookies/total?color=1f6feb&logo=github)](https://github.com/naczo5/Account-Switcher-with-Cookies/releases)
[![Stars](https://img.shields.io/github/stars/naczo5/Account-Switcher-with-Cookies?color=e3b341&logo=github)](https://github.com/naczo5/Account-Switcher-with-Cookies/stargazers)
[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9%20%7C%201.21.11%20%7C%2026.2%20%7C%2026.3-brightgreen)](#支持的版本)

这是 [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher) 的一个分支，支持 **Forge**、**Fabric**、**Lunar Fabric** 以及 **Localts** cookie 小号文件。无需重启游戏即可在游戏内切换 Minecraft 账号，并可通过导入从 Localts 导出的 cookie 文件或标准 Netscape cookie 转储来添加 Microsoft 账号。

## 本分支新增的功能

- **Localts 导入** — 自动识别单行 `M.C…` Microsoft 刷新令牌（Localts 导出格式），并将其兑换为完整的 Minecraft 会话。
- **Netscape cookie 导入** — 包含 `__Host-MSAAUTH` / `__Host-MSAAUTHP` 的制表符分隔浏览器 cookie 导出文件仍可通过 Xbox SISU 正常使用。
- **Checker 转储导入** — 一个包含多个 `Source:` / Netscape / `--------` 块的 `.txt` 文件会按块解析为多个账号，失效的小号只会单独导入失败。
- **批量导入** — 将文件放入 `.minecraft/config/cookieias/import`，然后在账号管理器中一键导入。
- **实时会话切换** — 登录 cookie 小号后会真正切换到该账号。
- **HttpOnly Netscape cookie** — 保留 `#HttpOnly_` 开头的行（`__Host-MSAAUTH`、`MSPAuth`、`WLSSID` 等）。
- **令牌登录** — 从文件导入或直接粘贴 Minecraft 访问令牌。
- **多文件 cookie 导入** — 文件选择器支持一次选择多个小号文件。
- **Lunar Client 支持** — 在 Lunar 自定义主菜单中（原版标题界面按钮不会显示），可通过快捷键（默认 `O`）和 Mod Menu 入口打开。
- **Direct Play 绕过按钮** — 账号切换界面上提供单人游戏世界列表和多人游戏直连（服务器 IP）快捷入口，用于绕过 Lunar 的账号检查拦截。
- **导入 cookie 时不弹出加密提示** — cookie 导入会跳过 Crypt 选择界面，并以未加密方式存储。

## 支持的版本

| Minecraft 版本 | 加载器 | 构建命令（PowerShell） | 输出 jar |
|-----------|--------|-------------------|------------|
| **26.3** | Fabric / Lunar Fabric | `.\gradlew.bat :26.3-fabric:jar` | `build\libs\CookieIAS-*+26.3-fabric.jar` |
| **26.2** | Fabric / Lunar Fabric | `.\gradlew.bat :26.2-fabric:jar` | `build\libs\CookieIAS-*+26.2-fabric.jar` |
| **1.21.11** | Fabric / Lunar Fabric | `.\gradlew.bat :1.21.11-fabric:remapJar` | `build\libs\CookieIAS-*+1.21.11-fabric.jar` |
| **1.8.9** | Forge | `cd forge-1.8; .\gradlew.bat build`（需要 **JDK 8**） | `forge-1.8\build\libs\CookieIAS-3.3.1-pre1+1.8.9-forge.jar` |

如果你需要在 Lunar Client 1.8.9 上使用 cookie 小号，可以看看我的注入工具：https://github.com/naczo5/lunar-cookies

## 依赖

**Fabric (Lunar)：** [Fabric API](https://modrinth.com/mod/fabric-api)（必需），[Mod Menu](https://modrinth.com/mod/modmenu)（推荐）

## 安装

### 1.8.9 (Forge)

1. 为 **1.8.9** 安装 Forge。
2. 将 `forge-1.8\build\libs\CookieIAS-3.3.1-pre1+1.8.9-forge.jar` 复制到整合包的 **mods** 文件夹。
3. 通过标题界面按钮或按 **`O`** 打开账号切换器。

### 26.3 / 26.2 / 1.21.11（Fabric 或 Lunar Fabric）

1. 使用与上述受支持版本匹配的 **Fabric** 或 Lunar **Fabric** 配置文件。
2. 将 `build\libs\` 中对应的 jar 复制到该配置文件的 **mods** 文件夹，或通过 Lunar 启动器模组浏览器安装。
3. 确保该 Minecraft 版本已安装 **Fabric API**。

Lunar Fabric 的设置与疑难解答请参见 [docs/LUNAR.md](docs/LUNAR.md)。

## 导入 Localts 或 cookie 小号文件

1. 打开账号切换器 — 在主菜单按 **`O`**（默认为快捷键，可在 **控制 → In-Game Account Switcher** 中修改），或打开 **Mod Menu → In-Game Account Switcher**。
2. **添加（Add）** → **导入 Cookie（Import Cookie）**。
3. 选择提供文件的方式：
   - **文件路径（File Path）** — 你的 `.txt` 小号文件路径，例如 `C:\alts\myaccount.txt`，或点击 **...** 打开系统文件选择器（允许选择多个文件）
   - **粘贴（Paste）** — 将完整的 cookie 文件粘贴到多行文本框，或留空后点击 **导入（Import）** 以使用剪贴板内容

如果导入失败，请查看整合包文件夹中的 `latest.log`，搜索 `IAS/Cookie`。

### 支持的文件格式

| 格式 | 示例 | 说明 |
|--------|-------------------|-------|
| **Localts** | 以 `M.C` 开头的一行内容，通常以 `MsaArtifacts` 结尾 | 本分支主要支持的格式 |
| **Netscape** | 包含 `.login.live.com` 域名的制表符分隔行 | 完整的浏览器 cookie jar |
| **Cookie header** | 一行或多行以分号分隔的 `name=value` 键值对 | 从开发者工具中粘贴的内容 |

请将个人小号文件放在本地 `cookies/` 文件夹中（已被 gitignore 忽略）——**绝不要**将它们提交到仓库。

## 常见问题

**Q:** 主菜单上没有显示模组按钮。
**A:** 按 **`O`**，或使用 Mod Menu（Fabric 配置文件）。在 1.8.9 上，使用标题界面按钮或按 **`O`**。

**Q:** 导入 cookie 时提示已过期或无效。  
**A:** Localts 令牌和会话 cookie 会过期或被吊销。请从 Localts 重新导出新的小号并再次导入。

**Q:** 使用 Check Hypixel 会让我的大号被 IP 封禁吗？  
**A:** 被封禁的**无等级**账号可能导致连接 IP 被封禁。默认情况下，检查器**不会**从你的机器加入无等级/等级未知的小号。有等级（VIP+）的小号仍会在本地加入。如需检查无等级小号，请在 `config/cookieias/ias.json` 中配置代理：

```json
"hypixelCheckProxy": "",
"liquidProxyHost": "",
"liquidProxyPort": 1080,
"liquidProxyUsername": "",
"liquidProxyPassword": "",
"liquidProxyType": "socks5",
"liquidProxyRoute": "",
"hypixelApiKey": ""
```

`hypixelCheckProxy` 是通用的 SOCKS5 或 HTTP 代理（`socks5://user:pass@host:1080`）。`liquidProxy*` 字段为可选的 LiquidProxy 凭据。`hypixelApiKey` 为可选，仅用于在加入前读取 package rank（套餐等级）。如果 Hypixel 返回 IP/网络封禁，其余账号将不会加入。

**Q:** 可以用普通的 Microsoft 登录代替 cookie 吗？  
**A:** 可以。**添加 → Microsoft** 仍然可用，并可选择密码或硬件加密。

**Q:** 这个分支托管在哪里？  
**A:** [GitHub — naczo5/Account-Switcher-with-Cookies](https://github.com/naczo5/Account-Switcher-with-Cookies)。上游 IAS：[Modrinth](https://modrinth.com/mod/in-game-account-switcher)、[CurseForge](https://www.curseforge.com/minecraft/mc-mods/in-game-account-switcher)。

**Q:** 这个模组是开源的吗？  
**A:** 是的，采用 [GNU LGPLv3](LICENSE)，与上游 IAS 相同。

## 构建

```powershell
# 26.3 Fabric / Lunar Fabric
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=26.3-fabric"
.\gradlew.bat :26.3-fabric:jar

# 26.2 Fabric / Lunar Fabric
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=26.2-fabric"
.\gradlew.bat :26.2-fabric:jar

# 1.21.11 Fabric
$env:GRADLE_OPTS = "-Dru.vidtu.ias.only=1.21.11-fabric"
.\gradlew.bat :1.21.11-fabric:remapJar

# 1.8.9 Forge（独立模块 — 需要 JDK 8）
cd forge-1.8
.\gradlew.bat build
cd ..
```

构建生成的 jar 会出现在 `build\libs\` 中。

## 鸣谢

- 最初由 **VidTu**、**The_Fireplace** 和 IAS 贡献者开发。
- Cookie 认证分支、Localts 集成、Hypixel 检查以及 1.8.9 Forge 移植由 **naczo5** 完成。
- Checker 转储导入、实时会话切换、Hypixel IP 封禁保护和可选的 LiquidProxy 由 [**antlmao1337**](https://github.com/antlmao1337/Account-Switcher-with-Cookies) 提供。
- 配置文件管理、皮肤更新和多文件选择功能由 [**Articuling / xCheezie**](https://github.com/xCheezie) 提供。
- Hypixel 封禁检查逻辑改编自 cooldood-dev 的 [mcchecker](https://github.com/cooldood-dev/mcchecker)。

Microsoft 认证流程参考：[minecraft.wiki/Microsoft_authentication](https://minecraft.wiki/Microsoft_authentication)。
