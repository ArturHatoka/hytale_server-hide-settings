package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityUiBaselineCache {

    private static final ConcurrentHashMap<Long, int[]> BASELINE_COMPONENT_IDS = new ConcurrentHashMap<>();

    private EntityUiBaselineCache() {
    }

    public static long entityKey(@Nonnull final Ref<EntityStore> ref) {
        try {
            final String[] candidates = new String[]{"getId", "getEntityId", "getIndex", "getNetworkId"};
            for (String mName : candidates) {
                try {
                    final Method m = ref.getClass().getMethod(mName);
                    final Object v = m.invoke(ref);
                    if (v instanceof Number) {
                        return ((Number) v).longValue();
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return System.identityHashCode(ref);
    }

    public static int[] getOrCreateBaseline(final long key, @Nonnull final int[] currentIds) {
        return BASELINE_COMPONENT_IDS.computeIfAbsent(key, k -> currentIds.clone());
    }

    public static int[] getBaseline(final long key) {
        return BASELINE_COMPONENT_IDS.get(key);
    }

    public static void remove(@Nonnull final Ref<EntityStore> ref) {
        BASELINE_COMPONENT_IDS.remove(entityKey(ref));
    }
}
