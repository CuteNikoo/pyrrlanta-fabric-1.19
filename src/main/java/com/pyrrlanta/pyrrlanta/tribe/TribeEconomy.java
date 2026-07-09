package com.pyrrlanta.pyrrlanta.tribe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

// Ore: iron/gold/diamonds can be deposited into a tribe's treasury to fund
// claiming land beyond the tribe's free founding claim. Values and costs are
// tunable in TribeConfig; iron is worth relatively little since mods like
// Create make it trivial to automate, while gold and diamonds have no such
// easy automated loop and are priced higher accordingly.
public final class TribeEconomy {
    private TribeEconomy() {
    }

    public static boolean isAccepted(Item item) {
        return item == Items.IRON_INGOT || item == Items.GOLD_INGOT || item == Items.DIAMOND;
    }

    public static int valueOf(Item item) {
        TribeConfig config = TribeConfig.get();
        if (item == Items.IRON_INGOT) {
            return config.ironValue;
        }
        if (item == Items.GOLD_INGOT) {
            return config.goldValue;
        }
        if (item == Items.DIAMOND) {
            return config.diamondValue;
        }
        return 0;
    }

    // Cost of the next claim, given how many the tribe already owns (including its
    // free founding claim). Scales up so expansion isn't free forever off one deposit.
    public static int claimCost(int currentClaimCount) {
        TribeConfig config = TribeConfig.get();
        int paidClaimsSoFar = Math.max(0, currentClaimCount - 1);
        return config.claimBaseCost + paidClaimsSoFar * config.claimCostIncrement;
    }
}
