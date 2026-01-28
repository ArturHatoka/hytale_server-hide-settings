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
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Defensive baseline-cache garbage collection pass.
 */
final class HideEntityUiBaselineGcPass {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HideEntityUiBaselineGcPass() {
    }

    /**
     * Perform a conservative baseline-cache sweep for a single world.
     */
    static void baselineGcSweepWorld(@Nonnull final World world) {
        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();
        final boolean log = cfg.debug != null && cfg.debug.logRefreshStats;
        final long t0 = log ? System.nanoTime() : 0L;

        final LongHashSet aliveKeys = new LongHashSet(256);
        final IntHashSet storeIds = new IntHashSet(8);

        final int[] seen = new int[2]; // [0]=players, [1]=npcs

        // Preferred path: gather alive keys via the world's EntityStore.
        final Store<EntityStore> worldStore = WorldStoreAccess.tryGetWorldStore(world);
        if (worldStore != null) {
            try {
                worldStore.forEachChunk(Player.getComponentType(), (archetypeChunk, commandBuffer) -> {
                    for (int i = 0; i < archetypeChunk.size(); i++) {
                        final Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) continue;

                        final long key = EntityUiBaselineCache.entityKey(ref);
                        aliveKeys.add(key);
                        storeIds.add(EntityUiBaselineCache.storeIdFromKey(key));
                        seen[0]++;
                    }
                });

                worldStore.forEachChunk(NPCEntity.getComponentType(), (archetypeChunk, commandBuffer) -> {
                    for (int i = 0; i < archetypeChunk.size(); i++) {
                        final Ref<EntityStore> ref = archetypeChunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) continue;

                        final long key = EntityUiBaselineCache.entityKey(ref);
                        aliveKeys.add(key);
                        storeIds.add(EntityUiBaselineCache.storeIdFromKey(key));
                        seen[1]++;
                    }
                });
            } catch (Throwable ignored) {
                // If store iteration fails for any reason, fall back to best-effort API methods below.
            }
        }

        // Fallback path: Players are accessible via world.getPlayerRefs().
        if (storeIds.isEmpty()) {
            try {
                for (PlayerRef playerRef : world.getPlayerRefs()) {
                    if (playerRef == null) continue;
                    final Ref<EntityStore> ref = PlayerRefAccess.safeGetPlayerEntityRef(playerRef);
                    if (ref == null || !ref.isValid()) continue;

                    final long key = EntityUiBaselineCache.entityKey(ref);
                    aliveKeys.add(key);
                    storeIds.add(EntityUiBaselineCache.storeIdFromKey(key));
                    seen[0]++;
                }
            } catch (Throwable ignored) {
            }

            // NPCs may be unavailable depending on server build.
            final Iterable<?> npcRefs = NpcRefAccess.getNpcRefs(world);
            if (npcRefs != null) {
                try {
                    for (Object npcRefLike : npcRefs) {
                        if (npcRefLike == null) continue;
                        final Ref<EntityStore> ref = NpcRefAccess.coerceToEntityRef(npcRefLike);
                        if (ref == null || !ref.isValid()) continue;

                        final long key = EntityUiBaselineCache.entityKey(ref);
                        aliveKeys.add(key);
                        storeIds.add(EntityUiBaselineCache.storeIdFromKey(key));
                        seen[1]++;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // We can only sweep stores for which we observed at least one entity ref.
        if (storeIds.isEmpty()) return;

        // We can see the world's EntityStore -> safe to sweep both kinds.
        // On fallback paths, keep previous conservative behavior.
        final int sweepKindsMask = (worldStore != null)
                ? (EntityUiBaselineCache.KIND_PLAYER | EntityUiBaselineCache.KIND_NPC)
                : (EntityUiBaselineCache.KIND_PLAYER | (seen[1] > 0 ? EntityUiBaselineCache.KIND_NPC : 0));

        int removed = 0;
        for (int i = 0; i < storeIds.capacity(); i++) {
            if (!storeIds.isUsedAt(i)) continue;
            final int storeId = storeIds.getValueAt(i);
            removed += EntityUiBaselineCache.sweepOrphanedForStore(storeId, aliveKeys, sweepKindsMask);
        }

        if (log) {
            final long ms = (System.nanoTime() - t0) / 1_000_000L;
            LOGGER.at(Level.INFO).log(
                    "[ServerHideSettings][BaselineGC] world=%s stores=%d seenPlayers=%d seenNpcs=%d removed=%d timeMs=%d",
                    WorldThreadExecutor.safeWorldName(world), storeIds.count(), seen[0], seen[1], removed, ms
            );
        }
    }
}
