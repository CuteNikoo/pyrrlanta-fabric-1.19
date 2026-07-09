package com.pyrrlanta.pyrrlanta.mixin;

import com.pyrrlanta.pyrrlanta.tribe.TribeProtectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fabric has no block-place event equivalent to NeoForge's BlockEvent.EntityPlaceEvent, so
// this intercepts every block-item placement directly instead.
@Mixin(BlockItem.class)
public abstract class MixinTribeBlockPlace {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!TribeProtectionEvents.canModify(serverLevel, context.getClickedPos(), player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
