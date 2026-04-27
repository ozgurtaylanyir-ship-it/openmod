package gg.openmod.ui.screens;

import gg.openmod.OpenMod;
import gg.openmod.features.FriendListChangedEvent;
import gg.openmod.features.FriendManager;
import gg.openmod.ui.widgets.OpenModButton;
import gg.openmod.ui.widgets.TextFieldWidget2;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FriendsScreen extends OpenModScreen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 240;

    private TextFieldWidget2 addFriendField;
    private String           addFriendStatus = "";
    private int              statusColorHex  = 0xFF8888AA;

    private final List<FriendManager.FriendEntry>   displayList  = new ArrayList<>();
    private final List<FriendManager.FriendRequest> pendingList  = new ArrayList<>();

    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 5;

    // BUG 1 FIX: Subscription cancel handle — must be stored and called on close
    private Runnable cancelFriendListSub;

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"), parent);
    }

    @Override
    protected void init() {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        addFriendField = new TextFieldWidget2(
            this.font, cx + PADDING, cy + 32, 160, 18,
            Component.literal("Username or UUID…")
        );
        addFriendField.setMaxLength(64);
        this.addWidget(addFriendField);

        this.addRenderableWidget(new OpenModButton(
            cx + PADDING + 164, cy + 32, 56, 18,
            Component.literal("Add"),
            btn -> addFriend()
        ));

        this.addRenderableWidget(new OpenModButton(
            cx + PANEL_W - 20, cy + 4, 16, 16,
            Component.literal("×"),
            btn -> this.onClose()
        ));

        // BUG 1 FIX: Store the cancel handle so we can unsubscribe in removed()
        cancelFriendListSub = OpenMod.get().getEventBus().subscribe(
            FriendListChangedEvent.class,
            e -> minecraft.execute(this::refreshList)
        );

        OpenMod.get().getRelayClient().getDispatcher().register("lookup_error", pkt -> {
            String msg = pkt.has("message") ? pkt.get("message").getAsString() : "User not found";
            minecraft.execute(() -> {
                addFriendStatus = "§c✗ " + msg;
                statusColorHex  = 0xFFFF5555;
            });
        });

        refreshList();
    }

    // BUG 1 FIX: Called by Minecraft when the screen is actually removed from the stack
    @Override
    public void removed() {
        super.removed();
        if (cancelFriendListSub != null) {
            cancelFriendListSub.run();
            cancelFriendListSub = null;
        }
    }

    private void refreshList() {
        displayList.clear();
        pendingList.clear();
        var friends = OpenMod.get().getFriendManager().getAllFriends();
        friends.stream().filter(FriendManager.FriendEntry::online).forEach(displayList::add);
        friends.stream().filter(f -> !f.online()).forEach(displayList::add);
        pendingList.addAll(OpenMod.get().getFriendManager().getPendingRequests());
    }

    private void addFriend() {
        String input = addFriendField.getValue().trim();
        if (input.isEmpty()) return;
        try {
            UUID uuid = UUID.fromString(input);
            OpenMod.get().getFriendManager().addFriend(uuid);
            addFriendStatus = "§7Sending request…";
            statusColorHex  = 0xFF8888AA;
        } catch (IllegalArgumentException e) {
            OpenMod.get().getRelayClient().send(
                String.format("{\"type\":\"lookup_username\",\"username\":\"%s\"}", input));
            addFriendStatus = "§7Looking up \"" + input + "\"…";
            statusColorHex  = 0xFF8888AA;
        }
        addFriendField.setValue("");
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gfx, mouseX, mouseY, partialTick);

        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        drawPanel(gfx, cx, cy, PANEL_W, PANEL_H);
        gfx.fill(cx, cy, cx + PANEL_W, cy + 22, COLOR_ACCENT_DARK);
        gfx.drawString(font, "§lFriends", cx + PADDING, cy + 7, COLOR_TEXT, false);
        int onlineCount = OpenMod.get().getFriendManager().getOnlineCount();
        String countText = onlineCount + " online";
        gfx.drawString(font, countText,
            cx + PANEL_W - PADDING - font.width(countText) - 20, cy + 7, COLOR_GREEN, false);

        gfx.drawString(font, "§7Add friend:", cx + PADDING, cy + 22, COLOR_TEXT_DIM, false);
        addFriendField.render(gfx, mouseX, mouseY, partialTick);

        if (!addFriendStatus.isEmpty()) {
            gfx.drawString(font, addFriendStatus, cx + PADDING, cy + 54, statusColorHex, false);
        }

        drawDivider(gfx, cx, cy + 62, PANEL_W);

        int contentY = cy + 66;

        if (!pendingList.isEmpty()) {
            gfx.drawString(font, "§eRequests (" + pendingList.size() + ")",
                cx + PADDING, contentY, COLOR_YELLOW, false);
            contentY += 12;
            for (FriendManager.FriendRequest req : pendingList) {
                renderPendingRow(gfx, req, cx + PADDING, contentY,
                    PANEL_W - PADDING * 2, mouseX, mouseY);
                contentY += ROW_H + 2;
            }
            drawDivider(gfx, cx, contentY, PANEL_W);
            contentY += 4;
        }

        int listEndY  = cy + PANEL_H - PADDING;
        int available = listEndY - contentY;
        int visible   = available / ROW_H;

        for (int i = 0; i < visible && i + scrollOffset < displayList.size(); i++) {
            FriendManager.FriendEntry entry = displayList.get(i + scrollOffset);
            renderFriendRow(gfx, entry, cx + PADDING, contentY + i * ROW_H,
                PANEL_W - PADDING * 2, mouseX, mouseY);
        }

        if (displayList.isEmpty() && pendingList.isEmpty()) {
            String msg = "No friends yet. Add one above!";
            gfx.drawString(font, msg,
                cx + (PANEL_W - font.width(msg)) / 2,
                cy + PANEL_H / 2, COLOR_TEXT_DIM, false);
        }

        if (displayList.size() > visible) {
            int total  = displayList.size();
            int sbH    = available - 4;
            int thumbH = Math.max(16, sbH * visible / total);
            int thumbY = contentY + scrollOffset * (sbH - thumbH) / Math.max(1, total - visible);
            gfx.fill(cx + PANEL_W - 4, contentY, cx + PANEL_W - 2, contentY + sbH, COLOR_BORDER);
            gfx.fill(cx + PANEL_W - 4, thumbY,   cx + PANEL_W - 2, thumbY + thumbH, COLOR_ACCENT);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderPendingRow(GuiGraphics gfx, FriendManager.FriendRequest req,
                                   int x, int y, int w, int mouseX, int mouseY) {
        drawHover(gfx, x - 2, y, w + 4, ROW_H, mouseX, mouseY);
        gfx.fill(x, y + 5, x + 5, y + 10, COLOR_YELLOW);
        gfx.drawString(font, "§e" + req.fromUsername(), x + 10, y + 6, COLOR_TEXT, false);

        boolean hoverAccept = mouseX >= x + w - 90 && mouseX < x + w - 48
            && mouseY >= y + 3 && mouseY < y + ROW_H - 3;
        gfx.fill(x + w - 90, y + 3, x + w - 48, y + ROW_H - 3,
            hoverAccept ? COLOR_GREEN : 0xFF225522);
        gfx.drawString(font, "§aAccept", x + w - 86, y + 7, COLOR_TEXT, false);

        boolean hoverDecline = mouseX >= x + w - 44 && mouseX < x + w
            && mouseY >= y + 3 && mouseY < y + ROW_H - 3;
        gfx.fill(x + w - 44, y + 3, x + w, y + ROW_H - 3,
            hoverDecline ? COLOR_RED : 0xFF552222);
        gfx.drawString(font, "§cDecline", x + w - 40, y + 7, COLOR_TEXT, false);
    }

    private void renderFriendRow(GuiGraphics gfx, FriendManager.FriendEntry entry,
                                  int x, int y, int w, int mouseX, int mouseY) {
        drawHover(gfx, x - 2, y, w + 4, ROW_H - 2, mouseX, mouseY);
        int dotColor = entry.online() ? COLOR_GREEN : COLOR_TEXT_DIM;
        gfx.fill(x, y + 7, x + 5, y + 12, dotColor);
        String name = entry.username();
        if (name.length() > 18) name = name.substring(0, 15) + "...";
        gfx.drawString(font, name, x + 10, y + 6, entry.online() ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        if (entry.currentServer() != null) {
            String srv = entry.currentServer();
            if (srv.length() > 14) srv = srv.substring(0, 11) + "...";
            gfx.drawString(font, "§7" + srv, x + 120, y + 6, COLOR_TEXT_DIM, false);
        } else if (!entry.online()) {
            gfx.drawString(font, "§7offline", x + 120, y + 6, COLOR_TEXT_DIM, false);
        }
        if (entry.online() && OpenMod.get().getWorldHostManager().isHosting()) {
            boolean hovering = mouseX >= x + w - 46 && mouseX < x + w
                && mouseY >= y + 3 && mouseY < y + ROW_H - 3;
            gfx.fill(x + w - 46, y + 3, x + w, y + ROW_H - 3,
                hovering ? COLOR_ACCENT : COLOR_ACCENT_DARK);
            gfx.drawString(font, "Invite", x + w - 38, y + 7, COLOR_TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = (this.width  - PANEL_W) / 2;
        int cy = (this.height - PANEL_H) / 2;

        int pendingStartY = cy + 78;
        for (int i = 0; i < pendingList.size(); i++) {
            FriendManager.FriendRequest req = pendingList.get(i);
            int rowY = pendingStartY + i * (ROW_H + 2);
            int x    = cx + PADDING;
            int w    = PANEL_W - PADDING * 2;
            if (mouseX >= x + w - 90 && mouseX < x + w - 48
                && mouseY >= rowY + 3 && mouseY < rowY + ROW_H - 3) {
                OpenMod.get().getFriendManager().acceptFriend(req.fromUuid());
                addFriendStatus = "§a✓ Accepted " + req.fromUsername();
                statusColorHex  = 0xFF55FF7F;
                refreshList();
                return true;
            }
            if (mouseX >= x + w - 44 && mouseX < x + w
                && mouseY >= rowY + 3 && mouseY < rowY + ROW_H - 3) {
                OpenMod.get().getFriendManager().declineFriend(req.fromUuid());
                addFriendStatus = "§7Declined.";
                statusColorHex  = 0xFF8888AA;
                refreshList();
                return true;
            }
        }

        if (OpenMod.get().getWorldHostManager().isHosting()) {
            int pendingOffset = pendingList.isEmpty() ? 0 : pendingList.size() * (ROW_H + 2) + 16;
            int listStartY    = cy + 66 + pendingOffset;
            int visible       = (PANEL_H - 70 - pendingOffset) / ROW_H;
            for (int i = 0; i < visible && i + scrollOffset < displayList.size(); i++) {
                FriendManager.FriendEntry entry = displayList.get(i + scrollOffset);
                if (!entry.online()) continue;
                int rowY = listStartY + i * ROW_H;
                int bx   = cx + PADDING + (PANEL_W - PADDING * 2) - 46;
                if (mouseX >= bx && mouseX < bx + 46 && mouseY >= rowY + 3 && mouseY < rowY + ROW_H - 3) {
                    OpenMod.get().getWorldHostManager().inviteFriends(List.of(entry.uuid()));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, displayList.size() - VISIBLE_ROWS);
        scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && addFriendField.isFocused()) {
            addFriend();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
