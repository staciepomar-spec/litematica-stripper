package com.litematicastripper.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Marker - actual registration happens in fabric.mod.json entrypoints.
 */
@Environment(EnvType.CLIENT)
public final class StripperModMenu {
    private StripperModMenu() {}
}
