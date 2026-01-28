package com.example.hideenemyhealth.worldmap;

import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed accessor for marker providers inside {@link WorldMapManager}.
 */
final class WorldMapProviderAccessor {

    private static final ConcurrentHashMap<Class<?>, Accessor> ACCESSOR_CACHE = new ConcurrentHashMap<>();

    private WorldMapProviderAccessor() {
    }

    @Nonnull
    static Accessor resolve(@Nonnull WorldMapManager manager) {
        return ACCESSOR_CACHE.computeIfAbsent(manager.getClass(), Accessor::resolve);
    }

    /**
     * Per-class resolved access strategy.
     */
    static final class Accessor {

        @Nullable
        private final Method getProviderMethod;

        @Nullable
        private final Method removeProviderMethod;

        @Nullable
        private final Field providerMapField;

        private Accessor(@Nullable Method getProviderMethod, @Nullable Method removeProviderMethod, @Nullable Field providerMapField) {
            this.getProviderMethod = getProviderMethod;
            this.removeProviderMethod = removeProviderMethod;
            this.providerMapField = providerMapField;
        }

        /**
         * Resolve how to get/set providers for a given WorldMapManager class.
         */
        @Nonnull
        private static Accessor resolve(@Nonnull Class<?> managerClass) {
            Method getter = findProviderGetter(managerClass);
            Method remover = findProviderRemover(managerClass);
            Field mapField = findProviderMapField(managerClass);
            return new Accessor(getter, remover, mapField);
        }

        /**
         * Get current provider for a key (may return null).
         */
        @Nullable
        public WorldMapManager.MarkerProvider getProvider(@Nonnull WorldMapManager manager, @Nonnull String key) {
            try {
                if (getProviderMethod != null) {
                    Object r = getProviderMethod.invoke(manager, key);
                    return (r instanceof WorldMapManager.MarkerProvider p) ? p : null;
                }
                Map<Object, Object> map = tryGetProviderMap(manager);
                if (map != null) {
                    Object r = map.get(key);
                    return (r instanceof WorldMapManager.MarkerProvider p) ? p : null;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        /**
         * Set provider for a key.
         */
        public void setProvider(@Nonnull WorldMapManager manager,
                                @Nonnull String key,
                                @Nonnull WorldMapManager.MarkerProvider provider) {
            try {
                Map<Object, Object> map = tryGetProviderMap(manager);
                if (map != null) {
                    map.put(key, provider);
                    return;
                }
            } catch (Throwable ignored) {
            }

            try {
                // Public API (works on builds where addMarkerProvider replaces by key).
                manager.addMarkerProvider(key, provider);
            } catch (Throwable ignored) {
            }
        }

        /**
         * Remove provider for a key (best-effort).
         */
        public void removeProvider(@Nonnull WorldMapManager manager, @Nonnull String key) {
            try {
                Map<Object, Object> map = tryGetProviderMap(manager);
                if (map != null) {
                    map.remove(key);
                    return;
                }
            } catch (Throwable ignored) {
            }

            try {
                if (removeProviderMethod != null) {
                    removeProviderMethod.invoke(manager, key);
                }
            } catch (Throwable ignored) {
            }
        }

        /**
         * Read the internal providers map if it looks like {@code Map<String, MarkerProvider>}.
         */
        @Nullable
        @SuppressWarnings("unchecked")
        private Map<Object, Object> tryGetProviderMap(@Nonnull WorldMapManager manager) {
            if (providerMapField == null) return null;
            try {
                Object v = providerMapField.get(manager);
                if (!(v instanceof Map<?, ?> raw)) return null;

                // Quick plausibility check: if it's empty we can't validate; accept it.
                if (raw.isEmpty()) {
                    return (Map<Object, Object>) raw;
                }

                // Validate at least one entry: String key and MarkerProvider value (or null).
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    Object k = e.getKey();
                    Object val = e.getValue();
                    if (k instanceof String && (val == null || val instanceof WorldMapManager.MarkerProvider)) {
                        return (Map<Object, Object>) raw;
                    }
                    break;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        /**
         * Find a method like {@code getMarkerProvider(String)} returning MarkerProvider.
         */
        @Nullable
        private static Method findProviderGetter(@Nonnull Class<?> cls) {
            for (Method m : cls.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && WorldMapManager.MarkerProvider.class.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            }
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                            && WorldMapManager.MarkerProvider.class.isAssignableFrom(m.getReturnType())) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {
                        }
                        return m;
                    }
                }
            }
            return null;
        }

        /**
         * Find a remover method like {@code removeMarkerProvider(String)} (if present).
         */
        @Nullable
        private static Method findProviderRemover(@Nonnull Class<?> cls) {
            for (Method m : cls.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                        && m.getName().toLowerCase().contains("remove")) {
                    return m;
                }
            }
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
                            && m.getName().toLowerCase().contains("remove")) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {
                        }
                        return m;
                    }
                }
            }
            return null;
        }

        /**
         * Try to locate an internal providers map field inside WorldMapManager.
         */
        @Nullable
        private static Field findProviderMapField(@Nonnull Class<?> cls) {
            Field best = null;

            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(f.getType())) continue;

                    final String name = f.getName().toLowerCase();
                    final boolean looksRelevant = name.contains("provider") || name.contains("marker");
                    if (best == null || looksRelevant) {
                        best = f;
                    }
                }
                if (best != null) break;
            }

            if (best != null) {
                try {
                    best.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            return best;
        }
    }
}
