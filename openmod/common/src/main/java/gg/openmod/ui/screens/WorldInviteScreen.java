package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.hosting.WorldInviteData;
import gg.openmod.compat.ModListCollector;
import gg.openmod.compat.ModCompatResult;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Arkadaştan gelen dünya davetiyesi popup'ı.
 *
 * ┌──────────────────────────────────┐
 * │  World Invite                    │
 * │  §bFriendName §7wants you to     │
 * │  join their world.               │
 * │                                  │
 * │  ✓ Mods compatible (14 mods)    │
 * │  — veya —                        │
 * │  ⚠ Missing 3 mods:              │
 * │    - create  - sodium            │
 * │    - iris                        │
 * │                                  │
 * │  [Accept]          [Decline]     │
 * └──────────────────────────────────┘
 */
public class WorldInviteScreen extends OpenModScreen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 160;

    private final WorldInviteData invite;
    private final ModCompatResult compatResult;

    public WorldInviteScreen(Screen parent, WorldInviteData invite) {
        super(Component.literal("World Invite"), parent);
        this.invite = invite;
        this.compatResult = ModListCollector.checkCompatibility(invite.modList());
    }

    @Override
    protected void init() {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        // Accept
        this.addRenderableWidget(new OpenModButton(
            cx + PADDING, cy + PANEL_H - 28, 100, 20,
            Component.literal("✓  Accept"),
            btn -> accept()
        ));

        // Decline
        OpenModButton declineBtn = this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - PADDING - 80, cy + PANEL_H - 28, 80, 20,
            Component.literal("✗  Decline"),
            btn -> this.onClose()
        ));
        declineBtn.setColor(0xFFAA4444, 0xFF882222);
    }

    private void accept() {
        OpenMod.get().getWorldHostManager().acceptInvite(invite);
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 22, COLOR_ACCENT_DARK);
        gfx.drawString(font, "§lWorld Invite", cx + PADDING, cy + 7, COLOR_TEXT, false);

        int y = cy + 28;

        // Kim davet ediyor
        String msg1 = "§b" + invite.fromName() + " §7invited you to join";
        String msg2 = "§7their world!";
        gfx.drawString(font, msg1, cx + PANEL_W / 2 - font.width(msg1) / 2, y, COLOR_TEXT, false);
        gfx.drawString(font, msg2, cx + PANEL_W / 2 - font.width(msg2) / 2, y + 12, COLOR_TEXT, false);

        drawDivider(gfx, cx + PADDING, y + 26, PANEL_W - PADDING * 2);

        // Mod uyumluluk
        int compatY = y + 34;
        if (compatResult.compatible()) {
            String ok = "§a✓ §7Mods compatible (" + invite.modList().size() + " mods)";
            gfx.drawString(font, ok, cx + PADDING, compatY, COLOR_TEXT, false);
        } else {
            gfx.drawString(font, "§e⚠ §7Missing " + compatResult.missingMods().size() + " mods:",
                cx + PADDING, compatY, COLOR_TEXT, false);

            int missingY = compatY + 14;
            int shown = 0;
            for (String mod : compatResult.missingMods()) {
                if (shown >= 4) {
                    gfx.drawString(font, "§7  + " + (compatResult.missingMods().size() - shown) + " more...",
                        cx + PADDING, missingY, COLOR_TEXT_DIM, false);
                    break;
                }
                gfx.drawString(font, "§c  - " + mod, cx + PADDING, missingY, COLOR_TEXT, false);
                missingY += 11;
                shown++;
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }
}
