package gg.openmod.hosting;

public record HostOptions(
    int maxPlayers,
    boolean allowCheats,
    boolean shareResourcePack,
    boolean friendsOnly
) {
    public static HostOptions defaults() {
        return new HostOptions(8, false, true, true);
    }
}
