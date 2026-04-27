package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.hosting.HostOptions;
import gg.openmod.hosting.WorldHostManager;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * BUG 7 FIX: startHosting() disables startBtn immediately before the async
 *             operation begins.  Previously the button stayed active until
 *             the CompletableFuture resolved, allowing double-clicks to start
 *             multiple hosting sessions.
 */
public class WorldHostScreen extends OpenModScreen {

    private static final int PANEL_W = 270;
    private static final int PANEL_H = 230;

    private boolean friendsOnly = true;
    private boolean allowCheats = false;
    private int     maxPlayers  = 8;

    private OpenModButton startBtn;
    private OpenModButton stopBtn;
    private OpenModButton inviteBtn;

    public WorldHostScreen(Screen parent) {
        super(Component.literal("Host World"), parent);
    }

    @Override
    protected void init() {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        startBtn = this.addRenderableWidget(new OpenModButton(
            cx + PADDING, cy + PANEL_H - 56, 120, 20,
            Component.literal("▶  Start Hosting"),
            btn -> startHosting()
        ));

        stopBtn = this.addRenderableWidget(new OpenModButton(
            cx + PADDING, cy + PANEL_H - 32, 80, 18,
            Component.literal("■  Stop"),
            btn -> stopHosting()
        ));
        stopBtn.setColor(0xFFCC4444, 0xFFAA2222);

        inviteBtn = this.addRenderableWidget(new OpenModButton(
            cx + PADDING + 124, cy + PANEL_H - 56, 126, 20,
            Component.literal("✉  Invite Friends"),
            btn -> this.minecraft.setScreen(new FriendsScreen(this))
        ));

        this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - 20, cy + 4, 16, 16,
            Component.literal("×"),
            btn -> this.onClose()
        ));

        updateButtonStates();
    }

    private void startHosting() {
        // BUG 7 FIX: Disable button immediately — don't wait for async completion
        startBtn.active  = false;
        inviteBtn.active = false;

        HostOptions opts = new HostOptions(maxPlayers, allowCheats, true, friendsOnly);
        OpenMod.get().getWorldHostManager()
            .startHosting(opts)
            .thenAccept(session -> {
                OpenMod.LOGGER.info("[UI] Hosting started: {}", session.localAddress());
                this.minecraft.execute(this::updateButtonStates);
            })
            .exceptionally(e -> {
                OpenMod.LOGGER.error("[UI] Host failed", e);
                // Re-enable button on failure so the user can retry
                this.minecraft.execute(this::updateButtonStates);
                return null;
            });
    }

    private void stopHosting() {
        OpenMod.get().getWorldHostManager().stopHosting();
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hosting   = OpenMod.get().getWorldHostManager().isHosting();
        startBtn.active  = !hosting;
        stopBtn.active   =  hosting;
        inviteBtn.active =  hosting;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 22, COLOR_ACCENT_DARK);
        gfx.drawString(font, "§lHost Your World", cx + PADDING, cy + 7, COLOR_TEXT, false);

        WorldHostManager wh = OpenMod.get().getWorldHostManager();
        boolean hosting = wh.isHosting();

        drawDivider(gfx, cx, cy + 22, PANEL_W);
        int infoY = cy + 28;
        drawInfoRow(gfx, cx + PADDING, infoY,      "Status",  hosting ? "§aHosting" : "§7Idle");

        wh.getSession().ifPresent(s -> {
            drawInfoRow(gfx, cx + PADDING, infoY + 14, "Address", "§7" + s.localAddress());
            drawInfoRow(gfx, cx + PADDING, infoY + 28, "Mods",    "§7" + s.modList().size() + " loaded");
        });

        if (!hosting) {
            drawInfoRow(gfx, cx + PADDING, infoY + 14, "Address", "§7—");
            drawInfoRow(gfx, cx + PADDING, infoY + 28, "Mods",
                "§7" + gg.openmod.compat.ModListCollector.getInstalledMods().size() + " loaded");
        }

        drawDivider(gfx, cx, cy + 100, PANEL_W);
        int optY = cy + 106;
        gfx.drawString(font, "§7Options", cx + PADDING, optY, COLOR_TEXT_DIM, false);

        renderToggle(gfx, cx + PADDING, optY + 14, friendsOnly, "Friends Only", mouseX, mouseY);
        renderToggle(gfx, cx + PADDING, optY + 32, allowCheats, "Allow Cheats",  mouseX, mouseY);

        gfx.drawString(font, "§7Max Players:  §f" + maxPlayers, cx + PADDING, optY + 50, COLOR_TEXT_DIM, false);
        drawSmallBtn(gfx, cx + PADDING + 148, optY + 48, "–", mouseX, mouseY);
        drawSmallBtn(gfx, cx + PADDING + 164, optY + 48, "+", mouseX, mouseY);

        drawDivider(gfx, cx, cy + PANEL_H - 62, PANEL_W);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawInfoRow(GuiGraphics gfx, int x, int y, String label, String value) {
        gfx.drawString(font, "§7" + label + ": ", x,      y, COLOR_TEXT_DIM, false);
        gfx.drawString(font, value,               x + 68, y, COLOR_TEXT,     false);
    }

    private void renderToggle(GuiGraphics gfx, int x, int y, boolean state, String label, int mx, int my) {
        int toggleW = 28; int toggleH = 12;
        gfx.fill(x, y + 2, x + toggleW, y + toggleH - 2, state ? COLOR_ACCENT : COLOR_BORDER);
        int kx = state ? x + toggleW - 10 : x + 2;
        gfx.fill(kx, y + 1, kx + 8, y + toggleH - 1, COLOR_TEXT);
        gfx.drawString(font, label, x + 32, y + 1, COLOR_TEXT, false);
    }

    private void drawSmallBtn(GuiGraphics gfx, int x, int y, String label, int mx, int my) {
        boolean hover = mx >= x && mx < x + 14 && my >= y && my < y + 12;
        gfx.fill(x, y, x + 14, y + 12, hover ? COLOR_ACCENT : COLOR_ACCENT_DARK);
        gfx.drawString(font, label, x + 4, y + 2, COLOR_TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;
        int optY = cy + 106;

        if (mouseX >= cx + PADDING && mouseX < cx + PADDING + 120) {
            if (mouseY >= optY + 14 && mouseY < optY + 26) { friendsOnly = !friendsOnly; return true; }
            if (mouseY >= optY + 32 && mouseY < optY + 44) { allowCheats = !allowCheats; return true; }
        }
        if (mouseY >= optY + 48 && mouseY < optY + 60) {
            if (mouseX >= cx + PADDING + 148 && mouseX < cx + PADDING + 162) {
                maxPlayers = Math.max(1,  maxPlayers - 1); return true;
            }
            if (mouseX >= cx + PADDING + 164 && mouseX < cx + PADDING + 178) {
                maxPlayers = Math.min(20, maxPlayers + 1); return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
