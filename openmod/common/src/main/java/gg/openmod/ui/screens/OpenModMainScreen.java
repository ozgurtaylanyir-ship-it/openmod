package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.network.RelayClient;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Ana hub ekranı — title screen'deki "OpenMod" butonundan açılır.
 *
 * FIX: Eksik "🌐 Browse Servers" butonu eklendi.
 *      Panel yüksekliği 200 → 228 (yeni buton için alan açıldı).
 */
public class OpenModMainScreen extends OpenModScreen {

    private static final int PANEL_W = 220;
    // FIX: 5. buton için yükseklik artırıldı
    private static final int PANEL_H = 228;

    public OpenModMainScreen(Screen parent) {
        super(Component.literal("OpenMod"), parent);
    }

    @Override
    protected void init() {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        int bx = cx + PADDING;
        int bw = PANEL_W - PADDING * 2;
        int by = cy + 36;

        addBtn(bx, by,       bw, "👥  Friends",        btn -> minecraft.setScreen(new FriendsScreen(this)));
        addBtn(bx, by + 28,  bw, "🌍  Host World",     btn -> minecraft.setScreen(new WorldHostScreen(this)));
        // FIX: Eksik buton — sunucu listesine ulaşma yolu yoktu
        addBtn(bx, by + 56,  bw, "🌐  Browse Servers", btn -> minecraft.setScreen(new ServerBrowserScreen(this)));
        addBtn(bx, by + 84,  bw, "🎨  Skin Manager",   btn -> minecraft.setScreen(new SkinManagerScreen(this)));
        addBtn(bx, by + 112, bw, "📷  Screenshots",    btn -> minecraft.setScreen(new ScreenshotGalleryScreen(this)));

        this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - 20, cy + 4, 16, 16,
            Component.literal("×"),
            btn -> this.onClose()
        ));
    }

    private void addBtn(int x, int y, int w, String label, net.minecraft.client.gui.components.Button.OnPress action) {
        this.addRenderableWidget(new OpenModButton(x, y, w, 22, Component.literal(label), action));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 30, COLOR_ACCENT_DARK);

        gfx.drawString(font, "§b§lOpen§f§lMod", cx + PADDING, cy + 8, COLOR_TEXT, false);
        gfx.drawString(font, "§7v" + OpenMod.VERSION, cx + PADDING, cy + 18, COLOR_TEXT_DIM, false);

        RelayClient.State state = OpenMod.get().getRelayClient().getState();
        String statusStr;
        int statusColor;
        switch (state) {
            case CONNECTED    -> { statusStr = "● Online";      statusColor = COLOR_GREEN; }
            case CONNECTING   -> { statusStr = "◌ Connecting…"; statusColor = COLOR_YELLOW; }
            case RECONNECTING -> { statusStr = "↺ Reconnecting"; statusColor = COLOR_YELLOW; }
            default           -> { statusStr = "○ Offline";     statusColor = COLOR_RED; }
        }
        int sw = font.width(statusStr);
        gfx.drawString(font, statusStr, cx + PANEL_W - sw - PADDING, cy + 13, statusColor, false);

        super.render(gfx, mouseX, mouseY, partialTick);
    }
}
