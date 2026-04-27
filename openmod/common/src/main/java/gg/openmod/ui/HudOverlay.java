package gg.openmod.ui;

import dev.architectury.event.events.client.ClientGuiEvent;
import gg.openmod.OpenMod;
import gg.openmod.features.FriendManager;
import gg.openmod.features.FriendStatusChangedEvent;
import gg.openmod.features.WorldInviteEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Oyun içi HUD.
 *  - Sağ üst: online arkadaş sayısı (sadece > 0 iken gösterilir)
 *  - Sağ alt:  bildirim toast'ları — kaydırma animasyonu ile girer/çıkar
 */
public class HudOverlay {

    private static final int TOAST_DURATION   = 80;  // tick
    private static final int TOAST_FADE_IN    = 8;   // ilk N tick: slide-in
    private static final int TOAST_FADE_OUT   = 15;  // son N tick: fade-out
    private static final int TOAST_W          = 180;
    private static final int TOAST_H          = 26;
    private static final int TOAST_GAP        = 4;
    private static final int MAX_VISIBLE      = 3;
    private static final int MAX_QUEUE        = 5;

    private final FriendManager       friendManager;
    private final Deque<Toast>        toastQueue = new ArrayDeque<>();

    public HudOverlay(FriendManager friendManager) {
        this.friendManager = friendManager;

        OpenMod.get().getEventBus().subscribe(FriendStatusChangedEvent.class, e ->
            friendManager.getFriend(e.uuid()).ifPresent(f -> {
                if (e.online()) pushToast("§a" + f.username() + " §7is online");
                else            pushToast("§7" + f.username() + " went offline");
            })
        );

        OpenMod.get().getEventBus().subscribe(WorldInviteEvent.class, e ->
            pushToast("§e" + e.fromName() + " §7invited you to their world!")
        );

        ClientGuiEvent.RENDER_HUD.register(this::renderHud);
    }

    // ------------------------------------------------------------------ //

    public void tick() {
        toastQueue.removeIf(t -> --t.ticksLeft <= 0);
    }

    private void renderHud(GuiGraphics gfx, float tickDelta) {
        if (!OpenMod.get().getConfig().isHudEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Online sayısı — sağ üst
        int onlineCount = friendManager.getOnlineCount();
        if (onlineCount > 0) {
            String txt = "§a● §f" + onlineCount + " online";
            int tw = mc.font.width(txt);
            gfx.drawString(mc.font, txt, sw - tw - 6, 6, 0xFFFFFFFF, true);
        }

        // Toast listesi — sağ alt, yukarı doğru stack
        int bottomY = sh - 10;
        int shown   = 0;
        for (Toast toast : toastQueue) {
            if (shown >= MAX_VISIBLE) break;

            float progress = getProgress(toast);     // 0→1 slide-in, 1→0 fade-out
            int   slideX   = (int) ((1f - progress) * (TOAST_W + 8)); // sağdan giriş
            int   alpha    = (int) (progress * 220);

            int x = sw - TOAST_W - 6 + slideX;
            int y = bottomY - TOAST_H;

            renderToast(gfx, mc, x, y, alpha, toast.message);

            bottomY -= TOAST_H + TOAST_GAP;
            shown++;
        }
    }

    /** 0f = gizli, 1f = tam görünür */
    private float getProgress(Toast toast) {
        int elapsed = TOAST_DURATION - toast.ticksLeft;
        if (elapsed < TOAST_FADE_IN)               return elapsed / (float) TOAST_FADE_IN;
        if (toast.ticksLeft < TOAST_FADE_OUT)      return toast.ticksLeft / (float) TOAST_FADE_OUT;
        return 1f;
    }

    private void renderToast(GuiGraphics gfx, Minecraft mc, int x, int y, int alpha, String message) {
        // Arka plan
        gfx.fill(x, y, x + TOAST_W, y + TOAST_H, (alpha << 24) | 0x101018);
        // Sol accent çizgisi
        gfx.fill(x, y, x + 2, y + TOAST_H, (alpha << 24) | 0x5B8CFF);
        // Mesaj metni
        int textY = y + (TOAST_H - mc.font.lineHeight) / 2;
        gfx.drawString(mc.font, Component.literal(message), x + 6, textY, 0xFFFFFF | (alpha << 24), false);
    }

    public void pushToast(String message) {
        if (toastQueue.size() >= MAX_QUEUE) toastQueue.removeLast();
        toastQueue.addFirst(new Toast(message, TOAST_DURATION));
    }

    // ------------------------------------------------------------------ //

    private static final class Toast {
        final String message;
        int ticksLeft;
        Toast(String msg, int ticks) { this.message = msg; this.ticksLeft = ticks; }
    }
}
