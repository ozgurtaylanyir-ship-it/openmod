package gg.openmod.hosting.nat;

import gg.openmod.OpenMod;
import gg.openmod.network.RelayClient;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * UDP Hole Punching koordinatörü.
 *
 * Akış (HOST):
 *  1. STUN → external IP:port öğren
 *  2. Relay'e host_external_addr bildir
 *  3. Join request gelince joiner'a punch gönder
 *
 * Akış (JOINER):
 *  1. STUN → external IP:port öğren
 *  2. Relay üzerinden host'a bildir (nat_punch_request)
 *  3. Relay'den host'un adresi gelince çift yönlü punch gönder
 *  4. NAT açılır → TCP bağlantısı kurulabilir
 *  Başarısızsa: relay TCP proxy fallback
 */
public class NatPunchManager {

    private static final int PUNCH_COUNT    = 5;
    private static final int PUNCH_INTERVAL = 100; // ms
    private static final int TIMEOUT_S      = 10;

    private static final byte[] PUNCH_PAYLOAD = "OPENMOD_PUNCH\0".getBytes();

    private final RelayClient                                        relay;
    private volatile InetSocketAddress                               externalAddress;
    private final ConcurrentHashMap<String, CompletableFuture<InetSocketAddress>> pending = new ConcurrentHashMap<>();

    public NatPunchManager(RelayClient relay) {
        this.relay = relay;
        relay.getDispatcher().register("nat_punch_info", this::onPunchInfo);
    }

    // ------------------------------------------------------------------ //
    //  Host tarafı                                                         //
    // ------------------------------------------------------------------ //

    /**
     * STUN discovery yap ve relay'e external adres bildir.
     */
    public CompletableFuture<InetSocketAddress> discoverAndReport(String sessionId) {
        return StunClient.discover().thenApply(addr -> {
            this.externalAddress = addr;
            relay.send(String.format(
                "{\"type\":\"host_external_addr\",\"session\":\"%s\",\"ip\":\"%s\",\"port\":%d}",
                sessionId, addr.getHostString(), addr.getPort()
            ));
            OpenMod.LOGGER.info("[NAT] Reported external: {}", addr);
            return addr;
        });
    }

    // ------------------------------------------------------------------ //
    //  Joiner tarafı                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Host'a punch başlat — önce kendi external addr'ını öğren, sonra relay'e bildir.
     * @return Host'un adresi resolve edilince tamamlanan Future (timeout: 10s → null)
     */
    public CompletableFuture<InetSocketAddress> punchToHost(String sessionId) {
        CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();
        pending.put(sessionId, future);
        future.completeOnTimeout(null, TIMEOUT_S, TimeUnit.SECONDS);
        future.whenComplete((r, ex) -> pending.remove(sessionId));

        StunClient.discover().thenAccept(myAddr ->
            relay.send(String.format(
                "{\"type\":\"nat_punch_request\",\"session\":\"%s\",\"ip\":\"%s\",\"port\":%d}",
                sessionId, myAddr.getHostString(), myAddr.getPort()
            ))
        );

        return future;
    }

    // ------------------------------------------------------------------ //
    //  Paket handler                                                       //
    // ------------------------------------------------------------------ //

    private void onPunchInfo(com.google.gson.JsonObject pkt) {
        String session = pkt.get("session").getAsString();
        String ip      = pkt.get("ip").getAsString();
        int    port    = pkt.get("port").getAsInt();

        InetSocketAddress target = new InetSocketAddress(ip, port);
        OpenMod.LOGGER.info("[NAT] Punching → {}", target);

        sendPunches(target, PUNCH_COUNT).thenRun(() -> {
            CompletableFuture<InetSocketAddress> f = pending.remove(session);
            if (f != null) f.complete(target);
        });
    }

    private CompletableFuture<Void> sendPunches(InetSocketAddress target, int count) {
        return CompletableFuture.runAsync(() -> {
            try (DatagramSocket sock = new DatagramSocket()) {
                DatagramPacket pkt = new DatagramPacket(
                    PUNCH_PAYLOAD, PUNCH_PAYLOAD.length, target.getAddress(), target.getPort());
                for (int i = 0; i < count; i++) {
                    sock.send(pkt);
                    Thread.sleep(PUNCH_INTERVAL);
                }
                OpenMod.LOGGER.debug("[NAT] Sent {} punch packets to {}", count, target);
            } catch (Exception e) {
                OpenMod.LOGGER.warn("[NAT] Punch failed: {}", e.getMessage());
            }
        });
    }

    public InetSocketAddress getExternalAddress() { return externalAddress; }
}
