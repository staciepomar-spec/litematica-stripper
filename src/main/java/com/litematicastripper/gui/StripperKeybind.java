package com.litematicastripper.gui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class StripperKeybind implements ClientModInitializer {
    private static KeyBinding openScreenKey;

    @Override
    public void onInitializeClient() {
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.litematica-stripper.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.litematica-stripper"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new StripperScreen());
                }
            }
        });
    }
}
