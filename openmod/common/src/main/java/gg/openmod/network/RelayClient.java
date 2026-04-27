package gg.openmod.network;

import gg.openmod.OpenMod;
import gg.openmod.auth.OfflineSession;
import gg.openmod.network.packets.PacketDispatcher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Relay sunucuya WebSocket bağlantısı.
 * Java 11+ HttpClient kullanır — ek bağımlılık yok.
 *
 * Özellikler:
 *  - Exponential backoff ile otomatik yeniden bağlanma (10s → 20s → 40s → max 120s)
 *  - Ping/pong heartbeat (30s aralıkla)
 *  - Thread-safe gönderme kuyruğu
 *
 * Protokol (JSON):
 *   → {"type":"auth","uuid":"...","username":"...","token":"...","licensed":false}
 *   ← {"type":"auth_ok","server_uuid":"..."}
 *   → {"type":"ping"}
 *   ← {"type":"pong"}
 */
public class RelayClient {

    public enum State { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    // Backoff sabitleri (saniye)
    private static final int BACKOFF_BASE_S = 10;
    private static final int BACKOFF_MAX_S  = 120;
    private static final int HEARTBEAT_S    = 30;

    private final String                  relayUrl;
    private final HttpClient              httpClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "openmod-relay"); t.setDaemon(true); return t; });
    private final ExecutorService         ioExecutor = Executors.newCachedThreadPool(
            r -> { Thread t = new Thread(r, "openmod-io"); t.setDaemon(true); return t; });
    private final PacketDispatcher        dispatcher = new PacketDispatcher();
    private final AtomicBoolean           shouldReconnect = new AtomicBoolean(false);
    private final AtomicInteger           reconnectAttempt = new AtomicInteger(0);

    private volatile WebSocket webSocket;
    private volatile State     state = State.DISCONNECTED;
    private OfflineSession     session;

    private ScheduledFuture<?> heartbeatTask;

    private Consumer<String> onMessage;
    private Runnable         onConnected;
    private Runnable         onDisconnected;

    public RelayClient(String relayUrl) {
        this.relayUrl  = relayUrl;
        this.httpClient = HttpClient.newBuilder()
                .executor(ioExecutor)
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    public void connect(OfflineSession session) {
        this.session = session;
        shouldReconnect.set(true);
        reconnectAttempt.set(0);
        doConnect();
    }

    public void disconnect() {
        shouldReconnect.set(false);
        stopHeartbeat();
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
        state = State.DISCONNECTED;
        OpenMod.LOGGER.info("[Relay] Disconnected (user requested).");
    }

    /**
     * JSON paket gönder. Bağlı değilse sessizce drop eder.
     */
    public void send(String json) {
        if (state != State.CONNECTED || webSocket == null) {
            OpenMod.LOGGER.debug("[Relay] Cannot send ({}): {}", state, json);
            return;
        }
        webSocket.sendText(json, true)
                 .exceptionally(ex -> {
                     OpenMod.LOGGER.warn("[Relay] Send failed: {}", ex.getMessage());
                     return null;
                 });
    }

    public void onMessage(Consumer<String> handler)  { this.onMessage = handler; }
    public void onConnected(Runnable handler)         { this.onConnected = handler; }
    public void onDisconnected(Runnable handler)      { this.onDisconnected = handler; }

    public State  getState()      { return state; }
    public boolean isConnected()  { return state == State.CONNECTED; }
    public PacketDispatcher getDispatcher() { return dispatcher; }

    // ------------------------------------------------------------------ //
    //  Bağlantı yönetimi                                                  //
    // ------------------------------------------------------------------ //

    private void doConnect() {
        if (state == State.CONNECTED || state == State.CONNECTING) return;
        state = State.CONNECTING;
        OpenMod.LOGGER.info("[Relay] Connecting to {}… (attempt {})",
                relayUrl, reconnectAttempt.get() + 1);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(relayUrl), new RelayListener())
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((ws, ex) -> {
                    if (ex != null) {
                        OpenMod.LOGGER.warn("[Relay] Connection failed: {}", ex.getMessage());
                        state = State.RECONNECTING;
                        scheduleReconnect();
                    }
                    // Başarı durumu RelayListener.onOpen() içinde ele alınır
                });
    }

    private void scheduleReconnect() {
        if (!shouldReconnect.get()) return;
        int attempt = reconnectAttempt.getAndIncrement();
        long delay = Math.min((long) BACKOFF_BASE_S * (1L << Math.min(attempt, 3)), BACKOFF_MAX_S);
        OpenMod.LOGGER.info("[Relay] Retrying in {}s…", delay);
        scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(
            () -> send("{\"type\":\"ping\"}"),
            HEARTBEAT_S, HEARTBEAT_S, TimeUnit.SECONDS
        );
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    // ------------------------------------------------------------------ //
    //  WebSocket Listener                                                  //
    // ------------------------------------------------------------------ //

    private class RelayListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            state     = State.CONNECTED;
            reconnectAttempt.set(0);
            OpenMod.LOGGER.info("[Relay] Connected.");

            // Kimlik doğrula
            ws.sendText(session.toAuthPacket().toJson(), true);

            startHeartbeat();
            if (onConnected != null) onConnected.run();
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                dispatcher.dispatch(msg);
                if (onMessage != null) onMessage.accept(msg);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            state = State.DISCONNECTED;
            stopHeartbeat();
            OpenMod.LOGGER.info("[Relay] Closed ({} {})", statusCode, reason);
            if (onDisconnected != null) onDisconnected.run();
            if (shouldReconnect.get()) scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            OpenMod.LOGGER.error("[Relay] Error: {}", error.getMessage());
            state = State.DISCONNECTED;
            stopHeartbeat();
            if (shouldReconnect.get()) scheduleReconnect();
        }
    }
}
