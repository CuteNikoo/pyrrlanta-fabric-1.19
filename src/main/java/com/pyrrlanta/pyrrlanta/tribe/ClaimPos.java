package com.pyrrlanta.pyrrlanta.tribe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

// A claimed chunk, identified by dimension + chunk coordinates.
public record ClaimPos(ResourceKey<Level> dimension, ChunkPos chunk) {

    public static ClaimPos of(Level level, BlockPos pos) {
        return new ClaimPos(level.dimension(), new ChunkPos(pos));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.location().toString());
        tag.putInt("x", chunk.x);
        tag.putInt("z", chunk.z);
        return tag;
    }

    public static ClaimPos load(CompoundTag tag) {
        ResourceLocation dimensionId = new ResourceLocation(tag.getString("dimension"));
        ResourceKey<Level> dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
        return new ClaimPos(dimension, new ChunkPos(tag.getInt("x"), tag.getInt("z")));
    }
}
