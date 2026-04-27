package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.features.skin.SkinEntry;
import gg.openmod.features.skin.SkinManager;
import gg.openmod.ui.widgets.OpenModButton;
import gg.openmod.ui.widgets.TextFieldWidget2;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * BUG 2 FIX: thenAccept()/exceptionally() now wrap UI updates in minecraft.execute()
 *             (TextField.setValue and statusMsg updates must run on the main thread).
 * BUG 3 FIX: Delete button now renders per row and is handled in mouseClicked().
 * BUG 4 FIX: Download status message (success/error) displayed below the input area.
 */
public class SkinManagerScreen extends OpenModScreen {

    private static final int PANEL_W  = 280;
    private static final int PANEL_H  = 240;
    private static final int SKIN_ROW = 24;

    private TextFieldWidget2 urlField;
    private TextFieldWidget2 nameField;
    private int scrollOffset = 0;

    // BUG 4 FIX: Status feedback state
    private String statusMsg   = "";
    private int    statusColor = 0xFF8888AA;

    public SkinManagerScreen(Screen parent) {
        super(Component.literal("Skin Manager"), parent);
    }

    @Override
    protected void init() {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        urlField = new TextFieldWidget2(font, cx + PADDING, cy + 28, 180, 16,
            Component.literal("Skin URL (NameMC, crafatar…)"));
        urlField.setMaxLength(256);
        this.addWidget(urlField);

        nameField = new TextFieldWidget2(font, cx + PADDING, cy + 48, 120, 16,
            Component.literal("Name"));
        nameField.setMaxLength(32);
        this.addWidget(nameField);

        this.addRenderableWidget(new OpenModButton(
            cx + PADDING + 124, cy + 46, 60, 18,
            Component.literal("⬇ Download"),
            btn -> downloadSkin()
        ));

        this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - 20, cy + 4, 16, 16,
            Component.literal("×"),
            btn -> this.onClose()
        ));
    }

    private void downloadSkin() {
        String url  = urlField.getValue().trim();
        String name = nameField.getValue().trim();
        if (url.isEmpty() || name.isEmpty()) {
            statusMsg   = "§cURL and name are required.";
            statusColor = 0xFFFF5555;
            return;
        }

        statusMsg   = "§7Downloading…";
        statusColor = 0xFF8888AA;

        OpenMod.get().getSkinManager()
            .downloadSkin(url, name)
            .thenAccept(entry -> {
                // BUG 2 FIX: Run UI updates on the main thread
                minecraft.execute(() -> {
                    urlField.setValue("");
                    nameField.setValue("");
                    statusMsg   = "§a✓ Downloaded: " + entry.name();  // BUG 4 FIX
                    statusColor = 0xFF55FF7F;
                });
            })
            .exceptionally(e -> {
                // BUG 2 FIX: Same — main thread
                minecraft.execute(() -> {
                    statusMsg   = "§c✗ " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    statusColor = 0xFFFF5555;
                });
                OpenMod.LOGGER.error("[UI] Skin download failed", e);
                return null;
            });
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 22, COLOR_ACCENT_DARK);
        gfx.drawString(font, "§lSkin Manager", cx + PADDING, cy + 7, COLOR_TEXT, false);

        gfx.drawString(font, "§7URL:", cx + PADDING, cy + 22, COLOR_TEXT_DIM, false);
        urlField.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawString(font, "§7Name:", cx + PADDING, cy + 48, COLOR_TEXT_DIM, false);
        nameField.render(gfx, mouseX, mouseY, partialTick);

        // BUG 4 FIX: Status message
        if (!statusMsg.isEmpty()) {
            gfx.drawString(font, statusMsg, cx + PADDING, cy + 68, statusColor, false);
        }

        drawDivider(gfx, cx, cy + 78, PANEL_W);
        gfx.drawString(font, "§7Library", cx + PADDING, cy + 82, COLOR_TEXT_DIM, false);

        SkinManager sm = OpenMod.get().getSkinManager();
        List<SkinEntry> skins = sm.getSkins();
        int listY = cy + 94;

        if (skins.isEmpty()) {
            String msg = "No skins yet. Download one above.";
            gfx.drawString(font, msg,
                cx + (PANEL_W - font.width(msg)) / 2,
                listY + 20, COLOR_TEXT_DIM, false);
        }

        int visibleRows = (PANEL_H - 100) / SKIN_ROW;
        for (int i = 0; i < visibleRows && i + scrollOffset < skins.size(); i++) {
            SkinEntry skin = skins.get(i + scrollOffset);
            int rowY = listY + i * SKIN_ROW;

            drawHover(gfx, cx + PADDING - 2, rowY, PANEL_W - PADDING * 2 + 4, SKIN_ROW - 2, mouseX, mouseY);

            boolean isActive = skin.equals(sm.getActiveSkin());
            int nameColor = isActive ? COLOR_ACCENT : COLOR_TEXT;
            gfx.drawString(font, (isActive ? "§a▶ " : "  ") + skin.name(),
                cx + PADDING + 4, rowY + 7, nameColor, false);

            // Apply button
            if (!isActive) {
                boolean hoverApply = mouseX >= cx + PANEL_W - 90 && mouseX < cx + PANEL_W - PADDING - 20
                    && mouseY >= rowY + 3 && mouseY < rowY + SKIN_ROW - 3;
                gfx.fill(cx + PANEL_W - 90, rowY + 3, cx + PANEL_W - PADDING - 20, rowY + SKIN_ROW - 3,
                    hoverApply ? COLOR_ACCENT : COLOR_ACCENT_DARK);
                gfx.drawString(font, "Apply", cx + PANEL_W - 82, rowY + 7, COLOR_TEXT, false);
            }

            // BUG 3 FIX: Delete button now actually renders
            boolean hoverDel = mouseX >= cx + PANEL_W - PADDING - 16 && mouseX < cx + PANEL_W - PADDING
                && mouseY >= rowY + 3 && mouseY < rowY + SKIN_ROW - 3;
            gfx.fill(cx + PANEL_W - PADDING - 16, rowY + 3, cx + PANEL_W - PADDING, rowY + SKIN_ROW - 3,
                hoverDel ? COLOR_RED : 0xFF552222);
            gfx.drawString(font, "§c✗", cx + PANEL_W - PADDING - 11, rowY + 7, COLOR_TEXT, false);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        SkinManager sm = OpenMod.get().getSkinManager();
        List<SkinEntry> skins = sm.getSkins();
        int listY = cy + 94;
        int visibleRows = (PANEL_H - 100) / SKIN_ROW;

        for (int i = 0; i < visibleRows && i + scrollOffset < skins.size(); i++) {
            SkinEntry skin = skins.get(i + scrollOffset);
            int rowY = listY + i * SKIN_ROW;

            // Apply click
            if (!skin.equals(sm.getActiveSkin())
                && mouseX >= cx + PANEL_W - 90 && mouseX < cx + PANEL_W - PADDING - 20
                && mouseY >= rowY + 3 && mouseY < rowY + SKIN_ROW - 3) {
                sm.applySkin(skin);
                statusMsg   = "§a✓ Applied: " + skin.name();
                statusColor = 0xFF55FF7F;
                return true;
            }

            // BUG 3 FIX: Delete click now handled
            if (mouseX >= cx + PANEL_W - PADDING - 16 && mouseX < cx + PANEL_W - PADDING
                && mouseY >= rowY + 3 && mouseY < rowY + SKIN_ROW - 3) {
                sm.removeSkin(skin);
                statusMsg   = "§7Deleted: " + skin.name();
                statusColor = 0xFF8888AA;
                // Clamp scroll in case last row was removed
                int maxScroll = Math.max(0, sm.getSkins().size() - visibleRows);
                scrollOffset = Math.min(scrollOffset, maxScroll);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, OpenMod.get().getSkinManager().getSkins().size() - 6);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - sy));
        return true;
    }
}
