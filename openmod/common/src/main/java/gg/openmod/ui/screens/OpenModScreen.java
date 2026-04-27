package gg.openmod.ui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tüm OpenMod ekranlarının base class'ı.
 * Ortak arka plan render, başlık ve padding sağlar.
 */
public abstract class OpenModScreen extends Screen {

    // Design tokens
    protected static final int COLOR_BG         = 0xE0101018;
    protected static final int COLOR_PANEL       = 0xCC1A1A2E;
    protected static final int COLOR_BORDER      = 0xFF2A2A4A;
    protected static final int COLOR_ACCENT      = 0xFF5B8CFF;
    protected static final int COLOR_ACCENT_DARK = 0xFF3A6AE0;
    protected static final int COLOR_TEXT        = 0xFFEEEEFF;
    protected static final int COLOR_TEXT_DIM    = 0xFF8888AA;
    protected static final int COLOR_GREEN       = 0xFF55FF7F;
    protected static final int COLOR_RED         = 0xFFFF5555;
    protected static final int COLOR_YELLOW      = 0xFFFFCC44;

    protected static final int PADDING = 10;
    protected static final int ROW_H   = 22;

    protected final Screen parent;

    protected OpenModScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
        gfx.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    /** Panel (kart) çizer */
    protected void drawPanel(GuiGraphics gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, COLOR_PANEL);
        drawBorder(gfx, x, y, w, h, COLOR_BORDER);
    }

    /** 1px border */
    protected void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w,     y + 1,     color); // top
        gfx.fill(x,         y + h - 1, x + w,     y + h,     color); // bottom
        gfx.fill(x,         y,         x + 1,     y + h,     color); // left
        gfx.fill(x + w - 1, y,         x + w,     y + h,     color); // right
    }

    /** Accent renkli yatay çizgi */
    protected void drawDivider(GuiGraphics gfx, int x, int y, int w) {
        gfx.fill(x, y, x + w, y + 1, COLOR_ACCENT);
    }

    /** Hover highlight */
    protected void drawHover(GuiGraphics gfx, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            gfx.fill(x, y, x + w, y + h, 0x20FFFFFF);
        }
    }

    /** Yuvarlak köşe simülasyonu (pixel art) */
    protected void drawRoundRect(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x + 1, y,         x + w - 1, y + h,         color);
        gfx.fill(x,     y + 1,     x + w,     y + h - 1,     color);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
