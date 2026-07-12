package the_fireplace.ias;

import com.github.mrebhan.ingameaccountswitcher.MR;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Session;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import the_fireplace.ias.config.ConfigValues;
import the_fireplace.ias.events.ClientEvents;
import the_fireplace.ias.input.IASKeyBindings;
import the_fireplace.ias.tools.Reference;
import the_fireplace.ias.tools.SkinTools;
import the_fireplace.iasencrypt.Standards;
import java.util.Objects;
/**
 * @author The_Fireplace
 */
@Mod(modid=Reference.MODID, name=Reference.MODNAME, clientSideOnly=true, guiFactory="the_fireplace.ias.config.IASGuiFactory", updateJSON = "http://thefireplace.bitnamiapp.com/jsons/ias.json", acceptedMinecraftVersions = "[1.8.9,)")
public class IAS {
	public static Configuration config;
	private static Property CASESENSITIVE_PROPERTY;
	private static Property ENABLERELOG_PROPERTY;
	/** The account selected by the launcher before IAS changes any session. */
	private static volatile Session launchSession;

	/**
	 * Captures the launcher-selected session once, before an imported cookie can
	 * replace it.
	 */
	public static void captureLaunchSession() {
		if (launchSession != null) return;
		synchronized (IAS.class) {
			if (launchSession == null) {
				Session current = Minecraft.getMinecraft().getSession();
				if (current != null) launchSession = current;
			}
		}
	}

	/** @return whether the launcher session is available and is not current. */
	public static boolean canRestoreLaunchSession() {
		captureLaunchSession();
		Session initial = launchSession;
		Session current = Minecraft.getMinecraft().getSession();
		return initial != null && current != null
				&& (!Objects.equals(initial.getPlayerID(), current.getPlayerID())
						|| !Objects.equals(initial.getToken(), current.getToken()));
	}

	/** Restores the session selected by the launcher when Minecraft started. */
	public static void restoreLaunchSession() throws Exception {
		captureLaunchSession();
		if (launchSession == null) throw new IllegalStateException("Launcher session is unavailable.");
		MR.setSession(launchSession);
	}

	public static void syncConfig(){
		ConfigValues.CASESENSITIVE = CASESENSITIVE_PROPERTY.getBoolean();
		ConfigValues.ENABLERELOG = ENABLERELOG_PROPERTY.getBoolean();
		if(config.hasChanged())
			config.save();
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		CASESENSITIVE_PROPERTY = config.get(Configuration.CATEGORY_GENERAL, ConfigValues.CASESENSITIVE_NAME, ConfigValues.CASESENSITIVE_DEFAULT, I18n.format(ConfigValues.CASESENSITIVE_NAME+".tooltip"));
		ENABLERELOG_PROPERTY = config.get(Configuration.CATEGORY_GENERAL, ConfigValues.ENABLERELOG_NAME, ConfigValues.ENABLERELOG_DEFAULT, I18n.format(ConfigValues.ENABLERELOG_NAME+".tooltip"));
		syncConfig();
		if(!event.getModMetadata().version.equals("${version}"))//Dev environment needs to use a local list, to avoid issues
			Standards.updateFolder();
		else
			System.out.println("Dev environment detected!");
	}
	@EventHandler
	public void init(FMLInitializationEvent event){
		MR.init();
		captureLaunchSession();
		ClientRegistry.registerKeyBinding(IASKeyBindings.OPEN);
		ClientEvents clientEvents = new ClientEvents();
		MinecraftForge.EVENT_BUS.register(clientEvents);
		FMLCommonHandler.instance().bus().register(clientEvents);
		Standards.importAccounts();
	}
	@EventHandler
	public void postInit(FMLPostInitializationEvent event){
		SkinTools.cacheSkins();
	}
}
