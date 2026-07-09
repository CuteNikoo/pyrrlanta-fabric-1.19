package com.pyrrlanta.pyrrlanta.tribe;

import com.flowpowered.math.vector.Vector2d;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import com.pyrrlanta.pyrrlanta.Pyrrlanta;

import java.util.Optional;

// Soft integration with BlueMap (https://bluemap.bluecolored.de/): draws each tribe's
// claimed chunks as a colored, labeled rectangle on the web map, Towny-style.
//
// This class references BlueMap's API classes directly, so it must only ever be touched
// (even just classloaded) when the "bluemap" mod is confirmed present -- see the
// FabricLoader#isModLoaded("bluemap") check in Pyrrlanta.onInitialize(). Never reference
// this class unconditionally, or servers without BlueMap installed will crash with a
// NoClassDefFoundError.
//
// Dynmap's Fabric build has no public API for third-party mods to hook into (its MarkerAPI
// sits behind a package-private field with no readiness event, unlike BlueMap's clean
// BlueMapAPI.onEnable() callback), so BlueMap is used here too, same as the NeoForge side.
// BlueMap markers aren't persistent -- they must be redrawn whenever BlueMap (re)loads -- so
// instead of hooking every tribe-mutating command, this just redraws everything on a fixed
// timer. Claim counts are small, so the cost of a full rebuild every few seconds is negligible.
public final class TribeMapIntegration {
    private static final String MARKER_SET_ID = "pyrrlanta-tribes";
    private static final int REFRESH_INTERVAL_TICKS = 100; // ~5 seconds
    private static final float MARKER_HEIGHT = 64; // fixed flat height; not terrain-following

    private static int tickCounter = 0;

    private TribeMapIntegration() {
    }

    public static void init() {
        BlueMapAPI.onEnable(api -> Pyrrlanta.LOGGER.info("BlueMap detected; tribe claim markers enabled"));
        ServerTickEvents.END_SERVER_TICK.register(TribeMapIntegration::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < REFRESH_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        BlueMapAPI.getInstance().ifPresent(api -> refreshAll(api, server));
    }

    private static void refreshAll(BlueMapAPI api, MinecraftServer server) {
        TribeSavedData data = TribeSavedData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            Optional<BlueMapWorld> world = api.getWorld(level);
            if (world.isEmpty()) {
                continue;
            }
            for (BlueMapMap map : world.get().getMaps()) {
                MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(MARKER_SET_ID,
                        id -> MarkerSet.builder().label("Tribes").build());
                markerSet.getMarkers().clear();
                for (Tribe tribe : data.getAllTribes()) {
                    addTribeMarkers(markerSet, tribe, level);
                }
            }
        }
    }

    private static void addTribeMarkers(MarkerSet markerSet, Tribe tribe, ServerLevel level) {
        Color fill = shade(tribe, 0.35f);
        Color line = shade(tribe, 0.9f);
        String detail = "<b>" + tribe.getName() + "</b><br>Members: " + tribe.getMembers().size()
                + (tribe.isProtectionEnabled() ? "<br>Protected territory" : "<br>Open territory");

        for (ClaimPos claim : tribe.getClaims()) {
            if (!claim.dimension().equals(level.dimension())) {
                continue;
            }
            ChunkPos c = claim.chunk();
            Shape shape = Shape.createRect(
                    new Vector2d(c.getMinBlockX(), c.getMinBlockZ()),
                    new Vector2d(c.getMaxBlockX() + 1, c.getMaxBlockZ() + 1));
            ShapeMarker marker = ShapeMarker.builder()
                    .label(tribe.getName())
                    .shape(shape, MARKER_HEIGHT)
                    .fillColor(fill)
                    .lineColor(line)
                    .lineWidth(2)
                    .detail(detail)
                    .build();
            markerSet.getMarkers().put(tribe.getId() + "_" + c.x + "_" + c.z, marker);
        }
    }

    // Uses the tribe's custom color (/tribe set color) if set, otherwise a color
    // deterministically derived from the tribe's UUID so it's still stable across restarts.
    private static Color shade(Tribe tribe, float alpha) {
        if (tribe.hasColor()) {
            return new Color(tribe.getColor(), alpha);
        }
        float hue = (Math.abs(tribe.getId().hashCode()) % 360) / 360f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.6f, 0.9f) & 0xFFFFFF;
        return new Color(rgb, alpha);
    }
}
