package gg.openmod.features.screenshot;

import java.nio.file.Path;

public record ScreenshotEntry(Path path, long timestamp) {
    public String filename() { return path.getFileName().toString(); }
}
