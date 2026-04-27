package gg.openmod.hosting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record WorldInviteData(
    String fromUuid,
    String fromName,
    String sessionId,
    String address,
    List<String> modList
) {
    public static WorldInviteData fromPacket(JsonObject pkt) {
        List<String> mods = new ArrayList<>();
        if (pkt.has("mods")) {
            JsonArray arr = pkt.getAsJsonArray("mods");
            arr.forEach(e -> mods.add(e.getAsString()));
        }
        return new WorldInviteData(
            pkt.get("from_uuid").getAsString(),
            pkt.get("from_name").getAsString(),
            pkt.has("session") ? pkt.get("session").getAsString() : "",
            pkt.get("address").getAsString(),
            mods
        );
    }
}
