package gg.openmod.hosting;

/**
 * Aktif host session bilgileri.
 *
 * FIX: proxyUrl alanı eklendi — relay'deki WebSocket proxy adresi.
 */
public record HostSession(
    String joinCode,
    String sessionId,
    String proxyUrl,     // wss://relay/proxy?code=XXX
    String localAddress  // yedek LAN adresi
) {}
