package gg.openmod.auth;

import java.util.UUID;

/**
 * Relay sunucusuna gönderilen kimlik doğrulama paketi.
 */
public record AuthPacket(
        UUID   uuid,
        String username,
        String sessionToken,
        boolean licensed
) {
    public String toJson() {
        return String.format(
            "{\"type\":\"auth\",\"uuid\":\"%s\",\"username\":\"%s\",\"token\":\"%s\",\"licensed\":%b}",
            uuid, username, sessionToken, licensed
        );
    }
}
