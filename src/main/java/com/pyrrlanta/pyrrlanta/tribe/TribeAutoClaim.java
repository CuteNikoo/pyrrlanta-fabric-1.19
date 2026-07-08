package com.pyrrlanta.pyrrlanta.tribe;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Tracks which players currently have autoclaim mode on (/tribe autoclaim true). This is a
// transient, per-session, per-player mode -- not persisted to NBT, similar in spirit to a
// movement toggle like sprinting -- cleared on logout so it can't leak across sessions.
// The actual claiming happens in TribeMessageEvents.onPlayerTick, piggybacking on the chunk-
// change detection that's already computed there for the greeting/farewell messages.
public final class TribeAutoClaim {
    private static final Set<UUID> ENABLED = new HashSet<>();

    private TribeAutoClaim() {
    }

    public static void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            ENABLED.add(playerId);
        } else {
            ENABLED.remove(playerId);
        }
    }

    public static boolean isEnabled(UUID playerId) {
        return ENABLED.contains(playerId);
    }

    public static void clear(UUID playerId) {
        ENABLED.remove(playerId);
    }
}
