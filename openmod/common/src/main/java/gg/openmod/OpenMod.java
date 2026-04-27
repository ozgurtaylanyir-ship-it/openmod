package gg.openmod;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import gg.openmod.auth.OfflineSession;
import gg.openmod.core.ConfigManager;
import gg.openmod.core.EventBus;
import gg.openmod.features.FriendManager;
import gg.openmod.features.screenshot.ScreenshotManager;
import gg.openmod.features.skin.SkinManager;
import gg.openmod.features.zoom.ZoomFeature;
import gg.openmod.hosting.WorldHostManager;
import gg.openmod.hosting.nat.NatPunchManager;
import gg.openmod.network.RelayClient;
import gg.openmod.network.RelayPacketHandler;
import gg.openmod.ui.HudOverlay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public final class OpenMod {

    public static final String MOD_ID   = "openmod";
    public static final String MOD_NAME = "OpenMod";
    public static final String VERSION  = "0.1.0";
    public static final Logger LOGGER   = LogManager.getLogger(MOD_NAME);

    private static OpenMod instance;

    private ConfigManager     configManager;
    private OfflineSession    offlineSession;
    private RelayClient       relayClient;
    private FriendManager     friendManager;
    private WorldHostManager  worldHostManager;
    private NatPunchManager   natPunchManager;
    private ScreenshotManager screenshotManager;
    private SkinManager       skinManager;
    private ZoomFeature       zoomFeature;
    private HudOverlay        hudOverlay;
    private EventBus          eventBus;

    /** Thread-safe singleton accessor. */
    public static OpenMod get() {
        return Objects.requireNonNull(instance, "OpenMod not yet initialized");
    }

    public OpenMod() {
        instance = this;
    }

    public void init() {
        LOGGER.info("[OpenMod] Initializing v{}", VERSION);

        eventBus          = new EventBus();
        configManager     = new ConfigManager();
        configManager.load();

        offlineSession    = new OfflineSession();
        relayClient       = new RelayClient(configManager.getRelayUrl());
        friendManager     = new FriendManager(relayClient);
        natPunchManager   = new NatPunchManager(relayClient);
        worldHostManager  = new WorldHostManager(relayClient);
        screenshotManager = new ScreenshotManager();
        skinManager       = new SkinManager();
        zoomFeature       = new ZoomFeature();
        hudOverlay        = new HudOverlay(friendManager);

        RelayPacketHandler.register();
        registerLifecycleEvents();

        LOGGER.info("[OpenMod] Ready.  UUID={}  Licensed={}",
                offlineSession.getLocalUUID(), offlineSession.isLicensed());
    }

    private void registerLifecycleEvents() {
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            LOGGER.info("[OpenMod] Client started — connecting relay…");
            relayClient.connect(offlineSession);
        });

        ClientLifecycleEvent.CLIENT_STOPPING.register(client -> {
            LOGGER.info("[OpenMod] Client stopping — cleaning up…");
            if (worldHostManager.isHosting()) worldHostManager.stopHosting();
            relayClient.disconnect();
            configManager.save();
        });

        ClientTickEvent.CLIENT_POST.register(client -> hudOverlay.tick());
    }

    // ---- Getters ----
    public ConfigManager     getConfig()           { return configManager; }
    public OfflineSession    getSession()           { return offlineSession; }
    public RelayClient       getRelayClient()       { return relayClient; }
    public FriendManager     getFriendManager()     { return friendManager; }
    public WorldHostManager  getWorldHostManager()  { return worldHostManager; }
    public NatPunchManager   getNatPunchManager()   { return natPunchManager; }
    public ScreenshotManager getScreenshotManager() { return screenshotManager; }
    public SkinManager       getSkinManager()       { return skinManager; }
    public ZoomFeature       getZoomFeature()       { return zoomFeature; }
    public HudOverlay        getHudOverlay()        { return hudOverlay; }
    public EventBus          getEventBus()          { return eventBus; }
}
