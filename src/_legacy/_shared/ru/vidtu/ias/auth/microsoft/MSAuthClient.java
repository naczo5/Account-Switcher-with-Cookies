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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.handlers.CreateHandler;
import ru.vidtu.ias.auth.microsoft.fields.DeviceAuth;
import ru.vidtu.ias.auth.microsoft.fields.MSTokens;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.utils.IUtils;
import ru.vidtu.ias.utils.exceptions.DevicePendingException;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for MS authentication.
 *
 * @author VidTu
 */
public final class MSAuthClient implements Closeable {
    /**
     * Logger for this class.
     */
    @NotNull
    public static final Logger LOGGER = LoggerFactory.getLogger("IAS/MSAuthClient");

    /**
     * Account crypt.
     */
    @NotNull
    private final Crypt crypt;

    /**
     * Login handler.
     */
    @NotNull
    private final CreateHandler handler;

    /**
     * Device auth.
     */
    private DeviceAuth auth;

    /**
     * Expire instant.
     */
    @Nullable
    private Instant expire;

    /**
     * Next poll instant.
     */
    @Nullable
    private Instant poll;

    /**
     * Polling task, if any.
     */
    @Nullable
    private ScheduledFuture<?> task;

    /**
     * Creates an HTTP client for MS auth.
     *
     * @param crypt   Account crypt
     * @param handler Creation handler
     */
    @Contract(pure = true)
    public MSAuthClient(@NotNull Crypt crypt, @NotNull CreateHandler handler) {
        this.crypt = crypt;
        this.handler = handler;
    }

    /**
     * Starts the client device auth.
     *
     * @return Future that will complete with device auth, with null (on cancel) or exceptionally
     */
    @CheckReturnValue
    @NotNull
    public CompletableFuture<DeviceAuth> start() {
        // Stop if cancelled.
        if (this.handler.cancelled()) return CompletableFuture.completedFuture(null);

        // Request DAC.
        return MSAuth.requestDac().thenApplyAsync(auth -> {
            // Stop if cancelled.
            if (this.handler.cancelled()) return null;
            LOGGER.info("IAS: Got device auth code.");

            // Flush the auth.
            Duration interval = auth.interval();
            this.auth = auth;
            this.expire = Instant.now().plus(auth.expire());
            this.poll = Instant.now().plus(interval);

            // Flush the task.
            this.close();
            this.task = IAS.executor().scheduleWithFixedDelay(this::tick, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.info("IAS: HTTP polling started with delay of {}.", interval);

            // Return as-is.
            return auth;
        }, IAS.executor());
    }

    /**
     * Ticks the task.
     */
    private void tick() {
        try {
            // Stop if cancelled.
            if (this.handler.cancelled()) {
                this.close();
                return;
            }

            // Get state.
            MSTokens ms;
            try {
                ms = MSAuth.dacToMsaMsr(this.auth.device());
            } catch (Throwable t) {
                // Pending exception, ignore.
                if (IUtils.anyInCausalChain(t, DevicePendingException.class::isInstance)) {
                    return;
                }

                // Close and throw for any other.
                this.close();
                this.handler.error(new RuntimeException("HTTP polling error.", t));
                return;
            }

            // Stop task.
            this.close();

            // Stop if cancelled.
            if (this.handler.cancelled()) return;

            // Log it and display progress.
            LOGGER.info("IAS: Processing response...");
            this.handler.stage(MicrosoftAccount.PROCESSING);

            // Create the account from tokens.
            MSAccountFactory.create(this.crypt, ms, this.handler);
        } catch (Throwable t) {
            // Handle.
            this.handler.error(new RuntimeException("Unable to finalize MS auth.", t));
        }
    }

    @Override
    public void close() {
        // Cancel any task.
        if (this.task != null) {
            this.task.cancel(false);
            this.task = null;
            LOGGER.info("IAS: HTTP polling stopped.");
        }
    }

    @Contract(pure = true)
    @Override
    @NotNull
    public String toString() {
        return "MSAuthClient{" +
                "crypt=" + this.crypt +
                ", expire=" + this.expire +
                ", poll=" + this.poll +
                '}';
    }
}
