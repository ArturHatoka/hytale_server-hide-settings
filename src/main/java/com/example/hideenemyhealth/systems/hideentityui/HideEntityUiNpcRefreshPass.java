package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.util.WorldThreadExecutor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Refresh pass for applying the current UI-hide config to already-loaded NPCs in a world.
 */
final class HideEntityUiNpcRefreshPass {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HideEntityUiNpcRefreshPass() {
    }

    /**
     * Refresh all NPC-like entities currently present in a world.
     */
    static void refreshNpcs(@Nonnull final World world) {
        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();
        final boolean log = cfg.debug != null && cfg.debug.logRefreshStats;
        final long t0 = log ? System.nanoTime() : 0L;

        // Preferred path: iterate via ECS chunk iteration over NPCEntity component.
        final Store<EntityStore> worldStore = WorldStoreAccess.tryGetWorldStore(world);
        if (worldStore != null) {
            final int[] stats = new int[2]; // [0]=visited, [1]=changed
            try {
                worldStore.forEachChunk(NPCEntity.getComponentType(), (archetypeChunk, commandBuffer) -> {
                    for (int i = 0; i < archetypeChunk.size(); i++) {
                        final Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) continue;

                        // Fast skip: NPC archetypes without UIComponentList cannot be modified.
                        if (archetypeChunk.getComponent(i, UIComponentList.getComponentType()) == null) continue;

                        stats[0]++;
                        if (HideEntityUiApplier.applyForRefAndReport(ref, worldStore, commandBuffer, Boolean.TRUE)) {
                            stats[1]++;
                        }
                    }
                });
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[ServerHideSettings] Failed to refresh NPCs in world: %s", WorldThreadExecutor.safeWorldName(world));
            } finally {
                if (log) {
                    final long ms = (System.nanoTime() - t0) / 1_000_000L;
                    LOGGER.at(Level.INFO).log(
                            "[ServerHideSettings][Refresh] world=%s npcs visited=%d changed=%d timeMs=%d",
                            WorldThreadExecutor.safeWorldName(world), stats[0], stats[1], ms
                    );
                }
            }
            return;
        }

        final Iterable<?> npcRefs = NpcRefAccess.getNpcRefs(world);
        if (npcRefs == null) {
            if (log) {
                LOGGER.at(Level.INFO).log(
                        "[ServerHideSettings][Refresh] world=%s npcs skipped (API not available)",
                        WorldThreadExecutor.safeWorldName(world)
                );
            }
            return;
        }

        int visited = 0;
        int changed = 0;

        try {
            for (Object npcRefLike : npcRefs) {
                if (npcRefLike == null) continue;

                final Ref<EntityStore> ref = NpcRefAccess.coerceToEntityRef(npcRefLike);
                if (ref == null || !ref.isValid()) continue;

                visited++;

                final Store<EntityStore> store = ref.getStore();
                if (store == null) continue;

                if (HideEntityUiApplier.applyForRefAndReport(ref, store, null, Boolean.TRUE)) {
                    changed++;
                }
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[ServerHideSettings] NPC refresh skipped due to API differences (world=%s)", WorldThreadExecutor.safeWorldName(world));
        } finally {
            if (log) {
                final long ms = (System.nanoTime() - t0) / 1_000_000L;
                LOGGER.at(Level.INFO).log(
                        "[ServerHideSettings][Refresh] world=%s npcs visited=%d changed=%d timeMs=%d",
                        WorldThreadExecutor.safeWorldName(world), visited, changed, ms
                );
            }
        }
    }
}
