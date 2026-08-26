package net.isswr.blockfilter.mixin;

import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.isswr.blockfilter.gui.BlockFilterScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConfigs.class)
public abstract class GuiConfigsMixin extends GuiBase
{
    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void blockfilter$addOpenButton(CallbackInfo ci)
    {
        Screen screen = (Screen) (Object) this;
        ButtonGeneric button = new ButtonGeneric(screen.width - 110, 26, 100, 20, "方块过滤");
        this.addButton(button, (sourceButton, mouseButton) ->
                MinecraftClient.getInstance().setScreen(new BlockFilterScreen(screen)));
    }
}
