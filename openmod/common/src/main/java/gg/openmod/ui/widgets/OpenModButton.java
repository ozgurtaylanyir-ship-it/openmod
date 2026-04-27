package gg.openmod.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class OpenModButton extends Button {
    private int colorNormal = 0xFF3A6AE0;
    private int colorHover  = 0xFF5B8CFF;

    public OpenModButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    public void setColor(int normal, int hover) { this.colorNormal = normal; this.colorHover = hover; }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        int bg = !this.active ? 0xFF1A1A2E : (hovered ? colorHover : colorNormal);
        gfx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
        int border = this.active ? 0xFF7090FF : 0xFF3A3A5A;
        gfx.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
        gfx.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
        gfx.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
        gfx.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
        Font font = Minecraft.getInstance().font;
        int textColor = this.active ? 0xFFEEEEFF : 0xFF666688;
        gfx.drawString(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + (getHeight() - font.lineHeight) / 2, textColor, false);
    }
}
