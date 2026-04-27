package gg.openmod.hosting.nat;

import gg.openmod.OpenMod;

import java.net.*;
import java.util.Enumeration;

/**
 * LAN IP adresi tespiti için yardımcı sınıf.
 * İki yöntemle çalışır:
 *  1. UDP socket hilesi (gerçekten bağlanmaz, ama routing table'ı kullanır)
 *  2. Tüm interface'leri tarama — VPN ve virtual interface'leri atlar
 */
public final class LocalNetworkUtil {

    private LocalNetworkUtil() {}

    /**
     * Geçerli LAN IPv4 adresini döndürür.
     * Bulunamazsa "127.0.0.1" döner.
     */
    public static String getLocalAddress() {
        // Yöntem 1: UDP routing table (en güvenilir)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            String addr = socket.getLocalAddress().getHostAddress();
            if (isValidLan(addr)) return addr;
        } catch (Exception ignored) {}

        // Yöntem 2: Interface tarama
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) continue;

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address inet4 && !inet4.isLoopbackAddress()) {
                        String ip = inet4.getHostAddress();
                        if (isValidLan(ip)) return ip;
                    }
                }
            }
        } catch (SocketException e) {
            OpenMod.LOGGER.error("[Network] IP detection failed", e);
        }

        OpenMod.LOGGER.warn("[Network] Could not detect LAN address — using loopback.");
        return "127.0.0.1";
    }

    /** RFC 1918 özel adres aralıkları: 10.x, 172.16-31.x, 192.168.x */
    public static boolean isValidLan(String ip) {
        if (ip == null || ip.isBlank() || ip.startsWith("127.")) return false;
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
