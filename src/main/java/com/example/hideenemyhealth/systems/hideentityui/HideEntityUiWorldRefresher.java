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
import java.util.logging.Level;

/**
 * Refresh already-loaded entities (players + NPCs) across all loaded worlds.
 *
 * Notes:
 * - World operations should run in the world's execution context: world.execute(() -> { ... }).
 * - Entity Refs can become invalid at any time; always check ref != null && ref.isValid().
 */
public final class HideEntityUiWorldRefresher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Reflection caches: we keep them to tolerate minor API differences between server builds.
    private static final ConcurrentHashMap<Class<?>, Method> EXECUTE_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> EXECUTE_METHOD_MISSING = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, Method> GET_NPC_REFS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GET_NPC_REFS_MISSING = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, Method> GET_REFERENCE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GET_REFERENCE_MISSING = new ConcurrentHashMap<>();

    private HideEntityUiWorldRefresher() {
    }

    public static void refreshLoadedEntities() {
        try {
            final Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null || worlds.isEmpty()) return;

            for (World world : worlds.values()) {
                if (world == null) continue;
                runOnWorldThread(world, () -> refreshWorld(world));
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] refreshLoadedEntities failed");
        }
    }

    private static void refreshWorld(@Nonnull final World world) {
        refreshPlayers(world);
        refreshNpcs(world);
    }

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

    private static void runOnWorldThread(@Nonnull final World world, @Nonnull final Runnable task) {
        final Method execute = getCached(world.getClass(), "execute", new Class<?>[]{Runnable.class}, EXECUTE_METHOD_CACHE, EXECUTE_METHOD_MISSING);
        if (execute == null) {
            // Without world.execute(), we cannot safely touch stores/entities.
            return;
        }

        try {
            execute.invoke(world, task);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] World.execute failed (world=%s)", safeWorldName(world));
        }
    }

    @Nullable
    private static Method getCachedNoArg(@Nonnull final Class<?> clazz,
                                        @Nonnull final String name,
                                        @Nonnull final ConcurrentHashMap<Class<?>, Method> cache,
                                        @Nonnull final ConcurrentHashMap<Class<?>, Boolean> missing) {
        return getCached(clazz, name, new Class<?>[0], cache, missing);
    }

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
            final Method m = clazz.getMethod(name, params);
            cache.put(clazz, m);
            return m;
        } catch (NoSuchMethodException e) {
            // Log only once per class.
            if (missing.putIfAbsent(clazz, Boolean.TRUE) == null) {
                LOGGER.at(Level.FINE).log("[HideEnemyHealth] %s.%s(...) not available; skipping that refresh path.", clazz.getSimpleName(), name);
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

    private static String safeWorldName(@Nonnull final World world) {
        try {
            return world.getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
