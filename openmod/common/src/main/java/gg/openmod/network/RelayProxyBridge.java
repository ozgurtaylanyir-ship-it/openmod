package gg.openmod.network;

import gg.openmod.OpenMod;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minecraft TCP trafiğini relay'deki WebSocket proxy'siyle köprüler.
 *
 * HOST modu:
 *   relay /proxy?code=XXX&role=host ←→ localhost:minecraftPort
 *   WorldHostManager.startHosting() sonrasında çağrılır.
 *
 * CLIENT modu:
 *   relay /proxy?code=XXX&role=client ←→ localhost:localPort (rastgele)
 *   join_info alındığında çağrılır.
 *   Minecraft bu localPort'a bağlanır.
 */
public class RelayProxyBridge {

    public enum Mode { HOST, CLIENT }

    private final String          proxyUrl;
    private final Mode            mode;
    private final int             localPort;   // HOST: MC sunucu portu, CLIENT: 0=rastgele
    private       WebSocketClient wsClient;
    private       ServerSocket    localServer; // CLIENT modunda yerel TCP sunucu
    private       Socket          tcpSocket;
    private final AtomicBoolean   running    = new AtomicBoolean(false);
    private final ExecutorService executor   = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "OpenMod-ProxyBridge");
        t.setDaemon(true);
        return t;
    });

    /** Dışarıdan bağlantı geldiğinde Minecraft'a söylenecek adres (CLIENT modunda). */
    private int boundLocalPort = 0;

    public RelayProxyBridge(String proxyUrl, Mode mode, int localPort) {
        this.proxyUrl  = proxyUrl;
        this.mode      = mode;
        this.localPort = localPort;
    }

    // ------------------------------------------------------------------ //
    //  HOST modu: MC sunucusunun portunu relay'e bridge et               //
    // ------------------------------------------------------------------ //

    public void startHostBridge() throws Exception {
        if (mode != Mode.HOST) throw new IllegalStateException("Wrong mode");
        running.set(true);

        wsClient = new WebSocketClient(URI.create(proxyUrl + "&role=host")) {
            @Override public void onOpen(ServerHandshake hs) {
                OpenMod.LOGGER.info("[ProxyBridge] HOST: relay'e bağlandı.");
                executor.submit(() -> readFromMinecraftAndSendToRelay());
            }
            @Override public void onMessage(String msg) { /* relay'den text mesaj — yoksay */ }
            @Override public void onMessage(ByteBuffer buf) {
                // relay → MC sunucusuna veri gönder
                if (tcpSocket != null && tcpSocket.isConnected()) {
                    try {
                        byte[] data = new byte[buf.remaining()];
                        buf.get(data);
                        tcpSocket.getOutputStream().write(data);
                        tcpSocket.getOutputStream().flush();
                    } catch (IOException e) {
                        if (running.get()) OpenMod.LOGGER.error("[ProxyBridge] HOST write err", e);
                    }
                }
            }
            @Override public void onClose(int code, String reason, boolean remote) {
                OpenMod.LOGGER.info("[ProxyBridge] HOST: relay bağlantısı kapandı. {}", reason);
                stop();
            }
            @Override public void onError(Exception e) {
                OpenMod.LOGGER.error("[ProxyBridge] HOST ws error", e);
            }
        };
        wsClient.connectBlocking();
    }

    private void readFromMinecraftAndSendToRelay() {
        try {
            tcpSocket = new Socket("127.0.0.1", localPort);
            OpenMod.LOGGER.info("[ProxyBridge] HOST: MC port {} bağlandı.", localPort);
            InputStream in = tcpSocket.getInputStream();
            byte[] buf = new byte[32768];
            int n;
            while (running.get() && (n = in.read(buf)) != -1) {
                if (wsClient != null && wsClient.isOpen()) {
                    wsClient.send(java.util.Arrays.copyOf(buf, n));
                }
            }
        } catch (IOException e) {
            if (running.get()) OpenMod.LOGGER.warn("[ProxyBridge] HOST tcp read: {}", e.getMessage());
        } finally {
            stop();
        }
    }

    // ------------------------------------------------------------------ //
    //  CLIENT modu: relay'den gelen veriyi yerel TCP'ye ilet             //
    // ------------------------------------------------------------------ //

    /**
     * Yerel rastgele bir TCP port açar, relay'e bağlanır.
     * @return Minecraft'ın bağlanması gereken yerel port ("localhost:PORT")
     */
    public int startClientBridge() throws Exception {
        if (mode != Mode.CLIENT) throw new IllegalStateException("Wrong mode");
        running.set(true);

        // Yerel TCP sunucu — Minecraft bu porta bağlanacak
        localServer   = new ServerSocket(0); // 0 = OS rastgele port seçer
        boundLocalPort = localServer.getLocalPort();
        OpenMod.LOGGER.info("[ProxyBridge] CLIENT: yerel port {} açıldı.", boundLocalPort);

        // Önce relay'e bağlan
        wsClient = new WebSocketClient(URI.create(proxyUrl + "&role=client")) {
            @Override public void onOpen(ServerHandshake hs) {
                OpenMod.LOGGER.info("[ProxyBridge] CLIENT: relay'e bağlandı.");
            }
            @Override public void onMessage(String msg) { /* yoksay */ }
            @Override public void onMessage(ByteBuffer buf) {
                // relay'den gelen MC verisi → yerel TCP'ye yaz
                if (tcpSocket != null && tcpSocket.isConnected()) {
                    try {
                        byte[] data = new byte[buf.remaining()];
                        buf.get(data);
                        tcpSocket.getOutputStream().write(data);
                        tcpSocket.getOutputStream().flush();
                    } catch (IOException e) {
                        if (running.get()) OpenMod.LOGGER.error("[ProxyBridge] CLIENT write err", e);
                    }
                }
            }
            @Override public void onClose(int code, String reason, boolean remote) {
                OpenMod.LOGGER.info("[ProxyBridge] CLIENT kapandı. {}", reason);
                stop();
            }
            @Override public void onError(Exception e) {
                OpenMod.LOGGER.error("[ProxyBridge] CLIENT ws error", e);
            }
        };
        wsClient.connectBlocking();

        // Minecraft'ın bağlanmasını bekle
        executor.submit(() -> {
            try {
                tcpSocket = localServer.accept();
                OpenMod.LOGGER.info("[ProxyBridge] CLIENT: Minecraft bağlandı.");
                readFromMinecraftAndSendToRelayClient();
            } catch (IOException e) {
                if (running.get()) OpenMod.LOGGER.error("[ProxyBridge] CLIENT accept err", e);
            }
        });

        return boundLocalPort;
    }

    private void readFromMinecraftAndSendToRelayClient() {
        try {
            InputStream in = tcpSocket.getInputStream();
            byte[] buf = new byte[32768];
            int n;
            while (running.get() && (n = in.read(buf)) != -1) {
                if (wsClient != null && wsClient.isOpen()) {
                    wsClient.send(java.util.Arrays.copyOf(buf, n));
                }
            }
        } catch (IOException e) {
            if (running.get()) OpenMod.LOGGER.warn("[ProxyBridge] CLIENT tcp read: {}", e.getMessage());
        } finally {
            stop();
        }
    }

    // ------------------------------------------------------------------ //

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { if (wsClient  != null) wsClient.close();  } catch (Exception ignored) {}
        try { if (tcpSocket != null) tcpSocket.close(); } catch (Exception ignored) {}
        try { if (localServer != null) localServer.close(); } catch (Exception ignored) {}
        executor.shutdownNow();
        OpenMod.LOGGER.info("[ProxyBridge] Durduruldu.");
    }

    public int getBoundLocalPort() { return boundLocalPort; }
    public boolean isRunning()     { return running.get(); }
}
