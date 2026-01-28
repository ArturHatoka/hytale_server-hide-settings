package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility layer for iterating NPC refs on different server builds.
 */
final class NpcRefAccess {

    private static final ConcurrentHashMap<Class<?>, Method> GET_NPC_REFS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GET_NPC_REFS_MISSING = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<?>, Method> GET_REFERENCE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GET_REFERENCE_MISSING = new ConcurrentHashMap<>();

    private NpcRefAccess() {
    }

    /**
     * Reflectively call {@code world.getNpcRefs()} if available.
     */
    @Nullable
    static Iterable<?> getNpcRefs(@Nonnull final World world) {
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
     */
    @Nullable
    static Ref<EntityStore> coerceToEntityRef(@Nonnull final Object npcRefLike) {
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

            missing.putIfAbsent(clazz, Boolean.TRUE);
            return null;

        } catch (Throwable t) {
            missing.putIfAbsent(clazz, Boolean.TRUE);
            return null;
        }
    }

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
}
