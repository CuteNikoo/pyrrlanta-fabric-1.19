package com.pyrrlanta.pyrrlanta.tribe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Manages tribe chunk force-loading (vanilla ServerLevel#setChunkForced). A tribe's
// officers designate which of their claimed chunks to keep loaded; how many actually stay
// forced is capped by the tribe's current TribeTier#forceLoadLimit, so a tribe that drops a
// tier automatically sheds its excess forced chunks.
//
// reconcile() is the single authority: it re-asserts exactly the desired forced state and
// releases anything that shouldn't be forced (over the tier limit, or no longer claimed).
// It's cheap (tribe/claim counts are small) and idempotent -- setChunkForced no-ops when the
// state already matches -- so it's safe to call every tick from TribeTierEffects and once on
// server start (to reapply after a restart, since vanilla's own forced-chunk persistence is
// not treated as authoritative here).
public final class TribeForceLoad {
    private TribeForceLoad() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> reconcileAll(server, TribeSavedData.get(server)));
    }

    public static void reconcileAll(MinecraftServer server, TribeSavedData data) {
        for (Tribe tribe : data.getAllTribes()) {
            reconcile(server, data, tribe);
        }
    }

    public static void reconcile(MinecraftServer server, TribeSavedData data, Tribe tribe) {
        int limit = TribeTier.of(tribe).forceLoadLimit();
        boolean dirty = false;
        int forcedSoFar = 0;

        Iterator<ClaimPos> it = tribe.getForcedChunks().iterator();
        while (it.hasNext()) {
            ClaimPos pos = it.next();
            // A designated chunk the tribe no longer owns can never be forced; drop it.
            if (!tribe.getClaims().contains(pos)) {
                setForced(server, pos, false);
                it.remove();
                dirty = true;
                continue;
            }
            boolean shouldForce = forcedSoFar < limit;
            if (shouldForce) {
                forcedSoFar++;
            }
            setForced(server, pos, shouldForce);
        }

        if (dirty) {
            data.setDirty();
        }
    }

    // Officer designates the given chunk for force-loading. Returns a short result message.
    public static String designate(MinecraftServer server, TribeSavedData data, Tribe tribe, ClaimPos pos) {
        if (!tribe.getClaims().contains(pos)) {
            return "Your tribe hasn't claimed this chunk, so it can't be force-loaded.";
        }
        if (tribe.getForcedChunks().contains(pos)) {
            return "This chunk is already set to force-load.";
        }
        int limit = TribeTier.of(tribe).forceLoadLimit();
        if (limit <= 0) {
            return "Your tribe's tier (" + TribeTier.of(tribe).displayName() + ") can't force-load any chunks yet.";
        }
        if (tribe.getForcedChunks().size() >= limit) {
            return "Your tribe is already force-loading its limit of " + limit
                    + " chunk(s). Remove one first or reach a higher tier.";
        }
        tribe.getForcedChunks().add(pos);
        data.setDirty();
        reconcile(server, data, tribe);
        return "Now force-loading chunk " + pos.chunk().x + ", " + pos.chunk().z + " ("
                + tribe.getForcedChunks().size() + "/" + limit + ").";
    }

    public static String undesignate(MinecraftServer server, TribeSavedData data, Tribe tribe, ClaimPos pos) {
        if (!tribe.getForcedChunks().remove(pos)) {
            return "This chunk isn't set to force-load.";
        }
        setForced(server, pos, false);
        data.setDirty();
        reconcile(server, data, tribe);
        return "Stopped force-loading chunk " + pos.chunk().x + ", " + pos.chunk().z + ".";
    }

    public static List<ClaimPos> list(Tribe tribe) {
        return new ArrayList<>(tribe.getForcedChunks());
    }

    private static void setForced(MinecraftServer server, ClaimPos pos, boolean forced) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null) {
            return;
        }
        ChunkPos chunk = pos.chunk();
        level.setChunkForced(chunk.x, chunk.z, forced);
    }
}
