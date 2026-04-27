package gg.openmod.features.zoom;

import java.util.concurrent.atomic.AtomicReference;

/**
 * ZoomMixin ile ZoomFeature arasında FOV değerini paylaşır.
 */
public final class ZoomFovHolder {

    private static final AtomicReference<Float> overrideFov = new AtomicReference<>(null);

    private ZoomFovHolder() {}

    public static void setOverrideFov(float fov) { overrideFov.set(fov); }
    public static void clearOverride()           { overrideFov.set(null); }
    public static Float getOverrideFov()         { return overrideFov.get(); }
    public static boolean isActive()             { return overrideFov.get() != null; }
}
