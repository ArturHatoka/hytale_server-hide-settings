package com.example.hideenemyhealth.worldmap;

import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
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
        // Respect world gameplay config: if players are not meant to be displayed, do nothing anyway.
        try {
            WorldMapConfig worldMapConfig = world.getGameplayConfig().getWorldMapConfig();
            if (!worldMapConfig.isDisplayPlayers()) {
                return;
            }
        } catch (Throwable ignored) {
            // If config access changes, we still do nothing (hide).
        }
    }
}
