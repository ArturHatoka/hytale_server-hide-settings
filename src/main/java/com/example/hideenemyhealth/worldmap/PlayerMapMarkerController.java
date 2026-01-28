package com.example.hideenemyhealth.worldmap;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerTracker;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Controls visibility of player markers (icons) on the world map by overriding the built-in
 * marker provider under the {@code "playerIcons"} key.
 *
 * <p>This integrates the "HidePlayersPlugin" idea into HideEnemyHealth: we register a no-op
 * marker provider to suppress player markers when enabled. When disabled, we restore the original
 * provider we observed for that world.</p>
 *
 * <p>Thread-safety: any interaction with {@link WorldMapManager} is executed on the world thread
 * via {@code world.execute(Runnable)} (resolved reflectively for compatibility).</p>
 */
public final class PlayerMapMarkerController {

    /** The built-in key used by the server for player marker provider. */
    public static final String PLAYER_ICONS_KEY = "playerIcons";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** No-op provider that suppresses markers by doing nothing in {@link #update(World, MapMarkerTracker, int, int, int)}. */
    private static final WorldMapManager.MarkerProvider NOOP_PROVIDER = new NoopPlayerIconsProvider();

    /**
     * World -> original provider mapping.
     *
     * <p>We intentionally use {@link WeakHashMap} so worlds can be GC'd if unloaded and not referenced elsewhere.</p>
     */
    private static final Map<World, WorldMapManager.MarkerProvider> ORIGINAL_PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Reflection caches to tolerate minor API changes between server builds. */
    private static final ConcurrentHashMap<Class<?>, Accessor> ACCESSOR_CACHE = new ConcurrentHashMap<>();

    /** Ensures we don't spam logs if reflection lookup fails. */
    private static final AtomicBoolean WARNED_NO_ACCESSOR = new AtomicBoolean(false);

    private PlayerMapMarkerController() {
    }

    /**
     * Register world hooks that apply map-marker settings for newly added worlds.
     *
     * <p>Call this once during plugin setup.</p>
     *
     * @param registry plugin event registry
     */
    public static void register(@Nonnull EventRegistry registry) {
        registry.registerGlobal(AddWorldEvent.class, event -> {
            try {
                applyToWorld(event.getWorld(), HideEnemyHealthPlugin.getInstance().getConfig());
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Apply current map-marker settings to all loaded worlds.
     *
     * @param cfg current config
     */
    public static void applyToAllLoadedWorlds(@Nonnull HideEnemyHealthConfig cfg) {
        final Map<String, World> worlds = Universe.get().getWorlds();
        if (worlds == null || worlds.isEmpty()) return;

        for (World world : worlds.values()) {
            if (world == null) continue;
            applyToWorld(world, cfg);
        }
    }

    /**
     * Best-effort restore original player icon provider for all currently loaded worlds.
     *
     * <p>Called on plugin shutdown to avoid leaving overridden providers across hot reloads.</p>
     */
    public static void restoreAllLoadedWorlds() {
        final Map<String, World> worlds = Universe.get().getWorlds();
        if (worlds == null || worlds.isEmpty()) return;

        for (World world : worlds.values()) {
            if (world == null) continue;
            runOnWorldThread(world, () -> restoreOnWorldThread(world));
        }
    }

    /**
     * Apply the configured state for a single world.
     *
     * @param world target world
     * @param cfg optional config (if null, uses current stored settings if any were previously captured)
     */
    private static void applyToWorld(@Nonnull final World world, @Nullable final HideEnemyHealthConfig cfg) {
        // We only need config on the world thread; capture booleans now to avoid races.
        final boolean hide =
                cfg != null
                        && cfg.enabled
                        && cfg.getMap() != null
                        && cfg.getMap().hidePlayerMarkers;

        runOnWorldThread(world, () -> applyOnWorldThread(world, hide));
    }

    /**
     * World-thread application: capture original provider and then override/restore based on flag.
     */
    private static void applyOnWorldThread(@Nonnull World world, boolean hide) {
        final WorldMapManager manager;
        try {
            manager = world.getWorldMapManager();
        } catch (Throwable t) {
            return;
        }
        if (manager == null) return;

        final Accessor accessor = resolveAccessor(manager);
        if (accessor == null) {
            if (WARNED_NO_ACCESSOR.compareAndSet(false, true)) {
                LOGGER.at(Level.WARNING).log("[ServerHideSettings] Cannot access WorldMapManager providers; map marker hiding disabled on this server build");
            }
            return;
        }

        // Capture original provider once per world.
        ORIGINAL_PROVIDERS.computeIfAbsent(world, w -> accessor.getProvider(manager, PLAYER_ICONS_KEY));

        if (hide) {
            accessor.setProvider(manager, PLAYER_ICONS_KEY, NOOP_PROVIDER);
        } else {
            restoreOnWorldThread(world);
        }
    }

    /**
     * Restore original provider for the given world (world thread).
     */
    private static void restoreOnWorldThread(@Nonnull World world) {
        final WorldMapManager manager;
        try {
            manager = world.getWorldMapManager();
        } catch (Throwable t) {
            return;
        }
        if (manager == null) return;

        final Accessor accessor = resolveAccessor(manager);
        if (accessor == null) return;

        final WorldMapManager.MarkerProvider original;
        synchronized (ORIGINAL_PROVIDERS) {
            original = ORIGINAL_PROVIDERS.get(world);
        }

        if (original != null) {
            accessor.setProvider(manager, PLAYER_ICONS_KEY, original);
        } else {
            accessor.removeProvider(manager, PLAYER_ICONS_KEY);
        }
    }

    /**
     * Resolve cached accessor for a given manager instance.
     */
    @Nullable
    private static Accessor resolveAccessor(@Nonnull WorldMapManager manager) {
        return ACCESSOR_CACHE.computeIfAbsent(manager.getClass(), Accessor::resolve);
    }

    /**
     * Schedule work on the world thread.
     *
     * <p>Some server builds expose {@code world.execute(Runnable)} as non-public; we resolve it reflectively.
     * If resolution fails, we run the task inline as a best-effort fallback (matching the behavior that made
     * earlier versions of this plugin work across builds).</p>
     */
    private static void runOnWorldThread(@Nonnull World world, @Nonnull Runnable task) {
        try {
            final Method exec = Accessor.resolveWorldExecute(world.getClass());
            if (exec != null) {
                exec.invoke(world, task);
                return;
            }
        } catch (Throwable ignored) {
        }

        // Fallback: execute inline (may be unsafe on some builds, but keeps compatibility where execute is absent).
        task.run();
    }

    /**
     * No-op provider that intentionally never emits player markers.
     */
    private static final class NoopPlayerIconsProvider implements WorldMapManager.MarkerProvider {

        @Override
        public void update(@Nonnull World world, @Nonnull MapMarkerTracker tracker, int chunkViewRadius, int playerChunkX, int playerChunkZ) {
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

    /**
     * Reflection-backed accessor for marker providers inside {@link WorldMapManager}.
     */
    private static final class Accessor {

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
         * Resolve how to get/set providers for a given WorldMapManager class.
         */
        @Nonnull
        private static Accessor resolve(@Nonnull Class<?> managerClass) {
            Method getter = findProviderGetter(managerClass);
            Method remover = findProviderRemover(managerClass);
            Field mapField = findProviderMapField(managerClass);

            if (getter == null && mapField == null) {
                // Cannot read original provider -> disable feature on this build.
                return new Accessor(null, remover, null);
            }
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
         *
         * <p>Prefer direct map mutation (stable replacement semantics). Fall back to {@code addMarkerProvider} if needed.</p>
         */
        public void setProvider(@Nonnull WorldMapManager manager, @Nonnull String key, @Nonnull WorldMapManager.MarkerProvider provider) {
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
                    // Heuristic: we prefer fields that look like "markerProviders" / "providers" etc.
                    final boolean looksRelevant = name.contains("provider") || name.contains("marker");
                    if (best == null || looksRelevant) {
                        best = f;
                        if (looksRelevant) {
                            // Keep searching this class in case there's an even better match, but "looksRelevant" is strong.
                        }
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

        @Nullable
        private static volatile Method WORLD_EXECUTE_METHOD;

        /**
         * Resolve {@code world.execute(Runnable)} for a given world class (cached).
         */
        @Nullable
        private static Method resolveWorldExecute(@Nonnull Class<?> worldClass) {
            Method m = WORLD_EXECUTE_METHOD;
            if (m != null) return m;

            // First try public method.
            try {
                m = worldClass.getMethod("execute", Runnable.class);
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                WORLD_EXECUTE_METHOD = m;
                return m;
            } catch (Throwable ignored) {
            }

            // Then declared method (non-public).
            for (Class<?> c = worldClass; c != null; c = c.getSuperclass()) {
                try {
                    m = c.getDeclaredMethod("execute", Runnable.class);
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    WORLD_EXECUTE_METHOD = m;
                    return m;
                } catch (Throwable ignored) {
                }
            }
            return null;
        }
    }
}
