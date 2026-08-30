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

package ru.vidtu.ias.account;

import com.google.errorprone.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.auth.LoginData;
import ru.vidtu.ias.auth.handlers.LoginHandler;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.auth.microsoft.fields.MSTokens;
import ru.vidtu.ias.auth.microsoft.fields.XHashedToken;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.utils.Holder;
import ru.vidtu.ias.utils.IUtils;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Encrypted Microsoft account instance.
 *
 * @author VidTu
 */
public final class MicrosoftAccount implements Account {
    /**
     * Initializing login.
     */
    @NotNull
    public static final String INITIALIZING = "ias.login.initializing";

    /**
     * Starting server.
     */
    @NotNull
    public static final String SERVER = "ias.login.server";

    /**
     * Link has been open in browser.
     */
    @NotNull
    public static final String BROWSER = "ias.login.link";

    /**
     * Link has been open in browser.
     */
    @NotNull
    public static final String CLIENT_BROWSER = "ias.login.linkClient";

    /**
     * Processing response.
     */
    @NotNull
    public static final String PROCESSING = "ias.login.processing";

    /**
     * Decrypting tokens.
     */
    @NotNull
    public static final String DECRYPTING = "ias.login.decrypting";

    /**
     * Encrypting tokens.
     */
    @NotNull
    public static final String ENCRYPTING = "ias.login.encrypting";

    /**
     * Creating services.
     */
    @NotNull
    public static final String SERVICES = "ias.login.services";

    /**
     * Converting Microsoft Authentication Code (MSAC) to Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens.
     */
    @NotNull
    public static final String MSAC_TO_MSA_MSR = "ias.login.msacToMsaMsr";

    /**
     * Converting Microsoft Refresh (MSR) token to Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens.
     */
    @NotNull
    public static final String MSR_TO_MSA_MSR = "ias.login.msrToMsaMsr";

    /**
     * Converting Microsoft Access (MSA) token to Xbox Live (XBL) token.
     */
    @NotNull
    public static final String MSA_TO_XBL = "ias.login.msaToXbl";

    /**
     * Converting Xbox Live (XBL) token to Xbox Secure Token Service (XSTS) token.
     */
    @NotNull
    public static final String XBL_TO_XSTS = "ias.login.xblToXsts";

    /**
     * Converting Xbox Secure Token Service (XSTS) token to Minecraft Access (MCA) token.
     */
    @NotNull
    public static final String XSTS_TO_MCA = "ias.login.xstsToMca";

    /**
     * Converting Minecraft Access (MCA) token to Minecraft Profile. (MCP)
     */
    @NotNull
    public static final String MCA_TO_MCP = "ias.login.mcaToMcp";

    /**
     * Converting cookies to Microsoft Access (MSA) and Microsoft Refresh (MSR) tokens.
     */
    @NotNull
    public static final String COOKIES_TO_MSA_MSR = "ias.login.cookiesToMsaMsr";

    /**
     * Finalizing login.
     */
    @NotNull
    public static final String FINALIZING = "ias.login.finalizing";

    /**
     * Logger for this class.
     */
    @NotNull
    public static final Logger LOGGER = LoggerFactory.getLogger("IAS/MicrosoftAccount");

    /**
     * Marker for cookie-backed accounts where the stored refresh field contains a cookie header.
     */
    @NotNull
    public static final String COOKIE_HEADER_PREFIX = "ias:cookie-header:";

    /**
     * Whether the account is insecurely stored.
     */
    private final boolean insecure;

    /**
     * Account UUID.
     */
    @NotNull
    private UUID uuid;

    /**
     * Account name.
     */
    @NotNull
    private String name;

    /**
     * Encrypted account data.
     */
    private byte @NotNull [] data;

    /**
     * Creates a new Microsoft account.
     *
     * @param insecure Whether the account is insecurely stored
     * @param uuid     Account UUID
     * @param name     Account name
     * @param data     Encrypted account data
     */
    @Contract(pure = true)
    public MicrosoftAccount(boolean insecure, @NotNull UUID uuid, @NotNull String name, byte @NotNull [] data) {
        this.insecure = insecure;
        this.uuid = uuid;
        this.name = name;
        this.data = data.clone();
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public String type() {
        return "ias:microsoft_v1";
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public String typeTipKey() {
        return "ias.accounts.tip.type.microsoft";
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public UUID uuid() {
        return this.uuid;
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public String name() {
        return this.name;
    }

    @Contract(value = "-> true", pure = true)
    @Override
    public boolean canLogin() {
        // Online accounts should be loggable.
        return true;
    }

    @Contract(pure = true)
    @Override
    public boolean insecure() {
        return this.insecure;
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public UUID skin() {
        return this.uuid;
    }

    /**
     * Updates the stored profile identity after Minecraft Services confirms a change.
     *
     * @param uuid New account UUID
     * @param name New account name
     */
    public void updateProfile(@NotNull UUID uuid, @NotNull String name) {
        this.uuid = uuid;
        this.name = name;
    }

    /**
     * Creates the stored refresh marker used for cookie-backed accounts.
     *
     * @param cookieHeader HTTP cookie header used for refresh
     * @return Stored refresh marker
     */
    @Contract(pure = true)
    @NotNull
    public static String cookieRefresh(@NotNull String cookieHeader) {
        return COOKIE_HEADER_PREFIX + cookieHeader;
    }

    @Override
    public void login(@NotNull LoginHandler handler, Runnable onComplete) {
        try {
            // Skip if cancelled.
            if (handler.cancelled()) return;

            // Log it and display progress.
            LOGGER.info("IAS: Logging (Microsoft) as {}/{}", this.uuid, this.name);
            handler.stage(INITIALIZING);

            // Value holders.
            Holder<Crypt> crypt = new Holder<>();
            Holder<String> access = new Holder<>();
            Holder<String> refresh = new Holder<>();
            Holder<String> cookieHeader = new Holder<>("");
            Holder<Boolean> recrypt = new Holder<>(false);

            // Read the crypt.
            CompletableFuture<Crypt> future;
            byte[] crypted;
            try (ByteArrayInputStream byteIn = new ByteArrayInputStream(this.data);
                 DataInputStream in = new DataInputStream(byteIn)) {
                // Read and process the crypt.
                future = Crypt.readType(in, handler::password);

                // Crypted data.
                crypted = in.readAllBytes();
            }

            // Decrypt.
            future.thenApplyAsync(value -> {
                // Skip if cancelled.
                if (value == null || handler.cancelled()) return null;

                // Log it and display progress.
                LOGGER.info("IAS: Decrypting tokens...");
                handler.stage(DECRYPTING);

                // Decrypt.
                byte[] data = value.decrypt(crypted);

                // Migrate and set the crypt.
                Crypt migrate = value.migrate();
                if (migrate != null) {
                    crypt.set(migrate);
                    recrypt.set(true);
                } else {
                    crypt.set(value);
                }

                // Continue.
                return data;
            }, IAS.executor()).thenApplyAsync(value -> {
                // Skip if cancelled.
                if (value == null || handler.cancelled()) return false;

                // Read the decrypted data into tokens.
                try (ByteArrayInputStream byteIn = new ByteArrayInputStream(value);
                     DataInputStream in = new DataInputStream(byteIn)) {
                    // Read the access token.
                    access.set(in.readUTF());

                    // Read the refresh token, or the saved cookie refresh marker for cookie-backed imports.
                    String refreshToken = in.readUTF();
                    refresh.set(refreshToken);
                    if (refreshToken.startsWith(COOKIE_HEADER_PREFIX)) {
                        cookieHeader.set(refreshToken.substring(COOKIE_HEADER_PREFIX.length()));
                    }

                    // Verify the buffer.
                    int available = in.available();
                    if (available != 0) {
                        throw new IOException("Leftover: " + in.available());
                    }

                    // Return continue.
                    return true;
                } catch (Throwable t) {
                    throw new RuntimeException("Unable to read the tokens.", t);
                }
            }, IAS.executor()).thenComposeAsync(value -> {
                // Skip if cancelled.
                if (!value || handler.cancelled()) return CompletableFuture.completedFuture(null);

                // If the account has stored session cookies, exchange them for a persistent refresh token.
                String cookie = cookieHeader.get();
                if (cookie != null && !cookie.isBlank()) {
                    LOGGER.info("IAS: Stored cookie session found. Converting cookies to MCA/refresh token...");
                    handler.stage(COOKIES_TO_MSA_MSR);
                    recrypt.set(true);

                    return MSAuth.cookiesToMcaFromCookies(cookie).thenComposeAsync(result -> {
                        if (result == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

                        access.set(result.mca());
                        String nextRefresh = result.refresh();
                        refresh.set(nextRefresh == null || nextRefresh.isBlank() ? cookieRefresh(cookie) : nextRefresh);

                        LOGGER.info("IAS: Converting MCA to MCP... (cookie refreshed)");
                        handler.stage(MCA_TO_MCP);

                        return MSAuth.mcaToMcp(result.mca());
                    }, IAS.executor()).exceptionallyComposeAsync(cookieErr -> {
                        String storedAccess = access.get();
                        if (storedAccess != null && !storedAccess.isBlank()) {
                            return MSAuth.mcaToMcp(storedAccess).exceptionallyAsync(orig -> {
                                if (IUtils.anyInCausalChain(cookieErr, err -> err instanceof UnresolvedAddressException || err instanceof NoRouteToHostException || err instanceof HttpTimeoutException || err instanceof ConnectException)) {
                                    throw new FriendlyException("Unable to connect to cookie auth servers.", cookieErr, "ias.error.connect");
                                }
                                FriendlyException friendly = FriendlyException.friendlyInChain(cookieErr);
                                if (friendly != null) {
                                    throw friendly;
                                }
                                throw new RuntimeException("Unable to authenticate cookie session.", cookieErr);
                            }, IAS.executor());
                        }
                        if (IUtils.anyInCausalChain(cookieErr, err -> err instanceof UnresolvedAddressException || err instanceof NoRouteToHostException || err instanceof HttpTimeoutException || err instanceof ConnectException)) {
                            throw new FriendlyException("Unable to connect to cookie auth servers.", cookieErr, "ias.error.connect");
                        }
                        FriendlyException friendly = FriendlyException.friendlyInChain(cookieErr);
                        if (friendly != null) {
                            throw friendly;
                        }
                        throw new RuntimeException("Unable to authenticate cookie session.", cookieErr);
                    }, IAS.executor());
                }

                // Log it and display progress.
                LOGGER.info("IAS: Converting MCA to MCP... (stored)");
                handler.stage(MCA_TO_MCP);

                // Convert MCA to MCP.
                return MSAuth.mcaToMcp(access.get()).exceptionallyComposeAsync(original -> {
                    // Skip if cancelled.
                    if (handler.cancelled()) return CompletableFuture.completedFuture(null);

                    String refreshToken = refresh.get();
                    if (refreshToken == null || refreshToken.isBlank()) {
                        throw new FriendlyException("Session expired. Re-import your account to log in again.", original, "ias.error.cookie.expired");
                    }

                    // Log it and display progress.
                    LOGGER.warn("IAS: MCA is (probably) expired. Refreshing...");
                    LOGGER.info("IAS: Converting MSR to MSA/MSR...");
                    handler.stage(MSR_TO_MSA_MSR);

                    // Require recrypting data.
                    recrypt.set(true);

                    // Convert MSR to MSA/MSR, then MSA to XBL.
                    // Localts / Minecraft launcher tokens (M.C...) must use the launcher OAuth client + t=;
                    // IAS-copied refresh tokens use the IAS client + d=.
                    return refreshToXbl(refreshToken, refresh, handler).thenComposeAsync(xbl -> {
                        // Skip if cancelled.
                        if (xbl == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

                        // Log it and display progress.
                        LOGGER.info("IAS: Converting XBL to XSTS...");
                        handler.stage(XBL_TO_XSTS);

                        // Convert XBL to XSTS.
                        return MSAuth.xblToXsts(xbl.token(), xbl.hash());
                    }, IAS.executor()).thenComposeAsync(xsts -> {
                        // Skip if cancelled.
                        if (xsts == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

                        // Log it and display progress.
                        LOGGER.info("IAS: Converting XSTS to MCA...");
                        handler.stage(XSTS_TO_MCA);

                        // Convert XSTS to MCA.
                        return MSAuth.xstsToMca(xsts.token(), xsts.hash());
                    }, IAS.executor()).thenComposeAsync(token -> {
                        // Skip if cancelled.
                        if (token == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

                        // Update the access token.
                        access.set(token);

                        // Log it and display progress.
                        LOGGER.info("IAS: Converting MCA TO MCP... (refreshed)");
                        handler.stage(MCA_TO_MCP);

                        // Convert MCA to MCP.
                        return MSAuth.mcaToMcp(token);
                    }, IAS.executor()).exceptionallyAsync(t -> {
                        t.addSuppressed(original);

                        // Probable case - no internet connection.
                        if (IUtils.anyInCausalChain(t, err -> err instanceof UnresolvedAddressException || err instanceof NoRouteToHostException || err instanceof HttpTimeoutException || err instanceof ConnectException)) {
                            throw new FriendlyException("Unable to connect to MSR servers.", t, "ias.error.connect");
                        }

                        // Handle error.
                        throw new RuntimeException("Unable to perform MSR auth.", t);
                    }, IAS.executor()).exceptionallyAsync(t -> {
                        // Rethrow. (adding original)
                        t.addSuppressed(original);
                        throw new RuntimeException("Unable to refresh MSR.", t);
                    }, IAS.executor());
                }, IAS.executor());
            }, IAS.executor()).thenAcceptAsync(profile -> {
                // Skip if cancelled.
                if (profile == null || handler.cancelled()) return;

                // Re-encrypt if required.
                boolean saveStorage = false;
                if (recrypt.get()) {
                    // Log it and display progress.
                    LOGGER.info("IAS: Encrypting tokens...");
                    handler.stage(ENCRYPTING);

                    // Write the tokens.
                    String accessMem = access.get();
                    String refreshMem = refresh.get();
                    byte[] unencrypted;
                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream(accessMem.length() + refreshMem.length() + 4);
                         DataOutputStream out = new DataOutputStream(byteOut)) {
                        // Write the access token.
                        out.writeUTF(accessMem);

                        // Write the refresh token.
                        out.writeUTF(refreshMem);

                        // Flush it.
                        unencrypted = byteOut.toByteArray();
                    } catch (Throwable t) {
                        throw new RuntimeException("Unable to write the tokens.", t);
                    }

                    // Encrypt the tokens.
                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream(unencrypted.length + 32);
                         DataOutputStream out = new DataOutputStream(byteOut)) {
                        // Encrypt.
                        Crypt val = crypt.get();
                        byte[] encrypted = val.encrypt(unencrypted);

                        // Write data.
                        out.writeUTF(val.type());
                        out.write(encrypted);

                        // Flush it.
                        this.data = byteOut.toByteArray();
                        saveStorage = true;
                    } catch (Throwable t) {
                        throw new RuntimeException("Unable to encrypt the tokens.", t);
                    }
                }

                // Authentication successful, refresh the profile.
                UUID uuid = profile.uuid();
                String name = profile.name();
                if (!this.uuid.equals(uuid) || !this.name.equals(name)) {
                    this.uuid = profile.uuid();
                    this.name = profile.name();
                    saveStorage = true;
                }

                // Log it and display progress.
                LOGGER.info("IAS: Successful login as {}", profile);
                handler.stage(FINALIZING);

                // Expose the real refresh token, if any. Cookie-backed accounts may only have
                // an internal cookie-header marker stored (see COOKIE_HEADER_PREFIX), which is
                // not a usable Microsoft refresh token on its own, so it's not exposed here.
                String refreshValue = refresh.get();
                String exposedRefresh = (refreshValue != null && !refreshValue.isBlank() && !refreshValue.startsWith(COOKIE_HEADER_PREFIX))
                        ? refreshValue : null;

                // Create and return the data.
                LoginData login = new LoginData(this.name, this.uuid, access.get(), exposedRefresh, true);
                handler.success(login, saveStorage);

                // Run onComplete.
                if (onComplete != null) onComplete.run();
            }, IAS.executor()).exceptionallyAsync(t -> {
                // Handle error.
                handler.error(new RuntimeException("Unable to login as MS account", t));

                // Return null.
                return null;
            }, IAS.executor());
        } catch (Throwable t) {
            // Handle.
            handler.error(new RuntimeException("Unable to begin MS auth.", t));
        }
    }

    /**
     * Exchanges a stored Microsoft refresh token for an Xbox Live token.
     * Localts {@code M.C...} tokens use the official Minecraft OAuth client ({@code t=});
     * IAS-copied refresh tokens use the IAS client ({@code d=}). If the first client
     * rejects the token, the other client is tried.
     *
     * @param refreshToken Stored refresh token
     * @param refresh      Holder updated with the rotated refresh token
     * @param handler      Login handler
     * @return Future that completes with the XBL token, or {@code null} if cancelled
     */
    @CheckReturnValue
    @NotNull
    private static CompletableFuture<XHashedToken> refreshToXbl(@NotNull String refreshToken, @NotNull Holder<String> refresh, @NotNull LoginHandler handler) {
        boolean launcher = looksLikeMinecraftRefresh(refreshToken);
        Holder<String> ticket = new Holder<>(launcher ? "t=" : "d=");
        CompletableFuture<MSTokens> primary = launcher
                ? MSAuth.minecraftRefreshToMsaMsr(refreshToken)
                : MSAuth.msrToMsaMsr(refreshToken);
        return primary.handleAsync((ms, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(ms);
            }
            LOGGER.warn("IAS: Refresh via {} OAuth client failed, trying the other client...", launcher ? "launcher" : "IAS", error);
            ticket.set(launcher ? "d=" : "t=");
            return launcher ? MSAuth.msrToMsaMsr(refreshToken) : MSAuth.minecraftRefreshToMsaMsr(refreshToken);
        }, IAS.executor()).thenCompose(future -> future).thenComposeAsync(ms -> {
            if (ms == null || handler.cancelled()) return CompletableFuture.completedFuture(null);
            refresh.set(ms.refresh());
            LOGGER.info("IAS: Converting MSA to XBL...");
            handler.stage(MSA_TO_XBL);
            return MSAuth.msaToXbl(ms.access(), ticket.get());
        }, IAS.executor());
    }

    /**
     * Whether the value looks like a Minecraft launcher / Localts refresh token.
     *
     * @param refresh Stored refresh token
     * @return {@code true} if the token starts with {@code M.C}
     */
    @Contract(value = "null -> false", pure = true)
    private static boolean looksLikeMinecraftRefresh(@Nullable String refresh) {
        return refresh != null && refresh.startsWith("M.C");
    }

    @Contract(value = "null -> false", pure = true)
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MicrosoftAccount that)) return false;
        return Objects.equals(this.uuid, that.uuid) && Objects.equals(this.name, that.name);
    }

    @Contract(pure = true)
    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + Objects.hashCode(this.uuid);
        hash = 31 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public String toString() {
        return "MicrosoftAccount{" +
                "uuid=" + this.uuid +
                ", name='" + this.name + '\'' +
                ", data='[DATA]'" +
                '}';
    }

    @Override
    public void write(@NotNull DataOutput out) throws IOException {
        // Write the insecure.
        out.writeBoolean(this.insecure);

        // Write the UUID.
        out.writeLong(this.uuid.getMostSignificantBits());
        out.writeLong(this.uuid.getLeastSignificantBits());

        // Write the name.
        out.writeUTF(this.name);

        // Write the data.
        out.writeShort(this.data.length);
        out.write(this.data);
    }

    /**
     * Reads the account from the input.
     *
     * @param in Target input
     * @return Read account
     * @throws IOException On I/O error
     */
    @CheckReturnValue
    @NotNull
    public static MicrosoftAccount read(@NotNull DataInput in) throws IOException {
        // Read the insecure.
        boolean insecure = in.readBoolean();

        // Read the UUID.
        UUID uuid = new UUID(in.readLong(), in.readLong());

        // Read the name.
        String name = in.readUTF();

        // Read the data.
        int length = in.readUnsignedShort();
        byte[] data = new byte[length];
        in.readFully(data);

        // Create and return.
        return new MicrosoftAccount(insecure, uuid, name, data);
    }

    /**
     * Reads a version 4 account from the input.
     *
     * @param in Target input
     * @return Read account
     * @throws IOException On I/O error
     */
    @CheckReturnValue
    @NotNull
    public static MicrosoftAccount readV4(@NotNull DataInput in) throws IOException {
        MicrosoftAccount account = read(in);

        // v4 stored local skin PNG metadata after the normal Microsoft account data.
        // Keep the login data and drop those local paths while migrating back to the current format.
        in.readBoolean();
        in.readUTF();
        in.readUTF();
        in.readUTF();

        return account;
    }
}
