package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.util.WorldThreadExecutor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Refresh pass for applying the current UI-hide config to already-loaded players in a world.
 */
final class HideEntityUiPlayerRefreshPass {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HideEntityUiPlayerRefreshPass() {
    }

    /**
     * Refresh all players currently present in a world.
     */
    static void refreshPlayers(@Nonnull final World world) {
        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();
        final boolean log = cfg.debug != null && cfg.debug.logRefreshStats;
        final long t0 = log ? System.nanoTime() : 0L;

        // Preferred path: iterate via the world's EntityStore using ECS chunk iteration.
        // This gives us a CommandBuffer, which is the most reliable way to apply component updates.
        final Store<EntityStore> worldStore = WorldStoreAccess.tryGetWorldStore(world);
        if (worldStore != null) {
            final int[] stats = new int[2]; // [0]=visited, [1]=changed
            try {
                worldStore.forEachChunk(Player.getComponentType(), (archetypeChunk, commandBuffer) -> {
                    for (int i = 0; i < archetypeChunk.size(); i++) {
                        final Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) continue;

                        stats[0]++;
                        // Hot hide: update component list only (no component recreate).
                        // Hot hide: force a component-level recreate so clients update already-spawned player UI
                        // without requiring relog / re-stream.
                        if (HideEntityUiApplier.applyForRefAndReport(ref, worldStore, commandBuffer, Boolean.FALSE, true)) {
                            stats[1]++;
                        }
                    }
                });
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[ServerHideSettings] Failed to refresh players in world: %s", WorldThreadExecutor.safeWorldName(world));
            } finally {
                if (log) {
                    final long ms = (System.nanoTime() - t0) / 1_000_000L;
                    LOGGER.at(Level.INFO).log(
                            "[ServerHideSettings][Refresh] world=%s players visited=%d changed=%d timeMs=%d",
                            WorldThreadExecutor.safeWorldName(world), stats[0], stats[1], ms
                    );
                }
            }
            return;
        }

        // Fallback path: iterate via public player refs and write directly.
        int visited = 0;
        int changed = 0;
        try {
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                if (playerRef == null) continue;

                final Ref<EntityStore> ref = PlayerRefAccess.safeGetPlayerEntityRef(playerRef);
                if (ref == null || !ref.isValid()) continue;

                visited++;

                final Store<EntityStore> store = ref.getStore();
                if (store == null) continue;

                if (HideEntityUiApplier.applyForRefAndReport(ref, store, null, Boolean.FALSE, false)) {
                    changed++;
                }
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[ServerHideSettings] Failed to refresh players in world: %s", WorldThreadExecutor.safeWorldName(world));
        } finally {
            if (log) {
                final long ms = (System.nanoTime() - t0) / 1_000_000L;
                LOGGER.at(Level.INFO).log(
                        "[ServerHideSettings][Refresh] world=%s players visited=%d changed=%d timeMs=%d",
                        WorldThreadExecutor.safeWorldName(world), visited, changed, ms
                );
            }
        }
    }
}
