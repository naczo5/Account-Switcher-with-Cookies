package the_fireplace.ias.gui;

import com.github.mrebhan.ingameaccountswitcher.tools.Config;
import com.github.mrebhan.ingameaccountswitcher.tools.Tools;
import com.github.mrebhan.ingameaccountswitcher.MR;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AccountData;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltManager;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;
import the_fireplace.ias.account.AlreadyLoggedInException;
import the_fireplace.ias.account.ExtendedAccountData;
import the_fireplace.ias.IAS;
import the_fireplace.ias.config.ConfigValues;
import the_fireplace.ias.enums.EnumBool;
import the_fireplace.ias.tools.HttpTools;
import the_fireplace.ias.tools.JavaTools;
import the_fireplace.ias.tools.SkinTools;
import the_fireplace.iasencrypt.EncryptionTools;
import ru.vidtu.iasfork.cookie.CookieAuth;
import ru.vidtu.iasfork.cookie.CookieAuthException;
import ru.vidtu.ias.auth.hypixel.HypixelBanChecker;
import ru.vidtu.ias.auth.hypixel.HypixelBanResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * The GUI where you can log in to, add, and remove accounts
 * @author The_Fireplace
 */
public class GuiAccountSelector extends GuiScreen {
	private int selectedAccountIndex = 0;
	private int prevIndex = 0;
	private Throwable loginfailed;
	private ArrayList<ExtendedAccountData> queriedaccounts = convertData();
	private GuiAccountSelector.List accountsgui;
	private java.util.List<String> hoveredTooltip;
	//Buttons that can be disabled need to be here
	private GuiButton login;
	private GuiButton loginoffline;
	private GuiButton delete;
	private GuiButton edit;
	private GuiButton reloadskins;
	private GuiButton logout;
	private GuiButton checkHypixel;
	private GuiButton manageProfile;
	//Search
	private String query;
	private GuiTextField search;
	private volatile boolean hypixelCheckRunning;
	private final Map<String, HypixelBanResult> hypixelResults = new HashMap<String, HypixelBanResult>();
	private final Map<String, HypixelBanPhase> hypixelPhases = new HashMap<String, HypixelBanPhase>();
	private boolean hypixelCacheLoaded;

	private enum HypixelBanPhase {
		UNKNOWN,
		CHECKING,
		NOT_APPLICABLE
	}

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		if (!hypixelCacheLoaded) {
			try {
				hypixelResults.putAll(ru.vidtu.iasfork.checks.ChecksCache.load());
			} catch (Throwable ignored) {
			}
			hypixelCacheLoaded = true;
		}
		accountsgui = new GuiAccountSelector.List(this.mc);
		accountsgui.registerScrollButtons(5, 6);
		query = I18n.format("ias.search");
		this.buttonList.clear();
		//Above Top Row
		this.buttonList.add(reloadskins = new GuiButton(8, this.width / 2 - 154 - 10, this.height - 76 - 8, 80, 20, I18n.format("ias.reloadskins")));
		this.buttonList.add(manageProfile = new GuiButton(11, this.width / 2 - 80, this.height - 76 - 8, 80, 20, "Skin / Name"));
		this.buttonList.add(logout = new GuiButton(9, this.width / 2 + 4, this.height - 76 - 8, 76, 20, I18n.format("ias.logout")));
		this.buttonList.add(checkHypixel = new GuiButton(10, this.width / 2 + 84, this.height - 76 - 8, 80, 20, I18n.format("ias.accounts.checkHypixel")));
		//Top Row
		this.buttonList.add(new GuiButton(0, this.width / 2 + 4 + 40, this.height - 52, 120, 20, I18n.format("ias.addaccount")));
		this.buttonList.add(login = new GuiButton(1, this.width / 2 - 154 - 10, this.height - 52, 120, 20, I18n.format("ias.login")));
		this.buttonList.add(edit = new GuiButton(7, this.width / 2 - 40, this.height - 52, 80, 20, I18n.format("ias.edit")));
		//Bottom Row
		this.buttonList.add(loginoffline = new GuiButton(2, this.width / 2 - 154 - 10, this.height - 28, 110, 20, I18n.format("ias.login")+" "+I18n.format("ias.offline")));
		this.buttonList.add(new GuiButton(3, this.width / 2 + 4 + 50, this.height - 28, 110, 20, I18n.format("gui.cancel")));
		this.buttonList.add(delete = new GuiButton(4, this.width / 2 - 50, this.height - 28, 100, 20, I18n.format("ias.delete")));
		//Direct Play shortcuts (parity with modern AccountScreen)
		this.buttonList.add(new GuiButton(12, this.width - 104, 8, 100, 20, I18n.format("menu.singleplayer")));
		this.buttonList.add(new GuiButton(13, this.width - 104, 32, 100, 20, I18n.format("menu.multiplayer")));
		search  = new GuiTextField(8, this.fontRendererObj, this.width / 2 - 80, 14, 160, 16);
		search.setText(query);
		updateButtons();
		if(!queriedaccounts.isEmpty())
		SkinTools.buildSkin(queriedaccounts.get(selectedAccountIndex).alias);
	}
	@Override
	public void handleMouseInput() throws IOException
	{
		super.handleMouseInput();
		this.accountsgui.handleMouseInput();
	}

	@Override
	public void updateScreen(){
		this.search.updateCursorCounter();
		updateText();
		updateButtons();
		if(!(prevIndex == selectedAccountIndex)) {
			updateShownSkin();
			prevIndex = selectedAccountIndex;
		}
	}

	private void updateShownSkin(){
		if (!queriedaccounts.isEmpty())
			SkinTools.buildSkin(queriedaccounts.get(selectedAccountIndex).alias);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException
	{
		super.mouseClicked(mouseX, mouseY, mouseButton);
		boolean flag = search.isFocused();
		this.search.mouseClicked(mouseX, mouseY, mouseButton);
		if(!flag && search.isFocused()){
			query = "";
			updateText();
			updateQueried();
		}
	}

	private void updateText(){
		search.setText(query);
	}

	@Override
	public void onGuiClosed()
	{
		hypixelCheckId++;
		hypixelCheckRunning = false;
		Keyboard.enableRepeatEvents(false);
		Config.save();
	}

	@Override
	public void drawScreen(int par1, int par2, float par3) {
		hoveredTooltip = null;
		accountsgui.drawScreen(par1, par2, par3);
		this.drawCenteredString(fontRendererObj, I18n.format("ias.selectaccount"), this.width / 2, 4, -1);
		if (loginfailed != null) {
			this.drawCenteredString(fontRendererObj, loginfailed.getLocalizedMessage(), this.width / 2, this.height - 62, 16737380);
		}
		search.drawTextBox();
		super.drawScreen(par1, par2, par3);
		if(!queriedaccounts.isEmpty()){
			SkinTools.javDrawSkin(8, height/2-64-16, 64, 128);
			Tools.drawBorderedRect(width-8-64, height/2-64-16, width-8, height/2+64-16, 2, -5855578, -13421773);
			if(queriedaccounts.get(selectedAccountIndex).premium == EnumBool.TRUE)
				this.drawString(fontRendererObj, I18n.format("ias.premium"), width-8-61, height/2-64-13, 6618980);
			else if(queriedaccounts.get(selectedAccountIndex).premium == EnumBool.FALSE)
				this.drawString(fontRendererObj, I18n.format("ias.notpremium"), width-8-61, height/2-64-13, 16737380);
			this.drawString(fontRendererObj, I18n.format("ias.timesused"), width-8-61, height/2-64-15+12, -1);
			this.drawString(fontRendererObj, String.valueOf(queriedaccounts.get(selectedAccountIndex).useCount), width-8-61, height/2-64-15+21, -1);
			if(queriedaccounts.get(selectedAccountIndex).useCount > 0){
				this.drawString(fontRendererObj, I18n.format("ias.lastused"), width-8-61, height/2-64-15+30, -1);
				this.drawString(fontRendererObj, JavaTools.getJavaCompat().getFormattedDate(), width-8-61, height/2-64-15+39, -1);
			}
		}
		if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
			this.drawHoveringText(hoveredTooltip, par1, par2);
		}
	}

	@Override
	protected void actionPerformed(GuiButton button){
		if (button.enabled)
		{
			if(button.id == 3){
				escape();
			}else if(button.id == 0){
				add();
			}else if(button.id == 4){
				delete();
			}else if(button.id == 1){
				login(selectedAccountIndex);
			}else if(button.id == 2){
				logino(selectedAccountIndex);
			}else if(button.id == 7){
				edit();
			}else if(button.id == 8){
				reloadSkins();
			}else if(button.id == 9){
				logout();
			}else if(button.id == 10){
				checkAllHypixelBans();
			}else if(button.id == 11){
				mc.displayGuiScreen(new GuiManageProfile(this, queriedaccounts.get(selectedAccountIndex)));
			}else if(button.id == 12){
				mc.displayGuiScreen(new GuiSelectWorld(this));
			}else if(button.id == 13){
				mc.displayGuiScreen(new GuiMultiplayer(this));
			}else{
				accountsgui.actionPerformed(button);
			}
		}
	}

	/**
	 * Reload Skins
     */
	private void reloadSkins(){
		Config.save();
		SkinTools.cacheSkins();
		updateShownSkin();
	}

	/**
	 * Leave the gui
	 */
	private void escape(){
		mc.displayGuiScreen(null);
	}
	/** Restores the account Prism/the launcher selected when the game started. */
	private void logout(){
		try {
			IAS.restoreLaunchSession();
			loginfailed = null;
			mc.displayGuiScreen(null);
		} catch (Throwable t) {
			loginfailed = t;
		}
	}
	/**
	 * Delete the selected account
	 */
	private void delete(){
		AltDatabase.getInstance().getAlts().remove(getCurrentAsEditable());
		if(selectedAccountIndex > 0)
			selectedAccountIndex--;
		updateQueried();
		updateButtons();
	}

	/**
	 * Copies the active access token of the selected account to the clipboard.
	 * Requires a second press within 8s as confirmation (modern parity).
	 */
	private String copyTokenPendingAlias = "";
	private long copyTokenConfirmUntil;
	private String copyRefreshPendingAlias = "";
	private long copyRefreshConfirmUntil;

	private boolean confirmCopy(String alias, boolean isRefresh) {
		long now = System.currentTimeMillis();
		if (isRefresh) {
			if (copyRefreshPendingAlias.equals(alias) && now < copyRefreshConfirmUntil) {
				copyRefreshPendingAlias = "";
				copyRefreshConfirmUntil = 0L;
				return true;
			}
			copyRefreshPendingAlias = alias;
			copyRefreshConfirmUntil = now + 8000L;
			return false;
		}
		if (copyTokenPendingAlias.equals(alias) && now < copyTokenConfirmUntil) {
			copyTokenPendingAlias = "";
			copyTokenConfirmUntil = 0L;
			return true;
		}
		copyTokenPendingAlias = alias;
		copyTokenConfirmUntil = now + 8000L;
		return false;
	}

	private void copyToken(){
		if (queriedaccounts.isEmpty()) return;
		ExtendedAccountData data = queriedaccounts.get(selectedAccountIndex);
		String token = "";
		try {
			if (isCookieAccount(data)) {
				token = data.cookieAccessToken();
			} else if (Minecraft.getMinecraft().getSession() != null && data.alias.equals(Minecraft.getMinecraft().getSession().getUsername())) {
				token = Minecraft.getMinecraft().getSession().getToken();
			}
		} catch (Throwable ignored) {
			token = "";
		}
		if (token != null && !token.isEmpty()) {
			if (!confirmCopy(data.alias, false)) {
				loginfailed = new Throwable(I18n.format("ias.copyToken.confirm", data.alias));
				return;
			}
			setClipboardString(token);
			loginfailed = new Throwable("Copied session token for " + data.alias + " to clipboard.");
		}
	}

	/**
	 * Copies the refresh token of the selected account to the clipboard.
	 */
	private void copyRefreshToken(){
		if (queriedaccounts.isEmpty()) return;
		ExtendedAccountData data = queriedaccounts.get(selectedAccountIndex);
		String ref = "";
		try {
			ref = data.cookieRefreshToken();
		} catch (Throwable ignored) {
			ref = "";
		}
		if (ref != null && !ref.isEmpty()) {
			if (!confirmCopy(data.alias, true)) {
				loginfailed = new Throwable(I18n.format("ias.copyRefreshToken.confirm", data.alias));
				return;
			}
			setClipboardString(ref);
			loginfailed = new Throwable("Copied refresh token for " + data.alias + " to clipboard.");
		} else {
			loginfailed = new Throwable(I18n.format("ias.accounts.copyRefreshToken.offline"));
		}
	}

	/**
	 * Add an account
	 */
	private void add(){
		mc.displayGuiScreen(new GuiAddAccount());
	}
	/**
	 * Login to the account in offline mode, then return to main menu
	 * @param selected
	 * 		The index of the account to log in to
	 */
	private void logino(int selected){
		ExtendedAccountData data = queriedaccounts.get(selected);
		AltManager.getInstance().setUserOffline(data.alias);
		loginfailed = null;
		Minecraft.getMinecraft().displayGuiScreen(null);
		ExtendedAccountData current = getCurrentAsEditable();
		current.useCount++;
		current.lastused=JavaTools.getJavaCompat().getDate();
	}
	/**
	 * Attempt login to the account, then return to main menu if successful
	 * @param selected
	 * 		The index of the account to log in to
	 */
	private void login(int selected){
		ExtendedAccountData data = queriedaccounts.get(selected);
		boolean cookieAccount = isCookieAccount(data);
		loginfailed = cookieAccount ? setCookieSession(data) : AltManager.getInstance().setUser(data.user, data.pass);
		if (loginfailed == null) {
			Minecraft.getMinecraft().displayGuiScreen(null);
			ExtendedAccountData current = getCurrentAsEditable();
			current.premium=EnumBool.TRUE;
			current.useCount++;
			current.lastused=JavaTools.getJavaCompat().getDate();
		}else if(loginfailed instanceof AlreadyLoggedInException){
			getCurrentAsEditable().lastused=JavaTools.getJavaCompat().getDate();
		}else if(!cookieAccount && HttpTools.ping("http://minecraft.net")){
			getCurrentAsEditable().premium=EnumBool.FALSE;
		}
	}
	/**
	 * Cookie imports already contain a Minecraft services access token.  Passing
	 * one to the legacy Yggdrasil password login endpoint causes the
	 * "Cannot contact authentication server" error when switching accounts.
	 */
	private Throwable setCookieSession(ExtendedAccountData data) {
		try {
			String username = "";
			try {
				username = EncryptionTools.decode(data.user);
			} catch (Throwable ignored) {
				username = data.alias == null ? "" : data.alias;
			}
			String token = "";
			try {
				token = data.cookieAccessToken();
			} catch (Throwable ignored) {
				token = "";
			}
			String uuid = data.cookieUuid;
			try {
				net.minecraft.util.Session session = Minecraft.getMinecraft().getSession();
				if (session != null && session.getUsername() != null && session.getUsername().equals(username)
						&& session.getToken() != null && session.getToken().equals(token)
						&& !ConfigValues.ENABLERELOG) {
					return new AlreadyLoggedInException();
				}
			} catch (Throwable ignored) {
			}

			CookieAuth.MinecraftProfile profile;
			try {
				if (token == null || token.isEmpty()) {
					throw new CookieAuthException("Empty session token.", "ias.error.cookie.expired");
				}
				profile = CookieAuth.profileFromAccessToken(token);
			} catch (Throwable expired) {
				String refresh = "";
				try {
					refresh = data.cookieRefreshToken();
				} catch (Throwable ignored) {
				}
				if (refresh == null || refresh.isEmpty()) {
					throw expired;
				}
				profile = CookieAuth.profileFromRefreshToken(refresh);
			}
			username = profile.name;
			uuid = profile.uuid;
			token = profile.token;
			data.updateCookieTokens(token, profile.refreshToken, uuid, username);
			Config.save();
			MR.setSession(new net.minecraft.util.Session(username, uuid, token, "mojang"));
			return null;
		} catch (Throwable t) {
			return t;
		}
	}

	/** Recognizes the JWT-shaped access tokens stored by older cookie imports. */
	private boolean isCookieAccount(ExtendedAccountData data) {
		if (data.isCookieSession()) {
			return true;
		}
		try {
			String token = EncryptionTools.decode(data.pass);
			int firstDot = token.indexOf('.');
			return token.startsWith("eyJ") && firstDot > 3 && token.indexOf('.', firstDot + 1) > firstDot + 1;
		} catch (Throwable ignored) {
			return false;
		}
	}
	/**
	 * Edits the current account's information
	 */
	private void edit(){
		mc.displayGuiScreen(new GuiEditAccount(selectedAccountIndex));
	}

	private void updateQueried(){
		queriedaccounts = convertData();
		if(!query.equals(I18n.format("ias.search")) && !query.equals("")){
			for(int i=0;i<queriedaccounts.size();i++){
				if(!queriedaccounts.get(i).alias.contains(query) && ConfigValues.CASESENSITIVE){
					queriedaccounts.remove(i);
					i--;
				}else if(!queriedaccounts.get(i).alias.toLowerCase().contains(query.toLowerCase()) && !ConfigValues.CASESENSITIVE){
					queriedaccounts.remove(i);
					i--;
				}
			}
		}
		if(!queriedaccounts.isEmpty()){
			while(selectedAccountIndex >= queriedaccounts.size()){
				selectedAccountIndex--;
			}
		}
	}

	@Override
	protected void keyTyped(char character, int keyIndex) {
		if (keyIndex == Keyboard.KEY_UP && !queriedaccounts.isEmpty()) {
			if (selectedAccountIndex > 0) {
				selectedAccountIndex--;
			}
		} else if (keyIndex == Keyboard.KEY_DOWN && !queriedaccounts.isEmpty()) {
			if (selectedAccountIndex < queriedaccounts.size() - 1) {
				selectedAccountIndex++;
			}
		} else if(keyIndex == Keyboard.KEY_ESCAPE){
			escape();
		} else if(keyIndex == Keyboard.KEY_DELETE && delete.enabled){
			delete();
		} else if(character == '+'){
			add();
		} else if(character == '/' && edit.enabled){
			edit();
		} else if(!search.isFocused() && isCtrlKeyDown() && keyIndex == Keyboard.KEY_C) {
			copyToken();
		} else if(!search.isFocused() && isCtrlKeyDown() && (keyIndex == Keyboard.KEY_R || keyIndex == Keyboard.KEY_X)) {
			copyRefreshToken();
		} else if(!search.isFocused() && keyIndex == Keyboard.KEY_R) {
			reloadSkins();
		} else if(keyIndex == Keyboard.KEY_RETURN && !search.isFocused() && (login.enabled || loginoffline.enabled)){
			if((Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) && loginoffline.enabled){
				logino(selectedAccountIndex);
			}else{
				if(login.enabled)
					login(selectedAccountIndex);
			}
		} else if(keyIndex == Keyboard.KEY_BACK){
			if (search.isFocused() && query.length() > 0) {
				query = query.substring(0, query.length()-1);
				updateText();
				updateQueried();
			}
		} else if (keyIndex == Keyboard.KEY_F5) {
			reloadSkins();
		} else if (character != 0) {
			if (search.isFocused()) {
				if(keyIndex == Keyboard.KEY_RETURN){
					search.setFocused(false);
					updateText();
					updateQueried();
					return;
				}
				query += character;
				updateText();
				updateQueried();
			}
		}
	}
	private ArrayList<ExtendedAccountData> convertData(){
		ArrayList<AccountData> tmp = (ArrayList<AccountData>) AltDatabase.getInstance().getAlts().clone();
		ArrayList<ExtendedAccountData> converted = new ArrayList();
		int index=0;
		for(AccountData data : tmp){
			if(data instanceof ExtendedAccountData){
				converted.add((ExtendedAccountData) data);
			}else{
				converted.add(new ExtendedAccountData(EncryptionTools.decode(data.user), EncryptionTools.decode(data.pass), data.alias));
				AltDatabase.getInstance().getAlts().set(index, new ExtendedAccountData(EncryptionTools.decode(data.user), EncryptionTools.decode(data.pass), data.alias));
			}
			index++;
		}
		return converted;
	}
	private ArrayList<AccountData> getAccountList(){
		return AltDatabase.getInstance().getAlts();
	}
	private ExtendedAccountData getCurrentAsEditable(){
		for(AccountData dat : getAccountList()){
			if(dat instanceof ExtendedAccountData){
				if(dat.equals(queriedaccounts.get(selectedAccountIndex))){
					return (ExtendedAccountData) dat;
				}
			}
		}
		return null;
	}
	private void updateButtons(){
		boolean hasAccounts = !queriedaccounts.isEmpty();
		boolean loginOk = false;
		if (hasAccounts) {
			try {
				String pass = EncryptionTools.decode(queriedaccounts.get(selectedAccountIndex).pass);
				loginOk = pass != null && !pass.equals("");
			} catch (Throwable ignored) {
				loginOk = false;
			}
		}
		login.enabled = hasAccounts && loginOk;
		loginoffline.enabled = hasAccounts;
		delete.enabled = hasAccounts;
		edit.enabled = hasAccounts;
		reloadskins.enabled = !AltDatabase.getInstance().getAlts().isEmpty();
		try {
			logout.enabled = IAS.canRestoreLaunchSession();
		} catch (Throwable ignored) {
			logout.enabled = false;
		}
		if (manageProfile != null) {
			boolean cookieOnly = false;
			if (hasAccounts) {
				try {
					cookieOnly = isCookieAccount(queriedaccounts.get(selectedAccountIndex));
				} catch (Throwable ignored) {
					cookieOnly = false;
				}
			}
			manageProfile.enabled = cookieOnly;
		}
		if (checkHypixel != null) {
			checkHypixel.enabled = !hypixelCheckRunning && !queriedaccounts.isEmpty();
			checkHypixel.displayString = hypixelCheckRunning
					? I18n.format("ias.accounts.checkHypixel.running")
					: I18n.format("ias.accounts.checkHypixel");
		}
	}

	private String hypixelKey(ExtendedAccountData data) {
		if (data.isCookieSession() && data.cookieUuid != null && !data.cookieUuid.isEmpty()) {
			return "uuid:" + data.cookieUuid;
		}
		try {
			String user = EncryptionTools.decode(data.user);
			if (user != null && !user.isEmpty()) {
				return "user:" + user;
			}
		} catch (Throwable ignored) {
		}
		return "alias:" + (data.alias == null ? "" : data.alias);
	}

	private volatile int hypixelCheckId;

	private void checkAllHypixelBans() {
		if (hypixelCheckRunning) {
			return;
		}
		hypixelCheckRunning = true;
		final int myId = ++hypixelCheckId;
		updateButtons();
		final ArrayList<ExtendedAccountData> accounts = convertData();
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (ExtendedAccountData data : accounts) {
					if (myId != hypixelCheckId) {
						break;
					}
					final String key = hypixelKey(data);
					if (!canCheckHypixel(data)) {
						hypixelPhases.put(key, HypixelBanPhase.NOT_APPLICABLE);
						hypixelResults.remove(key);
						scheduleRefresh();
						continue;
					}
					hypixelPhases.put(key, HypixelBanPhase.CHECKING);
					hypixelResults.remove(key);
					scheduleRefresh();
					try {
						HypixelBanResult result = resolveAndCheck(data);
						hypixelResults.put(key, result);
					} catch (Throwable t) {
						hypixelResults.put(key, HypixelBanResult.error(t.getMessage() != null ? t.getMessage() : "Unknown error"));
					}
					hypixelPhases.put(key, HypixelBanPhase.UNKNOWN);
					scheduleRefresh();
					try {
						Thread.sleep(6000L);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				if (myId == hypixelCheckId) {
					hypixelCheckRunning = false;
					try {
						ru.vidtu.iasfork.checks.ChecksCache.save(hypixelResults);
					} catch (Throwable ignored) {
					}
					scheduleRefresh();
				}
			}
		}, "IAS-HypixelCheck").start();
	}

	private void scheduleRefresh() {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null) {
				hypixelCheckRunning = false;
				return;
			}
			mc.addScheduledTask(new Runnable() {
				@Override
				public void run() {
					try {
						updateButtons();
					} catch (Throwable ignored) {
					}
				}
			});
		} catch (Throwable ignored) {
		}
	}

	/**
	 * Yggdrasil password auth was shut down by Mojang; only cookie/Microsoft
	 * sessions can be checked. Password accounts are marked NOT_APPLICABLE.
	 */
	private boolean canCheckHypixel(ExtendedAccountData data) {
		try {
			return isCookieAccount(data);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private HypixelBanResult resolveAndCheck(ExtendedAccountData data) throws Exception {
		if (!isCookieAccount(data)) {
			throw new IllegalStateException(I18n.format("ias.accounts.copyRefreshToken.offline"));
		}
		CookieAuth.MinecraftProfile profile = resolveCookieProfile(data);
		return HypixelBanChecker.checkBan(profile.name, parseUuid(profile.uuid), profile.token);
	}

	private CookieAuth.MinecraftProfile resolveCookieProfile(ExtendedAccountData data) throws Exception {
		String token = "";
		try {
			token = data.cookieAccessToken();
		} catch (Throwable ignored) {
			token = "";
		}
		try {
			if (token != null && !token.isEmpty()) {
				return CookieAuth.profileFromAccessToken(token);
			}
			throw new CookieAuthException("Empty session token.", "ias.error.cookie.expired");
		} catch (Throwable expired) {
			String refresh = "";
			try {
				refresh = data.cookieRefreshToken();
			} catch (Throwable ignored) {
				refresh = "";
			}
			if (refresh == null || refresh.isEmpty()) {
				if (expired instanceof Exception) {
					throw (Exception) expired;
				}
				throw new Exception(expired);
			}
			CookieAuth.MinecraftProfile refreshed = CookieAuth.profileFromRefreshToken(refresh);
			try {
				data.updateCookieTokens(refreshed.token, refreshed.refreshToken, refreshed.uuid, refreshed.name);
				Config.save();
			} catch (Throwable ignored) {
			}
			return refreshed;
		}
	}

	private UUID parseUuid(String uuid) {
		if (uuid == null || uuid.trim().isEmpty()) {
			return new UUID(0L, 0L);
		}
		try {
			if (uuid.contains("-")) {
				return UUID.fromString(uuid.trim());
			}
			return UUIDTypeAdapter.fromString(uuid.trim());
		} catch (Throwable ignored) {
			return new UUID(0L, 0L);
		}
	}

	private String hypixelSuffix(ExtendedAccountData data) {
		String key = hypixelKey(data);
		HypixelBanPhase phase = hypixelPhases.get(key);
		if (phase == HypixelBanPhase.NOT_APPLICABLE) {
			return "";
		}
		if (phase == HypixelBanPhase.CHECKING) {
			return " H?";
		}
		HypixelBanResult result = hypixelResults.get(key);
		if (result == null) {
			return "";
		}
		switch (result.status()) {
			case UNBANNED:
				return " H\u2713";
			case BANNED:
				return " H\u2715";
			case ERROR:
			default:
				return " H!";
		}
	}

	private int hypixelColor(ExtendedAccountData data) {
		String key = hypixelKey(data);
		HypixelBanPhase phase = hypixelPhases.get(key);
		if (phase == HypixelBanPhase.CHECKING) {
			return 0xFFFF00;
		}
		HypixelBanResult result = hypixelResults.get(key);
		if (result == null) {
			return 0x808080;
		}
		switch (result.status()) {
			case UNBANNED:
				return 0x00FF00;
			case BANNED:
				return 0xFF4040;
			case ERROR:
			default:
				return 0xFFA000;
		}
	}

	private java.util.List<String> hypixelTooltip(ExtendedAccountData data) {
		String key = hypixelKey(data);
		HypixelBanPhase phase = hypixelPhases.get(key);
		if (phase == HypixelBanPhase.CHECKING) {
			return Collections.singletonList(I18n.format("ias.hypixel.checking"));
		}
		HypixelBanResult result = hypixelResults.get(key);
		if (result == null) {
			return Collections.singletonList(I18n.format("ias.hypixel.unknown"));
		}
		java.util.List<String> lines = new ArrayList<String>();
		switch (result.status()) {
			case UNBANNED:
				lines.add(I18n.format("ias.hypixel.unbanned"));
				break;
			case BANNED:
				lines.add(I18n.format("ias.hypixel.banned"));
				if (result.banType() != null && !result.banType().trim().isEmpty()) {
					lines.add(I18n.format("ias.hypixel.type", result.banType()));
				}
				if (result.duration() != null && !result.duration().trim().isEmpty()) {
					lines.add(I18n.format("ias.hypixel.duration", result.duration()));
				}
				if (result.reason() != null && !result.reason().trim().isEmpty()) {
					lines.add(I18n.format("ias.hypixel.reason", result.reason()));
				}
				break;
			case ERROR:
			default:
				lines.add(I18n.format("ias.hypixel.error", result.errorMessage() != null ? result.errorMessage() : "Unknown error"));
				break;
		}
		return lines;
	}

	class List extends GuiSlot
	{
		public List(Minecraft mcIn)
		{
			super(mcIn, GuiAccountSelector.this.width, GuiAccountSelector.this.height, 32, GuiAccountSelector.this.height - 64, 14);
		}

		@Override
		protected int getSize()
		{
			return GuiAccountSelector.this.queriedaccounts.size();
		}

		@Override
		protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY)
		{
			GuiAccountSelector.this.selectedAccountIndex = slotIndex;
			GuiAccountSelector.this.updateButtons();

			if (isDoubleClick && GuiAccountSelector.this.login.enabled)
			{
				GuiAccountSelector.this.login(slotIndex);
			}
		}

		@Override
		protected boolean isSelected(int slotIndex)
		{
			return slotIndex == GuiAccountSelector.this.selectedAccountIndex;
		}

		@Override
		protected int getContentHeight()
		{
			return GuiAccountSelector.this.queriedaccounts.size() * 14;
		}

		@Override
		protected void drawBackground()
		{
			GuiAccountSelector.this.drawDefaultBackground();
		}

		@Override
		protected void drawSlot(int entryID, int slotX, int slotY, int slotH, int mouseX, int mouseY)
		{
			ExtendedAccountData data = queriedaccounts.get(entryID);
			String s = data.alias;
			if (StringUtils.isEmpty(s))
			{
				s = I18n.format("ias.alt") + " " + (entryID + 1);
			}
			int color = 16777215;
			try {
				if (Minecraft.getMinecraft().getSession() != null
						&& Minecraft.getMinecraft().getSession().getUsername() != null
						&& Minecraft.getMinecraft().getSession().getUsername().equals(data.alias))
				{
					color = 0x00FF00;
				}
			} catch (Throwable ignored) {
			}
			GuiAccountSelector.this.drawString(GuiAccountSelector.this.fontRendererObj, s, slotX + 2, slotY + 1, color);
			String suffix = "";
			try {
				suffix = GuiAccountSelector.this.hypixelSuffix(data);
			} catch (Throwable ignored) {
				suffix = "";
			}
			if (!suffix.isEmpty()) {
				int suffixX = slotX + 2 + GuiAccountSelector.this.fontRendererObj.getStringWidth(s);
				int suffixWidth = GuiAccountSelector.this.fontRendererObj.getStringWidth(suffix);
				int suffixColor = 0x808080;
				try {
					suffixColor = GuiAccountSelector.this.hypixelColor(data);
				} catch (Throwable ignored) {
				}
				GuiAccountSelector.this.drawString(GuiAccountSelector.this.fontRendererObj, suffix, suffixX, slotY + 1, suffixColor);
				// mouseX/mouseY are screen coords; slotY is the entry's Y, so the
				// hover test is scroll-safe (old code mixed list bounds with slot Y).
				if (mouseX >= suffixX && mouseX <= suffixX + suffixWidth + 4
						&& mouseY >= slotY && mouseY <= slotY + 14) {
					try {
						GuiAccountSelector.this.hoveredTooltip = GuiAccountSelector.this.hypixelTooltip(data);
					} catch (Throwable ignored) {
					}
				}
			}
		}
	}
}
