package com.pyrrlanta.pyrrlanta.tribe;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

import java.util.function.Consumer;

// The main tribe "GUI". Reuses vanilla's largest generic chest menu/screen
// (MenuType.GENERIC_9x6) so no custom client-side rendering code is needed at all -- the
// client already knows how to draw any ChestMenu. This isn't real item storage: clicks on
// the top 54 slots are intercepted server-side and turned into tribe actions instead of
// moving items, and quickMoveStack is disabled entirely so nothing can be shift-clicked out.
//
// Layout (each row's items are centered around the row's own middle slot, with the true
// center and outer edges left blank as breathing room):
//   Row 0: tribe identity
//   Row 1: member/claim/treasury stats
//   Row 2: claim/unclaim/sethome/home/deposit/map/leave
//   Row 3: greeting/farewell/color (text-input settings)
//   Row 4-5: all 8 toggles
//
// Deliberately NOT buttons: disband (destructive, kept command-only), invite/kick/promote/
// demote/transfer/trust/untrust (need a player-name argument), accept/deny/join (only
// relevant before you're in a tribe -- see TribeTextInputMenu-based creation flow instead),
// and info/list for tribes other than your own. All of those remain text commands.
public class TribeMenu extends ChestMenu {
    private static final int SIZE = 54;

    private final Tribe tribe;
    private final TribeSavedData data;
    private final ServerPlayer viewer;

    private TribeMenu(int containerId, Inventory playerInventory, ServerPlayer viewer, Tribe tribe, TribeSavedData data) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, new SimpleContainer(SIZE), 6);
        this.tribe = tribe;
        this.data = data;
        this.viewer = viewer;
        populate();
    }

    public static void openFor(ServerPlayer player, Tribe tribe, TribeSavedData data) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new TribeMenu(containerId, inventory, player, tribe, data),
                Component.literal(tribe.getName())));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < SIZE) {
            handleClick(slotId);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void handleClick(int slotId) {
        switch (slotId) {
            case 18 -> doClaim();
            case 19 -> doUnclaim();
            case 20 -> doSetHome();
            case 21 -> doHome();
            case 23 -> doDeposit();
            case 24 -> doMap();
            case 25 -> doLeave();
            case 30 -> promptText("Set Greeting", "Type the new greeting message into the anvil, then click the result slot to confirm.", tribe::setGreeting);
            case 31 -> promptText("Set Farewell", "Type the new farewell message into the anvil, then click the result slot to confirm.", tribe::setFarewell);
            case 32 -> promptColor();
            case 38 -> toggle("Protection", tribe.isProtectionEnabled(), tribe::setProtectionEnabled);
            case 39 -> toggle("Chest/container protection", tribe.isChestProtectionEnabled(), tribe::setChestProtectionEnabled);
            case 41 -> toggle("PvP", tribe.isPvpEnabled(), tribe::setPvpEnabled);
            case 42 -> toggle("Mob spawning blocked", tribe.isMobSpawningBlocked(), tribe::setMobSpawningBlocked);
            case 47 -> toggle("Fire spread blocked", tribe.isFireSpreadBlocked(), tribe::setFireSpreadBlocked);
            case 48 -> toggle("Keep inventory", tribe.isKeepInventory(), tribe::setKeepInventory);
            case 50 -> toggle("Open membership", tribe.isOpen(), tribe::setOpen);
            case 51 -> toggle("Taxes", tribe.isTaxesEnabled(), tribe::setTaxesEnabled);
            default -> {
            }
        }
    }

    private boolean requireOfficer() {
        if (!tribe.hasPermission(viewer.getUUID(), TribeRole.OFFICER)) {
            viewer.sendSystemMessage(Component.literal("Only officers and the leader can do that."));
            return false;
        }
        return true;
    }

    private void toggle(String label, boolean current, Consumer<Boolean> setter) {
        if (!requireOfficer()) {
            return;
        }
        setter.accept(!current);
        data.setDirty();
        if (label.equals("Taxes") && !current && !TribeConfig.get().taxesEnabled) {
            viewer.sendSystemMessage(Component.literal("Note: the server has taxes disabled entirely, so this won't do anything yet."));
        }
        populate();
        broadcastChanges();
    }

    private void doClaim() {
        if (!requireOfficer()) {
            return;
        }
        ClaimPos pos = ClaimPos.of(viewer.getLevel(), viewer.blockPosition());
        Tribe existing = data.getTribeAt(pos);
        if (existing != null) {
            viewer.sendSystemMessage(Component.literal(existing == tribe
                    ? "This chunk is already claimed by your tribe."
                    : "This chunk is already claimed by " + existing.getName() + "."));
            return;
        }
        int maxClaims = TribeConfig.get().maxClaimsPerTribe;
        if (maxClaims > 0 && tribe.getClaims().size() >= maxClaims) {
            viewer.sendSystemMessage(Component.literal("Your tribe has reached the claim limit (" + maxClaims + ")."));
            return;
        }
        boolean founding = tribe.getClaims().isEmpty();
        int cost = TribeEconomy.claimCost(tribe.getClaims().size());
        if (!founding) {
            if (!data.isAdjacent(tribe, pos)) {
                viewer.sendSystemMessage(Component.literal("New claims must be adjacent to your tribe's existing territory."));
                return;
            }
            if (tribe.getTreasury() < cost) {
                viewer.sendSystemMessage(Component.literal("Not enough ore. Claiming here costs " + cost + ", your tribe has " + tribe.getTreasury() + "."));
                return;
            }
            tribe.setTreasury(tribe.getTreasury() - cost);
        }
        data.claim(tribe, pos);
        viewer.sendSystemMessage(Component.literal("Claimed chunk " + pos.chunk().x + ", " + pos.chunk().z
                + (founding ? " (founding claim, free)." : " for " + cost + " ore.")));
        populate();
        broadcastChanges();
    }

    private void doUnclaim() {
        if (!requireOfficer()) {
            return;
        }
        ClaimPos pos = ClaimPos.of(viewer.getLevel(), viewer.blockPosition());
        if (!data.unclaim(tribe, pos)) {
            viewer.sendSystemMessage(Component.literal("Your tribe does not own this chunk."));
            return;
        }
        viewer.sendSystemMessage(Component.literal("Unclaimed chunk " + pos.chunk().x + ", " + pos.chunk().z + "."));
        populate();
        broadcastChanges();
    }

    private void doSetHome() {
        if (!requireOfficer()) {
            return;
        }
        ClaimPos here = ClaimPos.of(viewer.getLevel(), viewer.blockPosition());
        if (data.getTribeAt(here) != tribe) {
            viewer.sendSystemMessage(Component.literal("You must be standing in your tribe's territory to set the home."));
            return;
        }
        tribe.setHome(viewer.getLevel().dimension(), viewer.getX(), viewer.getY(), viewer.getZ(), viewer.getYRot(), viewer.getXRot());
        data.setDirty();
        viewer.sendSystemMessage(Component.literal("Tribe home set."));
    }

    private void doHome() {
        if (!tribe.hasHome()) {
            viewer.sendSystemMessage(Component.literal("Your tribe has not set a home yet."));
            return;
        }
        ServerLevel level = viewer.getLevel().getServer().getLevel(tribe.getHomeDimension());
        if (level == null) {
            viewer.sendSystemMessage(Component.literal("Tribe home dimension is not loaded."));
            return;
        }
        viewer.closeContainer();
        viewer.teleportTo(level, tribe.getHomeX(), tribe.getHomeY(), tribe.getHomeZ(), tribe.getHomeYaw(), tribe.getHomePitch());
    }

    private void doDeposit() {
        ItemStack held = viewer.getMainHandItem();
        if (held.isEmpty() || !TribeEconomy.isAccepted(held.getItem())) {
            viewer.sendSystemMessage(Component.literal("Hold iron ingots, gold ingots, or diamonds in your main hand to deposit them."));
            return;
        }
        int toDeposit = held.getCount();
        String itemName = held.getItem().getDescription().getString();
        int value = TribeEconomy.valueOf(held.getItem()) * toDeposit;
        held.shrink(toDeposit);
        tribe.setTreasury(tribe.getTreasury() + value);
        data.setDirty();
        viewer.sendSystemMessage(Component.literal("Deposited " + toDeposit + " " + itemName + " for " + value
                + " ore. Treasury: " + tribe.getTreasury() + "."));
        populate();
        broadcastChanges();
    }

    private void doMap() {
        viewer.sendSystemMessage(Component.literal(TribeCommand.renderMap(viewer, data)));
    }

    private void doLeave() {
        if (viewer.getUUID().equals(tribe.getLeader())) {
            viewer.sendSystemMessage(Component.literal("The leader cannot leave. Transfer leadership first, or /tribe disband."));
            return;
        }
        data.removeMember(tribe, viewer.getUUID());
        viewer.sendSystemMessage(Component.literal("You left tribe '" + tribe.getName() + "'."));
        viewer.closeContainer();
    }

    private void promptText(String title, String instructions, Consumer<String> setter) {
        if (!requireOfficer()) {
            return;
        }
        TribeTextInputMenu.open(viewer, title, instructions, typed -> {
            setter.accept(typed);
            data.setDirty();
            viewer.sendSystemMessage(Component.literal(title + " updated."));
            openFor(viewer, tribe, data);
        });
    }

    private void promptColor() {
        if (!requireOfficer()) {
            return;
        }
        TribeTextInputMenu.open(viewer, "Set Color (hex, no #)",
                "Type a 6-digit hex color (no #, e.g. 3498db) into the anvil, then click the result slot to confirm.", typed -> {
            String cleaned = typed.startsWith("#") ? typed.substring(1) : typed;
            if (!cleaned.matches("[0-9a-fA-F]{6}")) {
                viewer.sendSystemMessage(Component.literal("Color must be a 6-digit hex code (no #), e.g. 3498db."));
            } else {
                tribe.setColor(Integer.parseInt(cleaned, 16));
                data.setDirty();
                viewer.sendSystemMessage(Component.literal(tribe.getName() + "'s color updated."));
            }
            openFor(viewer, tribe, data);
        });
    }

    private void populate() {
        setSlot(4, named(Items.BOOK, tribe.getName() + " - Leader: " + leaderName()));

        setSlot(11, named(Items.PLAYER_HEAD, "Members: " + tribe.getMembers().size()));
        setSlot(13, named(Items.GRASS_BLOCK, "Claims: " + tribe.getClaims().size() + claimsLimitSuffix()));
        setSlot(15, named(Items.DIAMOND, "Treasury: " + tribe.getTreasury() + " ore"));

        setSlot(18, named(Items.WHITE_BANNER, "Claim this chunk"));
        setSlot(19, named(Items.BARRIER, "Unclaim this chunk"));
        setSlot(20, named(Items.RED_BED, "Set tribe home here"));
        setSlot(21, named(Items.COMPASS, "Teleport to tribe home"));
        setSlot(23, named(Items.GOLD_INGOT, "Deposit held ore (iron/gold/diamonds)"));
        setSlot(24, named(Items.MAP, "Show tribe map"));
        setSlot(25, named(Items.OAK_DOOR, "Leave tribe"));

        setSlot(30, named(Items.OAK_SIGN, "Set greeting message"));
        setSlot(31, named(Items.DARK_OAK_SIGN, "Set farewell message"));
        setSlot(32, named(Items.WHITE_DYE, "Set tribe color"));

        setSlot(38, toggleIcon("Protection", tribe.isProtectionEnabled()));
        setSlot(39, toggleIcon("Chest/container protection", tribe.isChestProtectionEnabled()));
        setSlot(41, toggleIcon("PvP", tribe.isPvpEnabled()));
        setSlot(42, toggleIcon("Mob spawning blocked", tribe.isMobSpawningBlocked()));

        setSlot(47, toggleIcon("Fire spread blocked", tribe.isFireSpreadBlocked()));
        setSlot(48, toggleIcon("Keep inventory", tribe.isKeepInventory()));
        setSlot(50, toggleIcon("Open membership", tribe.isOpen()));
        setSlot(51, toggleIcon("Taxes", tribe.isTaxesEnabled()));
    }

    private String claimsLimitSuffix() {
        int max = TribeConfig.get().maxClaimsPerTribe;
        return max > 0 ? " / " + max : " (unlimited)";
    }

    private void setSlot(int index, ItemStack stack) {
        getSlot(index).set(stack);
    }

    private String leaderName() {
        ServerPlayer online = viewer.getLevel().getServer().getPlayerList().getPlayer(tribe.getLeader());
        return online != null ? online.getName().getString() : tribe.getLeader().toString();
    }

    private static ItemStack toggleIcon(String label, boolean on) {
        ItemStack stack = new ItemStack(on ? Items.LIME_DYE : Items.GRAY_DYE);
        stack.setHoverName(Component.literal(label + ": " + (on ? "ON" : "OFF") + " (click to toggle)"));
        return stack;
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name));
        return stack;
    }
}
