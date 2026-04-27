package gg.openmod.mixin;

import gg.openmod.features.zoom.ZoomFovHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameRenderer.getFov() metodunu intercept ederek zoom FOV'unu uygular.
 * Vanilla FOV hesaplamasına dokunmaz — sadece sonucu override eder.
 */
@Mixin(GameRenderer.class)
public class ZoomMixin {

    @Inject(
        method = "getFov(Lnet/minecraft/client/Camera;FZ)D",
        at = @At("RETURN"),
        cancellable = true
    )
    private void onGetFov(Camera camera, float partialTick, boolean useFovSetting,
                          CallbackInfoReturnable<Double> cir) {
        Float override = ZoomFovHolder.getOverrideFov();
        if (override != null) {
            cir.setReturnValue((double) override);
        }
    }
}
