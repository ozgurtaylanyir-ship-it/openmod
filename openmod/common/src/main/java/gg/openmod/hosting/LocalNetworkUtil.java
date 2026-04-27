package gg.openmod.hosting;

/**
 * @deprecated Kullanın: {@link gg.openmod.hosting.nat.LocalNetworkUtil}
 */
@Deprecated
public final class LocalNetworkUtil {
    private LocalNetworkUtil() {}
    public static String getLocalAddress() {
        return gg.openmod.hosting.nat.LocalNetworkUtil.getLocalAddress();
    }
}
