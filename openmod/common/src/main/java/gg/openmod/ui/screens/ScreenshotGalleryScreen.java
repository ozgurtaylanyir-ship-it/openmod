package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.features.screenshot.ScreenshotEntry;
import gg.openmod.features.screenshot.ScreenshotManager;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.util.List;

/**
 * BUG 5 FIX: deleteSelected() now calls sm.removeFromGallery() to update
 *             the cache, so the deleted file no longer appears in the list.
 */
public class ScreenshotGalleryScreen extends OpenModScreen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 250;
    private static final int THUMB_W = 64;
    private static final int THUMB_H = 40;
    private static final int COLS    = 4;
    private static final int GAP     = 6;

    private int scrollOffset  = 0;
    private int selectedIndex = -1;

    public ScreenshotGalleryScreen(Screen parent) {
        super(Component.literal("Screenshots"), parent);
    }

    @Override
    protected void init() {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        this.addRenderableWidget(new OpenModButton(
            cx + PADDING, cy + PANEL_H - 26, 90, 20,
            Component.literal("📷  Capture"),
            btn -> capture()
        ));

        this.addRenderableWidget(new OpenModButton(
            cx + PADDING + 94, cy + PANEL_H - 26, 90, 20,
            Component.literal("📁  Open Folder"),
            btn -> openFolder()
        ));

        this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - PADDING - 70, cy + PANEL_H - 26, 70, 20,
            Component.literal("🗑  Delete"),
            btn -> deleteSelected()
        ));

        this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - 20, cy + 4, 16, 16,
            Component.literal("×"),
            btn -> this.onClose()
        ));
    }

    private void capture() {
        OpenMod.get().getScreenshotManager().capture();
        OpenMod.get().getHudOverlay().pushToast("§aScreenshot captured!");
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(
                OpenMod.get().getScreenshotManager().getScreenshotDir().toFile()
            );
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Gallery] Cannot open folder", e);
        }
    }

    private void deleteSelected() {
        ScreenshotManager sm = OpenMod.get().getScreenshotManager();
        List<ScreenshotEntry> shots = sm.getGallery();
        if (selectedIndex < 0 || selectedIndex >= shots.size()) return;

        ScreenshotEntry target = shots.get(selectedIndex);
        try {
            java.nio.file.Files.deleteIfExists(target.path());
        } catch (Exception e) {
            OpenMod.LOGGER.error("[Gallery] Delete failed", e);
            return;
        }

        // BUG 5 FIX: Update the cache so the list reflects the deletion immediately
        sm.removeFromGallery(target);

        selectedIndex = -1;
        // Clamp scroll in case we deleted the last item in a row
        List<ScreenshotEntry> updated = sm.getGallery();
        int totalRows = (int) Math.ceil((double) updated.size() / COLS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, totalRows - 1));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 22, COLOR_ACCENT_DARK);
        gfx.drawString(font, "§lScreenshots", cx + PADDING, cy + 7, COLOR_TEXT, false);

        ScreenshotManager sm = OpenMod.get().getScreenshotManager();
        List<ScreenshotEntry> shots = sm.getGallery();

        int gridX = cx + PADDING;
        int gridY = cy + 26;
        int gridH = PANEL_H - 55;

        int rowsVisible = gridH / (THUMB_H + GAP);
        int totalRows   = (int) Math.ceil((double) shots.size() / COLS);

        for (int row = 0; row < rowsVisible; row++) {
            int shotRow = row + scrollOffset;
            if (shotRow >= totalRows) break;

            for (int col = 0; col < COLS; col++) {
                int idx = shotRow * COLS + col;
                if (idx >= shots.size()) break;

                int tx = gridX + col * (THUMB_W + GAP);
                int ty = gridY + row * (THUMB_H + GAP);

                boolean selected = idx == selectedIndex;
                boolean hovered  = mouseX >= tx && mouseX < tx + THUMB_W
                                && mouseY >= ty && mouseY < ty + THUMB_H;

                int borderColor = selected ? COLOR_ACCENT : (hovered ? COLOR_TEXT_DIM : COLOR_BORDER);
                gfx.fill(tx - 1, ty - 1, tx + THUMB_W + 1, ty + THUMB_H + 1, borderColor);
                gfx.fill(tx, ty, tx + THUMB_W, ty + THUMB_H, 0xFF0A0A14);

                String name = shots.get(idx).filename();
                if (name.length() > 8) name = name.substring(0, 6) + "..";
                gfx.drawString(font, name, tx + 2, ty + THUMB_H / 2 - 4, COLOR_TEXT_DIM, false);

                if (selected) {
                    gfx.fill(tx, ty + THUMB_H - 2, tx + THUMB_W, ty + THUMB_H, COLOR_ACCENT);
                }
            }
        }

        if (shots.isEmpty()) {
            String msg = "No screenshots yet. Press F2 or use Capture.";
            gfx.drawString(font, msg,
                cx + (PANEL_W - font.width(msg)) / 2,
                cy + PANEL_H / 2 - 20, COLOR_TEXT_DIM, false);
        }

        String countStr = shots.size() + " screenshots";
        gfx.drawString(font, countStr,
            cx + PANEL_W - PADDING - font.width(countStr) - 20,
            cy + 7, COLOR_TEXT_DIM, false);

        drawDivider(gfx, cx, cy + PANEL_H - 30, PANEL_W);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        int gridX = cx + PADDING;
        int gridY = cy + 26;
        int gridH = PANEL_H - 55;
        int rowsVisible = gridH / (THUMB_H + GAP);

        List<ScreenshotEntry> shots = OpenMod.get().getScreenshotManager().getGallery();

        for (int row = 0; row < rowsVisible; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scrollOffset) * COLS + col;
                if (idx >= shots.size()) break;

                int tx = gridX + col * (THUMB_W + GAP);
                int ty = gridY + row * (THUMB_H + GAP);

                if (mouseX >= tx && mouseX < tx + THUMB_W && mouseY >= ty && mouseY < ty + THUMB_H) {
                    if (selectedIndex == idx && button == 0) {
                        try { Desktop.getDesktop().open(shots.get(idx).path().toFile()); }
                        catch (Exception ignored) {}
                    }
                    selectedIndex = idx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        List<ScreenshotEntry> shots = OpenMod.get().getScreenshotManager().getGallery();
        int totalRows = (int) Math.ceil((double) shots.size() / COLS);
        int maxScroll = Math.max(0, totalRows - 3);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - sy));
        return true;
    }
}
