package the_fireplace.ias.tools;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Takes care of loading and drawing skin preview images to the screen.
 * @author dayanto
 * @author The_Fireplace
 */
public class SkinRender
{
	private final File file;
	private DynamicTexture previewTexture;
	private ResourceLocation resourceLocation;
	private final TextureManager textureManager;

	public SkinRender(TextureManager textureManager, File file)
	{
		this.textureManager = textureManager;
		this.file = file;
	}

	/**
	 * Attempts to load the image. Returns whether it was successful or not.
	 */
	private boolean loadPreview()
	{
		try {
			if (!file.exists()) return false;
			BufferedImage image = ImageIO.read(file);
			if (image == null) return false;
			previewTexture = new DynamicTexture(image);
			resourceLocation = textureManager.getDynamicTextureLocation(Reference.MODID, previewTexture);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public void drawImage(int xPos, int yPos, int width, int height)
	{
		if(previewTexture == null) {
			boolean successful = loadPreview();
			if(!successful){
				return;
			}
		}
		try {
			previewTexture.updateDynamicTexture();
		} catch (Throwable ignored) {
			return;
		}

		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		textureManager.bindTexture(resourceLocation);
		Gui.drawScaledCustomSizeModalRect(xPos, yPos, 0, 0, 16, 32, width, height, 16.0F, 32.0F);
		GlStateManager.disableBlend();
	}

	/** Frees the GL texture; call before replacing the renderer. */
	public void delete() {
		try {
			if (previewTexture != null) {
				previewTexture.deleteGlTexture();
			}
		} catch (Throwable ignored) {
		} finally {
			previewTexture = null;
		}
	}
}