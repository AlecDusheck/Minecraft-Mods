package me.wheelershigley.axolotlcalm;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AxolotlCalm implements ModInitializer {
    public static final String MOD_ID = "axolotlcalm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Axolotl Calm loaded - axolotls will now be 80% calmer!");
    }
}
