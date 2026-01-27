package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private static final ConcurrentHashMap<Long, int[]> BASELINE_COMPONENT_IDS = new ConcurrentHashMap<>();

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
     * Store baseline for a key if absent.
     *
     * @param key         entity key (see {@link #entityKey(Ref)})
     * @param baselineIds baseline IDs
     * @return stored baseline (existing or the provided one)
     */
    @Nonnull
    public static int[] putBaselineIfAbsent(final long key, @Nonnull final int[] baselineIds) {
        return BASELINE_COMPONENT_IDS.computeIfAbsent(key, k -> baselineIds.clone());
    }

    /**
     * @param key entity key
     * @return baseline IDs, or null if the entity was never touched
     */
    @Nullable
    public static int[] getBaseline(final long key) {
        return BASELINE_COMPONENT_IDS.get(key);
    }

    /**
     * Remove baseline entry for a ref.
     */
    public static void remove(@Nonnull final Ref<EntityStore> ref) {
        BASELINE_COMPONENT_IDS.remove(entityKey(ref));
    }

    /**
     * Clear all baseline entries (used on plugin shutdown / hot-reload).
     */
    public static void clearAll() {
        BASELINE_COMPONENT_IDS.clear();
    }
}
