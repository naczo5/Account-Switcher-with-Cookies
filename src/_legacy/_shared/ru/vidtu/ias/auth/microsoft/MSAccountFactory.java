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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.handlers.CreateHandler;
import ru.vidtu.ias.auth.microsoft.fields.MSTokens;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.utils.Holder;
import ru.vidtu.ias.utils.IUtils;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Shared Microsoft account creation pipeline (MSA/MSR to encrypted {@link MicrosoftAccount}).
 *
 * @author VidTu
 */
public final class MSAccountFactory {
    /**
     * Logger for this class.
     */
    @NotNull
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/MSAccountFactory");

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    @Contract(value = "-> fail", pure = true)
    private MSAccountFactory() {
        throw new AssertionError("No instances.");
    }

    /**
     * Creates a {@link MicrosoftAccount} from browser session cookies (SISU flow, no refresh token).
     *
     * @param crypt        Account encryption
     * @param cookieHeader HTTP {@code Cookie} header value
     * @param handler      Creation handler
     * @return Future that completes when creation finishes or fails
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<Void> createFromCookies(@NotNull Crypt crypt, @NotNull String cookieHeader, @NotNull CreateHandler handler) {
        Holder<String> access = new Holder<>();
        Holder<String> refresh = new Holder<>("");
        Holder<byte[]> data = new Holder<>();

        return MSAuth.cookiesToMcaFromCookies(cookieHeader).thenComposeAsync(result -> {
            if (result == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

            access.set(result.mca());
            refresh.set(result.refresh());

            LOGGER.info("IAS: Converting MCA to MCP (cookie import)...");
            handler.stage(MicrosoftAccount.MCA_TO_MCP);
            return MSAuth.mcaToMcp(result.mca());
        }, IAS.executor()).exceptionallyAsync(t -> {
            if (IUtils.anyInCausalChain(t, err -> err instanceof UnresolvedAddressException || err instanceof NoRouteToHostException || err instanceof HttpTimeoutException || err instanceof ConnectException)) {
                throw new FriendlyException("Unable to connect to MS servers.", t, "ias.error.connect");
            }
            FriendlyException friendly = FriendlyException.friendlyInChain(t);
            if (friendly != null) {
                throw friendly;
            }
            throw new RuntimeException("Unable to perform cookie auth.", t);
        }, IAS.executor()).thenApplyAsync(profile -> {
            if (profile == null || handler.cancelled()) return null;

            LOGGER.info("IAS: Encrypting tokens (cookie import)...");
            handler.stage(MicrosoftAccount.ENCRYPTING);

            byte[] unencrypted;
            try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(byteOut)) {
                out.writeUTF(access.get());
                out.writeUTF(refresh.get());
                unencrypted = byteOut.toByteArray();
            } catch (Throwable t) {
                throw new RuntimeException("Unable to write the tokens.", t);
            }

            try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(byteOut)) {
                byte[] encrypted = crypt.encrypt(unencrypted);
                out.writeUTF(crypt.type());
                out.write(encrypted);
                data.set(byteOut.toByteArray());
            } catch (Throwable t) {
                throw new RuntimeException("Unable to encrypt the tokens.", t);
            }

            return profile;
        }, IAS.executor()).thenAcceptAsync(profile -> {
            if (profile == null || handler.cancelled()) return;

            UUID uuid = profile.uuid();
            String name = profile.name();

            LOGGER.info("IAS: Successfully added {} (cookie import)", profile);
            handler.stage(MicrosoftAccount.FINALIZING);

            MicrosoftAccount account = new MicrosoftAccount(crypt.insecure(), uuid, name, data.get());
            handler.success(account);
        }, IAS.executor()).exceptionallyAsync(t -> {
            handler.error(t instanceof RuntimeException re ? re : new RuntimeException("Unable to create an MS account from cookies.", t));
            return null;
        }, IAS.executor());
    }

    /**
     * Creates a {@link MicrosoftAccount} from Microsoft tokens using the standard auth chain.
     *
     * @param crypt   Account encryption
     * @param ms      Microsoft access and refresh tokens
     * @param handler Creation handler
     * @return Future that completes when creation finishes or fails
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<Void> create(@NotNull Crypt crypt, @NotNull MSTokens ms, @NotNull CreateHandler handler) {
        return create(crypt, ms, handler, "d=");
    }

    /**
     * Creates a {@link MicrosoftAccount} from Microsoft tokens with Localts/Minecraft token semantics.
     *
     * @param crypt   Account encryption
     * @param ms      Microsoft access and refresh tokens
     * @param handler Creation handler
     * @return Future that completes when creation finishes or fails
     */
    @CheckReturnValue
    @NotNull
    public static CompletableFuture<Void> createFromMinecraftRefresh(@NotNull Crypt crypt, @NotNull MSTokens ms, @NotNull CreateHandler handler) {
        return create(crypt, ms, handler, "t=");
    }

    @CheckReturnValue
    @NotNull
    private static CompletableFuture<Void> create(@NotNull Crypt crypt, @NotNull MSTokens ms, @NotNull CreateHandler handler, @NotNull String ticketPrefix) {
        Holder<String> access = new Holder<>();
        Holder<byte[]> data = new Holder<>();

        return CompletableFuture.completedFuture(null).thenComposeAsync(ignored -> {
            if (handler.cancelled()) return CompletableFuture.completedFuture(null);

            LOGGER.info("IAS: Converting MSA to XBL...");
            handler.stage(MicrosoftAccount.MSA_TO_XBL);
            return MSAuth.msaToXbl(ms.access(), ticketPrefix);
        }, IAS.executor()).thenComposeAsync(xbl -> {
            if (xbl == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

            LOGGER.info("IAS: Converting XBL to XSTS...");
            handler.stage(MicrosoftAccount.XBL_TO_XSTS);
            return MSAuth.xblToXsts(xbl.token(), xbl.hash());
        }, IAS.executor()).thenComposeAsync(xsts -> {
            if (xsts == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

            LOGGER.info("IAS: Converting XSTS to MCA...");
            handler.stage(MicrosoftAccount.XSTS_TO_MCA);
            return MSAuth.xstsToMca(xsts.token(), xsts.hash());
        }, IAS.executor()).thenComposeAsync(token -> {
            if (token == null || handler.cancelled()) return CompletableFuture.completedFuture(null);

            access.set(token);

            LOGGER.info("IAS: Converting MCA to MCP...");
            handler.stage(MicrosoftAccount.MCA_TO_MCP);
            return MSAuth.mcaToMcp(token);
        }, IAS.executor()).exceptionallyAsync(t -> {
            if (IUtils.anyInCausalChain(t, err -> err instanceof UnresolvedAddressException || err instanceof NoRouteToHostException || err instanceof HttpTimeoutException || err instanceof ConnectException)) {
                throw new FriendlyException("Unable to connect to MS servers.", t, "ias.error.connect");
            }
            throw new RuntimeException("Unable to perform MS auth.", t);
        }, IAS.executor()).thenApplyAsync(profile -> {
            if (profile == null || handler.cancelled()) return null;

            LOGGER.info("IAS: Encrypting tokens...");
            handler.stage(MicrosoftAccount.ENCRYPTING);

            byte[] unencrypted;
            try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(byteOut)) {
                out.writeUTF(access.get());
                out.writeUTF(ms.refresh());
                unencrypted = byteOut.toByteArray();
            } catch (Throwable t) {
                throw new RuntimeException("Unable to write the tokens.", t);
            }

            try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(byteOut)) {
                byte[] encrypted = crypt.encrypt(unencrypted);
                out.writeUTF(crypt.type());
                out.write(encrypted);
                data.set(byteOut.toByteArray());
            } catch (Throwable t) {
                throw new RuntimeException("Unable to encrypt the tokens.", t);
            }

            return profile;
        }, IAS.executor()).thenAcceptAsync(profile -> {
            if (profile == null || handler.cancelled()) return;

            UUID uuid = profile.uuid();
            String name = profile.name();

            LOGGER.info("IAS: Successfully added {}", profile);
            handler.stage(MicrosoftAccount.FINALIZING);

            MicrosoftAccount account = new MicrosoftAccount(crypt.insecure(), uuid, name, data.get());
            handler.success(account);
        }, IAS.executor()).exceptionallyAsync(t -> {
            handler.error(new RuntimeException("Unable to create an MS account.", t));
            return null;
        }, IAS.executor());
    }
}
