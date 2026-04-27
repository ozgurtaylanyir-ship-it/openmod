package gg.openmod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import gg.openmod.OpenMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JSON tabanlı kalıcı ayar yönetimi.
 * Dosya bozuksa varsayılan değerlerle devam eder ve yeniden yazar.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private final Path configPath;
    private Config config;

    public ConfigManager() {
        this.configPath = Minecraft.getInstance()
                .gameDirectory.toPath()
                .resolve("openmod/config.json");
        this.config = new Config();
    }

    // ------------------------------------------------------------------ //
    //  Load / Save                                                         //
    // ------------------------------------------------------------------ //

    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }
        try {
            String json = Files.readString(configPath);
            Config loaded = GSON.fromJson(json, Config.class);
            if (loaded != null) {
                config = validate(loaded);
                OpenMod.LOGGER.info("[Config] Loaded from {}", configPath);
            } else {
                OpenMod.LOGGER.warn("[Config] Empty config file — using defaults.");
                save();
            }
        } catch (JsonSyntaxException e) {
            OpenMod.LOGGER.error("[Config] Parse error — resetting to defaults. ({})", e.getMessage());
            config = new Config();
            save();
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Config] Load failed — using defaults.", e);
            config = new Config();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Config] Save failed.", e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Validation                                                          //
    // ------------------------------------------------------------------ //

    /** Eksik veya geçersiz alanları varsayılanlarla doldurur. */
    private Config validate(Config c) {
        Config def = new Config();
        if (c.relayUrl == null || c.relayUrl.isBlank())         c.relayUrl = def.relayUrl;
        if (c.skinLibraryPath == null || c.skinLibraryPath.isBlank()) c.skinLibraryPath = def.skinLibraryPath;
        if (c.screenshotPath == null || c.screenshotPath.isBlank())   c.screenshotPath = def.screenshotPath;
        if (c.friends == null)                                         c.friends = new ArrayList<>();
        return c;
    }

    // ------------------------------------------------------------------ //
    //  Getters                                                             //
    // ------------------------------------------------------------------ //

    public String      getRelayUrl()         { return config.relayUrl; }
    public boolean     isHudEnabled()        { return config.hudEnabled; }
    public boolean     isZoomEnabled()       { return config.zoomEnabled; }
    public String      getZoomKey()          { return config.zoomKey; }
    public List<UUID>  getFriends()          { return config.friends; }
    public String      getSkinLibraryPath()  { return config.skinLibraryPath; }
    public String      getScreenshotPath()   { return config.screenshotPath; }

    // ------------------------------------------------------------------ //
    //  Setters                                                             //
    // ------------------------------------------------------------------ //

    public void setHudEnabled(boolean v)   { config.hudEnabled = v; }
    public void setZoomEnabled(boolean v)  { config.zoomEnabled = v; }
    public void setRelayUrl(String url)    { config.relayUrl = (url != null && !url.isBlank()) ? url : config.relayUrl; }

    public void addFriend(UUID uuid) {
        if (!config.friends.contains(uuid)) {
            config.friends.add(uuid);
            save();
        }
    }

    public void removeFriend(UUID uuid) {
        if (config.friends.remove(uuid)) save();
    }

    // ------------------------------------------------------------------ //
    //  Config model                                                        //
    // ------------------------------------------------------------------ //

    public static class Config {
        @SerializedName("relay_url")
        public String relayUrl = "wss://relay.openmod.gg";

        @SerializedName("hud_enabled")
        public boolean hudEnabled = true;

        @SerializedName("zoom_enabled")
        public boolean zoomEnabled = false;

        @SerializedName("zoom_key")
        public String zoomKey = "key.keyboard.c";

        @SerializedName("friends")
        public List<UUID> friends = new ArrayList<>();

        @SerializedName("skin_library_path")
        public String skinLibraryPath = "openmod/skins";

        @SerializedName("screenshot_path")
        public String screenshotPath = "openmod/screenshots";
    }
}
