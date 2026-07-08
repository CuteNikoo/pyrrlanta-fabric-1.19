package com.pyrrlanta.pyrrlanta.mixin;

import com.pyrrlanta.pyrrlanta.tribe.ClaimPos;
import com.pyrrlanta.pyrrlanta.tribe.Tribe;
import com.pyrrlanta.pyrrlanta.tribe.TribeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fabric has no death-drops event equivalent to NeoForge's LivingDropsEvent. Death loot
// (dropEquipment/dropCustomDeathLoot) funnels through spawnAtLocation(ItemStack) one stack at
// a time, giving equivalent per-item interception -- reinsert into the dying player's
// inventory and suppress the ground-drop entity only if the reinsert fully succeeds, same as
// the NeoForge version's Inventory#add()-gated behavior. Scoped to ServerPlayer only, matching
// the original's keep-inventory feature (voluntary item drops via the Q key construct their
// own ItemEntity directly and don't go through this method, so they're unaffected).
@Mixin(Entity.class)
public abstract class MixinTribeLivingDrops {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void onSpawnAtLocation(ItemStack stack, CallbackInfoReturnable<ItemEntity> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level instanceof ServerLevel serverLevel)) {
            return;
        }
        TribeSavedData data = TribeSavedData.get(serverLevel.getServer());
        Tribe owner = data.getTribeAt(ClaimPos.of(serverLevel, player.blockPosition()));
        if (owner == null || !owner.isKeepInventory()) {
            return;
        }
        if (player.getInventory().add(stack)) {
            cir.setReturnValue(null);
        }
    }
}
