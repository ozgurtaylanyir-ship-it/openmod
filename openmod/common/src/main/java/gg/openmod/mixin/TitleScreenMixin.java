package gg.openmod.mixin;

import gg.openmod.ui.screens.OpenModMainScreen;
import gg.openmod.ui.widgets.OpenModButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() { super(Component.empty()); }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        addRenderableWidget(new OpenModButton(
            width - 84, height - 24, 80, 20,
            Component.literal("§bOpenMod"),
            btn -> mc.setScreen(new OpenModMainScreen((TitleScreen)(Object)this))
        ));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        gfx.drawString(Minecraft.getInstance().font, "§7OpenMod", 6, height - 12, 0xFF445566, false);
    }
}
