package com.pyrrlanta.pyrrlanta.mixin;

import com.pyrrlanta.pyrrlanta.tribe.ClaimPos;
import com.pyrrlanta.pyrrlanta.tribe.Tribe;
import com.pyrrlanta.pyrrlanta.tribe.TribeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Enforces a tribe's "fire spread blocked" toggle by cancelling a fire block's scheduled tick
// inside protected claims. FireBlock#tick is where fire ages, burns out, and -- crucially --
// ignites neighbouring blocks; cancelling it at HEAD stops the spread entirely while leaving
// the fire itself in place.
//
// This deliberately does NOT remove existing fire (an earlier approach scanned claims and
// deleted every fire block, which broke Create machines like the bulk haunter that depend on
// a persistent fire). A frozen fire simply sits without spreading; the tribe can extinguish
// it by hand if they want it gone.
@Mixin(FireBlock.class)
public abstract class MixinTribeFireSpread {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onFireTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        TribeSavedData data = TribeSavedData.get(level.getServer());
        Tribe owner = data.getTribeAt(ClaimPos.of(level, pos));
        if (owner != null && owner.isFireSpreadBlocked()) {
            ci.cancel();
        }
    }
}
