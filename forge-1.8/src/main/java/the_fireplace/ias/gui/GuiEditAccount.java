package the_fireplace.ias.gui;

import com.github.mrebhan.ingameaccountswitcher.tools.alt.AccountData;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase;
import the_fireplace.ias.account.ExtendedAccountData;
import the_fireplace.ias.enums.EnumBool;
import the_fireplace.ias.tools.JavaTools;
import the_fireplace.iasencrypt.EncryptionTools;
/**
 * The GUI where the alt is added
 * @author The_Fireplace
 * @author evilmidget38
 */
class GuiEditAccount extends AbstractAccountGui {
	private final ExtendedAccountData data;
	private final int selectedIndex;

	public GuiEditAccount(int index){
		super("ias.editaccount");
		this.selectedIndex=index;
		AccountData data = AltDatabase.getInstance().getAlts().get(index);

		if(data instanceof ExtendedAccountData){
			this.data = (ExtendedAccountData) data;
		}else{
			this.data = new ExtendedAccountData(data.user, data.pass, data.alias, 0, JavaTools.getJavaCompat().getDate(), EnumBool.UNKNOWN);
		}
	}

	@Override
	public void initGui() {
		super.initGui();
		setUsername(EncryptionTools.decode(data.user));
		setPassword(EncryptionTools.decode(data.pass));
		if (data.isCookieSession() || (data.cookieAccessToken() != null && !data.cookieAccessToken().isEmpty()) || (data.cookieRefreshToken() != null && !data.cookieRefreshToken().isEmpty())) {
			this.buttonList.add(new net.minecraft.client.gui.GuiButton(10, this.width / 2 - 100, this.height - 52, 200, 20, "Manage Skin / Name Online"));
		}
	}

	@Override
	protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
		super.actionPerformed(button);
		if (button.id == 10) {
			this.mc.displayGuiScreen(new GuiManageProfile(this, this.data));
		}
	}

	@Override
	public void complete()
	{
		ExtendedAccountData updated = new ExtendedAccountData(getUsername(), getPassword(), hasUserChanged ? getUsername() : data.alias, data.useCount, data.lastused, data.premium);
		updated.cookieSession = data.cookieSession;
		updated.cookieUuid = data.cookieUuid;
		updated.cookieAccess = data.cookieAccess;
		updated.cookieRefresh = data.cookieRefresh;
		AltDatabase.getInstance().getAlts().set(selectedIndex, updated);
	}

}
