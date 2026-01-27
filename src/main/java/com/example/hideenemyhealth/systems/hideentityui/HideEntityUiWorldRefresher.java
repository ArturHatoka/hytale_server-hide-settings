package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Utilities to force-refresh already loaded entities (players / NPCs) across all loaded worlds.
 *
 * <p>Important thread-safety rule:
 * any direct access to {@link Ref#getStore()}, {@link Store} operations, or entity components must happen
 * in the world's execution context via {@code world.execute(Runnable)}.</p>
 */
public final class HideEntityUiWorldRefresher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Reflection caches: we keep them to tolerate minor API differences between server builds.
    private static final ConcurrentHashMap<Class<?>, Method> EXECUTE_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> EXECUTE_METHOD_MISSING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> EXECUTE_FALLBACK_WARNED = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, Method> GET_NPC_REFS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GET_NPC_REFS_MISSING = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, Method> GET_REFERENCE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GET_REFERENCE_MISSING = new ConcurrentHashMap<>();

    private HideEntityUiWorldRefresher() {
    }

    /**
     * Refresh players and NPCs in all currently loaded worlds.
     */
    public static void refreshLoadedEntities() {
        forEachWorldOnWorldThread(HideEntityUiWorldRefresher::refreshWorld);
    }

    /**
     * Refresh players only in all currently loaded worlds.
     */
    public static void refreshLoadedPlayers() {
        forEachWorldOnWorldThread(HideEntityUiWorldRefresher::refreshPlayers);
    }

    /**
     * Refresh NPCs only in all currently loaded worlds.
     */
    public static void refreshLoadedNpcs() {
        forEachWorldOnWorldThread(HideEntityUiWorldRefresher::refreshNpcs);
    }

    /**
     * Iterate all loaded worlds and run a task on each world thread.
     */
    private static void forEachWorldOnWorldThread(@Nonnull final Consumer<World> perWorldTask) {
        try {
            final Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null || worlds.isEmpty()) return;

            for (World world : worlds.values()) {
                if (world == null) continue;
                runOnWorldThread(world, () -> perWorldTask.accept(world));
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] World iteration failed");
        }
    }

    /**
     * Refresh both players and NPCs for a single world.
     */
    private static void refreshWorld(@Nonnull final World world) {
        refreshPlayers(world);
        refreshNpcs(world);
    }

    /**
     * Refresh all players currently present in a world.
     */
    private static void refreshPlayers(@Nonnull final World world) {
        try {
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                if (playerRef == null) continue;

                final Ref<EntityStore> ref = safeGetPlayerEntityRef(playerRef);
                if (ref == null || !ref.isValid()) continue;

                final Store<EntityStore> store = ref.getStore();
                if (store == null) continue;

                HideEntityUiApplier.applyForRef(ref, store, null, Boolean.FALSE);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] Failed to refresh players in world: %s", safeWorldName(world));
        }
    }

    /**
     * Refresh all NPC-like entities currently present in a world.
     *
     * <p>NPC access is done via reflection because server API may evolve.
     * If the method isn't available, we quietly skip NPC refresh.</p>
     */
    private static void refreshNpcs(@Nonnull final World world) {
        final Iterable<?> npcRefs = getNpcRefs(world);
        if (npcRefs == null) return;

        try {
            for (Object npcRefLike : npcRefs) {
                if (npcRefLike == null) continue;

                final Ref<EntityStore> ref = coerceToEntityRef(npcRefLike);
                if (ref == null || !ref.isValid()) continue;

                final Store<EntityStore> store = ref.getStore();
                if (store == null) continue;

                HideEntityUiApplier.applyForRef(ref, store, null, Boolean.TRUE);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[HideEnemyHealth] NPC refresh skipped due to API differences (world=%s)", safeWorldName(world));
        }
    }

    /**
     * Extract an ECS ref from {@link PlayerRef}, if API provides {@code getReference()}.
     */
    @Nullable
    private static Ref<EntityStore> safeGetPlayerEntityRef(@Nonnull final PlayerRef playerRef) {
        try {
            @SuppressWarnings("unchecked")
            final Ref<EntityStore> ref = (Ref<EntityStore>) playerRef.getReference();
            return ref;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Reflectively call {@code world.getNpcRefs()} if available.
     */
    @Nullable
    private static Iterable<?> getNpcRefs(@Nonnull final World world) {
        final Method m = getCachedNoArg(world.getClass(), "getNpcRefs", GET_NPC_REFS_CACHE, GET_NPC_REFS_MISSING);
        if (m == null) return null;

        try {
            final Object res = m.invoke(world);
            return (res instanceof Iterable<?> it) ? it : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Convert a "NPCRef"-like object into an ECS {@link Ref}.
     *
     * <p>Some server builds may directly return {@code Iterable<Ref<EntityStore>>};
     * others may return wrapper objects that expose {@code getReference()}.</p>
     */
    @Nullable
    private static Ref<EntityStore> coerceToEntityRef(@Nonnull final Object npcRefLike) {
        if (npcRefLike instanceof Ref<?> refObj) {
            @SuppressWarnings("unchecked")
            final Ref<EntityStore> cast = (Ref<EntityStore>) refObj;
            return cast;
        }

        final Method getReference = getCachedNoArg(npcRefLike.getClass(), "getReference", GET_REFERENCE_CACHE, GET_REFERENCE_MISSING);
        if (getReference == null) return null;

        try {
            final Object refObj = getReference.invoke(npcRefLike);
            if (refObj instanceof Ref<?> r2) {
                @SuppressWarnings("unchecked")
                final Ref<EntityStore> cast2 = (Ref<EntityStore>) r2;
                return cast2;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Run a task via {@code world.execute(Runnable)}.
     *
     * <p>If {@code execute} is not available, we fall back to running synchronously. This keeps the plugin
     * functional on server builds where world execution scheduling is not exposed publicly, at the cost of
     * potentially violating thread-affinity expectations.</p>
     */
    private static void runOnWorldThread(@Nonnull final World world, @Nonnull final Runnable task) {
        final Class<?> worldClass = world.getClass();
        final Method execute = getCached(worldClass, "execute", new Class<?>[]{Runnable.class},
                EXECUTE_METHOD_CACHE, EXECUTE_METHOD_MISSING);

        if (execute != null) {
            try {
                execute.invoke(world, task);
                return;
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[HideEnemyHealth] World.execute failed (world=%s)", safeWorldName(world));
            }
        }

        // Compatibility fallback:
        // Some server builds expose world execution via non-public methods or a different scheduler.
        // If we cannot schedule safely, we still run synchronously to preserve functionality.
        // This mirrors the previous behavior that the plugin relied on.
        if (EXECUTE_FALLBACK_WARNED.putIfAbsent(worldClass, Boolean.TRUE) == null) {
            LOGGER.at(Level.WARNING).log(
                    "[HideEnemyHealth] World.execute(Runnable) not accessible on %s; running refresh task directly (may be unsafe)",
                    worldClass.getName()
            );
        }

        try {
            task.run();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] Fallback refresh execution failed (world=%s)", safeWorldName(world));
        }
    }

    /**
     * Lookup and cache a no-arg method.
     */
    @Nullable
    private static Method getCachedNoArg(@Nonnull final Class<?> clazz,
                                        @Nonnull final String name,
                                        @Nonnull final ConcurrentHashMap<Class<?>, Method> cache,
                                        @Nonnull final ConcurrentHashMap<Class<?>, Boolean> missing) {
        return getCached(clazz, name, new Class<?>[0], cache, missing);
    }

    /**
     * Lookup and cache a method by name and signature.
     */
    @Nullable
    private static Method getCached(@Nonnull final Class<?> clazz,
                                    @Nonnull final String name,
                                    @Nonnull final Class<?>[] params,
                                    @Nonnull final ConcurrentHashMap<Class<?>, Method> cache,
                                    @Nonnull final ConcurrentHashMap<Class<?>, Boolean> missing) {

        final Method cached = cache.get(clazz);
        if (cached != null) return cached;
        if (Boolean.TRUE.equals(missing.get(clazz))) return null;

        try {
            // 1) Public method (includes inherited)
            final Method m = clazz.getMethod(name, params);
            cache.put(clazz, m);
            return m;
        } catch (NoSuchMethodException ignored) {
            // 2) Non-public method somewhere in class hierarchy
            final Method declared = findDeclaredMethod(clazz, name, params);
            if (declared != null) {
                try {
                    declared.setAccessible(true);
                } catch (Throwable ignored2) {
                    // ignore
                }
                cache.put(clazz, declared);
                return declared;
            }

            // Log only once per class.
            if (missing.putIfAbsent(clazz, Boolean.TRUE) == null) {
                LOGGER.at(Level.FINE).log(
                        "[HideEnemyHealth] %s.%s(...) not available; skipping that refresh path.",
                        clazz.getSimpleName(), name
                );
            }
            return null;

        } catch (Throwable t) {
            if (missing.putIfAbsent(clazz, Boolean.TRUE) == null) {
                LOGGER.at(Level.FINE).withCause(t)
                        .log("[HideEnemyHealth] Failed to reflect %s.%s(...)", clazz.getSimpleName(), name);
            }
            return null;
        }
    }

    /**
     * Find a declared (possibly non-public) method in a class hierarchy.
     */
    @Nullable
    private static Method findDeclaredMethod(@Nonnull final Class<?> start,
                                             @Nonnull final String name,
                                             @Nonnull final Class<?>[] params) {
        Class<?> c = start;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /**
     * Best-effort world name for logging.
     */
    @Nonnull
    private static String safeWorldName(@Nonnull final World world) {
        try {
            return world.getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
