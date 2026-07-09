package com.pyrrlanta.pyrrlanta.tribe;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

// Reuses vanilla's anvil GUI purely as a free-text input box -- no custom client rendering
// needed, AnvilScreen already exists and knows how to drive any AnvilMenu. Type the value
// into the anvil's own rename field, then click the result slot to confirm (a chat message
// explains this when the menu opens, since the anvil gives no hint on its own).
//
// The result-slot click is intercepted directly in clicked() rather than left to vanilla's
// normal slot-click handling -- letting the normal pickup flow run (even with mayPickup
// overridden to allow it) puts the placeholder name tag on the player's cursor and then into
// their inventory once the menu closes, which was a real bug players hit. Intercepting the
// click before it ever reaches super.clicked() avoids that path entirely.
public class TribeTextInputMenu extends AnvilMenu {
    private final Consumer<String> onConfirm;
    private String typedName = "";

    private TribeTextInputMenu(int containerId, Inventory playerInventory, Consumer<String> onConfirm) {
        super(containerId, playerInventory);
        this.onConfirm = onConfirm;
        getSlot(INPUT_SLOT).set(new ItemStack(Items.NAME_TAG));
        getSlot(RESULT_SLOT).set(new ItemStack(Items.NAME_TAG));
    }

    public static void open(ServerPlayer player, String title, String instructions, Consumer<String> onConfirm) {
        player.sendSystemMessage(Component.literal(instructions));
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new TribeTextInputMenu(containerId, inventory, onConfirm),
                Component.literal(title)));
    }

    @Override
    public void setItemName(String itemName) {
        this.typedName = itemName;
    }

    @Override
    public void createResult() {
        getSlot(RESULT_SLOT).set(new ItemStack(Items.NAME_TAG));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == RESULT_SLOT) {
            String typed = typedName.trim();
            // Player#closeContainer() is protected at this Minecraft version; ServerPlayer
            // overrides it as public, and this menu is only ever opened for real players.
            ((ServerPlayer) player).closeContainer();
            if (!typed.isEmpty()) {
                onConfirm.accept(typed);
            }
            return;
        }
        if (slotId == INPUT_SLOT || slotId == ADDITIONAL_SLOT) {
            // Keep the placeholder in place -- picking it up would disable the rename field.
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
