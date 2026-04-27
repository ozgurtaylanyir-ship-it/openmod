package gg.openmod.hosting;

import com.google.gson.JsonObject;
import gg.openmod.OpenMod;
import gg.openmod.network.RelayProxyBridge;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Dünya host etme yöneticisi.
 *
 * FIX: host_registered cevabında gelen proxy_url ile RelayProxyBridge başlatılır.
 *      Artık host, local IP göndermek yerine relay proxy'si üzerinden trafiği tüneller.
 *      Bu sayede farklı ağlardan oyuncular bağlanabilir — port forwarding gerekmez.
 */
public class WorldHostManager {

    private HostSession         activeSession = null;
    private RelayProxyBridge    proxyBridge   = null;

    public WorldHostManager() {
        OpenMod.get().getRelayClient().getDispatcher()
            .register("host_registered", this::onHostRegistered);
    }

    // ------------------------------------------------------------------ //

    public CompletableFuture<HostSession> startHosting(HostOptions opts) {
        CompletableFuture<HostSession> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Yerel LAN sunucuyu aç
                LocalWorldServer localServer = LocalWorldServer.open(opts);
                int mcPort = localServer.getPort();
                OpenMod.LOGGER.info("[Host] LAN sunucu port: {}", mcPort);

                // 2. Relay'e kayıt — proxy_url cevabını bekle (onHostRegistered handle eder)
                List<String> mods    = gg.openmod.compat.ModListCollector.getInstalledMods();
                String       worldId = OpenMod.get().getWorldSyncManager().getActiveWorldId();

                OpenMod.get().getRelayClient().send(String.format(
                    "{\"type\":\"host_register\",\"address\":\"127.0.0.1:%d\"," +
                    "\"worldId\":\"%s\",\"worldName\":\"%s\",\"mods\":%s,\"session\":\"%s\"}",
                    mcPort, worldId, localServer.getWorldName(),
                    modsToJson(mods), java.util.UUID.randomUUID()
                ));

                // Relay'den host_registered cevabını bekle (max 10s)
                long deadline = System.currentTimeMillis() + 10_000;
                while (activeSession == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }

                if (activeSession == null) {
                    localServer.close();
                    future.completeExceptionally(new RuntimeException("Relay cevap vermedi."));
                    return;
                }

                // 3. Proxy bridge'i başlat — relay üzerinden MC trafiğini tünelle
                RelayProxyBridge bridge = new RelayProxyBridge(
                    activeSession.proxyUrl(), RelayProxyBridge.Mode.HOST, mcPort
                );
                bridge.startHostBridge();
                this.proxyBridge = bridge;

                OpenMod.LOGGER.info("[Host] Proxy bridge başladı. Kod: {}", activeSession.joinCode());
                future.complete(activeSession);

            } catch (Exception e) {
                OpenMod.LOGGER.error("[Host] startHosting hatası", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void onHostRegistered(JsonObject pkt) {
        String code     = pkt.get("code").getAsString();
        String session  = pkt.get("session").getAsString();
        // FIX: proxy_url relay'den geliyor
        String proxyUrl = pkt.has("proxy_url") ? pkt.get("proxy_url").getAsString() : null;

        if (proxyUrl == null) {
            OpenMod.LOGGER.warn("[Host] proxy_url eksik — eski relay? Güncelle.");
        }

        activeSession = new HostSession(code, session, proxyUrl, "localhost");
        OpenMod.LOGGER.info("[Host] Kayıt OK. Kod: {} | Proxy: {}", code, proxyUrl);
    }

    public void stopHosting() {
        if (!isHosting()) return;
        OpenMod.get().getRelayClient().send("{\"type\":\"host_close\"}");
        if (proxyBridge != null) { proxyBridge.stop(); proxyBridge = null; }
        activeSession = null;
        OpenMod.LOGGER.info("[Host] Durduruldu.");
    }

    /**
     * Arkadaşları davet et — relay onlara world_invite gönderir.
     * (Relay host_register'da zaten herkese gönderdi, bu manual invite için.)
     */
    public void inviteFriends(List<java.util.UUID> uuids) {
        if (activeSession == null) return;
        for (java.util.UUID uuid : uuids) {
            OpenMod.get().getRelayClient().send(
                String.format("{\"type\":\"world_invite\",\"to\":\"%s\",\"code\":\"%s\"}",
                    uuid, activeSession.joinCode())
            );
        }
    }

    public void updatePlayerCount(int count) {
        OpenMod.get().getRelayClient().send(
            String.format("{\"type\":\"host_player_count\",\"count\":%d}", count));
    }

    public boolean          isHosting()   { return activeSession != null; }
    public Optional<HostSession> getSession() { return Optional.ofNullable(activeSession); }

    private static String modsToJson(List<String> mods) {
        return mods.stream()
            .map(m -> "\"" + m.replace("\"", "") + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
