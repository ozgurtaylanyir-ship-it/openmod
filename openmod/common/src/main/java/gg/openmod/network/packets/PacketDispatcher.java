package gg.openmod.network.packets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gg.openmod.OpenMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Relay'den gelen JSON paketleri "type" alanına göre ilgili handler'a yönlendirir.
 * Thread-safe: ConcurrentHashMap kullanır, handler'lar herhangi bir thread'de çağrılabilir.
 */
public class PacketDispatcher {

    private final Map<String, Consumer<JsonObject>> handlers = new ConcurrentHashMap<>();

    public PacketDispatcher() {
        // Varsayılan sistem handler'ları
        register("auth_ok", this::handleAuthOk);
        register("pong",    pkt -> { /* heartbeat cevabı — no-op */ });
        register("ping",    pkt -> sendPong());
        register("error",   pkt -> OpenMod.LOGGER.warn("[Relay] Server error: {}",
                pkt.has("message") ? pkt.get("message").getAsString() : "unknown"));
    }

    /**
     * Belirli bir paket tipini dinle.
     * Aynı tip için birden fazla kayıt olursa sonraki öncekini ezer.
     */
    public void register(String type, Consumer<JsonObject> handler) {
        handlers.put(type, handler);
    }

    /**
     * Ham JSON string'i parse edip uygun handler'a ilet.
     * Parse hatası veya handler exception'ı loglanır, fırlatılmaz.
     */
    public void dispatch(String rawJson) {
        JsonObject pkt;
        try {
            pkt = JsonParser.parseString(rawJson).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            OpenMod.LOGGER.error("[Relay] Malformed packet: {}", rawJson.length() > 200
                    ? rawJson.substring(0, 200) + "…" : rawJson);
            return;
        }

        if (!pkt.has("type")) {
            OpenMod.LOGGER.warn("[Relay] Packet missing 'type' field: {}", pkt);
            return;
        }

        String type = pkt.get("type").getAsString();
        Consumer<JsonObject> handler = handlers.get(type);

        if (handler != null) {
            try {
                handler.accept(pkt);
            } catch (Exception e) {
                OpenMod.LOGGER.error("[Relay] Handler exception for type '{}': {}", type, e.getMessage(), e);
            }
        } else {
            OpenMod.LOGGER.debug("[Relay] Unhandled packet type: {}", type);
        }
    }

    // ---- Dahili handler'lar ----

    private void handleAuthOk(JsonObject pkt) {
        String serverUuid = pkt.has("server_uuid") ? pkt.get("server_uuid").getAsString() : "?";
        OpenMod.LOGGER.info("[Relay] Authenticated. Server UUID: {}", serverUuid);
    }

    private void sendPong() {
        OpenMod.get().getRelayClient().send("{\"type\":\"pong\"}");
    }
}
