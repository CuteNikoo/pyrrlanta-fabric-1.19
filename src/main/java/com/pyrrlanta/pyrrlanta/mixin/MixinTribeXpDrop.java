package com.pyrrlanta.pyrrlanta.mixin;

import com.pyrrlanta.pyrrlanta.tribe.ClaimPos;
import com.pyrrlanta.pyrrlanta.tribe.Tribe;
import com.pyrrlanta.pyrrlanta.tribe.TribeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric has no XP-drop-cancel event equivalent to NeoForge's LivingExperienceDropEvent.
// Companion to MixinTribeLivingDrops: suppresses the dropped orbs, matching the "don't lose
// your stuff" spirit of the keep-inventory toggle -- does not attempt to replicate the
// vanilla keepInventory gamerule's XP-level preservation.
@Mixin(LivingEntity.class)
public abstract class MixinTribeXpDrop {
    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void onDropExperience(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level instanceof ServerLevel serverLevel)) {
            return;
        }
        TribeSavedData data = TribeSavedData.get(serverLevel.getServer());
        Tribe owner = data.getTribeAt(ClaimPos.of(serverLevel, player.blockPosition()));
        if (owner != null && owner.isKeepInventory()) {
            ci.cancel();
        }
    }
}
