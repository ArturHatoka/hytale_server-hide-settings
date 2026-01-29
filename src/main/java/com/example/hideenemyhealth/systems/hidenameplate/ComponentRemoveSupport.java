package com.example.hideenemyhealth.systems.hidenameplate;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Best-effort reflective access to removeComponent(...) on CommandBuffer and Store.
 *
 * <p>The Hytale Server API has had minor signature variations between builds, so we use reflection
 * to avoid compile-time coupling.</p>
 */
public final class ComponentRemoveSupport {

    private ComponentRemoveSupport() {
    }

    // --- CommandBuffer.removeComponent ---

    private static volatile boolean BUFFER_LOOKED_UP = false;
    private static volatile Method BUFFER_REMOVE;
    private static final AtomicBoolean WARNED_BUFFER_UNAVAILABLE = new AtomicBoolean(false);

    /**
     * Try to remove a component using the provided ECS {@link CommandBuffer}.
     */
    public static boolean tryRemoveFromBuffer(@Nonnull final CommandBuffer<EntityStore> buffer,
                                              @Nonnull final Ref<EntityStore> ref,
                                              @Nonnull final Object componentType) {
        ensureBufferRemoveMethod(buffer.getClass(), componentType.getClass());
        final Method m = BUFFER_REMOVE;
        if (m == null) {
            WARNED_BUFFER_UNAVAILABLE.compareAndSet(false, true);
            return false;
        }
        try {
            m.invoke(buffer, ref, componentType);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureBufferRemoveMethod(@Nonnull final Class<?> bufferClass,
                                                 @Nonnull final Class<?> componentTypeClass) {
        if (BUFFER_LOOKED_UP) return;
        synchronized (ComponentRemoveSupport.class) {
            if (BUFFER_LOOKED_UP) return;
            BUFFER_REMOVE = findRemoveMethod(bufferClass, componentTypeClass);
            if (BUFFER_REMOVE != null) {
                try { BUFFER_REMOVE.setAccessible(true); } catch (Throwable ignored) {}
            }
            BUFFER_LOOKED_UP = true;
        }
    }

    // --- Store.removeComponent ---

    private static volatile boolean STORE_LOOKED_UP = false;
    private static volatile Method STORE_REMOVE;
    private static final AtomicBoolean WARNED_STORE_UNAVAILABLE = new AtomicBoolean(false);

    /**
     * Try to remove a component using a direct {@link Store} (outside ECS iteration).
     */
    public static boolean tryRemoveFromStore(@Nonnull final Store<EntityStore> store,
                                             @Nonnull final Ref<EntityStore> ref,
                                             @Nonnull final Object componentType) {
        ensureStoreRemoveMethod(store.getClass(), componentType.getClass());
        final Method m = STORE_REMOVE;
        if (m == null) {
            WARNED_STORE_UNAVAILABLE.compareAndSet(false, true);
            return false;
        }
        try {
            m.invoke(store, ref, componentType);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureStoreRemoveMethod(@Nonnull final Class<?> storeClass,
                                                @Nonnull final Class<?> componentTypeClass) {
        if (STORE_LOOKED_UP) return;
        synchronized (ComponentRemoveSupport.class) {
            if (STORE_LOOKED_UP) return;
            STORE_REMOVE = findRemoveMethod(storeClass, componentTypeClass);
            if (STORE_REMOVE != null) {
                try { STORE_REMOVE.setAccessible(true); } catch (Throwable ignored) {}
            }
            STORE_LOOKED_UP = true;
        }
    }

    // --- Shared method lookup ---

    @Nullable
    private static Method findRemoveMethod(@Nonnull final Class<?> hostClass,
                                           @Nonnull final Class<?> componentTypeClass) {
        Method best = null;
        int bestScore = -1;

        // Declared methods across hierarchy.
        Class<?> c = hostClass;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                final int score = scoreRemoveCandidate(m, componentTypeClass);
                if (score > bestScore) {
                    best = m;
                    bestScore = score;
                }
            }
            c = c.getSuperclass();
        }

        // Public methods too.
        for (Method m : hostClass.getMethods()) {
            final int score = scoreRemoveCandidate(m, componentTypeClass);
            if (score > bestScore) {
                best = m;
                bestScore = score;
            }
        }

        return bestScore > 0 ? best : null;
    }

    private static int scoreRemoveCandidate(@Nonnull final Method m,
                                            @Nonnull final Class<?> componentTypeClass) {
        if (!"removeComponent".equals(m.getName())) return -1;
        if (m.getParameterCount() != 2) return -1;

        final Class<?>[] pt = m.getParameterTypes();
        if (pt.length != 2) return -1;

        // Param 0 must accept Ref
        if (!pt[0].isAssignableFrom(Ref.class)) return -1;

        // Param 1 must accept the specific component type object
        if (!pt[1].isAssignableFrom(componentTypeClass)) return -1;

        int score = 1;
        if (pt[0] == Ref.class) score += 2;
        if (pt[1].getName().toLowerCase().contains("componenttype")) score += 2;
        if ((m.getModifiers() & Modifier.PUBLIC) != 0) score += 1;
        return score;
    }
}
