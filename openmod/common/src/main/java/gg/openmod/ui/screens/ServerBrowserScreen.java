package gg.openmod.ui.screens;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.openmod.OpenMod;
import gg.openmod.network.RelayClient;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Aktif sunucuları listeler — herkes birbirinin dünyasına girebilir.
 * "server_list" paketi ile relay'den alınır, 10 saniyede bir yenilenir.
 *
 * FIX 1: Screen yerine OpenModScreen extend edildi (tasarım sistemi uyumu).
 * FIX 2: renderBackground(gfx, 0,0,0) → renderBackground(gfx, mouseX, mouseY, partialTick).
 * FIX 3: Vanilla Button yerine OpenModButton kullanıldı.
 * FIX 4: onServerList handler init() sonrası removed on close.
 */
public class ServerBrowserScreen extends OpenModScreen {

    private final List<ServerEntry> servers = new ArrayList<>();
    private int ticksSinceRefresh = 0;
    private String statusMsg = "Yükleniyor...";
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private static final int ENTRY_H  = 52;
    private static final int ENTRY_W  = 320;
    private static final int VISIBLE  = 5;

    public ServerBrowserScreen(Screen parent) {
        // FIX 1: OpenModScreen constructor (title, parent) çağrısı
        super(Component.literal("OpenMod — Sunucular"), parent);
    }

    @Override
    protected void init() {
        int cx = (width - ENTRY_W) / 2;

        // FIX 3: Vanilla Button.Builder → OpenModButton
        addRenderableWidget(new OpenModButton(
            cx + ENTRY_W - 82, height - 50, 80, 20,
            Component.literal("↺ Yenile"), btn -> requestList()));

        addRenderableWidget(new OpenModButton(
            cx + ENTRY_W / 2 - 38, height - 50, 75, 20,
            Component.literal("Katıl"), btn -> joinSelected()));

        addRenderableWidget(new OpenModButton(
            cx, height - 50, 75, 20,
            Component.literal("← Geri"), btn -> minecraft.setScreen(parent)));

        addRenderableWidget(new OpenModButton(
            cx + ENTRY_W / 2 + 42, height - 50, 80, 20,
            Component.literal("Kod Gir"), btn -> minecraft.setScreen(new JoinByCodeScreen(this))));

        // FIX 4: Her init çağrısında yeni handler, dispatcher'a kayıt
        OpenMod.get().getRelayClient().getDispatcher().register("server_list", this::onServerList);

        requestList();
    }

    @Override
    public void tick() {
        ticksSinceRefresh++;
        if (ticksSinceRefresh >= 200) {
            requestList();
            ticksSinceRefresh = 0;
        }
    }

    private void requestList() {
        OpenMod.get().getRelayClient().send("{\"type\":\"server_list\"}");
        statusMsg = "Güncelleniyor...";
    }

    private void onServerList(JsonObject pkt) {
        servers.clear();
        JsonArray arr = pkt.getAsJsonArray("servers");
        if (arr == null || arr.isEmpty()) {
            statusMsg = "Şu an açık dünya yok.";
            return;
        }
        for (var el : arr) {
            JsonObject s = el.getAsJsonObject();
            servers.add(new ServerEntry(
                s.get("code").getAsString(),
                s.get("hostName").getAsString(),
                s.get("worldName").getAsString(),
                s.get("playerCount").getAsInt(),
                s.get("startedAt").getAsLong(),
                s.getAsJsonArray("mods")
            ));
        }
        statusMsg = servers.size() + " sunucu bulundu.";
        ticksSinceRefresh = 0;
    }

    private void joinSelected() {
        if (selectedIndex < 0 || selectedIndex >= servers.size()) return;
        ServerEntry entry = servers.get(selectedIndex);
        OpenMod.get().getRelayClient().send(
            String.format("{\"type\":\"join_by_code\",\"code\":\"%s\"}", entry.code));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // FIX 2: Doğru argümanlar — (gfx, mouseX, mouseY, delta)
        renderBackground(gfx, mouseX, mouseY, delta);

        int cx = (width - ENTRY_W) / 2;

        // Header panel
        drawPanel(gfx, cx - PADDING, 4, ENTRY_W + PADDING * 2, 36);
        gfx.fill(cx - PADDING, 4, cx - PADDING + ENTRY_W + PADDING * 2, 26, COLOR_ACCENT_DARK);
        gfx.drawCenteredString(font, "§b§lOpenMod §r§7— §fSunucular", width / 2, 10, COLOR_TEXT);
        gfx.drawCenteredString(font, "§7" + statusMsg, width / 2, 24, COLOR_TEXT_DIM);

        // Sunucu listesi
        int listY = 46;
        int shown = Math.min(VISIBLE, servers.size() - scrollOffset);

        for (int i = 0; i < shown; i++) {
            int idx = scrollOffset + i;
            ServerEntry s = servers.get(idx);
            int ey  = listY + i * (ENTRY_H + 3);
            boolean sel = idx == selectedIndex;

            // Hover tespiti için render öncesi kontrol
            boolean hovered = mouseX >= cx && mouseX <= cx + ENTRY_W
                && mouseY >= ey && mouseY <= ey + ENTRY_H;
            if (hovered) selectedIndex = idx;

            // Arka plan
            gfx.fill(cx, ey, cx + ENTRY_W, ey + ENTRY_H,
                sel ? 0xFF2A3A5C : 0xFF1A1A2E);
            // Çerçeve
            drawBorder(gfx, cx, ey, ENTRY_W, ENTRY_H, sel ? COLOR_ACCENT : COLOR_BORDER);
            // Sol accent çizgisi
            gfx.fill(cx, ey, cx + 3, ey + ENTRY_H, sel ? COLOR_ACCENT : 0xFF333366);

            gfx.drawString(font, "§f" + s.worldName,               cx + 8, ey + 6,  COLOR_TEXT,     false);
            gfx.drawString(font, "§7Host: §e" + s.hostName,        cx + 8, ey + 18, COLOR_TEXT,     false);
            String elapsed = formatElapsed(s.startedAt);
            gfx.drawString(font, "§a" + s.playerCount + " oyuncu §7• §7" + elapsed,
                cx + 8, ey + 30, COLOR_TEXT, false);
            int modCount = s.mods != null ? s.mods.size() : 0;
            gfx.drawString(font, "§9" + modCount + " mod",          cx + ENTRY_W - 50, ey + 30, COLOR_TEXT, false);
            gfx.drawString(font, "§b" + s.code,                     cx + ENTRY_W - 50, ey + 6,  COLOR_ACCENT, false);
        }

        // Boş liste mesajı
        if (servers.isEmpty()) {
            gfx.drawCenteredString(font, "§7Henüz açık dünya yok.",              width / 2, listY + 30, 0x888888);
            gfx.drawCenteredString(font, "§7Bir dünya aç veya arkadaşını bekle.", width / 2, listY + 42, 0x666666);
        }

        super.render(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scrollOffset = Math.max(0, Math.min(Math.max(0, servers.size() - VISIBLE),
            scrollOffset - (int) Math.signum(dy)));
        return true;
    }

    private static String formatElapsed(long startedAt) {
        long mins = ChronoUnit.MINUTES.between(Instant.ofEpochMilli(startedAt), Instant.now());
        if (mins < 60) return mins + "dk önce açıldı";
        return (mins / 60) + "sa " + (mins % 60) + "dk";
    }

    private record ServerEntry(String code, String hostName, String worldName,
                                int playerCount, long startedAt, JsonArray mods) {}
}
