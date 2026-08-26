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

package ru.vidtu.ias.screen;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.account.OfflineAccount;
import ru.vidtu.ias.auth.LoginData;
import ru.vidtu.ias.auth.handlers.LoginHandler;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else
/*import net.minecraft.client.resources.PlayerSkin;*/

/**
 * Account GUI list.
 *
 * @author VidTu
 */
final class AccountList extends ObjectSelectionList<AccountEntry> {
    /**
     * Skins cache.
     */
    private static final Map<UUID, PlayerSkin> SKINS = new WeakHashMap<>(4);

    /**
     * Name-change availability cache.
     */
    private static final Map<UUID, NameChangeState> NAME_CHANGES = new WeakHashMap<>(4);

    /**
     * Name-change availability check queue.
     */
    private static final Map<UUID, MicrosoftAccount> NAME_CHANGE_QUEUE = new LinkedHashMap<>();

    /**
     * Synchronization lock for name-change checks.
     */
    private static final Object NAME_CHANGE_LOCK = new Object();

    /**
     * Whether a name-change check worker is active.
     */
    private static int nameChangePublicWorkers;

    /**
     * Name-change availability token fallback queue.
     */
    private static final Map<UUID, MicrosoftAccount> NAME_CHANGE_TOKEN_QUEUE = new LinkedHashMap<>();

    /**
     * Whether a token fallback worker is active.
     */
    private static boolean nameChangeTokenWorkerRunning;

    /**
     * Maximum concurrent NameMC checks.
     */
    private static final int NAME_CHANGE_PUBLIC_WORKERS = 6;

    /**
     * Delay between token fallback checks, to avoid rate-limiting normal logins.
     */
    private static final long NAME_CHANGE_TOKEN_CHECK_DELAY_MS = 5000L;

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/AccountList");

    /**
     * Parent screen.
     */
    private final AccountScreen screen;

    /**
     * Accounts marked for bulk operations.
     */
    private final Set<Account> selectedAccounts = new LinkedHashSet<>();

    /**
     * Entry currently being dragged for reordering.
     */
    @Nullable
    private AccountEntry draggedEntry;

    /**
     * Whether the active drag changed storage order.
     */
    private boolean draggedMoved;

    enum NameChangeState {
        UNKNOWN,
        CHECKING,
        AVAILABLE,
        UNAVAILABLE
    }

    /**
     * Creates a new accounts list widget.
     *
     * @param minecraft Minecraft instance
     * @param width     List width
     * @param height    List height
     * @param offset    List Y offset
     * @param item      Entry height
     */
    AccountList(AccountScreen screen, Minecraft minecraft, int width, int height, int offset, int item) {
        super(minecraft, width, height, offset, item);
        this.screen = screen;
        this.update(this.screen.search().getValue());
    }

    @Override
    public int getRowWidth() {
        return Math.min(super.getRowWidth(), this.screen.width - (85 + 10) * 2);
    }

    @Override
    public void setSelected(@Nullable AccountEntry entry) {
        // Select.
        super.setSelected(entry);

        // Notify parent.
        this.screen.updateSelected();
    }

    /**
     * Update the list by query.
     *
     * @param query Search query
     */
    void update(String query) {
        this.selectedAccounts.removeIf(account -> !IASStorage.ACCOUNTS.contains(account));

        // Add all if blank.
        if (query == null || query.isBlank()) {
            // Add every account.
            AccountEntry selected = this.getSelected();
            Account selectedAccount = selected != null ? selected.account() : null;
            this.replaceEntries(IASStorage.ACCOUNTS.stream()
                    .map(account -> new AccountEntry(this.minecraft, this, account))
                    .toList());
            this.setSelected(this.entryFor(selectedAccount));

            // Notify the root.
            this.screen.updateSelected();

            // Don't process search.
            return;
        }

        // Lowercase query.
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Add every account.
        AccountEntry selected = this.getSelected();
        Account selectedAccount = selected != null ? selected.account() : null;
        this.replaceEntries(IASStorage.ACCOUNTS.stream()
                .filter(account -> account.name().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .sorted((f, s) -> Boolean.compare(
                        s.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery),
                        f.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery)
                ))
                .map(account -> new AccountEntry(this.minecraft, this, account))
                .toList());
        this.setSelected(this.entryFor(selectedAccount));

        // Notify the root.
        this.screen.updateSelected();
    }

    @Nullable
    private AccountEntry entryFor(@Nullable Account account) {
        if (account == null) {
            return null;
        }
        for (AccountEntry entry : this.children()) {
            if (entry.account().equals(account)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Log in to this account.
     *
     * @param online Whether to try using online authentication
     * @apiNote The {@code online} parameter may be ignored if the current account doesn't support online authentication
     */
    void login(boolean online, Runnable onComplete) {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        // Check if we should log in online.
        if (online && account.canLogin()) {
            // Initialize and set the login screen.
            LoginPopupScreen login = new LoginPopupScreen(this.screen);
            //$ set_screen 'this.minecraft' 'login'
            this.minecraft.gui.setScreen(login);

            // Start login.
            IAS.executor().execute(() -> {
                account.login(login, onComplete);
            });
            // Don't process further.
            return;
        }

        // Initialize and set the login screen.
        LoginPopupScreen login = new LoginPopupScreen(this.screen);
        //$ set_screen 'this.minecraft' 'login'
        this.minecraft.gui.setScreen(login);

        // Login offline.
        String name = account.name();
        LoginData data = new LoginData(name, OfflineAccount.uuid(name), "ias:offline", null, false);
        login.success(data, false);
        if (onComplete != null) onComplete.run();
    }

    /**
     * Copies a fresh session token for the selected account to the clipboard.
     * Shows a confirmation screen first, since the token grants full access to the account.
     * Does nothing if nothing is selected.
     */
    void copyToken() {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        // Display confirmation screen before copying the sensitive token.
        final Screen confirm = new ConfirmPopupScreen(this.screen,
                Component.translatable("ias.copyToken.confirm.title"),
                Component.translatable("ias.copyToken.confirm", account.name()),
                Component.translatable("ias.copyToken.confirm.button"),
                () -> this.doCopyToken(account));
        //$ set_screen 'this.minecraft' confirm
        this.minecraft.gui.setScreen(confirm);
    }

    /**
     * Obtains a fresh token for the given account and copies it to the clipboard.
     * Called only after the user has confirmed the action.
     *
     * @param account Account to copy the token of
     */
    private void doCopyToken(Account account) {
        // Keep the background name-change checker from independently logging into (and
        // potentially rotating the refresh token of) this account while we copy from it.
        suppressNameChangeCheck(account.uuid());

        // Initialize and set the (re)login screen, used in copy-only mode.
        LoginPopupScreen login = new LoginPopupScreen(this.screen, LoginPopupScreen.CopyMode.ACCESS_TOKEN);
        //$ set_screen 'this.minecraft' 'login'
        this.minecraft.gui.setScreen(login);

        // Refresh online, if the account supports it.
        if (account.canLogin()) {
            IAS.executor().execute(() -> account.login(login, null));
            return;
        }

        // Otherwise, fall back to the placeholder offline token, same as the offline login flow.
        String name = account.name();
        LoginData data = new LoginData(name, OfflineAccount.uuid(name), "ias:offline", null, false);
        login.success(data, false);
    }

    /**
     * Copies a fresh Microsoft/Minecraft refresh token for the selected account to the clipboard.
     * Shows a confirmation screen first, since the refresh token grants full, long-lived access
     * to the account (it can mint new session tokens indefinitely, until revoked).
     * Does nothing if nothing is selected.
     */
    void copyRefreshToken() {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        // Display confirmation screen before copying the sensitive refresh token.
        final Screen confirm = new ConfirmPopupScreen(this.screen,
                Component.translatable("ias.copyRefreshToken.confirm.title"),
                Component.translatable("ias.copyRefreshToken.confirm", account.name()),
                Component.translatable("ias.copyRefreshToken.confirm.button"),
                () -> this.doCopyRefreshToken(account));
        //$ set_screen 'this.minecraft' confirm
        this.minecraft.gui.setScreen(confirm);
    }

    /**
     * Obtains a fresh refresh token for the given account and copies it to the clipboard.
     * Called only after the user has confirmed the action.
     *
     * @param account Account to copy the refresh token of
     */
    private void doCopyRefreshToken(Account account) {
        // Same race-avoidance as doCopyToken() - see suppressNameChangeCheck() javadoc.
        suppressNameChangeCheck(account.uuid());

        // Initialize and set the (re)login screen, used in copy-only mode.
        LoginPopupScreen login = new LoginPopupScreen(this.screen, LoginPopupScreen.CopyMode.REFRESH_TOKEN);
        //$ set_screen 'this.minecraft' 'login'
        this.minecraft.gui.setScreen(login);

        // Refresh online, if the account supports it. (Offline accounts never have a refresh
        // token, so there's nothing meaningful to copy for them; the login screen will show
        // a friendly error if this ends up being called on one anyway.)
        if (account.canLogin()) {
            IAS.executor().execute(() -> account.login(login, null));
            return;
        }

        String name = account.name();
        LoginData data = new LoginData(name, OfflineAccount.uuid(name), "ias:offline", null, false);
        login.success(data, false);
    }

    boolean hasMultiSelection() {
        return !this.selectedAccounts.isEmpty();
    }

    boolean isMultiSelected(AccountEntry entry) {
        return this.selectedAccounts.contains(entry.account());
    }

    void toggleMultiSelection(AccountEntry entry) {
        Account account = entry.account();
        if (!this.selectedAccounts.remove(account)) {
            this.selectedAccounts.add(account);
        }
        this.screen.updateSelected();
    }

    void clearMultiSelection() {
        if (this.selectedAccounts.isEmpty()) {
            return;
        }
        this.selectedAccounts.clear();
        this.screen.updateSelected();
    }

    void edit() {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        int index = this.children().indexOf(selected);
        if (index < 0 || index >= IASStorage.ACCOUNTS.size()) return;

        // Replace in storage.
        final Screen add = new AddPopupScreen(this.screen, true, account -> {
            //$ set_screen 'this.minecraft' 'this.screen'
            this.minecraft.gui.setScreen(this.screen);

            // Add the account and save it.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
            if (index >= IASStorage.ACCOUNTS.size()) {
                IASStorage.ACCOUNTS.add(account);
            } else {
                IASStorage.ACCOUNTS.set(index, account);
            }

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update the list.
            this.update(this.screen.search().getValue());
        });
        //$ set_screen 'this.minecraft' add
        this.minecraft.gui.setScreen(add);
    }

    /**
     * Deletes the selected account.
     * Does nothing if nothing is selected.
     *
     * @param confirm Whether to show the confirmation
     */
    void delete(boolean confirm) {
        if (!this.selectedAccounts.isEmpty()) {
            this.deleteSelected(confirm);
            return;
        }

        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        // Skip confirmation if shift is pressed.
        if (!confirm) {
            // Remove.
            IASStorage.ACCOUNTS.remove(account);

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update.
            this.update(this.screen.search().getValue());
            return;
        }

        // Display confirmation screen.
        final Screen delete = new DeletePopupScreen(this.screen, account, () -> {
            // Delete if confirmed.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update.
            this.update(this.screen.search().getValue());
        });
        //$ set_screen 'this.minecraft' delete
        this.minecraft.gui.setScreen(delete);
    }

    private void deleteSelected(boolean confirm) {
        Set<Account> accounts = new LinkedHashSet<>(this.selectedAccounts);
        if (accounts.isEmpty()) {
            return;
        }

        Runnable remove = () -> {
            IASStorage.ACCOUNTS.removeIf(accounts::contains);
            this.selectedAccounts.clear();
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }
            this.update(this.screen.search().getValue());
        };

        if (!confirm) {
            remove.run();
            return;
        }

        final Screen delete = new DeletePopupScreen(this.screen, Component.translatable("ias.delete.confirm.multi", accounts.size()), remove);
        //$ set_screen 'this.minecraft' delete
        this.minecraft.gui.setScreen(delete);
    }

    /**
     * Opens the account adding screen.
     */
    void add() {
        final Screen add = new AddPopupScreen(this.screen, false, account -> {
            // Set to this.
            //$ set_screen 'this.minecraft' 'this.screen'
            this.minecraft.gui.setScreen(this.screen);

            // Add the account.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
            IASStorage.ACCOUNTS.add(account);

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update the list.
            this.update(this.screen.search().getValue());
        });
        //$ set_screen 'this.minecraft' 'add'
        this.minecraft.gui.setScreen(add);
    }

    /**
     * Gets the skin for the account entry.
     *
     * @param entry Target account entry
     * @return Player skin, fetched or default
     */
    PlayerSkin skin(AccountEntry entry) {
        // Get and return the skin if already stored.
        UUID uuid = entry.account().skin();
        PlayerSkin skin = SKINS.get(uuid);
        if (skin != null) return skin;

        // Quickly put the replacer to avoid fetch spam.
        skin = DefaultPlayerSkin.get(uuid);
        SKINS.put(uuid, skin);

        // Skip fetching offline skins.
        if (uuid.version() != 4) return skin;

        // Load the skin.
        CompletableFuture.supplyAsync(() -> {
            // Fetch the profile
            //? if >=1.21.10 {
            ProfileResult result = this.minecraft.services().sessionService().fetchProfile(uuid, false);
            //?} else
            /*ProfileResult result = this.minecraft.getMinecraftSessionService().fetchProfile(uuid, false);*/

            // Skip if profile is null.
            if (result == null) return null;

            // Return the profile.
            return result.profile();
        }, IAS.executor()).thenComposeAsync(profile -> {
            // Skip if profile is null.
            if (profile == null) return CompletableFuture.completedFuture(null);

            // Load the skin.
            //? if >= 1.21.10 {
            return this.minecraft.getSkinManager().get(profile);
            //?} else
            /*return this.minecraft.getSkinManager().getOrLoad(profile);*/
        }, IAS.executor()).thenAcceptAsync(loaded -> {
            // Put into map.
            loaded.ifPresent(newSkin -> SKINS.put(uuid, newSkin));
        }, this.minecraft).exceptionally(t -> {
            // Log it.
            LOGGER.warn("IAS: Unable to load skin: {}", entry, t);

            // Return null.
            return null;
        });

        // Return quick skin.
        return skin;
    }

    static void clearSkin(UUID uuid) {
        SKINS.remove(uuid);
    }

    static void clearNameChange(UUID uuid) {
        synchronized (NAME_CHANGE_LOCK) {
            NAME_CHANGES.remove(uuid);
            NAME_CHANGE_QUEUE.remove(uuid);
            NAME_CHANGE_TOKEN_QUEUE.remove(uuid);
        }
    }

    /**
     * Removes the given account from the background name-change availability queues and,
     * if it wasn't already resolved, marks it as unresolved rather than leaving it queued.
     * <p>
     * The background name-change checker calls {@link MicrosoftAccount#login} on its own,
     * completely independently of anything the user does. Since Microsoft rotates an
     * account's refresh token on every redemption, that unrelated background login can
     * silently invalidate a refresh token the user just copied (or is about to copy) via
     * "Copy Token"/"Copy Refresh Token". This doesn't eliminate every possible race (e.g.
     * gameplay logins elsewhere still refresh independently), but it stops this specific,
     * very common source of it for the account currently being copied.
     *
     * @param uuid Account UUID
     */
    private static void suppressNameChangeCheck(UUID uuid) {
        synchronized (NAME_CHANGE_LOCK) {
            NAME_CHANGE_QUEUE.remove(uuid);
            NAME_CHANGE_TOKEN_QUEUE.remove(uuid);
            NAME_CHANGES.putIfAbsent(uuid, NameChangeState.UNKNOWN);
        }
    }

    static void updateNameChangeFromToken(UUID uuid, String token) {
        synchronized (NAME_CHANGE_LOCK) {
            NAME_CHANGES.put(uuid, NameChangeState.CHECKING);
            NAME_CHANGE_QUEUE.remove(uuid);
            NAME_CHANGE_TOKEN_QUEUE.remove(uuid);
        }
        MSAuth.nameChangeInfo(token).whenCompleteAsync((info, error) -> {
            synchronized (NAME_CHANGE_LOCK) {
                if (error != null || info == null) {
                    LOGGER.debug("IAS: Unable to update logged-in name-change availability for {}.", uuid, error);
                    NAME_CHANGES.put(uuid, NameChangeState.UNKNOWN);
                } else {
                    NAME_CHANGES.put(uuid, info.allowed() ? NameChangeState.AVAILABLE : NameChangeState.UNAVAILABLE);
                }
            }
        }, IAS.executor());
    }

    NameChangeState nameChangeState(AccountEntry entry) {
        Account account = entry.account();
        if (!(account instanceof MicrosoftAccount microsoft)) {
            return NameChangeState.UNKNOWN;
        }

        UUID uuid = microsoft.uuid();
        synchronized (NAME_CHANGE_LOCK) {
            NameChangeState state = NAME_CHANGES.get(uuid);
            if (state != null) {
                return state;
            }
            NAME_CHANGES.put(uuid, NameChangeState.CHECKING);
            NAME_CHANGE_QUEUE.putIfAbsent(uuid, microsoft);
            this.startNameChangePublicWorkers();
        }
        return NameChangeState.CHECKING;
    }

    private void startNameChangePublicWorkers() {
        while (true) {
            MicrosoftAccount account;
            synchronized (NAME_CHANGE_LOCK) {
                if (nameChangePublicWorkers >= NAME_CHANGE_PUBLIC_WORKERS || NAME_CHANGE_QUEUE.isEmpty()) {
                    return;
                }
                UUID uuid = NAME_CHANGE_QUEUE.keySet().iterator().next();
                account = NAME_CHANGE_QUEUE.remove(uuid);
                nameChangePublicWorkers++;
            }

            MicrosoftAccount checkAccount = account;
            MSAuth.nameChangeInfoFromNameMc(checkAccount.uuid()).whenCompleteAsync((info, error) -> {
                UUID uuid = checkAccount.uuid();
                boolean needsTokenFallback = false;
                synchronized (NAME_CHANGE_LOCK) {
                    nameChangePublicWorkers--;
                    if (error != null || info == null) {
                        FriendlyException friendly = FriendlyException.friendlyInChain(error);
                        if (friendly != null && "ias.profile.name.unknown".equals(friendly.key())) {
                            needsTokenFallback = true;
                            NAME_CHANGE_TOKEN_QUEUE.putIfAbsent(uuid, checkAccount);
                        } else {
                            LOGGER.warn("IAS: Unable to check public name-change availability for {}.", checkAccount, error);
                            NAME_CHANGES.put(uuid, NameChangeState.UNKNOWN);
                        }
                    } else {
                        NAME_CHANGES.put(uuid, info.allowed() ? NameChangeState.AVAILABLE : NameChangeState.UNAVAILABLE);
                    }
                }
                if (needsTokenFallback) {
                    this.startNameChangeTokenWorker();
                }
                this.startNameChangePublicWorkers();
            }, IAS.executor());
        }
    }

    private void startNameChangeTokenWorker() {
        synchronized (NAME_CHANGE_LOCK) {
            if (nameChangeTokenWorkerRunning) {
                return;
            }
            nameChangeTokenWorkerRunning = true;
        }
        CompletableFuture.delayedExecutor(NAME_CHANGE_TOKEN_CHECK_DELAY_MS, TimeUnit.MILLISECONDS, IAS.executor()).execute(this::runNextNameChangeTokenCheck);
    }

    private void runNextNameChangeTokenCheck() {
        MicrosoftAccount account;
        synchronized (NAME_CHANGE_LOCK) {
            if (NAME_CHANGE_TOKEN_QUEUE.isEmpty()) {
                nameChangeTokenWorkerRunning = false;
                return;
            }
            UUID uuid = NAME_CHANGE_TOKEN_QUEUE.keySet().iterator().next();
            account = NAME_CHANGE_TOKEN_QUEUE.remove(uuid);
        }

        this.loginForNameChange(account)
                .thenComposeAsync(data -> MSAuth.nameChangeInfo(data.token()), IAS.executor())
                .whenCompleteAsync((info, error) -> {
                    UUID uuid = account.uuid();
                    synchronized (NAME_CHANGE_LOCK) {
                        if (error != null || info == null) {
                            LOGGER.debug("IAS: Unable to check token name-change availability for {}.", account, error);
                            NAME_CHANGES.put(uuid, NameChangeState.UNKNOWN);
                        } else {
                            NAME_CHANGES.put(uuid, info.allowed() ? NameChangeState.AVAILABLE : NameChangeState.UNAVAILABLE);
                        }
                    }
                    CompletableFuture.delayedExecutor(NAME_CHANGE_TOKEN_CHECK_DELAY_MS, TimeUnit.MILLISECONDS, IAS.executor()).execute(this::runNextNameChangeTokenCheck);
                }, IAS.executor());
    }

    private CompletableFuture<LoginData> loginForNameChange(MicrosoftAccount account) {
        CompletableFuture<LoginData> token = new CompletableFuture<>();
        IAS.executor().execute(() -> account.login(new LoginHandler() {
            @Override
            public boolean cancelled() {
                return AccountList.this.minecraft.gui.screen() != AccountList.this.screen;
            }

            @Override
            public void stage(String stage, Object... args) {
                // Row availability checks do not show global login progress.
            }

            @Override
            public CompletableFuture<String> password() {
                token.completeExceptionally(new IllegalStateException("Password required for name-change availability check."));
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void success(LoginData data, boolean changed) {
                if (changed) {
                    AccountList.this.saveStorage();
                }
                token.complete(data);
            }

            @Override
            public void error(Throwable error) {
                token.completeExceptionally(error);
            }
        }, null));
        return token;
    }

    private void saveStorage() {
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }
    }

    void startDragging(AccountEntry entry) {
        if (entry == null || !this.children().contains(entry) || !this.screen.search().getValue().isBlank()) {
            return;
        }
        this.clearMultiSelection();
        this.setSelected(entry);
        this.draggedEntry = entry;
        this.draggedMoved = false;
        this.setDragging(true);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggedEntry != null && event.button() == 0) {
            this.autoScrollWhileDragging(event.y());
            AccountEntry target = this.getEntryAtPosition(event.x(), event.y());
            if (target != null && !target.equals(this.draggedEntry)) {
                this.moveDraggedEntry(target);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (this.draggedEntry != null && event.button() == 0) {
            this.finishDragging();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void autoScrollWhileDragging(double mouseY) {
        int edge = 16;
        if (mouseY < this.getY() + edge) {
            this.setScrollAmount(this.scrollAmount() - this.defaultEntryHeight);
        } else if (mouseY > this.getBottom() - edge) {
            this.setScrollAmount(this.scrollAmount() + this.defaultEntryHeight);
        }
    }

    private void moveDraggedEntry(AccountEntry target) {
        AccountEntry dragged = this.draggedEntry;
        if (dragged == null) {
            return;
        }

        int fromRow = this.children().indexOf(dragged);
        int targetRow = this.children().indexOf(target);
        int fromStorage = IASStorage.ACCOUNTS.indexOf(dragged.account());
        int targetStorage = IASStorage.ACCOUNTS.indexOf(target.account());
        if (fromRow < 0 || targetRow < 0 || fromStorage < 0 || targetStorage < 0 || fromStorage == targetStorage) {
            return;
        }

        Account account = IASStorage.ACCOUNTS.remove(fromStorage);
        if (fromStorage < targetStorage) {
            targetStorage--;
        }
        int insert = targetStorage + (targetRow > fromRow ? 1 : 0);
        insert = Math.max(0, Math.min(insert, IASStorage.ACCOUNTS.size()));
        IASStorage.ACCOUNTS.add(insert, account);

        this.draggedMoved = true;
        this.update(this.screen.search().getValue());
        this.draggedEntry = this.entryFor(account);
        if (this.draggedEntry != null) {
            this.setSelected(this.draggedEntry);
        }
    }

    private void finishDragging() {
        boolean save = this.draggedMoved;
        this.draggedEntry = null;
        this.draggedMoved = false;
        this.setDragging(false);
        if (save) {
            this.saveStorage();
        }
    }

    /**
     * Swaps the entry with the account above, if possible.
     *
     * @param entry Target entry
     */
    void swapUp(AccountEntry entry) {
        // Get and validate indexes.
        int idx = this.children().indexOf(entry);
        if (idx < 0 || idx >= IASStorage.ACCOUNTS.size()) return;
        int upIdx = idx - 1;
        if (upIdx < 0) return;

        // Move storage.
        IASStorage.ACCOUNTS.set(idx, IASStorage.ACCOUNTS.get(upIdx));
        IASStorage.ACCOUNTS.set(upIdx, entry.account());

        // Save storage.
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }

        // Move elements.
        //? if <1.21.10 {
        /*this.children().set(idx, this.children().get(upIdx));
        this.children().set(upIdx, entry);
        this.setSelected(entry);
        *///?} else
        this.swap(idx, upIdx);
    }

    /**
     * Swaps the entry with the account below, if possible.
     *
     * @param entry Target entry
     */
    void swapDown(AccountEntry entry) {
        // Get and validate indexes.
        int idx = this.children().indexOf(entry);
        if (idx < 0 || idx >= IASStorage.ACCOUNTS.size()) return;
        int downIdx = idx + 1;
        if (downIdx >= this.children().size() || downIdx >= IASStorage.ACCOUNTS.size()) return;

        // Move storage.
        IASStorage.ACCOUNTS.set(idx, IASStorage.ACCOUNTS.get(downIdx));
        IASStorage.ACCOUNTS.set(downIdx, entry.account());

        // Save storage.
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }

        // Move elements.
        //? if <1.21.10 {
        /*this.children().set(idx, this.children().get(downIdx));
        this.children().set(downIdx, entry);
        this.setSelected(entry);
        *///?} else
        this.swap(idx, downIdx);
    }

    /**
     * Gets the screen.
     *
     * @return Parent accounts screen
     */
    AccountScreen screen() {
        return this.screen;
    }

    @Override
    public String toString() {
        return "AccountList{" +
                "children=" + this.children() +
                '}';
    }
}
