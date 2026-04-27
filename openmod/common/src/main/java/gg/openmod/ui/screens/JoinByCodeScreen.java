package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.ui.widgets.OpenModButton;
import gg.openmod.ui.widgets.TextFieldWidget2;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FIX 1: Screen → OpenModScreen (tasarım sistemi uyumu).
 * FIX 2: renderBackground(gfx, 0,0,0) → renderBackground(gfx, mouseX, mouseY, partialTick).
 * FIX 3: Vanilla EditBox → TextFieldWidget2 (mevcut widget sistemi ile tutarlılık).
 * FIX 4: Vanilla Button → OpenModButton.
 */
public class JoinByCodeScreen extends OpenModScreen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 130;

    private TextFieldWidget2 codeInput;
    private String errorMsg = "";

    public JoinByCodeScreen(Screen parent) {
        super(Component.literal("Kod ile Katıl"), parent);
    }

    @Override
    protected void init() {
        int cx = (width  - PANEL_W) / 2;
        int cy = (height - PANEL_H) / 2;

        // FIX 3: TextFieldWidget2
        codeInput = new TextFieldWidget2(
            font, cx + PADDING, cy + 44, PANEL_W - PADDING * 2, 20,
            Component.literal("Örn: ABC123")
        );
        codeInput.setMaxLength(6);
        addWidget(codeInput);
        setInitialFocus(codeInput);

        // FIX 4: OpenModButton
        addRenderableWidget(new OpenModButton(
            cx + PADDING, cy + PANEL_H - 26, (PANEL_W / 2) - PADDING - 2, 20,
            Component.literal("Katıl"), btn -> joinByCode()));

        addRenderableWidget(new OpenModButton(
            cx + PANEL_W / 2 + 2, cy + PANEL_H - 26, (PANEL_W / 2) - PADDING - 2, 20,
            Component.literal("İptal"), btn -> onClose()));

        // Close butonu (× — sağ üst)
        addRenderableWidget(new OpenModButton(
            cx + PANEL_W - 20, cy + 4, 16, 16,
            Component.literal("×"), btn -> onClose()));

        OpenMod.get().getRelayClient().getDispatcher().register("join_error", pkt ->
            errorMsg = "§c" + (pkt.has("message") ? pkt.get("message").getAsString() : "Bağlanamadı")
        );
        OpenMod.get().getRelayClient().getDispatcher().register("join_info", pkt ->
            minecraft.execute(() -> minecraft.setScreen(null))
        );
    }

    private void joinByCode() {
        String code = codeInput.getValue().toUpperCase().trim();
        if (code.length() != 6) { errorMsg = "§cKod 6 haneli olmalı."; return; }
        errorMsg = "§7Bağlanıyor...";
        OpenMod.get().getRelayClient().send(
            String.format("{\"type\":\"join_by_code\",\"code\":\"%s\"}", code));
    }

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float delta) {
        // FIX 2: Doğru argümanlar
        renderBackground(gfx, mx, my, delta);

        int cx = (width  - PANEL_W) / 2;
        int cy = (height - PANEL_H) / 2;

        // Panel + header
        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 22, COLOR_ACCENT_DARK);
        gfx.drawString(font, "§lKod ile Katıl", cx + PADDING, cy + 7, COLOR_TEXT, false);

        drawDivider(gfx, cx, cy + 22, PANEL_W);
        gfx.drawString(font, "§76 haneli kodu gir:", cx + PADDING, cy + 30, COLOR_TEXT_DIM, false);

        codeInput.render(gfx, mx, my, delta);

        if (!errorMsg.isEmpty()) {
            gfx.drawCenteredString(font, errorMsg, cx + PANEL_W / 2, cy + PANEL_H - 38, 0xFFFFFF);
        }

        super.render(gfx, mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && codeInput.isFocused()) {
            joinByCode();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
