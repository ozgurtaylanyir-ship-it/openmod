package gg.openmod.features.zoom;

import gg.openmod.OpenMod;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * OptiFine gerektirmeden scroll zoom.
 * FOV'u smooth olarak değiştirir, spyglass animasyonu kullanır.
 */
public class ZoomFeature {

    private static final float DEFAULT_FOV    = -1f; // uninitialised
    private static final float ZOOM_FOV       = 10f;
    private static final float ZOOM_SPEED     = 0.3f; // interpolation hızı

    private final KeyMapping zoomKey;
    private float currentFov = DEFAULT_FOV;
    private float targetFov  = DEFAULT_FOV;
    private boolean zooming  = false;

    public ZoomFeature() {
        this.zoomKey = new KeyMapping(
            "key.openmod.zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.openmod"
        );
        KeyMappingRegistry.register(zoomKey);

        ClientTickEvent.CLIENT_POST.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!OpenMod.get().getConfig().isZoomEnabled()) return;

        boolean wantsZoom = zoomKey.isDown();

        if (wantsZoom && !zooming) {
            zooming = true;
            currentFov = (float) mc.options.fov().get();
            targetFov  = ZOOM_FOV;
            // Spyglass sesi çal (opsiyonel)
        } else if (!wantsZoom && zooming) {
            zooming = false;
            targetFov = (float) mc.options.fov().get();
        }

        if (zooming || Math.abs(currentFov - targetFov) > 0.1f) {
            // Smooth interpolation
            currentFov = lerp(currentFov, targetFov, ZOOM_SPEED);
            applyFov(mc, currentFov);
        }
    }

    private void applyFov(Minecraft mc, float fov) {
        // Mixin ile FOV override yapılır (ZoomMixin.java'da)
        ZoomFovHolder.setOverrideFov(fov);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public boolean isZooming() { return zooming; }
}
