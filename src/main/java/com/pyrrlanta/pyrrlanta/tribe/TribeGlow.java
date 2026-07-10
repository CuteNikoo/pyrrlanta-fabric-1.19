package com.pyrrlanta.pyrrlanta.tribe;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

// Per-observer entity glow, achieved without touching the target's real state or requiring a
// client mod (this mod is server-side, and players use vanilla clients).
//
// Every entity syncs a "shared flags" byte (data field 0) whose bit 6 is the glowing flag,
// which vanilla clients render as an outline drawn through terrain. We send a hand-crafted
// ClientboundSetEntityDataPacket carrying just that byte -- with the glow bit forced on -- to
// only the observers who should see the target glowing. Because the target's server-side
// SynchedEntityData is never modified, no one else sees any change, and the next genuine data
// update from the entity would naturally overwrite our override on the observer's client (so
// callers must re-send periodically and send an explicit "off" packet to revert cleanly).
//
// The packet has no list-taking constructor at this Minecraft version, only one that packs
// from a real SynchedEntityData (tied to an entity) -- so we build the wire form by hand via
// FriendlyByteBuf (writeVarInt(entityId) + SynchedEntityData.pack(items)) and use the packet's
// byte-buf decoding constructor, which is exactly what the entity would have produced.
public final class TribeGlow {
    private static final int GLOWING_FLAG_BIT = 6; // shared-flags bit 6 == glowing
    // Shared flags is always data field id 0 with the BYTE serializer. Reconstructing the
    // accessor (rather than reaching the target's private DATA_SHARED_FLAGS_ID) is fine:
    // SynchedEntityData looks values up by accessor id, and packing serializes by id.
    private static final EntityDataAccessor<Byte> SHARED_FLAGS =
            new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

    private TribeGlow() {
    }

    // Show `target` glowing to `observer` only, preserving the target's other shared flags
    // (sneaking/sprinting/etc.) so the observer's view stays otherwise accurate.
    public static void showGlow(ServerPlayer observer, ServerPlayer target) {
        byte flags = target.getEntityData().get(SHARED_FLAGS);
        byte withGlow = (byte) (flags | (1 << GLOWING_FLAG_BIT));
        sendSharedFlags(observer, target.getId(), withGlow);
    }

    // Revert `observer`'s view of `target` to the target's real shared flags (i.e. no forced
    // glow). Call this when the target should no longer appear glowing to this observer.
    public static void clearGlow(ServerPlayer observer, ServerPlayer target) {
        byte flags = target.getEntityData().get(SHARED_FLAGS);
        sendSharedFlags(observer, target.getId(), flags);
    }

    private static void sendSharedFlags(ServerPlayer observer, int targetId, byte flags) {
        List<SynchedEntityData.DataItem<?>> items =
                List.of(new SynchedEntityData.DataItem<>(SHARED_FLAGS, flags));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(targetId);
        SynchedEntityData.pack(items, buf);
        observer.connection.send(new ClientboundSetEntityDataPacket(buf));
    }
}
