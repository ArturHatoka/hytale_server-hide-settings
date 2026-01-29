package com.example.hideenemyhealth.worldmap;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;

import javax.annotation.Nonnull;

/**
 * No-op provider that intentionally never emits player markers.
 */
final class NoopPlayerIconsProvider implements WorldMapManager.MarkerProvider {

    @Override
    public void update(@Nonnull World world,
                       @Nonnull MapMarkerTracker tracker,
                       int chunkViewRadius,
                       int playerChunkX,
                       int playerChunkZ) {
        // Intentionally empty.
    }
}
