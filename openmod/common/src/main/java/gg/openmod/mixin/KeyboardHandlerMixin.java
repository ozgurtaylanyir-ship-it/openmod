package gg.openmod.mixin;

import gg.openmod.OpenMod;
import gg.openmod.ui.screens.OpenModMainScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HOME tuşuna bastığında OpenMod ana ekranını aç.
 * (Ayarlanabilir — ileride KeyMapping'e taşınabilir)
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        if (minecraft.screen != null) return; // Başka ekran açıkken tetiklenmesin

        // HOME → OpenMod ana ekranı
        if (key == GLFW.GLFW_KEY_HOME) {
            minecraft.setScreen(new OpenModMainScreen(null));
            ci.cancel();
        }
    }
}
