package gg.openmod.compat;

import dev.architectury.platform.Platform;
import gg.openmod.OpenMod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Yüklü mod listesini Architectury üzerinden toplar.
 * World hosting sırasında mod uyumluluk kontrolü için kullanılır.
 */
public final class ModListCollector {

    /**
     * Loader ve vanilla sistemine ait, listeye dahil edilmeyen mod ID'leri.
     */
    private static final Set<String> LOADER_IDS = Set.of(
        "minecraft", "forge", "neoforge", "fabricloader", "java", "architectury",
        "mixinextras", "fabric-api"
    );

    /**
     * Sunucu tarafında zorunlu olmayan bilinen client-side modlar.
     * Bu liste büyütülebilir veya config'e taşınabilir.
     */
    private static final Set<String> CLIENT_ONLY = Set.of(
        "sodium", "lithium", "phosphor", "iris", "optifabric",
        "replaymod", "journeymap", "minimaps", "xaerominimap", "xaerominimapfair",
        "jei", "rei", "emi", "appleskin",
        "mousedelayfix", "entityculling", "memoryleakfix",
        "betterf3", "modmenu",
        "openmod" // kendimiz
    );

    private ModListCollector() {}

    /**
     * Şu an yüklü, sunucu-gerekli mod ID'lerini döndürür.
     * Sıralı, tekrarsız.
     */
    public static List<String> getInstalledMods() {
        return Platform.getMods().stream()
                .map(mod -> mod.getModId())
                .filter(id -> !LOADER_IDS.contains(id))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Host mod listesiyle client mod listesini karşılaştırır.
     * Client'ta fazladan mod olması sorun değil.
     * Host'ta olup client'ta olmayan, client-only olmayan modlar → eksik.
     */
    public static ModCompatResult checkCompatibility(List<String> hostMods) {
        List<String> clientMods = getInstalledMods();
        List<String> missing = hostMods.stream()
                .filter(mod -> !clientMods.contains(mod))
                .filter(mod -> !CLIENT_ONLY.contains(mod))
                .collect(Collectors.toList());
        return new ModCompatResult(missing.isEmpty(), missing);
    }

    /** Mod listesini JSON array string'ine çevirir. */
    public static String toJsonArray(List<String> mods) {
        return mods.stream()
                .map(m -> "\"" + m + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
