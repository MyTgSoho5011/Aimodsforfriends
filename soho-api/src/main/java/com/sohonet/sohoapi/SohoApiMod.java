package com.sohonet.sohoapi;

import com.sohonet.sohoapi.api.SohoRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SohoApiMod implements ModInitializer {

    public static final String MOD_ID = "soho_api";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Soho API] loaded. Soho501 = {}", SohoRegistry.getString("Soho501"));
    }
}