package gg.openmod.sync;

import com.google.gson.JsonObject;
import gg.openmod.OpenMod;
import gg.openmod.network.RelayProxyBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Dünyaya katılma mantığı.
 *
 * FIX: join_info paketindeki proxy_url varsa RelayProxyBridge (CLIENT modu) başlatılır.
 *      Yerel bir TCP port açılır, Minecraft bu porta bağlanır.
 *      Tüm trafik relay üzerinden host'a ulaşır — port forwarding gerekmez.
 */
public class WorldSyncManager {

    private String             activeWorldId = null;
    private RelayProxyBridge   proxyBridge   = null;

    public WorldSyncManager() {
        OpenMod.get().getRelayClient().getDispatcher()
            .register("join_info", this::onJoinInfo);
    }

    private void onJoinInfo(JsonObject pkt) {
        String proxyUrl  = pkt.has("proxy_url") && !pkt.get("proxy_url").isJsonNull()
                           ? pkt.get("proxy_url").getAsString() : null;
        String directAddr = pkt.has("address") ? pkt.get("address").getAsString() : null;
        String worldName  = pkt.has("worldName") ? pkt.get("worldName").getAsString() : "Remote World";

        OpenMod.LOGGER.info("[Join] proxy_url={} | directAddr={}", proxyUrl, directAddr);

        if (proxyUrl != null) {
            // FIX: Proxy üzerinden bağlan — farklı ağ desteklenir
            connectViaProxy(proxyUrl, worldName);
        } else if (directAddr != null) {
            // Yedek: aynı LAN veya eski relay
            Minecraft.getInstance().execute(() -> connectDirect(directAddr, worldName));
        } else {
            OpenMod.LOGGER.error("[Join] Ne proxy_url ne address var!");
        }
    }

    private void connectViaProxy(String proxyUrl, String worldName) {
        // Eski bridge varsa kapat
        if (proxyBridge != null) { proxyBridge.stop(); proxyBridge = null; }

        OpenMod.get().getEventBus().fire(new JoinStartedEvent(worldName, true));

        new Thread(() -> {
            try {
                RelayProxyBridge bridge = new RelayProxyBridge(proxyUrl, RelayProxyBridge.Mode.CLIENT, 0);
                int localPort = bridge.startClientBridge();
                this.proxyBridge = bridge;

                OpenMod.LOGGER.info("[Join] Proxy bridge hazır. localhost:{}", localPort);

                // Minecraft'ı localhost:PORT'a bağla
                String connectAddr = "localhost:" + localPort;
                Minecraft.getInstance().execute(() -> connectDirect(connectAddr, worldName));

            } catch (Exception e) {
                OpenMod.LOGGER.error("[Join] Proxy bridge başlatılamadı", e);
                OpenMod.get().getHudOverlay().pushToast("§cBağlantı kurulamadı: " + e.getMessage());
            }
        }, "OpenMod-JoinProxy").start();
    }

    private void connectDirect(String address, String worldName) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ServerAddress sa = ServerAddress.parseString(address);
            mc.setScreen(new net.minecraft.client.gui.screens.ConnectScreen(
                mc.screen,
                mc,
                sa,
                net.minecraft.client.multiplayer.ServerData.createUnresolvedCustom(worldName)
            ));
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Join] connectDirect hatası: {}", address, e);
        }
    }

    /** Host kapanınca bridge'i kapat. */
    public void onHostClosed() {
        if (proxyBridge != null) { proxyBridge.stop(); proxyBridge = null; }
    }

    public String getActiveWorldId() { return activeWorldId; }
    public void setActiveWorldId(String id) { activeWorldId = id; }
}
