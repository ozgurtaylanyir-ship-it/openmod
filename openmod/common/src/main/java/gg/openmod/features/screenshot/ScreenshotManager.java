package gg.openmod.features.screenshot;

import com.mojang.blaze3d.pipeline.RenderTarget;
import gg.openmod.OpenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BUG 5 FIX: Added removeFromGallery() so ScreenshotGalleryScreen can
 *             update the in-memory list after a file delete.
 *
 * BUG 6 FIX: capture() now returns a CompletableFuture<Path> that
 *             completes AFTER the file is actually written (not before).
 *             The previous implementation resolved immediately with the
 *             path while mc.execute() queued the actual write for later —
 *             any caller using the path right away would find no file.
 */
public class ScreenshotManager {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path                   screenshotDir;
    private final List<ScreenshotEntry>  cache = new ArrayList<>();

    public ScreenshotManager() {
        String dir = OpenMod.get().getConfig().getScreenshotPath();
        this.screenshotDir = Minecraft.getInstance().gameDirectory.toPath().resolve(dir);
        try {
            Files.createDirectories(screenshotDir);
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Screenshot] Cannot create dir", e);
        }
        loadGallery();
    }

    /**
     * BUG 6 FIX: Returns a CompletableFuture that completes only after the file
     * has been written.  We schedule the capture on the render thread via
     * mc.execute(), then complete the future from inside that callback so
     * callers (e.g. shareWithFriend) can safely chain on the real file path.
     */
    public CompletableFuture<Path> capture() {
        Minecraft mc   = Minecraft.getInstance();
        String filename = "screenshot_" + LocalDateTime.now().format(TIMESTAMP) + ".png";
        Path outputPath = screenshotDir.resolve(filename);

        CompletableFuture<Path> future = new CompletableFuture<>();

        mc.execute(() -> {
            try {
                RenderTarget fb = mc.getMainRenderTarget();
                Screenshot.grab(outputPath.toFile(), fb, msg ->
                    OpenMod.LOGGER.info("[Screenshot] {}", msg.getString())
                );
                ScreenshotEntry entry = new ScreenshotEntry(outputPath, System.currentTimeMillis());
                cache.add(entry);
                OpenMod.get().getEventBus().fire(new ScreenshotTakenEvent(outputPath));
                future.complete(outputPath);    // Complete AFTER file is written
            } catch (Exception e) {
                OpenMod.LOGGER.error("[Screenshot] Capture failed", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * BUG 5 FIX: Remove an entry from the in-memory cache.
     * Called by ScreenshotGalleryScreen after it deletes the file.
     */
    public void removeFromGallery(ScreenshotEntry entry) {
        cache.remove(entry);
    }

    /**
     * Screenshot'u relay üzerinden arkadaşa gönder.
     */
    public void shareWithFriend(Path screenshotPath, String toUuid) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] data  = Files.readAllBytes(screenshotPath);
                String b64   = java.util.Base64.getEncoder().encodeToString(data);
                String fname = screenshotPath.getFileName().toString();

                int chunkSize   = 32768;
                int totalChunks = (int) Math.ceil((double) b64.length() / chunkSize);
                String transferId = java.util.UUID.randomUUID().toString();

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end   = Math.min(start + chunkSize, b64.length());
                    String chunk = b64.substring(start, end);

                    OpenMod.get().getRelayClient().send(String.format(
                        "{\"type\":\"screenshot_chunk\",\"to\":\"%s\",\"id\":\"%s\"," +
                        "\"chunk\":%d,\"total\":%d,\"data\":\"%s\",\"filename\":\"%s\"}",
                        toUuid, transferId, i, totalChunks, chunk, fname
                    ));
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                OpenMod.LOGGER.error("[Screenshot] Share failed", e);
            }
        });
    }

    private void loadGallery() {
        try (var stream = Files.list(screenshotDir)) {
            stream.filter(p -> p.toString().endsWith(".png"))
                .sorted()
                .forEach(p -> cache.add(new ScreenshotEntry(p, 0)));
        } catch (IOException e) {
            OpenMod.LOGGER.error("[Screenshot] Gallery load failed", e);
        }
    }

    public List<ScreenshotEntry> getGallery()  { return List.copyOf(cache); }
    public Path                  getScreenshotDir() { return screenshotDir; }
}
