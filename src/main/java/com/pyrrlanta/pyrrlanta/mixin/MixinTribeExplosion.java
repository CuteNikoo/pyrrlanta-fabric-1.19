package com.pyrrlanta.pyrrlanta.mixin;

import com.pyrrlanta.pyrrlanta.tribe.ClaimPos;
import com.pyrrlanta.pyrrlanta.tribe.Tribe;
import com.pyrrlanta.pyrrlanta.tribe.TribeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric has no explosion event equivalent to NeoForge's ExplosionEvent.Detonate. getToBlow()
// returns the explosion's live, mutable affected-block list, so it can be filtered in place
// right before finalizeExplosion() consumes it (removing/dropping blocks, damaging entities).
@Mixin(Explosion.class)
public abstract class MixinTribeExplosion {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void onFinalizeExplosion(boolean spawnParticles, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Explosion self = (Explosion) (Object) this;
        TribeSavedData data = TribeSavedData.get(serverLevel.getServer());
        self.getToBlow().removeIf(pos -> {
            Tribe owner = data.getTribeAt(ClaimPos.of(serverLevel, pos));
            return owner != null && owner.isProtectionEnabled();
        });
    }
}
