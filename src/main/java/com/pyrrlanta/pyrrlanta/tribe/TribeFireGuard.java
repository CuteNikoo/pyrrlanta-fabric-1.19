package com.pyrrlanta.pyrrlanta.tribe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// Fire spread has no clean cancelable event on either loader at this Minecraft version, so
// "block fire spread" can't be true instant prevention the way break/place/pvp/explosion
// protection are. Instead, this periodically scans claimed chunks that have fireSpreadBlocked
// on and extinguishes any fire found. Fire can visibly exist for up to SCAN_INTERVAL_TICKS
// before being cleared -- a real but minor imperfection, not a silent failure.
public final class TribeFireGuard {
    private static final int SCAN_INTERVAL_TICKS = 40; // ~2 seconds

    private static int tickCounter = 0;

    private TribeFireGuard() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TribeFireGuard::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        scanAll(server);
    }

    private static void scanAll(MinecraftServer server) {
        TribeSavedData data = TribeSavedData.get(server);
        for (Tribe tribe : data.getAllTribes()) {
            if (!tribe.isFireSpreadBlocked()) {
                continue;
            }
            for (ClaimPos claim : tribe.getClaims()) {
                ServerLevel level = server.getLevel(claim.dimension());
                if (level != null) {
                    extinguish(level, claim.chunk());
                }
            }
        }
    }

    private static void extinguish(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    pos.set(minX + x, y, minZ + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }
}
