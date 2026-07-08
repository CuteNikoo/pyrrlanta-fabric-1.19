package com.pyrrlanta.pyrrlanta.tribe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

// Periodic ore upkeep for tribes that have taxes enabled (off by default -- see
// Tribe.taxesEnabled / /tribe toggle taxes). Never lets a tribe's treasury go negative: if it
// can't fully cover the bill, whatever is available is taken and online members are
// notified, rather than the tribe going into debt or automatically losing claims.
public final class TribeTaxCollector {
    private static int tickCounter = 0;

    private TribeTaxCollector() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TribeTaxCollector::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (!TribeConfig.get().taxesEnabled) {
            return;
        }
        tickCounter++;
        if (tickCounter < TribeConfig.get().taxIntervalTicks) {
            return;
        }
        tickCounter = 0;
        collect(server);
    }

    private static void collect(MinecraftServer server) {
        TribeSavedData data = TribeSavedData.get(server);
        long perClaim = TribeConfig.get().taxPerClaim;
        for (Tribe tribe : data.getAllTribes()) {
            if (!tribe.isTaxesEnabled()) {
                continue;
            }
            long bill = perClaim * tribe.getClaims().size();
            if (bill <= 0) {
                continue;
            }
            long available = tribe.getTreasury();
            long paid = Math.min(bill, available);
            tribe.setTreasury(available - paid);
            data.setDirty();
            if (paid < bill) {
                notifyMembers(server, tribe, "Taxes: only paid " + paid + "/" + bill
                        + " ore -- treasury is empty. Deposit more with /tribe deposit.");
            } else {
                notifyMembers(server, tribe, "Taxes: paid " + paid + " ore. Treasury: " + tribe.getTreasury() + ".");
            }
        }
    }

    private static void notifyMembers(MinecraftServer server, Tribe tribe, String message) {
        for (UUID memberId : tribe.getMembers().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                online.sendSystemMessage(Component.literal("[" + tribe.getName() + "] " + message));
            }
        }
    }
}
