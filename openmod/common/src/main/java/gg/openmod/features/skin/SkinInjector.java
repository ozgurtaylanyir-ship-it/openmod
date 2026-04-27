package gg.openmod.features.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public final class SkinInjector {
    private SkinInjector() {}

    public static void inject(Minecraft mc, ResourceLocation location, Path skinPath) throws IOException {
        mc.getTextureManager().release(location);
        BufferedImage buffered = ImageIO.read(skinPath.toFile());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(buffered, "PNG", baos);
        NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
        mc.getTextureManager().register(location, new DynamicTexture(nativeImage));
    }
}
