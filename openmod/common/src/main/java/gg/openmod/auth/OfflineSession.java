package gg.openmod.auth;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Oyuncunun yerel kimliği.
 * Lisanslı hesaplarda Mojang UUID'sini kullanır.
 * Cracked hesaplarda Java Edition offline UUID standardını uygular.
 */
public final class OfflineSession {

    private final UUID   localUUID;
    private final String username;
    private final boolean isLicensed;
    private volatile String sessionToken;

    public OfflineSession() {
        Minecraft mc   = Minecraft.getInstance();
        GameProfile gp = new GameProfile(mc.getUser().getProfileId(), mc.getUser().getName());

        this.username  = gp.getName();
        UUID profileId = gp.getId();

        if (isValidOnlineUUID(profileId)) {
            // Gerçek Mojang hesabı: UUID v4
            this.localUUID  = profileId;
            this.isLicensed = true;
        } else {
            // Cracked / offline: "OfflinePlayer:<name>" MD5 bazlı UUID (Java Edition standardı)
            this.localUUID  = generateOfflineUUID(this.username);
            this.isLicensed = false;
        }
        this.sessionToken = generateToken();
    }

    // ------------------------------------------------------------------ //

    /** Mojang UUID'si versiyon 4 ise geçerli lisanslı hesap. */
    private static boolean isValidOnlineUUID(UUID uuid) {
        return uuid != null && uuid.version() == 4;
    }

    /** Java Edition offline UUID standardı: NameUUIDFromBytes("OfflinePlayer:<name>") */
    public static UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Session token'ı yenile (bağlantı yeniden kurulduğunda çağrılır). */
    public void refreshToken() {
        this.sessionToken = generateToken();
    }

    // ---- Getters ----
    public UUID    getLocalUUID()     { return localUUID; }
    public String  getUsername()      { return username; }
    public boolean isLicensed()       { return isLicensed; }
    public String  getSessionToken()  { return sessionToken; }

    public AuthPacket toAuthPacket() {
        return new AuthPacket(localUUID, username, sessionToken, isLicensed);
    }
}
