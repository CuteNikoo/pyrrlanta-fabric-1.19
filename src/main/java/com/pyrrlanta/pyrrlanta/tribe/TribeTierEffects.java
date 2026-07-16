package com.pyrrlanta.pyrrlanta.tribe;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Applies the per-tier tribe passives. Passives are cumulative: a tribe has every passive up
// to and including its current tier (so reaching a higher tier never costs you a lower one).
//   Tier 2 Outpost    -> Hearth: Regeneration I inside own claims, suppressed 10s after damage
//   Tier 3 Settlement -> Pack Instinct: Haste I within 16 blocks of a tribemate, anywhere
//   Tier 4 Commune    -> Long Watch: tribemates within 128 blocks glow through terrain (only
//                        the tribe sees it); death coordinates broadcast to tribe chat
//   Tier 5 Ascension  -> Enduring: keep XP levels on death, anywhere
//
// Continuous effects (regen/haste/glow) are re-applied on a short interval from the server
// tick, mirroring the throttled-scan pattern the rest of this mod uses; the one-shot passives
// (death coords, keep-XP) hook the relevant Fabric events directly. Force-load reconciliation
// and tier-up announcements piggyback on the same tick so a tribe that changes tier updates
// its allowance and gets announced without any per-command wiring.
public final class TribeTierEffects {
    private static final int PASSIVE_INTERVAL_TICKS = 20; // reapply regen/haste every second
    private static final int GLOW_INTERVAL_TICKS = 10;    // refresh glow twice a second
    private static final int EFFECT_DURATION_TICKS = 60;  // 3s, comfortably outlasts the interval
    private static final int HEARTH_SUPPRESS_TICKS = 200; // 10s of no regen after taking damage
    private static final double PACK_RADIUS_SQR = 16.0 * 16.0;
    private static final double LONG_WATCH_RADIUS_SQR = 128.0 * 128.0;

    private static long serverTick = 0;
    private static final Map<UUID, Long> LAST_DAMAGE_TICK = new HashMap<>();
    // Per observer: the tribemates it is currently being shown glowing, so we can send a clean
    // "stop glowing" packet when one leaves range or the tribe drops below Long Watch.
    private static final Map<UUID, Set<UUID>> SHOWN_GLOW = new HashMap<>();

    private TribeTierEffects() {
    }

    public static void init() {
        // Observe (never block) damage so Hearth can suppress regen for 10s afterward.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                LAST_DAMAGE_TICK.put(player.getUUID(), serverTick);
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                onPlayerDeath(player);
            }
        });
        // COPY_FROM fires on respawn; `alive` is false specifically for death respawns.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                onDeathRespawn(oldPlayer, newPlayer);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            LAST_DAMAGE_TICK.remove(id);
            SHOWN_GLOW.remove(id);
        });
        ServerTickEvents.END_SERVER_TICK.register(TribeTierEffects::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        serverTick++;
        if (!TribeConfig.get().tierSystemEnabled) {
            return;
        }
        TribeSavedData data = TribeSavedData.get(server);

        if (serverTick % PASSIVE_INTERVAL_TICKS == 0) {
            announceAndReconcile(server, data);
            applyContinuousPassives(server, data);
        }
        if (serverTick % GLOW_INTERVAL_TICKS == 0) {
            updateGlow(server, data);
        }
    }

    // Broadcast a one-time message when a tribe first reaches a new tier, and keep its
    // force-load allowance in sync with its (possibly changed) tier.
    private static void announceAndReconcile(MinecraftServer server, TribeSavedData data) {
        for (Tribe tribe : data.getAllTribes()) {
            TribeTier tier = TribeTier.of(tribe);
            if (tier.number() > tribe.getAnnouncedTier()) {
                tribe.setAnnouncedTier(tier.number());
                data.setDirty();
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        tribe.getName() + " has advanced to Tier " + tier.number() + " — "
                                + tier.displayName() + "!").withStyle(ChatFormatting.GOLD), false);
            }
            TribeForceLoad.reconcile(server, data, tribe);
        }
    }

    private static void applyContinuousPassives(MinecraftServer server, TribeSavedData data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Tribe tribe = data.getTribeOf(player.getUUID());
            if (tribe == null) {
                continue;
            }
            int tierNum = TribeTier.of(tribe).number();

            // Hearth (tier 2+): Regeneration I inside your own claims, unless you've taken
            // damage within the last 10 seconds.
            if (tierNum >= 2 && standingInOwnClaim(data, player, tribe) && !recentlyDamaged(player)) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                        EFFECT_DURATION_TICKS, 0, true, false, true), null);
            }

            // Pack Instinct (tier 3+): Haste I while within 16 blocks of an online tribemate.
            if (tierNum >= 3 && tribemateWithin(server, tribe, player, PACK_RADIUS_SQR)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,
                        EFFECT_DURATION_TICKS, 0, true, false, true), null);
            }
        }
    }

    // Long Watch (tier 4+): each observer sees its tribemates within 128 blocks outlined
    // through terrain, and no one else. Re-asserts the glow each cycle (a vanilla entity-data
    // update from the target would otherwise clear it) and sends an explicit revert when a
    // tribemate leaves range or the tribe falls below the tier.
    private static void updateGlow(MinecraftServer server, TribeSavedData data) {
        for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
            Set<UUID> desired = new HashSet<>();
            Tribe tribe = data.getTribeOf(observer.getUUID());
            if (tribe != null && TribeTier.of(tribe).number() >= 4) {
                for (UUID memberId : tribe.getMembers().keySet()) {
                    if (memberId.equals(observer.getUUID())) {
                        continue;
                    }
                    ServerPlayer target = server.getPlayerList().getPlayer(memberId);
                    if (target != null && target.getLevel() == observer.getLevel()
                            && observer.distanceToSqr(target) <= LONG_WATCH_RADIUS_SQR) {
                        desired.add(memberId);
                        TribeGlow.showGlow(observer, target);
                    }
                }
            }

            Set<UUID> previous = SHOWN_GLOW.getOrDefault(observer.getUUID(), Set.of());
            for (UUID prevId : previous) {
                if (!desired.contains(prevId)) {
                    ServerPlayer target = server.getPlayerList().getPlayer(prevId);
                    if (target != null) {
                        TribeGlow.clearGlow(observer, target);
                    }
                }
            }
            if (desired.isEmpty()) {
                SHOWN_GLOW.remove(observer.getUUID());
            } else {
                SHOWN_GLOW.put(observer.getUUID(), desired);
            }
        }
    }

    private static void onPlayerDeath(ServerPlayer player) {
        if (!TribeConfig.get().tierSystemEnabled) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        TribeSavedData data = TribeSavedData.get(server);
        Tribe tribe = data.getTribeOf(player.getUUID());
        if (tribe == null || TribeTier.of(tribe).number() < 4) {
            return;
        }
        BlockPos pos = player.blockPosition();
        String dimension = player.getLevel().dimension().location().toString();
        Component message = Component.literal("[" + tribe.getName() + "] " + player.getName().getString()
                + " died at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + dimension + ")")
                .withStyle(ChatFormatting.RED);
        for (UUID memberId : tribe.getMembers().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                online.sendSystemMessage(message);
            }
        }
    }

    // Enduring (tier 5): carry the player's XP levels/points through a death respawn.
    private static void onDeathRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        if (!TribeConfig.get().tierSystemEnabled) {
            return;
        }
        MinecraftServer server = newPlayer.getServer();
        if (server == null) {
            return;
        }
        TribeSavedData data = TribeSavedData.get(server);
        Tribe tribe = data.getTribeOf(newPlayer.getUUID());
        if (tribe == null || TribeTier.of(tribe).number() < 5) {
            return;
        }
        newPlayer.experienceLevel = oldPlayer.experienceLevel;
        newPlayer.experienceProgress = oldPlayer.experienceProgress;
        newPlayer.totalExperience = oldPlayer.totalExperience;
    }

    private static boolean standingInOwnClaim(TribeSavedData data, ServerPlayer player, Tribe tribe) {
        return data.getTribeAt(ClaimPos.of(player.getLevel(), player.blockPosition())) == tribe;
    }

    private static boolean recentlyDamaged(ServerPlayer player) {
        Long last = LAST_DAMAGE_TICK.get(player.getUUID());
        return last != null && (serverTick - last) < HEARTH_SUPPRESS_TICKS;
    }

    private static boolean tribemateWithin(MinecraftServer server, Tribe tribe, ServerPlayer player, double radiusSqr) {
        for (UUID memberId : tribe.getMembers().keySet()) {
            if (memberId.equals(player.getUUID())) {
                continue;
            }
            ServerPlayer mate = server.getPlayerList().getPlayer(memberId);
            if (mate != null && mate.getLevel() == player.getLevel()
                    && player.distanceToSqr(mate) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }
}
