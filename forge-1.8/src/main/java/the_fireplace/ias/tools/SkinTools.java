package the_fireplace.ias.tools;

import com.github.mrebhan.ingameaccountswitcher.tools.alt.AccountData;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
/**
 * Tools that have to do with Skins
 * @author The_Fireplace
 */
@SideOnly(Side.CLIENT)
public class SkinTools {
	public static final File cachedir = new File(Minecraft.getMinecraft().mcDataDir, "cachedImages/skins/");
	private static final File skinOut = new File(cachedir, "temp.png");
	private static SkinRender skinRenderer;
	private static long lastSkinModified = -1;

	public static void buildSkin(String name){
		BufferedImage skin;
		try{
			File file = new File(cachedir, name + ".png");
			if (!file.exists()) {
				file = new File(cachedir, "MHF_Steve.png");
			}
			if (!file.exists()) {
				if(skinOut.exists())
					skinOut.delete();
				return;
			}
			skin = ImageIO.read(file);
			if (skin == null) {
				if(skinOut.exists())
					skinOut.delete();
				return;
			}
		}catch(IOException e){
			if(skinOut.exists())
				skinOut.delete();
			return;
		}

		BufferedImage drawing = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
		if(skin.getHeight() == 64) {// 64x64 skin
			int[] head = skin.getRGB(8, 8, 8, 8, null, 0, 8);
			int[] hat = skin.getRGB(40, 8, 8, 8, null, 0, 8);
			int[] headComposited = compositeLayer(head, hat);

			int[] torso = skin.getRGB(20, 20, 8, 12, null, 0, 8);
			int[] jacket = skin.getRGB(20, 36, 8, 12, null, 0, 8);
			int[] torsoComposited = compositeLayer(torso, jacket);

			int[] rarm = skin.getRGB(44, 20, 4, 12, null, 0, 4);
			int[] rarm2 = skin.getRGB(44, 36, 4, 12, null, 0, 4);
			int[] rarmComposited = compositeLayer(rarm, rarm2);

			int[] larm = skin.getRGB(36, 52, 4, 12, null, 0, 4);
			int[] larm2 = skin.getRGB(52, 52, 4, 12, null, 0, 4);
			int[] larmComposited = compositeLayer(larm, larm2);

			int[] rleg = skin.getRGB(4, 20, 4, 12, null, 0, 4);
			int[] rleg2 = skin.getRGB(4, 36, 4, 12, null, 0, 4);
			int[] rlegComposited = compositeLayer(rleg, rleg2);

			int[] lleg = skin.getRGB(20, 52, 4, 12, null, 0, 4);
			int[] lleg2 = skin.getRGB(4, 52, 4, 12, null, 0, 4);
			int[] llegComposited = compositeLayer(lleg, lleg2);

			drawing.setRGB(4, 0, 8, 8, headComposited, 0, 8);
			drawing.setRGB(4, 8, 8, 12, torsoComposited, 0, 8);
			drawing.setRGB(0, 8, 4, 12, rarmComposited, 0, 4);
			drawing.setRGB(12, 8, 4, 12, larmComposited, 0, 4);
			drawing.setRGB(4, 20, 4, 12, rlegComposited, 0, 4);
			drawing.setRGB(8, 20, 4, 12, llegComposited, 0, 4);
		}else{// 64x32 skin
			int[] head = skin.getRGB(8, 8, 8, 8, null, 0, 8);
			int[] hat = skin.getRGB(40, 8, 8, 8, null, 0, 8);
			int[] headComposited = compositeLayer(head, hat);

			int[] torso = skin.getRGB(20, 20, 8, 12, null, 0, 8);
			int[] rarm = skin.getRGB(44, 20, 4, 12, null, 0, 4);
			int[] larm = flipHorizontally(rarm, 4, 12);
			int[] rleg = skin.getRGB(4, 20, 4, 12, null, 0, 4);
			int[] lleg = flipHorizontally(rleg, 4, 12);

			drawing.setRGB(4, 0, 8, 8, headComposited, 0, 8);
			drawing.setRGB(4, 8, 8, 12, torso, 0, 8);
			drawing.setRGB(0, 8, 4, 12, rarm, 0, 4);
			drawing.setRGB(12, 8, 4, 12, larm, 0, 4);
			drawing.setRGB(4, 20, 4, 12, rleg, 0, 4);
			drawing.setRGB(8, 20, 4, 12, lleg, 0, 4);
		}
		try{
			ImageIO.write(drawing, "png", skinOut);
			lastSkinModified = skinOut.lastModified();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	private static int[] compositeLayer(int[] base, int[] overlay) {
		int[] out = new int[base.length];
		for (int i = 0; i < base.length; i++) {
			int over = overlay[i];
			int alpha = (over >>> 24) & 0xFF;
			if (alpha == 0) {
				out[i] = base[i];
			} else if (alpha == 255) {
				out[i] = over;
			} else {
				int baseColor = base[i];
				int bA = (baseColor >>> 24) & 0xFF;
				int bR = (baseColor >>> 16) & 0xFF;
				int bG = (baseColor >>> 8) & 0xFF;
				int bB = baseColor & 0xFF;

				int oR = (over >>> 16) & 0xFF;
				int oG = (over >>> 8) & 0xFF;
				int oB = over & 0xFF;

				int r = (oR * alpha + bR * (255 - alpha)) / 255;
				int g = (oG * alpha + bG * (255 - alpha)) / 255;
				int b = (oB * alpha + bB * (255 - alpha)) / 255;
				int a = Math.max(bA, alpha);
				out[i] = (a << 24) | (r << 16) | (g << 8) | b;
			}
		}
		return out;
	}

	private static int[] flipHorizontally(int[] src, int width, int height) {
		int[] out = new int[src.length];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				out[y * width + (width - 1 - x)] = src[y * width + x];
			}
		}
		return out;
	}

	/**
	 * Renders the skin built in buildSkin(String name)
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public static void javDrawSkin(int x, int y, int width, int height){
		if(!skinOut.exists())
			return;
		long modified = skinOut.lastModified();
		if (skinRenderer == null || modified != lastSkinModified) {
			if (skinRenderer != null) {
				try {
					skinRenderer.delete();
				} catch (Throwable ignored) {
				}
			}
			skinRenderer = new SkinRender(Minecraft.getMinecraft().getTextureManager(), skinOut);
			lastSkinModified = modified;
		}
		skinRenderer.drawImage(x,y,width,height);
	}

	public static void cacheSkins(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				ensureSteveSkin();
				if(!cachedir.exists())
					if(!cachedir.mkdirs())
						System.out.println("Skin cache directory creation failed.");
				for(AccountData data : AltDatabase.getInstance().getAlts()){
					File file = new File(cachedir, data.alias+".png");
					try{
						java.net.URLConnection conn = new URL(String.format("https://minotar.net/skin/%s.png", data.alias)).openConnection();
						conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
						conn.setConnectTimeout(5000);
						conn.setReadTimeout(5000);
						InputStream is = conn.getInputStream();
						if(file.exists())
							file.delete();
						file.createNewFile();
						OutputStream os = new FileOutputStream(file);

						byte[] b = new byte[2048];
						int length;

						while((length = is.read(b)) != -1){
							os.write(b, 0, length);
						}
						is.close();
						os.close();
					}catch(IOException e){
						// Ignored, fallback to Steve
					}
				}
			}
		}).start();
	}

	public static void cacheSkin(final String name) {
		cacheSkin(name, false);
	}

	public static void cacheSkin(final String name, final boolean force) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (!cachedir.exists()) cachedir.mkdirs();
				File file = new File(cachedir, name + ".png");
				if (file.exists() && !force) return;
				try {
					java.net.URLConnection conn = new URL(String.format("https://minotar.net/skin/%s.png", name)).openConnection();
					conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
					conn.setConnectTimeout(5000);
					conn.setReadTimeout(5000);
					InputStream is = conn.getInputStream();
					if (file.exists()) file.delete();
					file.createNewFile();
					OutputStream os = new FileOutputStream(file);
					byte[] b = new byte[2048];
					int length;
					while ((length = is.read(b)) != -1) {
						os.write(b, 0, length);
					}
					is.close();
					os.close();
					buildSkin(name);
				} catch (Throwable ignored) {}
			}
		}).start();
	}

	public static void ensureSteveSkin() {
		File steve = new File(cachedir, "MHF_Steve.png");
		if (!steve.exists()) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						if (!cachedir.exists()) cachedir.mkdirs();
						java.net.URLConnection conn = new URL("https://minotar.net/skin/MHF_Steve.png").openConnection();
						conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
						conn.setConnectTimeout(5000);
						conn.setReadTimeout(5000);
						InputStream is = conn.getInputStream();
						File file = new File(cachedir, "MHF_Steve.png");
						if (file.exists()) file.delete();
						file.createNewFile();
						OutputStream os = new FileOutputStream(file);
						byte[] b = new byte[2048];
						int length;
						while ((length = is.read(b)) != -1) {
							os.write(b, 0, length);
						}
						is.close();
						os.close();
					} catch (Throwable ignored) {}
				}
			}).start();
		}
	}
}
