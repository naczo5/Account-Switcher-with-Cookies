/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2026 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package ru.vidtu.ias.auth.microsoft;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.auth.microsoft.fields.DeviceAuth;
import ru.vidtu.ias.auth.microsoft.fields.MCProfile;
import ru.vidtu.ias.auth.microsoft.fields.MSTokens;
import ru.vidtu.ias.auth.microsoft.fields.XHashedToken;
import ru.vidtu.ias.utils.GSONUtils;
import ru.vidtu.ias.utils.exceptions.DevicePendingException;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Class for Microsoft authentication system.
 *
 * @author VidTu
 * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme">wiki.vg/Microsoft_Authentication_Scheme</a>
 */
public final class MSAuth {
    /**
     * Request client.
     */
    @NotNull
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(IAS.TIMEOUT)
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(IAS.executor())
            .build();

    /**
     * Request client with sync.
     */
    @NotNull
    private static final HttpClient CLIENT_SYNC = HttpClient.newBuilder()
            .connectTimeout(IAS.TIMEOUT)
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(Runnable::run)
            .build();

    /**
     * SISU entry point used by Minecraft web login with existing Microsoft session cookies.
     */
    @NotNull
    private static final String SISU_AUTH_URL = "https://sisu.xboxlive.com/connect/XboxLive/?state=login&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d&tid=896928775&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin&aid=1142970254";

    /**
     * Browser-like user agent for cookie-based SISU authentication.
     */
    @NotNull
    private static final String COOKIE_AUTH_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:146.0) Gecko/20100101 Firefox/146.0";

    @NotNull
    private static final String MINECRAFT_OAUTH_CLIENT_ID = "00000000402b5328";

    @NotNull
    private static final String MINECRAFT_OAUTH_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    /**
     * MCA token plus optional refresh from cookie-based import.
     *
     * @param mca     Minecraft access token
     * @param refresh Microsoft refresh token, may be empty for cookie-only accounts
     */
    public record CookieMcaResult(@NotNull String mca, @NotNull String refresh) {
    }

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    @Contract(value = "-> fail", pure = true)
    private MSAuth() {
        throw new AssertionError("No instances.");
    }

    /**
     * Requests the Device Auth Code. (DAC)
     *
     * @return Future that will complete with Device Auth Code (DAC) or exceptionally
     * @see <a href="https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code">Reference</a>
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<DeviceAuth> requestDac() {
        // Create the payload.
        String payload = "client_id=" + IAS.CLIENT_ID +
                "&scope=XboxLive.signin%20XboxLive.offline_access";

        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the tokens and return them.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return DeviceAuth.fromJson(json);
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to request Device Auth Code (DAC) from (" + response + " with " + response.headers() + "): " + response.body();
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets the Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens from the Device Auth Code. (DAC)
     * <p>
     * Unlike other methods in this class, this method <b>WILL BLOCK</b>.
     *
     * @param code Microsoft Authentication Code (MSAC; e.g. from user auth redirect)
     * @return Future that will complete with Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Microsoft_OAuth2_Flow">Reference</a>
     * @see #msrToMsaMsr(String)
     */
    @CheckReturnValue
    @NotNull
    public static MSTokens dacToMsaMsr(@NotNull String code) {
        // Create the payload.
        String payload = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                "&client_id=" + IAS.CLIENT_ID +
                "&device_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

        // Send the request.
        HttpResponse<String> response;
        try {
            response = CLIENT_SYNC.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                    .header("User-Agent", IAS.USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(IAS.TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build(), HttpResponse.BodyHandlers.ofString());
        } catch (Throwable t) {
            // Rethrow.
            throw new RuntimeException("Unable to send DAC request.", t);
        }

        // Process the response.
        try {
            // Check the code.
            int status = response.statusCode();
            if (status != HttpURLConnection.HTTP_OK) {
                // Check for auth pending.
                try {
                    JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                    String error = GSONUtils.getStringOrThrow(json, "error");

                    // Declined
                    if ("authorization_declined".equals(error)) {
                        throw new FriendlyException("Cancelled: " + json, "ias.error.cancel");
                    }

                    // Pending.
                    if ("authorization_pending".equals(error)) {
                        throw new DevicePendingException("Pending auth: " + json);
                    }

                    // Other.
                    throw new IllegalStateException("Not pending auth.");
                } catch (Throwable t) {
                    // Other error.
                    throw new IllegalArgumentException("Invalid status code: " + status, t);
                }
            }

            // Decode the tokens and return them.
            JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
            Objects.requireNonNull(json, "Response is null");
            return MSTokens.fromJson(json);
        } catch (Throwable t) {
            // Rethrow, trying to remove sensitive data.
            String message = "Unable to convert Device Auth Code (DAC) to Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens (" + response + " with " + response.headers() + "): " + response.body();
            message = message.replace(code, "[DAC]");
            throw new RuntimeException(message, t);
        }
    }

    /**
     * Gets the Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens from the Microsoft Authentication Code. (MSAC)
     *
     * @param code     Microsoft Authentication Code (MSAC; e.g. from user auth redirect)
     * @param redirect Redirect URL
     * @return Future that will complete with Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Microsoft_OAuth2_Flow">Reference</a>
     * @see #msrToMsaMsr(String)
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<MSTokens> msacToMsaMsr(@NotNull String code, @NotNull String redirect) {
        // Create the payload.
        String payload = "client_id=" + IAS.CLIENT_ID +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8) +
                "&scope=XboxLive.signin%20XboxLive.offline_access";

        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://login.live.com/oauth20_token.srf"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the tokens and return them.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return MSTokens.fromJson(json);
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to convert Microsoft Authentication Code (MSAC) to Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(code, "[MSAC]");
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets the Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens using Microsoft Refresh (MSR) token.
     *
     * @param refresh Microsoft Refresh (MSR) token (e.g. from {@link MSTokens#refresh()})
     * @return Future that will complete with Microsoft Access (MSA) and Microsoft Refresh (MSR) Tokens or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Microsoft_OAuth2_Flow">Reference</a>
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<MSTokens> msrToMsaMsr(@NotNull String refresh) {
        // Create the payload.
        String payload = "client_id=" + IAS.CLIENT_ID +
                "&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8) +
                "&grant_type=refresh_token" +
                "&scope=XboxLive.signin%20XboxLive.offline_access";

        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://login.live.com/oauth20_token.srf"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the tokens and return them.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return MSTokens.fromJson(json);
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to convert Microsoft Refresh (MSR) token to Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(refresh, "[MSR]");
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets Microsoft tokens from a Localts/Minecraft refresh token.
     *
     * @param refresh Microsoft refresh token exported by Localts
     * @return Future that will complete with Microsoft access and refresh tokens
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<MSTokens> minecraftRefreshToMsaMsr(@NotNull String refresh) {
        String payload = "client_id=" + MINECRAFT_OAUTH_CLIENT_ID +
                "&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8) +
                "&grant_type=refresh_token" +
                "&redirect_uri=" + URLEncoder.encode("https://login.live.com/oauth20_desktop.srf", StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode(MINECRAFT_OAUTH_SCOPE, StandardCharsets.UTF_8);

        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://login.live.com/oauth20_token.srf"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            try {
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + response.statusCode());
                }
                return MSTokens.fromJson(json);
            } catch (Throwable t) {
                String message = "Unable to convert Localts refresh token to Microsoft tokens (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(refresh, "[MSR]");
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets the Xbox Live (XBL) token from the Microsoft Access (MSA) token.
     *
     * @param authToken Microsoft Access (MSA) token (e.g. from {@link MSTokens#access()})
     * @return Future that will complete with an XBL token and a user hash or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_Xbox_Live">Reference</a>
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<XHashedToken> msaToXbl(@NotNull String authToken) {
        return msaToXbl(authToken, "d=");
    }

    /**
     * Gets the Xbox Live (XBL) token from the Microsoft Access (MSA) token using a specific RPS ticket prefix.
     *
     * @param authToken Microsoft Access (MSA) token
     * @param ticketPrefix RPS ticket prefix ({@code d=} for normal IAS tokens, {@code t=} for Localts/Minecraft tokens)
     * @return Future that will complete with an XBL token and a user hash or exceptionally
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<XHashedToken> msaToXbl(@NotNull String authToken, @NotNull String ticketPrefix) {
        // Create the payload.
        // This is ugly, but I won't create custom classes and serializers just for this.
        JsonObject request = new JsonObject();

        JsonObject requestProperties = new JsonObject();
        requestProperties.addProperty("AuthMethod", "RPS");
        requestProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        requestProperties.addProperty("RpsTicket", ticketPrefix + authToken);
        request.add("Properties", requestProperties);

        // We disable "HTTP -> HTTPS" inspection here, because it's not an actual URL,
        // but a payload parameter (possibly) required by the specification:
        // https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_Xbox_Live
        //noinspection HttpUrlsUsage
        request.addProperty("RelyingParty", "http://auth.xboxlive.com");

        request.addProperty("TokenType", "JWT");
        String payload = GSONUtils.GSON.toJson(request);

        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the XBL and return it.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return XHashedToken.fromJson(json);
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to convert Microsoft Access (MSA) token to Xbox Live (XBL) token (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(authToken, "[MSA]");
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets the Xbox Secure Token Service (XSTS) token from the Xbox Live (XBL) access token.
     *
     * @param xbl  Xbox Live (XBL) token (e.g. from {@link #msaToXbl(String)})
     * @param hash User hash to verify, {@code null} to skip verification (e.g. from {@link #msaToXbl(String)})
     * @return Future that will complete with an XSTS token and a user hash or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Obtain_XSTS_token_for_Minecraft">Reference</a>
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<XHashedToken> xblToXsts(@NotNull String xbl, @Nullable String hash) {
        // Create the payload.
        // This is ugly, but I won't create custom classes and serializers just for this.
        JsonObject request = new JsonObject();

        JsonObject requestProperties = new JsonObject();
        JsonArray requestUserTokens = new JsonArray();
        requestUserTokens.add(xbl);

        requestProperties.add("UserTokens", requestUserTokens);
        requestProperties.addProperty("SandboxId", "RETAIL");

        request.add("Properties", requestProperties);
        request.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        request.addProperty("TokenType", "JWT");
        String payload = GSONUtils.GSON.toJson(request);

        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();

                // 401 - special cases.
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    try {
                        JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                        long err = GSONUtils.getLongOrThrow(json, "XErr");
                        if (err == 2148916233L) {
                            throw new FriendlyException("XErr from 401 status: 2148916233 (No Xbox linked)", "ias.error.noXbox");
                        }
                        if (err == 2148916235L) {
                            throw new FriendlyException("XErr from 401 status: 2148916235 (Xbox not available)", "ias.error.xboxAvailable");
                        }
                        if (err == 2148916236L || err == 2148916237L || err == 2148916238L) {
                            throw new FriendlyException("XErr from 401 status: " + err + " (Non-adult)", "ias.error.xboxAdult");
                        }
                        throw new RuntimeException("Unknown XErr from 401 status: " + err);
                    } catch (Throwable t) {
                        // Rethrow.
                        throw new IllegalArgumentException("Invalid status code: 401", t);
                    }
                }

                // Other errors.
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the XSTS.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);

                // Validate it.
                Objects.requireNonNull(json, "Response is null");
                XHashedToken token = XHashedToken.fromJson(json);
                if (hash != null && !hash.equals(token.hash())) {
                    throw new IllegalStateException("Mismatching XBL and XSTS user hashes.");
                }

                // Return it.
                return token;
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to convert Xbox Live (XBL) token to Xbox Secure Token Service (XSTS) token (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(xbl, "[XBL]");
                if (hash != null) {
                    message = message.replace(hash, "[HASH]");
                }
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets the Minecraft Access (MCA) token from the Xbox Secure Token Service (XSTS) token and user hash.
     *
     * @param xsts Xbox Secure Token Service (XSTS) token (e.g. from {@link #xblToXsts(String, String)})
     * @param hash User hash (e.g. from {@link XHashedToken#hash()})
     * @return Future that will complete with an MCA token or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_Minecraft">Reference</a>
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<String> xstsToMca(@NotNull String xsts, @NotNull String hash) {
        // Create the payload.
        // This is ugly, but I won't create custom classes and serializers just for this.
        JsonObject request = new JsonObject();
        request.addProperty("identityToken", "XBL3.0 x=" + hash + ";" + xsts);
        String payload = GSONUtils.GSON.toJson(request);

        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(IAS.TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the token and return it.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return GSONUtils.getStringOrThrow(json, "access_token");
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to convert Xbox Secure Token Service (XSTS) token to Minecraft Access (MCA) token (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(xsts, "[XSTS]");
                message = message.replace(hash, "[HASH]");
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Gets the Minecraft Profile (MCP) from the Minecraft Access (MCA) token.
     *
     * @param access Minecraft Access (MCA) token (e.g. from {@link #xstsToMca(String, String)})
     * @return Future that will complete with an MCP or exceptionally
     * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme#Getting_the_profile">Reference</a>
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<MCProfile> mcaToMcp(@NotNull String access) {
        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Authorization", "Bearer " + access)
                .timeout(IAS.TIMEOUT)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            // Process the response.
            try {
                // Check the code.
                int status = response.statusCode();

                // Probable case - no profile linked. (no Minecraft account)
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw new FriendlyException("Profile 404", "ias.error.noProfile");
                }

                // Other errors.
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the profile and return it.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return MCProfile.fromJson(json);
            } catch (Throwable t) {
                // Rethrow, trying to remove sensitive data.
                String message = "Unable to convert Minecraft Access (MCA) token to Minecraft Profile (MCP) (" + response + " with " + response.headers() + "): " + response.body();
                message = message.replace(access, "[MCA]");
                throw new RuntimeException(message, t);
            }
        }, IAS.executor());
    }

    /**
     * Resolve Minecraft Profile (MCP) from name using Mojang API.
     *
     * @param name Player name
     * @return Future with resolved profile (or offline as fallback)
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<MCProfile> nameToMcp(@NotNull String name) {
        // Send the request.
        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .header("User-Agent", IAS.USER_AGENT)
                .timeout(IAS.TIMEOUT)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString()).thenApplyAsync(response -> {
            try {
                // Check the code.
                int status = response.statusCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalArgumentException("Invalid status code: " + status);
                }

                // Decode the profile and return it.
                JsonObject json = GSONUtils.GSON.fromJson(response.body(), JsonObject.class);
                Objects.requireNonNull(json, "Response is null");
                return MCProfile.fromJson(json);
            } catch (Throwable t) {
                // Rethrow.
                throw new RuntimeException("Unable to obtain Minecraft profile by name '" + name + "' (" + response + " with " + response.headers() + "): " + response.body(), t);
            }
        }, IAS.executor());
    }

    /**
     * Gets a Minecraft Access (MCA) token from browser session cookies.
     *
     * @param cookieHeader HTTP {@code Cookie} header value
     * @return Future that will complete with MCA and optional refresh token or exceptionally
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<CookieMcaResult> cookiesToMcaFromCookies(@NotNull String cookieHeader) {
        return cookiesToMcaViaSisu(cookieHeader)
                .thenApply(mca -> new CookieMcaResult(mca, ""));
    }

    /**
     * Gets a Minecraft Access (MCA) token from browser session cookies via Xbox SISU.
     *
     * @param cookieHeader HTTP {@code Cookie} header value
     * @return Future that will complete with an MCA token or exceptionally
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<String> cookiesToMcaViaSisu(@NotNull String cookieHeader) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String redirect1 = followSisuRedirect(SISU_AUTH_URL, null, 1);
                String redirect2 = followSisuRedirect(redirect1, cookieHeader, 2);
                String redirect3 = followSisuRedirect(redirect2, cookieHeader, 3);

                String encoded = extractSisuAccessToken(redirect3);
                if (encoded == null || encoded.isBlank()) {
                    throw new FriendlyException("No Xbox access token in SISU cookie auth response.", "ias.error.cookie.expired");
                }

                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                int rp = decoded.indexOf("\"rp://api.minecraftservices.com/\",");
                if (rp < 0) {
                    throw new FriendlyException("Unexpected SISU token payload.", "ias.error.cookie.expired");
                }
                String slice = decoded.substring(rp);

                int uhsMarker = slice.indexOf("{\"DisplayClaims\":{\"xui\":[{\"uhs\":\"");
                int tokenMarker = slice.indexOf("\"Token\":\"");
                if (uhsMarker < 0 || tokenMarker < 0) {
                    throw new FriendlyException("Unable to parse SISU XSTS payload.", "ias.error.cookie.expired");
                }

                String hash = slice.substring(uhsMarker + "{\"DisplayClaims\":{\"xui\":[{\"uhs\":\"".length());
                hash = hash.substring(0, hash.indexOf('"'));
                String xsts = slice.substring(tokenMarker + "\"Token\":\"".length());
                xsts = xsts.substring(0, xsts.indexOf('"'));

                return hash + '\0' + xsts;
            } catch (FriendlyException e) {
                throw e;
            } catch (Throwable t) {
                throw new FriendlyException("Unable to exchange cookies for tokens via SISU.", t, "ias.error.cookie.expired");
            }
        }, IAS.executor()).thenComposeAsync(pair -> {
            int sep = pair.indexOf('\0');
            return xstsToMca(pair.substring(sep + 1), pair.substring(0, sep));
        }, IAS.executor());
    }

    @NotNull
    private static String followSisuRedirect(@NotNull String url, @Nullable String cookieHeader, int step) throws Exception {
        URI current = URI.create(url.replace(" ", "%20"));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(current)
                .header("User-Agent", COOKIE_AUTH_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.8")
                .timeout(IAS.TIMEOUT)
                .GET();
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            builder.header("Cookie", cookieHeader);
        }

        HttpResponse<Void> response = CLIENT_SYNC.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != HttpURLConnection.HTTP_MOVED_TEMP) {
            throw new FriendlyException("Unexpected SISU redirect status: " + response.statusCode(), "ias.error.cookie.expired");
        }

        String location = response.headers().firstValue("location").orElse(null);
        if (location == null || location.isBlank()) {
            throw new FriendlyException("SISU redirect missing Location header.", "ias.error.cookie.expired");
        }

        URI next = URI.create(location.replace(" ", "%20"));
        if (!next.isAbsolute()) {
            next = current.resolve(next);
        }
        return next.toString();
    }

    /**
     * Extracts the {@code accessToken} query parameter from a SISU redirect Location URL.
     */
    @Nullable
    private static String extractSisuAccessToken(@NotNull String url) {
        int idx = url.indexOf("accessToken=");
        if (idx < 0) {
            return null;
        }
        String raw = url.substring(idx + "accessToken=".length());
        int amp = raw.indexOf('&');
        if (amp >= 0) {
            raw = raw.substring(0, amp);
        }
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            raw = raw.substring(0, hash);
        }
        return urlDecode(raw);
    }

    /**
     * Gets Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens from browser session cookies.
     *
     * @param cookieHeader HTTP {@code Cookie} header value
     * @return Future that will complete with MSA/MSR tokens or exceptionally
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<MSTokens> cookiesToMsaMsr(@NotNull String cookieHeader) {
        return cookiesToMsaMsr(cookieHeader, IAS.CLIENT_ID,
                "XboxLive.signin%20XboxLive.offline_access",
                "https://login.live.com/oauth20_desktop.srf").exceptionallyCompose(t -> {
            return cookiesToMsaMsr(cookieHeader, "00000000402b5328",
                        "service::user.auth.xboxlive.com::MBI_SSL",
                        "https://login.live.com/oauth20_desktop.srf");
        }).exceptionallyAsync(t -> {
            FriendlyException friendly = FriendlyException.friendlyInChain(t);
            if (friendly != null) {
                throw friendly;
            }
            throw new FriendlyException("Unable to exchange cookies for tokens.", t, "ias.error.cookie.expired");
        }, IAS.executor());
    }

    /**
     * Attempts cookie-based OAuth implicit flow with the given client parameters.
     */
    @CheckReturnValue
    @NotNull
    private static CompletableFuture<MSTokens> cookiesToMsaMsr(@NotNull String cookieHeader, @NotNull String clientId,
            @NotNull String scope, @NotNull String redirectUri) {
        String url = "https://login.live.com/oauth20_authorize.srf" +
                "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_type=token" +
                "&scope=" + scope +
                "&prompt=none";

        return CLIENT.sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", IAS.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cookie", cookieHeader)
                .timeout(IAS.TIMEOUT)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString()).thenComposeAsync(response -> {
            try {
                return CompletableFuture.completedFuture(followCookieRedirects(cookieHeader, response, 0));
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }, IAS.executor());
    }

    /**
     * Follows OAuth redirects until tokens appear in the URL fragment or redirects are exhausted.
     */
    @NotNull
    private static MSTokens followCookieRedirects(@NotNull String cookieHeader, @NotNull HttpResponse<String> response, int depth)
            throws Exception {
        String finalUrl = response.uri().toString();
        int hash = finalUrl.indexOf('#');
        String location = response.headers().firstValue("location").orElse(null);
        if (depth > 10) {
            throw new FriendlyException("Too many OAuth redirects.", "ias.error.cookie.expired");
        }

        if (hash >= 0) {
            Map<String, String> fragment = parseQuery(finalUrl.substring(hash + 1));
            String access = fragment.get("access_token");
            String refresh = fragment.get("refresh_token");
            if (access != null && !access.isBlank()) {
                if (refresh == null || refresh.isBlank()) {
                    refresh = "";
                }
                return new MSTokens(access, refresh);
            }
        }
        if (location == null) {
            throw new FriendlyException("No OAuth token in cookie auth response.", "ias.error.cookie.expired");
        }

        if (location.indexOf('#') >= 0) {
            Map<String, String> fragment = parseQuery(location.substring(location.indexOf('#') + 1));
            String access = fragment.get("access_token");
            String refresh = fragment.get("refresh_token");
            if (access != null && !access.isBlank()) {
                if (refresh == null || refresh.isBlank()) {
                    refresh = "";
                }
                return new MSTokens(access, refresh);
            }
        }

        URI next = URI.create(location);
        if (!next.isAbsolute()) {
            next = response.uri().resolve(location);
        }

        HttpResponse<String> nextResponse = CLIENT_SYNC.send(HttpRequest.newBuilder()
                .uri(next)
                .header("User-Agent", IAS.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cookie", cookieHeader)
                .timeout(IAS.TIMEOUT)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        return followCookieRedirects(cookieHeader, nextResponse, depth + 1);
    }

    /**
     * Parses URL query or fragment parameters.
     */
    @NotNull
    private static Map<String, String> parseQuery(@NotNull String query) {
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String key = urlDecode(pair.substring(0, eq));
            String value = urlDecode(pair.substring(eq + 1));
            map.put(key, value);
        }
        return map;
    }

    @NotNull
    private static String urlDecode(@NotNull String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
