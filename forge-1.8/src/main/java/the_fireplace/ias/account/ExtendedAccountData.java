package the_fireplace.ias.account;

import java.util.Arrays;

import com.github.mrebhan.ingameaccountswitcher.tools.alt.AccountData;

import the_fireplace.ias.enums.EnumBool;
import the_fireplace.ias.tools.JavaTools;
import the_fireplace.iasencrypt.EncryptionTools;
/**
 * @author The_Fireplace
 */
public class ExtendedAccountData extends AccountData {
	private static final long serialVersionUID = -909128662161235160L;

	public EnumBool premium;
	public int[] lastused;
	public int useCount;
	/** True when {@link #pass} holds a Minecraft services access token, not a password. */
	public boolean cookieSession;
	/** Minecraft profile UUID required to restore a cookie-imported session. */
	public String cookieUuid;
	/** Latest encrypted Minecraft services access token for a cookie-imported session. */
	public String cookieAccess;
	/** Encrypted Localts refresh token, or empty for cookie-only imports. */
	public String cookieRefresh;

	public ExtendedAccountData(String user, String pass, String alias) {
		super(user, pass, alias);
		useCount = 0;
		lastused = JavaTools.getJavaCompat().getDate();
		premium = EnumBool.UNKNOWN;
		cookieSession = false;
		cookieUuid = "";
		cookieAccess = "";
		cookieRefresh = "";
	}

	public ExtendedAccountData(String user, String pass, String alias, int useCount, int[] lastused, EnumBool premium) {
		super(user, pass, alias);
		this.useCount = useCount;
		this.lastused = lastused;
		this.premium = premium;
		this.cookieSession = false;
		this.cookieUuid = "";
		this.cookieAccess = "";
		this.cookieRefresh = "";
	}

	/**
	 * Creates an account backed by a Minecraft services access token obtained
	 * from a cookie import.
	 */
	public static ExtendedAccountData cookieSession(String name, String token, String uuid, String refreshToken) {
		ExtendedAccountData data = new ExtendedAccountData(name, token, name);
		data.cookieSession = true;
		data.cookieUuid = uuid;
		data.cookieAccess = EncryptionTools.encode(token);
		data.cookieRefresh = EncryptionTools.encode(refreshToken == null ? "" : refreshToken);
		return data;
	}

	public String cookieAccessToken() {
		return cookieAccess == null || cookieAccess.isEmpty() ? EncryptionTools.decode(pass) : EncryptionTools.decode(cookieAccess);
	}

	public String cookieRefreshToken() {
		return cookieRefresh == null || cookieRefresh.isEmpty() ? "" : EncryptionTools.decode(cookieRefresh);
	}

	public void updateCookieTokens(String accessToken, String refreshToken, String uuid, String name) {
		cookieSession = true;
		cookieUuid = uuid;
		cookieAccess = EncryptionTools.encode(accessToken);
		if (refreshToken != null && !refreshToken.isEmpty()) {
			cookieRefresh = EncryptionTools.encode(refreshToken);
		}
		if (name != null && !name.isEmpty()) {
			alias = name;
		}
	}

	public boolean isCookieSession() {
		return cookieSession && cookieUuid != null && !cookieUuid.isEmpty();
	}

	/**
	 * Adds or replaces a cookie account, matching by UUID first, then alias.
	 * The old {@code remove(newObject)} pattern never matched once tokens
	 * rotated, creating a duplicate on every login.
	 *
	 * @return true if an existing entry was replaced (duplicate), false if added new.
	 */
	public static boolean replaceOrAddCookieAccount(com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase db,
			ExtendedAccountData fresh) {
		java.util.ArrayList<com.github.mrebhan.ingameaccountswitcher.tools.alt.AccountData> alts = db.getAlts();
		for (int i = 0; i < alts.size(); i++) {
			com.github.mrebhan.ingameaccountswitcher.tools.alt.AccountData existing = alts.get(i);
			if (!(existing instanceof ExtendedAccountData)) {
				continue;
			}
			ExtendedAccountData ext = (ExtendedAccountData) existing;
			boolean uuidMatch = fresh.cookieUuid != null && !fresh.cookieUuid.isEmpty()
					&& fresh.cookieUuid.equals(ext.cookieUuid);
			boolean aliasMatch = fresh.alias != null && !fresh.alias.isEmpty()
					&& fresh.alias.equalsIgnoreCase(ext.alias);
			if (uuidMatch || aliasMatch) {
				fresh.useCount = Math.max(fresh.useCount, ext.useCount);
				if (ext.lastused != null) {
					fresh.lastused = ext.lastused;
				}
				alts.set(i, fresh);
				return true;
			}
		}
		alts.remove(fresh);
		alts.add(fresh);
		return false;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ExtendedAccountData other = (ExtendedAccountData) obj;
		if (!Arrays.equals(lastused, other.lastused)) {
			return false;
		}
		if (premium != other.premium) {
			return false;
		}
		if (useCount != other.useCount) {
			return false;
		}
		return user.equals(other.user) && pass.equals(other.pass)
				&& cookieSession == other.cookieSession
				&& (cookieUuid == null ? other.cookieUuid == null : cookieUuid.equals(other.cookieUuid))
				&& (cookieAccess == null ? other.cookieAccess == null : cookieAccess.equals(other.cookieAccess))
				&& (cookieRefresh == null ? other.cookieRefresh == null : cookieRefresh.equals(other.cookieRefresh));
	}
}
