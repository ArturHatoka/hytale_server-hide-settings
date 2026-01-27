package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityUiBaselineCache {

    private static final ConcurrentHashMap<Long, int[]> BASELINE_COMPONENT_IDS = new ConcurrentHashMap<>();

    private EntityUiBaselineCache() {
    }

    /**
     * Build a stable key for a Ref within a Store.
     *
     * We use (store identity hash << 32) ^ ref.getIndex(). Indexes are per-store; combining with store id
     * prevents collisions across worlds.
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

    public static int[] putBaselineIfAbsent(final long key, @Nonnull final int[] baselineIds) {
        return BASELINE_COMPONENT_IDS.computeIfAbsent(key, k -> baselineIds.clone());
    }

    public static int[] getBaseline(final long key) {
        return BASELINE_COMPONENT_IDS.get(key);
    }

    public static void remove(@Nonnull final Ref<EntityStore> ref) {
        BASELINE_COMPONENT_IDS.remove(entityKey(ref));
    }

    public static void clearAll() {
        BASELINE_COMPONENT_IDS.clear();
    }
}
