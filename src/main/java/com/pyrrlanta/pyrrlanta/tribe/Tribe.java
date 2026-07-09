package com.pyrrlanta.pyrrlanta.tribe;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Tribe {
    private final UUID id;
    private String name;
    private UUID leader;
    private final Map<UUID, TribeRole> members = new LinkedHashMap<>();
    private final Set<ClaimPos> claims = new LinkedHashSet<>();
    private final Set<UUID> invites = new LinkedHashSet<>();
    // Non-members allowed to bypass protection without being full tribe members.
    private final Set<UUID> trusted = new LinkedHashSet<>();

    private String greeting = "";
    private String farewell = "";

    // --- Toggles (all default false/off; a high-ranking member opts in) ---
    private boolean pvpEnabled = false;
    private boolean protectionEnabled = false;
    // Separate from protectionEnabled: gates opening chests/barrels/furnaces/hoppers/
    // shulker boxes/etc specifically, independent of general build protection.
    private boolean chestProtectionEnabled = false;
    private boolean mobSpawningBlocked = false;
    private boolean fireSpreadBlocked = false;
    private boolean keepInventory = false;
    // When true, anyone can /tribe join without needing an invite.
    private boolean open = false;
    // When true, the tribe periodically pays ore upkeep per claim (see TribeTaxCollector).
    private boolean taxesEnabled = false;

    // Ore balance, funded via /tribe deposit, spent claiming land beyond the founding claim.
    private long treasury = 0;

    // Packed RGB, or -1 if unset (falls back to a hash-derived color, e.g. on the map).
    private int color = -1;

    // Home location. homeDimension == null means no home has been set.
    private ResourceKey<Level> homeDimension;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;

    public Tribe(UUID id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members.put(leader, TribeRole.LEADER);
    }

    private Tribe(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Map<UUID, TribeRole> getMembers() {
        return members;
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public TribeRole roleOf(UUID player) {
        return members.get(player);
    }

    public boolean hasPermission(UUID player, TribeRole required) {
        TribeRole role = members.get(player);
        return role != null && role.atLeast(required);
    }

    public Set<ClaimPos> getClaims() {
        return claims;
    }

    public Set<UUID> getInvites() {
        return invites;
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    public boolean isTrusted(UUID player) {
        return trusted.contains(player);
    }

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public String getFarewell() {
        return farewell;
    }

    public void setFarewell(String farewell) {
        this.farewell = farewell;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isProtectionEnabled() {
        return protectionEnabled;
    }

    public void setProtectionEnabled(boolean protectionEnabled) {
        this.protectionEnabled = protectionEnabled;
    }

    public boolean isChestProtectionEnabled() {
        return chestProtectionEnabled;
    }

    public void setChestProtectionEnabled(boolean chestProtectionEnabled) {
        this.chestProtectionEnabled = chestProtectionEnabled;
    }

    public boolean isMobSpawningBlocked() {
        return mobSpawningBlocked;
    }

    public void setMobSpawningBlocked(boolean mobSpawningBlocked) {
        this.mobSpawningBlocked = mobSpawningBlocked;
    }

    public boolean isFireSpreadBlocked() {
        return fireSpreadBlocked;
    }

    public void setFireSpreadBlocked(boolean fireSpreadBlocked) {
        this.fireSpreadBlocked = fireSpreadBlocked;
    }

    public boolean isKeepInventory() {
        return keepInventory;
    }

    public void setKeepInventory(boolean keepInventory) {
        this.keepInventory = keepInventory;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isTaxesEnabled() {
        return taxesEnabled;
    }

    public void setTaxesEnabled(boolean taxesEnabled) {
        this.taxesEnabled = taxesEnabled;
    }

    public long getTreasury() {
        return treasury;
    }

    public void setTreasury(long treasury) {
        this.treasury = treasury;
    }

    public boolean hasColor() {
        return color != -1;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public boolean hasHome() {
        return homeDimension != null;
    }

    public ResourceKey<Level> getHomeDimension() {
        return homeDimension;
    }

    public double getHomeX() {
        return homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    public float getHomeYaw() {
        return homeYaw;
    }

    public float getHomePitch() {
        return homePitch;
    }

    public void setHome(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
        this.homeDimension = dimension;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeYaw = yaw;
        this.homePitch = pitch;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("leader", leader);
        tag.putString("greeting", greeting);
        tag.putString("farewell", farewell);
        tag.putBoolean("pvp", pvpEnabled);
        tag.putBoolean("protected", protectionEnabled);
        tag.putBoolean("chestProtected", chestProtectionEnabled);
        tag.putBoolean("mobSpawningBlocked", mobSpawningBlocked);
        tag.putBoolean("fireSpreadBlocked", fireSpreadBlocked);
        tag.putBoolean("keepInventory", keepInventory);
        tag.putBoolean("open", open);
        tag.putBoolean("taxesEnabled", taxesEnabled);
        tag.putLong("treasury", treasury);
        tag.putInt("color", color);

        ListTag membersTag = new ListTag();
        for (Map.Entry<UUID, TribeRole> entry : members.entrySet()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("player", entry.getKey());
            memberTag.putString("role", entry.getValue().name());
            membersTag.add(memberTag);
        }
        tag.put("members", membersTag);

        ListTag invitesTag = new ListTag();
        for (UUID invitee : invites) {
            CompoundTag inviteTag = new CompoundTag();
            inviteTag.putUUID("player", invitee);
            invitesTag.add(inviteTag);
        }
        tag.put("invites", invitesTag);

        ListTag trustedTag = new ListTag();
        for (UUID trustedPlayer : trusted) {
            CompoundTag trustedEntry = new CompoundTag();
            trustedEntry.putUUID("player", trustedPlayer);
            trustedTag.add(trustedEntry);
        }
        tag.put("trusted", trustedTag);

        ListTag claimsTag = new ListTag();
        for (ClaimPos claim : claims) {
            claimsTag.add(claim.save());
        }
        tag.put("claims", claimsTag);

        if (hasHome()) {
            CompoundTag homeTag = new CompoundTag();
            homeTag.putString("dimension", homeDimension.location().toString());
            homeTag.putDouble("x", homeX);
            homeTag.putDouble("y", homeY);
            homeTag.putDouble("z", homeZ);
            homeTag.putFloat("yaw", homeYaw);
            homeTag.putFloat("pitch", homePitch);
            tag.put("home", homeTag);
        }

        return tag;
    }

    public static Tribe load(CompoundTag tag) {
        Tribe tribe = new Tribe(tag.getUUID("id"));
        tribe.name = tag.getString("name");
        tribe.leader = tag.getUUID("leader");
        tribe.greeting = tag.getString("greeting");
        tribe.farewell = tag.getString("farewell");
        tribe.pvpEnabled = tag.getBoolean("pvp");
        tribe.protectionEnabled = tag.getBoolean("protected");
        tribe.chestProtectionEnabled = tag.getBoolean("chestProtected");
        tribe.mobSpawningBlocked = tag.getBoolean("mobSpawningBlocked");
        tribe.fireSpreadBlocked = tag.getBoolean("fireSpreadBlocked");
        tribe.keepInventory = tag.getBoolean("keepInventory");
        tribe.open = tag.getBoolean("open");
        tribe.taxesEnabled = tag.getBoolean("taxesEnabled");
        tribe.treasury = tag.getLong("treasury");
        tribe.color = tag.contains("color") ? tag.getInt("color") : -1;

        ListTag membersTag = tag.getList("members", Tag.TAG_COMPOUND);
        for (Tag t : membersTag) {
            CompoundTag memberTag = (CompoundTag) t;
            tribe.members.put(memberTag.getUUID("player"), TribeRole.valueOf(memberTag.getString("role")));
        }

        ListTag invitesTag = tag.getList("invites", Tag.TAG_COMPOUND);
        for (Tag t : invitesTag) {
            tribe.invites.add(((CompoundTag) t).getUUID("player"));
        }

        ListTag trustedTag = tag.getList("trusted", Tag.TAG_COMPOUND);
        for (Tag t : trustedTag) {
            tribe.trusted.add(((CompoundTag) t).getUUID("player"));
        }

        ListTag claimsTag = tag.getList("claims", Tag.TAG_COMPOUND);
        for (Tag t : claimsTag) {
            tribe.claims.add(ClaimPos.load((CompoundTag) t));
        }

        if (tag.contains("home")) {
            CompoundTag homeTag = tag.getCompound("home");
            ResourceLocation dimensionId = new ResourceLocation(homeTag.getString("dimension"));
            tribe.homeDimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
            tribe.homeX = homeTag.getDouble("x");
            tribe.homeY = homeTag.getDouble("y");
            tribe.homeZ = homeTag.getDouble("z");
            tribe.homeYaw = homeTag.getFloat("yaw");
            tribe.homePitch = homeTag.getFloat("pitch");
        }

        return tribe;
    }
}
