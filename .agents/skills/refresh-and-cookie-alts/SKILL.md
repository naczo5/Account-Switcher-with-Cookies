---
name: refresh-and-cookie-alts
description: >-
  Comprehensive reference and guide for Microsoft refresh tokens (Localts) and Netscape cookie alts
  in Minecraft and In-Game Account Switcher (IAS). Use this skill when inspecting, parsing, importing,
  or debugging Minecraft alt accounts, authentication flows (SISU, OAuth2), and cookie file formats.
---

# Microsoft Refresh Tokens & Cookie Alts Reference

This skill provides an accurate technical guide to **Refresh Tokens** ("Refresh Alts") and **Cookie Alts** ("Cookie Alts / Netscape Cookies") as used in Minecraft authentication and the In-Game Account Switcher (IAS) mod.

---

## 1. Quick Comparison

| Feature | Refresh Token ("Refresh Alt" / Localts) | Cookie Alt ("Cookie Alt" / Netscape) |
| :--- | :--- | :--- |
| **Origin** | OAuth 2.0 authorization refresh token from Microsoft identity | Browser session export from `login.live.com` / `live.com` |
| **Format** | Single token string, `<username>:<token>`, or JSON (`{"mcToken": "..."}`) | Tab-delimited (TSV) Netscape 7-column format or HTTP `Cookie:` header |
| **Key Identifier** | Starts with `M.C` (e.g., `M.C512_BAY...`, `M.C508_BL2...`) | Contains `__Host-MSAAUTHP` and/or `__Host-MSAAUTH` |
| **Length** | Typically 300–450+ characters | Multi-line file (10–20+ lines, several kilobytes) |
| **Auth Flow** | Direct OAuth 2.0 `refresh_token` grant to `login.live.com/oauth20_token.srf` | Session cookie emulation via direct OAuth code grant or Xbox Live SISU redirect chain |
| **Longevity** | High; valid indefinitely until revoked (password change, session revoke) | Medium/Fragile; expires with web session or web logout |
| **IAS Auto-Upgrade** | Already in optimal refresh token format | IAS attempts direct OAuth on import to extract and save a persistent `M.C` refresh token |

---

## 2. Refresh Tokens ("Refresh Alts" / Localts)

### 2.1 Overview & Architecture
A **Refresh Token** (often termed a "refresh alt" in alt generator communities like Localts) is a persistent Microsoft OAuth 2.0 refresh credential. It is created when an application or service authenticates through Microsoft's Identity Platform (`login.live.com`) requesting offline access scopes (`XboxLive.offline_access` or `service::user.auth.xboxlive.com::MBI_SSL`).

Because it is a standard OAuth refresh token, it can be exchanged directly for new session access tokens without requiring browser interaction, passwords, or two-factor prompts.

### 2.2 Token Structure & Characteristics
- **Prefix**: Almost always starts with `M.C` (such as `M.C512_BAY.0.U.MsaArtifacts...` or `M.C508_BL2...`).
- **Character Set**: Base64URL-like characters mixed with special Microsoft token delimiters, including asterisks (`*`), exclamation marks (`!`), periods (`.`), and hyphens (`-`).
- **Length**: Usually between 300 and 450 characters.

### 2.3 Common Formats in the Wild
1. **Direct String (Bare Token)**:
   Pasted directly into the mod or client:
   ```text
   M.C000_EXAMPLE.0.U.MsaArtifacts.THIS-IS-A-FAKE-PLACEHOLDER...
   ```
2. **Localts Export File**:
   Downloaded from alt vendor services (e.g. Localts). Typically contains a service greeting header followed by `username:token`:
   ```text
   Thanks for choosing Localts!
   SamplePlayer:M.C000_EXAMPLE.0.U.MsaArtifacts.THIS-IS-A-FAKE-PLACEHOLDER...
   ```
3. **JSON Token Export**:
   Exported by various bot tools or custom launchers:
   ```json
   {
     "mcToken": "M.C000_EXAMPLE.0.U.MsaArtifacts.THIS-IS-A-FAKE-PLACEHOLDER..."
   }
   ```

### 2.4 How IAS Authenticates Refresh Tokens
In IAS (`ru.vidtu.ias.auth.TokenImporter` and `ru.vidtu.ias.auth.microsoft.MSAuth`):
1. **Extraction & Normalization**:
   - `TokenImporter.extractValues()` strips header lines (e.g., `Thanks for choosing Localts!`).
   - Strips the `username:` prefix if present.
   - Cleans leading/trailing quotes, carriage returns, and whitespace.
2. **OAuth Exchange**:
   - Sends a `POST` request to `https://login.live.com/oauth20_token.srf` with:
     - `client_id`: `00000000402b5328` (Minecraft OAuth Client ID)
     - `grant_type`: `refresh_token`
     - `refresh_token`: `<M.C...>`
     - `scope`: `service::user.auth.xboxlive.com::MBI_SSL`
     - `redirect_uri`: `https://login.live.com/oauth20_desktop.srf`
   - Returns a fresh **Microsoft Access (MSA)** token and a updated **Microsoft Refresh (MSR)** token.
3. **Xbox Live & Minecraft Authentication Chain**:
   - `msaToXbl`: MSA token exchanged for **Xbox Live (XBL)** token via `user.auth.xboxlive.com/user/authenticate` with RPS ticket prefix `t=`.
   - `xblToXsts`: XBL token exchanged for **Xbox Secure Token Service (XSTS)** token via `xsts.auth.xboxlive.com/xsts/authorize` targeting `rp://api.minecraftservices.com/`.
   - `xstsToMca`: XSTS token and user hash (`uhs`) exchanged for **Minecraft Access (MCA)** token via `api.minecraftservices.com/authentication/login_with_xbox`.
   - `mcaToMcp`: MCA token used to query `api.minecraftservices.com/minecraft/profile` to get player UUID, profile name, and skins.
4. **Storage**:
   - Encrypted with AES / configured password crypt and saved in `accounts.json`.

### 2.5 Example: Refresh Token (placeholder only)

See file: [refresh-token-direct.txt](./examples/refresh-token-direct.txt) or [refresh-token-localts.txt](./examples/refresh-token-localts.txt).

```text
Thanks for choosing Localts!
SamplePlayer:M.C000_EXAMPLE.0.U.MsaArtifacts.THIS-IS-A-FAKE-PLACEHOLDER-NOT-A-REAL-TOKEN
```

---

## 3. Cookie Alts (Netscape Cookie Jar Format)

### 3.1 Overview & Architecture
A **Cookie Alt** is an export of active browser session cookies captured from an authenticated web session on Microsoft (`login.live.com`). Instead of presenting an API token, the client presents the exact same cookies that a web browser would send when visiting Microsoft and Xbox Live web pages.

### 3.2 Netscape Format Structure
Cookie alts use the standard 7-column Netscape HTTP Cookie specification separated by **tab (`\t`)** characters:
```text
<domain>	<include_subdomains>	<path>	<secure>	<expiration>	<name>	<value>
```
1. `domain`: The host or domain where the cookie applies (e.g. `login.live.com` or `.login.live.com`).
2. `include_subdomains`: `TRUE` if all subdomains can access it, `FALSE` if restricted to exact domain.
3. `path`: URL path (typically `/`).
4. `secure`: `TRUE` if cookie is transmitted only over HTTPS.
5. `expiration`: UNIX epoch timestamp (seconds) when cookie expires (e.g. `3784011825` for distant future / session).
6. `name`: Cookie key.
7. `value`: Cookie payload.

### 3.3 Critical & Required Cookies
For IAS to authenticate a cookie alt, the file must contain the Microsoft session authentication cookies:
- **`__Host-MSAAUTHP`** or **`__Host-MSAAUTH`** (*Mandatory*):
  - Domain: `login.live.com` (exact match, flag `FALSE`).
  - Contains the core MSA authenticated session artifact (e.g. `11-M.C508_BL2...`).
- **`MSPPre`** / **`MSPCID`** / **`MSPRequ`**: User pre-auth identity, client ID, and request state.
- **`OParams`**: Encrypted OAuth flow parameters and session claims.
- **`SDIDC`**: Microsoft session device identifier.
- **`JSH`** / **`JSHP`**: User account hints, display names, and identity hashes.
- **`uaid`**: User agent / interaction session correlation ID.
- **`_abck`** / **`bm_sz`** (*Optional*): Akamai anti-bot cookies for Xbox Live endpoints.

### 3.4 How IAS Authenticates Cookie Alts
In IAS (`ru.vidtu.ias.auth.cookie.CookieParser` and `ru.vidtu.ias.screen.CookiePopupScreen`):
1. **Parsing & Validation**:
   - `CookieParser.fromText()` verifies tab separation (or auto-converts space-aligned lines).
   - Validates that `__Host-MSAAUTHP` or `__Host-MSAAUTH` is present; otherwise throws `ias.error.cookie.invalid`.
2. **Two-Stage Authentication Strategy**:
   - **Stage 1: Direct OAuth Code Exchange**:
     - IAS builds the cookie header and queries `https://login.live.com/oauth20_authorize.srf` with Minecraft's client ID.
     - If the web session is sufficiently authenticated, Microsoft redirects with an authorization `code`.
     - IAS immediately trades this code for a full **`M.C...` Microsoft Refresh Token**.
     - *Benefit*: This upgrades the fragile cookie alt into a permanent refresh token!
   - **Stage 2: SISU Redirect Chain Fallback**:
     - If direct OAuth requires interaction or consent, IAS switches to Microsoft's SISU SSO flow (`https://sisu.xboxlive.com/connect/XboxLive/?...`).
     - Follows up to 8 HTTP 302 redirects with a realistic browser `User-Agent`.
     - Reads the `accessToken` redirect parameter from the final hop back to `minecraft.net`.
     - Decodes the base64 payload to extract user hash (`uhs`) and XSTS token, then calls `login_with_xbox`.

### 3.5 Example: Cookie Alt (Netscape TSV, placeholder only)

See file: [cookie-alt-netscape.txt](./examples/cookie-alt-netscape.txt).

```tsv
.live.com	TRUE	/	TRUE	3784011825	PPLState	1
.login.live.com	TRUE	/	TRUE	3784011825	MSPPre	user12345%40example.com
login.live.com	FALSE	/	TRUE	3784011825	__Host-MSAAUTH	11
login.live.com	FALSE	/	TRUE	3784011825	__Host-MSAAUTHP	11-M.C000_EXAMPLE.FAKE-PLACEHOLDER-NOT-A-REAL-COOKIE
```

---

## 4. In-Game Account Switcher (IAS) Handling & Workflows

### 4.1 GUI Import Options
In the IAS GUI (`Cookie / Token` button):
- **File Mode**: Enter path directly or click `...` to invoke the native file dialog.
- **Batch Multi-File**: Selecting multiple files automatically queues them.
- **Paste Mode**: Click `Paste` to paste raw text from clipboard (handles single bare tokens, Localts text, multiple lines of tokens, or Netscape cookie files).

### 4.2 Rate Limiting & Batch Queue
When importing multiple cookie files or tokens:
- IAS enforces a **6,000 ms (6s) interval** between accounts (`MULTI_COOKIE_IMPORT_DELAY_MS`) to prevent Microsoft/Minecraft API rate-limits.
- If HTTP `429 Too Many Requests` is encountered, IAS waits **30,000 ms (30s)** (`MULTI_COOKIE_RATE_LIMIT_DELAY_MS`) and retries up to 2 times.

### 4.3 Troubleshooting Common Errors

| Error Key | Cause | Resolution |
| :--- | :--- | :--- |
| `ias.error.cookie.invalid` | Malformed file or missing required `__Host-MSAAUTHP` / `__Host-MSAAUTH` cookie. | Check that the cookie export came from `login.live.com` while logged in. |
| `ias.error.cookie.expired` | Session cookies timed out, logged out, or revoked by Microsoft. | Re-export fresh cookies from the browser session. |
| `ias.error.cookie.file` | Path does not exist or file permissions prevent reading. | Verify file path or paste contents directly into the mod. |
| `ias.error.rateLimited` | Too many requests to Microsoft or Minecraft auth endpoints in a short time. | Wait 30–60 seconds before retrying import. |
| `ias.error.noProfile` | Microsoft account authenticated, but has no Minecraft license/profile. | Ensure Minecraft Java Edition has been purchased and an in-game name chosen. |
| `ias.error.xboxAdult` | Microsoft account is registered as child without parental consent. | Adjust Microsoft family / age settings for Xbox Live. |
