package com.pyrrlanta.pyrrlanta.tribe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pyrrlanta.pyrrlanta.Pyrrlanta;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

// Fabric has no ModConfigSpec equivalent, so this is a hand-rolled GSON JSON config
// at config/pyrrlanta.json. Access fields directly via TribeConfig.get().fieldName instead
// of the NeoForge version's TribeConfig.FIELD.get() ModConfigSpec.IntValue pattern.
public class TribeConfig {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("pyrrlanta.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TribeConfig instance;

    // Ore cost of a tribe's 2nd claim (the 1st/founding claim is always free).
    public int claimBaseCost = 20;
    // Extra ore cost added per claim the tribe already owns, so expansion gets more expensive over time.
    public int claimCostIncrement = 5;
    // Maximum number of chunks a single tribe may claim. 0 = unlimited.
    public int maxClaimsPerTribe = 0;
    // Ore value of one iron ingot. Kept low by default since iron is trivial to automate (e.g. with Create).
    public int ironValue = 1;
    // Ore value of one gold ingot. No easy automated ore-doubling loop for gold, so it's worth more than iron.
    public int goldValue = 8;
    // Ore value of one diamond. The hardest of the three to automate, so it's worth the most.
    public int diamondValue = 25;
    // Master switch for the taxes feature. Off by default. If false, taxes never run server-wide
    // even for tribes that have turned on /tribe toggle taxes true.
    public boolean taxesEnabled = false;
    // Ore charged per claim, per tax cycle, for tribes that have taxes enabled (off by default).
    public int taxPerClaim = 2;
    // How often taxes are collected, in ticks. Default is 24000 (one Minecraft day).
    public int taxIntervalTicks = 24000;
    // Master switch for the tribe tier system (tier passives, force-loading, tier-up
    // announcements). On by default. If false, no tier passives apply and force-loaded chunks
    // are not maintained.
    public boolean tierSystemEnabled = true;

    public static synchronized TribeConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static TribeConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                TribeConfig loaded = GSON.fromJson(reader, TribeConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException e) {
                Pyrrlanta.LOGGER.error("Failed to load config/pyrrlanta.json, using defaults", e);
            }
        }
        TribeConfig defaults = new TribeConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Pyrrlanta.LOGGER.error("Failed to save config/pyrrlanta.json", e);
        }
    }
}
