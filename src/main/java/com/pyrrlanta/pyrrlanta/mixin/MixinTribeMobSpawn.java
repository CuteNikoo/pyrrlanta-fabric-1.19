package com.pyrrlanta.pyrrlanta.mixin;

import com.pyrrlanta.pyrrlanta.tribe.ClaimPos;
import com.pyrrlanta.pyrrlanta.tribe.Tribe;
import com.pyrrlanta.pyrrlanta.tribe.TribeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fabric has no natural-spawn-cancel event equivalent to NeoForge's FinalizeSpawnEvent.
// finalizeSpawn's return value doesn't actually control whether the mob gets added to the
// world -- NaturalSpawner calls level.addFreshEntity(mob) unconditionally afterward -- so
// cancelling a spawn here means discarding the entity directly, not just returning early.
@Mixin(Mob.class)
public abstract class MixinTribeMobSpawn {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void onFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                  SpawnGroupData spawnGroupData, CompoundTag tag, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (spawnType != MobSpawnType.NATURAL) {
            return;
        }
        Mob self = (Mob) (Object) this;
        if (!(self instanceof Enemy)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = self.blockPosition();
        TribeSavedData data = TribeSavedData.get(serverLevel.getServer());
        Tribe owner = data.getTribeAt(ClaimPos.of(serverLevel, pos));
        if (owner != null && owner.isMobSpawningBlocked()) {
            self.discard();
        }
    }
}
