package gg.openmod.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class TextFieldWidget2 extends EditBox {
    private final Component placeholder;

    public TextFieldWidget2(Font font, int x, int y, int w, int h, Component placeholder) {
        super(font, x, y, w, h, placeholder);
        this.placeholder = placeholder;
        setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        boolean focused = isFocused();
        gfx.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY() + getHeight() + 1, focused ? 0xFF5B8CFF : 0xFF2A2A4A);
        gfx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF0D0D18);
        super.renderWidget(gfx, mouseX, mouseY, partialTick);
        if (getValue().isEmpty() && !isFocused()) {
            Font font = Minecraft.getInstance().font;
            gfx.drawString(font, placeholder, getX() + 2, getY() + (getHeight() - font.lineHeight) / 2, 0xFF555577, false);
        }
    }
}
