package com.pyrrlanta.pyrrlanta;

import com.pyrrlanta.pyrrlanta.tribe.TribeCommand;
import com.pyrrlanta.pyrrlanta.tribe.TribeFireGuard;
import com.pyrrlanta.pyrrlanta.tribe.TribeMapIntegration;
import com.pyrrlanta.pyrrlanta.tribe.TribeMessageEvents;
import com.pyrrlanta.pyrrlanta.tribe.TribeProtectionEvents;
import com.pyrrlanta.pyrrlanta.tribe.TribeTaxCollector;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pyrrlanta implements ModInitializer {
	public static final String MOD_ID = "pyrrlanta";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Pyrrlanta initializing (Fabric 1.19.2 port)");
		TribeCommand.init();
		TribeProtectionEvents.init();
		TribeMessageEvents.init();
		TribeFireGuard.init();
		TribeTaxCollector.init();

		// Only classload TribeMapIntegration (which references BlueMap's API classes
		// directly) if BlueMap is actually present, or servers without it crash with a
		// NoClassDefFoundError.
		if (FabricLoader.getInstance().isModLoaded("bluemap")) {
			TribeMapIntegration.init();
		}
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
