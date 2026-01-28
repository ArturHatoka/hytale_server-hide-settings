package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores baseline (original) {@code UIComponentList.componentIds} for entities the plugin has touched.
 *
 * <p>We need baseline snapshots so we can:
 * <ul>
 *   <li>remove specific UI entries without permanently losing the rest</li>
 *   <li>restore the exact original list when un-hiding or disabling the plugin</li>
 * </ul>
 * </p>
 */
public final class EntityUiBaselineCache {

    /** Unknown entity category (avoid aggressive GC sweeps). */
    public static final byte KIND_UNKNOWN = 0;
    /** Baseline belongs to a player entity. */
    public static final byte KIND_PLAYER = 1;
    /** Baseline belongs to an NPC entity. */
    public static final byte KIND_NPC = 2;

    private static final ConcurrentHashMap<Long, BaselineEntry> BASELINES = new ConcurrentHashMap<>();

    private EntityUiBaselineCache() {
    }

    /**
     * Build a stable key for a {@link Ref} within a store.
     *
     * <p>We use {@code (storeIdentityHash << 32) ^ ref.getIndex()}. Indexes are per-store; combining with store id
     * prevents collisions across worlds.</p>
     *
     * @param ref entity reference
     * @return 64-bit key stable for the lifetime of the store
     */
    public static long entityKey(@Nonnull final Ref<EntityStore> ref) {
        try {
            if (!ref.isValid()) return System.identityHashCode(ref);
            final int storeId = System.identityHashCode(ref.getStore());
            final long idx = ((long) ref.getIndex()) & 0xFFFF_FFFFL;
            return (((long) storeId) << 32) ^ idx;
        } catch (Throwable ignored) {
            return System.identityHashCode(ref);
        }
    }

    /**
     * Extract store id from an {@link #entityKey(Ref)}.
     */
    public static int storeIdFromKey(final long key) {
        return (int) (key >>> 32);
    }

    /**
     * Store baseline for a key if absent.
     *
     * @param key         entity key (see {@link #entityKey(Ref)})
     * @param baselineIds baseline IDs
     * @param kind        entity category (player/npc)
     * @return stored baseline IDs (existing or the provided one)
     */
    @Nonnull
    public static int[] putBaselineIfAbsent(final long key, @Nonnull final int[] baselineIds, final byte kind) {
        final BaselineEntry entry = BASELINES.computeIfAbsent(key, k -> new BaselineEntry(baselineIds.clone(), kind));
        // If we learned the entity kind later, update it (unknown -> known).
        if (entry.kind == KIND_UNKNOWN && kind != KIND_UNKNOWN) {
            entry.kind = kind;
        }        return entry.baselineIds;
    }

    /**
     * @param key entity key
     * @return baseline IDs, or null if the entity was never touched
     */
    @Nullable
    public static int[] getBaseline(final long key) {
        final BaselineEntry entry = BASELINES.get(key);
        if (entry == null) return null;        return entry.baselineIds;
    }

    /**
     * Remove baseline entry for an entity key.
     */
    public static void remove(final long key) {
        BASELINES.remove(key);
    }

    /**
     * Remove baseline entry for a ref.
     */
    public static void remove(@Nonnull final Ref<EntityStore> ref) {
        remove(entityKey(ref));
    }

    /**
     * Clear all baseline entries (used on plugin shutdown / hot-reload).
     */
    public static void clearAll() {
        BASELINES.clear();
    }

    /**
     * Sweep orphan baseline entries for a specific store.
     *
     * <p>This is a defensive GC for rare cases where {@code onEntityRemove} is not called.
     * We only remove entries of kinds that were actually scanned during this sweep.</p>
     *
     * @param storeId        store identity hash used by {@link #entityKey(Ref)}
     * @param aliveKeys      keys observed during the scan
     * @param sweepKindsMask bitmask: {@link #KIND_PLAYER} and/or {@link #KIND_NPC}
     * @return number of removed baseline entries
     */
    public static int sweepOrphanedForStore(final int storeId,
                                           @Nonnull final LongKeySet aliveKeys,
                                           final int sweepKindsMask) {
        if (sweepKindsMask == 0) return 0;

        int removed = 0;
        for (Map.Entry<Long, BaselineEntry> e : BASELINES.entrySet()) {
            final long key = e.getKey();
            if (storeIdFromKey(key) != storeId) continue;

            final BaselineEntry entry = e.getValue();
            final int kindBit = (entry.kind == KIND_PLAYER) ? KIND_PLAYER : (entry.kind == KIND_NPC) ? KIND_NPC : 0;
            if (kindBit == 0) continue; // unknown kind -> do not sweep
            if ((sweepKindsMask & kindBit) == 0) continue;

            if (!aliveKeys.contains(key)) {
                if (BASELINES.remove(key, entry)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Minimal primitive long set used for GC sweeps.
     */
    public interface LongKeySet {
        boolean contains(long key);
    }

    /**
     * Internal baseline entry.
     */
    private static final class BaselineEntry {
        final int[] baselineIds;
        volatile byte kind;        BaselineEntry(@Nonnull final int[] baselineIds, final byte kind) {
            this.baselineIds = baselineIds;
            this.kind = kind;        }    }
}
