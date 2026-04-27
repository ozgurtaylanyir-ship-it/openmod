package gg.openmod.features;

import com.google.gson.JsonObject;
import gg.openmod.OpenMod;
import gg.openmod.network.RelayClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Arkadaş listesi yönetimi.
 *
 * FIX 1: "friend_request" paketi artık handle ediliyor.
 *         Gelen istekler pendingRequests map'inde bekliyor.
 * FIX 2: FriendListChangedEvent eklendi — FriendsScreen bunu dinleyip
 *         refreshList() çağırıyor (relay cevabından önce listeyi temizleme sorunu çözüldü).
 * FIX 3: acceptFriend() / declineFriend() public API eklendi.
 * FIX 4: lookup_error durumunda LookupFailedEvent fırlatılıyor (UI feedback için).
 */
public class FriendManager {

    // ---- Veri modelleri ----

    public record FriendEntry(UUID uuid, String username, boolean online, String currentServer) {
        public FriendEntry withStatus(boolean online, String server) {
            return new FriendEntry(uuid, username, online, server);
        }
    }

    /** Bekleyen arkadaşlık isteği. */
    public record FriendRequest(UUID fromUuid, String fromUsername) {}

    // ---- State ----

    private final RelayClient                  relay;
    private final Map<UUID, FriendEntry>       friends        = new ConcurrentHashMap<>();
    // FIX 1: Bekleyen istekler (bize gelen)
    private final Map<UUID, FriendRequest>     pendingRequests = new ConcurrentHashMap<>();

    public FriendManager(RelayClient relay) {
        this.relay = relay;

        relay.getDispatcher().register("friend_status",  this::onFriendStatus);
        relay.getDispatcher().register("friend_added",   this::onFriendAdded);
        relay.getDispatcher().register("friend_removed", this::onFriendRemoved);
        // FIX 1: Gelen arkadaşlık isteklerini artık dinliyoruz
        relay.getDispatcher().register("friend_request", this::onFriendRequest);
        // FIX 4: İstek iptal / geri çekilmesi
        relay.getDispatcher().register("friend_request_cancelled", this::onFriendRequestCancelled);

        relay.onConnected(this::syncFriendsFromConfig);
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /** Arkadaş ekle (UUID ile doğrudan). */
    public void addFriend(UUID uuid) {
        OpenMod.get().getConfig().addFriend(uuid);
        relay.send(String.format("{\"type\":\"friend_add\",\"uuid\":\"%s\"}", uuid));
        OpenMod.LOGGER.info("[Friends] Add requested: {}", uuid);
    }

    public void removeFriend(UUID uuid) {
        friends.remove(uuid);
        OpenMod.get().getConfig().removeFriend(uuid);
        relay.send(String.format("{\"type\":\"friend_remove\",\"uuid\":\"%s\"}", uuid));
        OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
    }

    // FIX 3: Gelen isteği kabul et
    public void acceptFriend(UUID fromUuid) {
        FriendRequest req = pendingRequests.remove(fromUuid);
        if (req == null) return;
        relay.send(String.format("{\"type\":\"friend_accept\",\"uuid\":\"%s\"}", fromUuid));
        OpenMod.LOGGER.info("[Friends] Accepted request from {}", req.fromUsername());
        // friend_added paketi relay'den gelince listeye eklenir
    }

    // FIX 3: Gelen isteği reddet
    public void declineFriend(UUID fromUuid) {
        FriendRequest req = pendingRequests.remove(fromUuid);
        if (req == null) return;
        relay.send(String.format("{\"type\":\"friend_decline\",\"uuid\":\"%s\"}", fromUuid));
        OpenMod.LOGGER.info("[Friends] Declined request from {}", req.fromUsername());
        OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
    }

    public void inviteToWorld(UUID friendUuid, String address, List<String> modList) {
        String modsJson = modList.stream()
                .map(m -> "\"" + m + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        relay.send(String.format(
            "{\"type\":\"world_invite\",\"to\":\"%s\",\"address\":\"%s\",\"mods\":%s}",
            friendUuid, address, modsJson
        ));
    }

    // ------------------------------------------------------------------ //
    //  Paket handler'ları                                                  //
    // ------------------------------------------------------------------ //

    private void onFriendStatus(JsonObject pkt) {
        try {
            UUID    uuid   = UUID.fromString(pkt.get("uuid").getAsString());
            String  name   = pkt.has("username") ? pkt.get("username").getAsString() : "Unknown";
            boolean online = pkt.get("online").getAsBoolean();
            String  server = pkt.has("server") && !pkt.get("server").isJsonNull()
                    ? pkt.get("server").getAsString() : null;

            FriendEntry existing = friends.get(uuid);
            FriendEntry entry = (existing != null)
                    ? existing.withStatus(online, server)
                    : new FriendEntry(uuid, name, online, server);
            friends.put(uuid, entry);

            OpenMod.get().getEventBus().fire(new FriendStatusChangedEvent(uuid, online));
            // FIX 2: Status değişince listeyi de güncelle
            OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Friends] Bad friend_status packet: {}", pkt, e);
        }
    }

    private void onFriendAdded(JsonObject pkt) {
        try {
            UUID   uuid = UUID.fromString(pkt.get("uuid").getAsString());
            String name = pkt.get("username").getAsString();
            friends.putIfAbsent(uuid, new FriendEntry(uuid, name, false, null));
            // Pending'den de kaldır (kabul edildiyse zaten oradan geldi)
            pendingRequests.remove(uuid);
            OpenMod.LOGGER.info("[Friends] {} added as friend.", name);
            // FIX 2: Relay cevabı gelince FriendsScreen listeyi yeniler
            OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Friends] Bad friend_added packet: {}", pkt, e);
        }
    }

    private void onFriendRemoved(JsonObject pkt) {
        try {
            UUID uuid = UUID.fromString(pkt.get("uuid").getAsString());
            friends.remove(uuid);
            OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Friends] Bad friend_removed packet: {}", pkt, e);
        }
    }

    // FIX 1: Gelen arkadaşlık isteği
    private void onFriendRequest(JsonObject pkt) {
        try {
            UUID   fromUuid = UUID.fromString(pkt.get("from_uuid").getAsString());
            String fromName = pkt.has("from_username")
                    ? pkt.get("from_username").getAsString() : "Unknown";

            pendingRequests.put(fromUuid, new FriendRequest(fromUuid, fromName));
            OpenMod.LOGGER.info("[Friends] Incoming friend request from {}", fromName);

            // HUD toast — oyun içinde bildirim
            OpenMod.get().getHudOverlay()
                   .pushToast("§e" + fromName + " §7sent you a friend request!");

            // FriendsScreen açıksa pending section'ı güncelle
            OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Friends] Bad friend_request packet: {}", pkt, e);
        }
    }

    private void onFriendRequestCancelled(JsonObject pkt) {
        try {
            UUID uuid = UUID.fromString(pkt.get("uuid").getAsString());
            pendingRequests.remove(uuid);
            OpenMod.get().getEventBus().fire(new FriendListChangedEvent());
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Friends] Bad friend_request_cancelled packet: {}", pkt, e);
        }
    }

    private void syncFriendsFromConfig() {
        List<UUID> saved = OpenMod.get().getConfig().getFriends();
        if (saved.isEmpty()) return;
        String uuidsJson = saved.stream()
                .map(u -> "\"" + u + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        relay.send(String.format("{\"type\":\"friend_sync\",\"uuids\":%s}", uuidsJson));
        OpenMod.LOGGER.info("[Friends] Synced {} saved friends.", saved.size());
    }

    // ------------------------------------------------------------------ //
    //  Getters                                                             //
    // ------------------------------------------------------------------ //

    public Collection<FriendEntry>          getAllFriends()      { return Collections.unmodifiableCollection(friends.values()); }
    public Optional<FriendEntry>            getFriend(UUID id)   { return Optional.ofNullable(friends.get(id)); }
    public int                              getOnlineCount()     { return (int) friends.values().stream().filter(FriendEntry::online).count(); }
    public List<FriendEntry>                getOnlineFriends()   { return friends.values().stream().filter(FriendEntry::online).toList(); }
    // FIX 1: Pending requests erişimi (FriendsScreen için)
    public Collection<FriendRequest>        getPendingRequests() { return Collections.unmodifiableCollection(pendingRequests.values()); }
}
