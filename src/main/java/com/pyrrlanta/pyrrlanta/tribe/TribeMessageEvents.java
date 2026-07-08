package com.pyrrlanta.pyrrlanta.tribe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Shows an actionbar message when a player crosses into or out of tribe territory.
// The last-known-chunk/tribe maps below are runtime-only session state (not persisted);
// they are rebuilt naturally as players move and cleared on logout.
//
// Fabric has no dedicated per-player tick event, so this runs off the server tick
// (ServerTickEvents.END_SERVER_TICK) and iterates the online player list itself, same
// end result as the NeoForge version's PlayerTickEvent.Post-per-player callback.
public final class TribeMessageEvents {
    private TribeMessageEvents() {
    }

    private static final Map<UUID, ChunkPos> LAST_CHUNK = new HashMap<>();
    private static final Map<UUID, UUID> LAST_TRIBE = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerTick(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.player.getUUID()));
    }

    private static void onPlayerTick(ServerPlayer player) {
        ChunkPos current = new ChunkPos(player.blockPosition());
        ChunkPos last = LAST_CHUNK.get(player.getUUID());
        if (current.equals(last)) {
            return;
        }
        LAST_CHUNK.put(player.getUUID(), current);

        ServerLevel level = player.getLevel();
        TribeSavedData data = TribeSavedData.get(level.getServer());
        ClaimPos pos = new ClaimPos(level.dimension(), current);
        tryAutoClaim(player, data, pos);
        Tribe newOwner = data.getTribeAt(pos);
        UUID newOwnerId = newOwner == null ? null : newOwner.getId();
        UUID oldOwnerId = LAST_TRIBE.get(player.getUUID());

        if (Objects.equals(newOwnerId, oldOwnerId)) {
            return;
        }
        LAST_TRIBE.put(player.getUUID(), newOwnerId);

        if (oldOwnerId != null) {
            Tribe oldOwner = data.getTribe(oldOwnerId);
            if (oldOwner != null) {
                String msg = oldOwner.getFarewell().isEmpty()
                        ? "Leaving " + oldOwner.getName() + " territory"
                        : oldOwner.getFarewell();
                player.displayClientMessage(Component.literal(msg), true);
            }
        }
        if (newOwner != null) {
            String msg = newOwner.getGreeting().isEmpty()
                    ? "Entering " + newOwner.getName() + " territory"
                    : newOwner.getGreeting();
            player.displayClientMessage(Component.literal(msg), true);
        }
    }

    // If the player has autoclaim on (/tribe autoclaim true), claims the chunk they just
    // stepped into for their tribe -- as long as it's unclaimed, they're at least an
    // officer, and (beyond the tribe's free founding claim) it's adjacent to existing
    // territory and affordable. Skips silently on any of those failing rather than
    // spamming messages while walking through the wilderness or after running out of ore;
    // only successful auto-claims are announced.
    private static void tryAutoClaim(ServerPlayer player, TribeSavedData data, ClaimPos pos) {
        if (!TribeAutoClaim.isEnabled(player.getUUID())) {
            return;
        }
        Tribe tribe = data.getTribeOf(player.getUUID());
        if (tribe == null || !tribe.hasPermission(player.getUUID(), TribeRole.OFFICER)) {
            return;
        }
        if (data.getTribeAt(pos) != null) {
            return;
        }
        boolean founding = tribe.getClaims().isEmpty();
        int cost = 0;
        if (!founding) {
            if (!data.isAdjacent(tribe, pos)) {
                return;
            }
            int maxClaims = TribeConfig.get().maxClaimsPerTribe;
            if (maxClaims > 0 && tribe.getClaims().size() >= maxClaims) {
                return;
            }
            cost = TribeEconomy.claimCost(tribe.getClaims().size());
            if (tribe.getTreasury() < cost) {
                return;
            }
            tribe.setTreasury(tribe.getTreasury() - cost);
        }
        data.claim(tribe, pos);
        int finalCost = cost;
        player.displayClientMessage(Component.literal("Auto-claimed " + pos.chunk().x + ", " + pos.chunk().z
                + (founding ? " (founding claim, free)" : " for " + finalCost + " ore")), true);
    }

    private static void onLogout(UUID id) {
        LAST_CHUNK.remove(id);
        LAST_TRIBE.remove(id);
        TribeAutoClaim.clear(id);
    }

    private static void onLogin(ServerPlayer player) {
        TribeSavedData data = TribeSavedData.get(player.getLevel().getServer());
        if (data.getTribeOf(player.getUUID()) != null) {
            return;
        }
        for (Tribe tribe : data.getAllTribes()) {
            if (tribe.getInvites().contains(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You have a pending invite to join tribe '" + tribe.getName()
                        + "'. Use /tribe accept or /tribe deny."));
            }
        }
    }
}
