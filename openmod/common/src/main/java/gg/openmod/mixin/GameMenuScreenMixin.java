package gg.openmod.mixin;

import gg.openmod.ui.screens.OpenModMainScreen;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin() { super(Component.empty()); }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // FIX: WorldHostScreen → OpenModMainScreen
        // Eskiden pause menüsü butonu doğrudan WorldHostScreen açıyordu;
        // bu Friends, Skin Manager gibi diğer özelliklere erişimi engelliyordu.
        addRenderableWidget(new OpenModButton(
            width - 84, height - 24, 80, 20,
            Component.literal("§bOpenMod"),
            btn -> mc.setScreen(new OpenModMainScreen((Screen)(Object) this))
        ));
    }
}
