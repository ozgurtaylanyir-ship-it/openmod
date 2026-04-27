package gg.openmod.fabric;

import net.fabricmc.api.ClientModInitializer;
import gg.openmod.OpenMod;

public class OpenModFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new OpenMod().init();
    }
}
