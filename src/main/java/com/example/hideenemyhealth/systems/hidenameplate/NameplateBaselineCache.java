package com.example.hideenemyhealth.systems.hidenameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores baseline {@link Nameplate} components for players we have modified.
 *
 * <p>Unlike the overhead UI feature, we must keep baselines even when the nameplate
 * component is removed (because restoring requires the original component).</p>
 */
public final class NameplateBaselineCache {

    private static final ConcurrentHashMap<Long, Entry> ENTRIES = new ConcurrentHashMap<>();

    private NameplateBaselineCache() {
    }

    /**
     * Build a stable key for a {@link Ref} within a store.
     *
     * <p>We use {@code (storeIdentityHash << 32) ^ ref.getIndex()}. Indexes are per-store; combining with store id
     * prevents collisions across worlds.</p>
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

    @Nonnull
    public static Entry getOrCreate(final long key) {
        return ENTRIES.computeIfAbsent(key, k -> new Entry());
    }

    @Nullable
    public static Entry get(final long key) {
        return ENTRIES.get(key);
    }

    public static void remove(final long key) {
        ENTRIES.remove(key);
    }

    public static void remove(@Nonnull final Ref<EntityStore> ref) {
        remove(entityKey(ref));
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    /**
     * Baseline entry.
     */
    public static final class Entry {
        /** Last known visible baseline nameplate (cloned/copy). */
        @Nullable
        public volatile Nameplate baseline;

        /** Whether the plugin has currently hidden the nameplate for this entity. */
        public volatile boolean hidden;

        private Entry() {
        }
    }
}
