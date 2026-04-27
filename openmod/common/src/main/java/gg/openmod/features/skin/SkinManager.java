package gg.openmod.features.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import gg.openmod.OpenMod;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lokal skin kütüphanesi.
 * - Skin dosyalarını openmod/skins/ klasöründe saklar
 * - Minecraft'ın skin renderer'ına inject eder
 * - Mojang hesabı olmadan çalışır
 * - URL'den indirme desteği
 */
public class SkinManager {

    private final Path skinDir;
    private final List<SkinEntry> skins = new ArrayList<>();
    private SkinEntry activeSkin;

    public SkinManager() {
        this.skinDir = Minecraft.getInstance().gameDirectory.toPath()
            .resolve(OpenMod.get().getConfig().getSkinLibraryPath());
        try {
            Files.createDirectories(skinDir);
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Skin] Cannot create skin dir", e);
        }
        loadLibrary();
    }

    /**
     * Skin dosyasını kütüphaneye ekle.
     */
    public CompletableFuture<SkinEntry> addSkin(Path sourcePath, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dest = skinDir.resolve(name + ".png");
                Files.copy(sourcePath, dest);
                SkinEntry entry = new SkinEntry(name, dest);
                skins.add(entry);
                return entry;
            } catch (IOException e) {
                throw new RuntimeException("Failed to add skin: " + name, e);
            }
        });
    }

    /**
     * URL'den skin indir ve kütüphaneye ekle.
     * NameMC, crafatar gibi sitelerden çalışır.
     */
    public CompletableFuture<SkinEntry> downloadSkin(String url, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dest = skinDir.resolve(name + ".png");
                try (InputStream in = new URL(url).openStream()) {
                    Files.copy(in, dest);
                }
                // 64x64 validate
                BufferedImage img = ImageIO.read(dest.toFile());
                if (img.getWidth() != 64 || img.getHeight() != 64) {
                    Files.delete(dest);
                    throw new IllegalArgumentException("Invalid skin dimensions: " + img.getWidth() + "x" + img.getHeight());
                }
                SkinEntry entry = new SkinEntry(name, dest);
                skins.add(entry);
                OpenMod.LOGGER.info("[Skin] Downloaded: {}", name);
                return entry;
            } catch (IOException e) {
                throw new RuntimeException("Skin download failed", e);
            }
        });
    }

    /**
     * Aktif skin'i değiştir.
     * Minecraft resource manager'a texture olarak yükler.
     */
    public void applySkin(SkinEntry skin) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                // Skin texture'u resource manager'a yükle
                net.minecraft.resources.ResourceLocation skinLocation =
                    new net.minecraft.resources.ResourceLocation("openmod", "skins/" + skin.name());

                // Native resource manager ile skin inject
                SkinInjector.inject(mc, skinLocation, skin.path());

                this.activeSkin = skin;
                OpenMod.LOGGER.info("[Skin] Applied: {}", skin.name());
                OpenMod.get().getEventBus().fire(new SkinChangedEvent(skin));
            } catch (Exception e) {
                OpenMod.LOGGER.error("[Skin] Apply failed", e);
            }
        });
    }

    public void removeSkin(SkinEntry skin) {
        try {
            Files.deleteIfExists(skin.path());
            skins.remove(skin);
            if (skin.equals(activeSkin)) activeSkin = null;
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Skin] Remove failed", e);
        }
    }

    private void loadLibrary() {
        try (var stream = Files.list(skinDir)) {
            stream.filter(p -> p.toString().endsWith(".png"))
                .forEach(p -> {
                    String name = p.getFileName().toString().replace(".png", "");
                    skins.add(new SkinEntry(name, p));
                });
            OpenMod.LOGGER.info("[Skin] Loaded {} skins", skins.size());
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Skin] Library load failed", e);
        }
    }

    public List<SkinEntry> getSkins()      { return List.copyOf(skins); }
    public SkinEntry getActiveSkin()       { return activeSkin; }
    public Path getSkinDir()               { return skinDir; }
}
