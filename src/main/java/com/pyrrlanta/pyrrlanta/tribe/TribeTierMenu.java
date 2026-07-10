package com.pyrrlanta.pyrrlanta.tribe;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// Read-only tier overview, opened from the main tribe GUI's tier button. Shows all five tiers
// as items (requirements, force-load allowance, and passive in the hover tooltip), with the
// tribe's current tier highlighted, plus a back button to the main menu. Like TribeMenu this
// reuses vanilla's chest screen and intercepts clicks server-side so nothing is a real item.
public class TribeTierMenu extends ChestMenu {
    private static final int SIZE = 27; // GENERIC_9x3
    private static final int BACK_SLOT = 22;
    // Tier items sit in the middle row, centered.
    private static final int[] TIER_SLOTS = {10, 11, 12, 13, 14};

    private final Tribe tribe;
    private final TribeSavedData data;
    private final ServerPlayer viewer;

    private TribeTierMenu(int containerId, Inventory playerInventory, ServerPlayer viewer, Tribe tribe, TribeSavedData data) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(SIZE), 3);
        this.tribe = tribe;
        this.data = data;
        this.viewer = viewer;
        populate();
    }

    public static void openFor(ServerPlayer player, Tribe tribe, TribeSavedData data) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new TribeTierMenu(containerId, inventory, player, tribe, data),
                Component.literal(tribe.getName() + " - Tiers")));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == BACK_SLOT) {
            TribeMenu.openFor(viewer, tribe, data);
            return;
        }
        if (slotId >= 0 && slotId < SIZE) {
            return; // tier items are informational; ignore all other top-inventory clicks
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void populate() {
        int current = TribeTier.of(tribe).number();
        TribeTier[] tiers = TribeTier.values();
        for (int i = 0; i < tiers.length && i < TIER_SLOTS.length; i++) {
            getSlot(TIER_SLOTS[i]).set(tierIcon(tiers[i], current));
        }
        getSlot(BACK_SLOT).set(named(Items.ARROW, "Back"));
    }

    private ItemStack tierIcon(TribeTier tier, int currentNumber) {
        Item icon;
        String suffix;
        if (tier.number() == currentNumber) {
            icon = Items.NETHER_STAR;
            suffix = " (CURRENT)";
        } else if (tier.number() < currentNumber) {
            icon = Items.EMERALD;
            suffix = " (unlocked)";
        } else {
            icon = Items.GRAY_DYE;
            suffix = " (locked)";
        }

        ItemStack stack = new ItemStack(icon);
        stack.setHoverName(Component.literal("Tier " + tier.number() + ": " + tier.displayName() + suffix)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));

        List<Component> lore = new ArrayList<>();
        String requirement = tier.minChunks() == 0
                ? "Requires: " + tier.minMembers() + " member" + (tier.minMembers() == 1 ? "" : "s")
                : "Requires: " + tier.minMembers() + " members, " + tier.minChunks() + " chunks";
        lore.add(loreLine(requirement, ChatFormatting.GRAY));
        lore.add(loreLine("Force-loaded chunks: " + tier.forceLoadLimit(), ChatFormatting.GRAY));
        lore.add(loreLine("", ChatFormatting.GRAY));
        for (String line : wrap(tier.passive(), 42)) {
            lore.add(loreLine(line, ChatFormatting.YELLOW));
        }
        setLore(stack, lore);
        return stack;
    }

    private static Component loreLine(String text, ChatFormatting color) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static void setLore(ItemStack stack, List<Component> lines) {
        ListTag loreTag = new ListTag();
        for (Component line : lines) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        stack.getOrCreateTagElement("display").put("Lore", loreTag);
    }

    // Vanilla item tooltips don't wrap lore lines, so break the passive text on spaces into
    // chunks of at most maxLen characters for readability.
    private static List<String> wrap(String text, int maxLen) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxLen) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name).setStyle(Style.EMPTY.withItalic(false)));
        return stack;
    }
}
