package gg.openmod.hosting.nat;

import gg.openmod.OpenMod;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

/**
 * RFC 5389 uyumlu minimal STUN client.
 * Public IP:port tespiti için kullanılır.
 *
 * Kullanım:
 *   StunClient.discover().thenAccept(addr -> relay.reportExternalAddress(addr));
 */
public final class StunClient {

    private static final SecureRandom RNG = new SecureRandom();

    private static final String[][] SERVERS = {
        {"stun.l.google.com",        "19302"},
        {"stun1.l.google.com",       "19302"},
        {"stun.cloudflare.com",      "3478" },
        {"stun.stunprotocol.org",    "3478" },
        {"stun.voip.blackberry.com", "3478" },
    };

    private static final int MAGIC_COOKIE   = 0x2112A442;
    private static final int TIMEOUT_MS     = 3_000;

    private StunClient() {}

    /** Asenkron STUN discovery — başarısızsa lokal adrese düşer. */
    public static CompletableFuture<InetSocketAddress> discover() {
        return CompletableFuture.supplyAsync(() -> {
            for (String[] srv : SERVERS) {
                try {
                    InetSocketAddress result = query(srv[0], Integer.parseInt(srv[1]));
                    if (result != null) {
                        OpenMod.LOGGER.info("[STUN] External address: {}:{}", result.getHostString(), result.getPort());
                        return result;
                    }
                } catch (Exception e) {
                    OpenMod.LOGGER.debug("[STUN] {} failed: {}", srv[0], e.getMessage());
                }
            }
            OpenMod.LOGGER.warn("[STUN] All servers failed — falling back to local address.");
            return new InetSocketAddress(LocalNetworkUtil.getLocalAddress(), 0);
        });
    }

    // ------------------------------------------------------------------ //

    private static InetSocketAddress query(String host, int port) throws IOException {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(TIMEOUT_MS);

            byte[]         txId    = randomTxId();
            byte[]         request = buildRequest(txId);
            InetAddress    stunHost = InetAddress.getByName(host);

            sock.send(new DatagramPacket(request, request.length, stunHost, port));

            byte[]         buf  = new byte[512];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            sock.receive(recv);

            return parseResponse(recv.getData(), recv.getLength(), txId);
        }
    }

    private static byte[] randomTxId() {
        byte[] txId = new byte[12];
        RNG.nextBytes(txId);
        return txId;
    }

    private static byte[] buildRequest(byte[] txId) {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putShort((short) 0x0001);   // Binding Request
        buf.putShort((short) 0);        // Message Length
        buf.putInt(MAGIC_COOKIE);
        buf.put(txId);
        return buf.array();
    }

    private static InetSocketAddress parseResponse(byte[] data, int len, byte[] expectedTxId) {
        if (len < 20) return null;
        ByteBuffer buf = ByteBuffer.wrap(data, 0, len);

        buf.getShort();              // message type
        int msgLen = buf.getShort() & 0xFFFF;
        if (buf.getInt() != MAGIC_COOKIE) return null; // magic cookie mismatch

        // Transaction ID doğrulaması
        byte[] rxTxId = new byte[12];
        buf.get(rxTxId);
        for (int i = 0; i < 12; i++) {
            if (rxTxId[i] != expectedTxId[i]) {
                OpenMod.LOGGER.debug("[STUN] Transaction ID mismatch — ignoring response.");
                return null;
            }
        }

        // Attribute'ları tara
        int remaining = msgLen;
        while (remaining >= 4 && buf.remaining() >= 4) {
            int attrType = buf.getShort() & 0xFFFF;
            int attrLen  = buf.getShort() & 0xFFFF;
            remaining -= 4;

            if ((attrType == 0x0020 || attrType == 0x0001) && attrLen >= 8) {
                buf.get();                      // reserved
                int family = buf.get() & 0xFF;
                if (family == 0x01) {           // IPv4
                    int rawPort = buf.getShort() & 0xFFFF;
                    int rawAddr = buf.getInt();

                    int port, addr;
                    if (attrType == 0x0020) {
                        port = rawPort ^ (MAGIC_COOKIE >>> 16);
                        addr = rawAddr ^ MAGIC_COOKIE;
                    } else {
                        port = rawPort;
                        addr = rawAddr;
                    }

                    byte[] ipBytes = ByteBuffer.allocate(4).putInt(addr).array();
                    try {
                        return new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
                    } catch (UnknownHostException e) {
                        return null;
                    }
                }
            }

            // Attribute'u atla (padding dahil)
            int skip = attrLen + (attrLen % 4 != 0 ? 4 - attrLen % 4 : 0);
            if (buf.remaining() < skip) break;
            buf.position(buf.position() + skip);
            remaining -= attrLen;
        }
        return null;
    }
}
