package gg.openmod.network;

import com.google.gson.JsonObject;
import gg.openmod.OpenMod;
import gg.openmod.network.packets.PacketDispatcher;
import gg.openmod.hosting.WorldInviteData;
import gg.openmod.ui.screens.WorldInviteScreen;
import net.minecraft.client.Minecraft;

/**
 * Relay'den gelen paketleri UI aksiyonlarına bağlar.
 *
 * FIX: "lookup_error" handler eklendi.
 *      Kullanıcı bulunamazsa FriendsScreen'de hata göstermek için
 *      dispatcher üzerinden "lookup_error" paketi iletilir.
 *      (FriendsScreen.init() içinde bu tipi zaten dinliyor.)
 */
public class RelayPacketHandler {

    public static void register() {
        PacketDispatcher dispatcher = OpenMod.get().getRelayClient().getDispatcher();

        dispatcher.register("world_invite",    RelayPacketHandler::onWorldInvite);
        dispatcher.register("lookup_result",   RelayPacketHandler::onLookupResult);
        // FIX: Bulunamayan kullanıcı → dispatcher'a bırak, FriendsScreen yakalar
        dispatcher.register("lookup_not_found", RelayPacketHandler::onLookupNotFound);
    }

    private static void onWorldInvite(JsonObject pkt) {
        WorldInviteData data = WorldInviteData.fromPacket(pkt);
        OpenMod.LOGGER.info("[Relay] World invite from {}", data.fromName());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new WorldInviteScreen(mc.screen, data)));
    }

    private static void onLookupResult(JsonObject pkt) {
        if (!pkt.get("found").getAsBoolean()) {
            // Eski relay protokolü "found:false" dönebilir — lookup_error olarak yeniden dispatch
            JsonObject errPkt = new JsonObject();
            errPkt.addProperty("type", "lookup_error");
            errPkt.addProperty("message",
                "User \"" + pkt.get("username").getAsString() + "\" not found");
            OpenMod.get().getRelayClient().getDispatcher().dispatch(errPkt.toString());
            return;
        }
        String uuid     = pkt.get("uuid").getAsString();
        String username = pkt.get("username").getAsString();
        OpenMod.LOGGER.info("[Relay] Resolved {} → {}", username, uuid);
        OpenMod.get().getFriendManager().addFriend(java.util.UUID.fromString(uuid));
    }

    // FIX: Relay "lookup_not_found" paketi gönderirse buradan ilet
    private static void onLookupNotFound(JsonObject pkt) {
        String username = pkt.has("username") ? pkt.get("username").getAsString() : "?";
        OpenMod.LOGGER.warn("[Relay] User not found: {}", username);
        // FriendsScreen'deki "lookup_error" handler'ı bunu gösterecek
        JsonObject errPkt = new JsonObject();
        errPkt.addProperty("type", "lookup_error");
        errPkt.addProperty("message", "User \"" + username + "\" not found");
        OpenMod.get().getRelayClient().getDispatcher().dispatch(errPkt.toString());
    }
}
