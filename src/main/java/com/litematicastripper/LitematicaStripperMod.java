package com.litematicastripper;

import com.litematicastripper.command.StripCommand;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LitematicaStripperMod implements ModInitializer {
    public static final String MOD_ID = "litematica-stripper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[投影剥离] Litematica Stripper 已加载");
        StripCommand.register();
    }
}
