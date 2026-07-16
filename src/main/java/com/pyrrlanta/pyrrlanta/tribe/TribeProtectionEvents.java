package com.pyrrlanta.pyrrlanta.tribe;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

// Enforces tribe claim protection: only members (or anyone, if the tribe allows public
// access) may break/place blocks or interact with blocks inside a claimed chunk.
// PvP is blocked inside claims unless the tribe has PvP enabled.
//
// Only block-break, block-interact, and PvP are wired here via real Fabric API callbacks --
// block placement, explosions, natural mob spawning, death drops, and XP drops have no Fabric
// API equivalent at this Minecraft version and are handled by Mixins instead (see the
// com.pyrrlanta.pyrrlanta.mixin package), which call back into canModify/canOpenContainer here.
public final class TribeProtectionEvents {
    private TribeProtectionEvents() {
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return true;
            }
            return canModify(serverLevel, pos, player);
        });

        // Vanilla's single interactBlock method (which UseBlockCallback wraps) runs block-use
        // (e.g. opening a chest/door) then item-use-on-block (e.g. placing a block) in one
        // pass -- there's no way to deny just one half on Fabric, but that matches this mod's
        // own logic anyway: canModify/canOpenContainer below always gate both together.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hitResult.getBlockPos();
            boolean isContainer = level.getBlockEntity(pos) instanceof Container;
            boolean allowed = isContainer ? canOpenContainer(serverLevel, pos, player) : canModify(serverLevel, pos, player);
            return allowed ? InteractionResult.PASS : InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(entity instanceof Player victim) || !(victim.level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }
            TribeSavedData data = TribeSavedData.get(serverLevel.getServer());
            ClaimPos pos = ClaimPos.of(serverLevel, victim.blockPosition());
            Tribe owner = data.getTribeAt(pos);
            if (owner != null && !owner.isPvpEnabled()) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    // Claims are open to everyone by default. A tribe only becomes protected once a
    // high-ranking member turns it on with /tribe toggle protect true, at which point only
    // members and explicitly trusted outsiders may build or interact.
    public static boolean canModify(ServerLevel level, BlockPos pos, Player player) {
        TribeSavedData data = TribeSavedData.get(level.getServer());
        Tribe owner = data.getTribeAt(ClaimPos.of(level, pos));
        if (owner == null || !owner.isProtectionEnabled()) {
            return true;
        }
        // Admin land has no members, so membership can never grant access there -- OP does
        // instead. Without this nobody at all could build on it, including the admins who
        // claimed it.
        if (owner.isAdminTribe()) {
            return player.hasPermissions(2);
        }
        UUID playerId = player.getUUID();
        return owner.isMember(playerId) || owner.isTrusted(playerId);
    }

    // Separate toggle (/tribe toggle chests true) so a tribe can protect its loot without
    // necessarily locking down general building, or vice versa.
    public static boolean canOpenContainer(ServerLevel level, BlockPos pos, Player player) {
        TribeSavedData data = TribeSavedData.get(level.getServer());
        Tribe owner = data.getTribeAt(ClaimPos.of(level, pos));
        if (owner == null || !owner.isChestProtectionEnabled()) {
            return true;
        }
        UUID playerId = player.getUUID();
        return owner.isMember(playerId) || owner.isTrusted(playerId);
    }
}
