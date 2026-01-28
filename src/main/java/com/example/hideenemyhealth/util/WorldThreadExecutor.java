package com.example.hideenemyhealth.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Best-effort world-thread scheduling via {@code world.execute(Runnable)}.
 */
public final class WorldThreadExecutor {

    private static final ConcurrentHashMap<Class<?>, Method> EXECUTE_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> EXECUTE_METHOD_MISSING = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> EXECUTE_FALLBACK_WARNED = new ConcurrentHashMap<>();

    private WorldThreadExecutor() {
    }

    /**
     * Run a task via {@code world.execute(Runnable)}.
     *
     * <p>If {@code execute} is not available, falls back to running synchronously. If {@code logger} is provided,
     * the fallback is logged once per world class.</p>
     */
    public static void runStrict(@Nonnull final World world,
                                 @Nonnull final Runnable task,
                                 @Nonnull final HytaleLogger logger) {
        if (tryInvokeExecute(world, task, logger)) {
            return;
        }

        if (EXECUTE_FALLBACK_WARNED.putIfAbsent(world.getClass(), Boolean.TRUE) == null) {
            logger.at(Level.WARNING).log(
                    "[ServerHideSettings] World.execute(Runnable) not accessible on %s; running refresh task directly (may be unsafe)",
                    world.getClass().getName()
            );
        }
        try {
            task.run();
        } catch (Throwable t) {
            logger.at(Level.WARNING).withCause(t)
                    .log("[ServerHideSettings] Fallback refresh execution failed (world=%s)", safeWorldName(world));
        }
    }

    /**
     * Run a task on the world thread if possible; otherwise run synchronously without logging.
     */
    public static void runQuiet(@Nonnull final World world, @Nonnull final Runnable task) {
        if (tryInvokeExecute(world, task, null)) {
            return;
        }
        task.run();
    }

    /**
     * Best-effort world name for logging.
     */
    @Nonnull
    public static String safeWorldName(@Nonnull final World world) {
        try {
            return world.getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static boolean tryInvokeExecute(@Nonnull final World world,
                                            @Nonnull final Runnable task,
                                            @Nullable final HytaleLogger logger) {
        final Method execute = getCached(world.getClass(), "execute", new Class<?>[]{Runnable.class});
        if (execute == null) {
            return false;
        }
        try {
            execute.invoke(world, task);
            return true;
        } catch (Throwable t) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(t)
                        .log("[ServerHideSettings] World.execute failed (world=%s)", safeWorldName(world));
            }
            return false;
        }
    }

    @Nullable
    private static Method getCached(@Nonnull final Class<?> clazz,
                                    @Nonnull final String name,
                                    @Nonnull final Class<?>[] params) {
        final Method cached = EXECUTE_METHOD_CACHE.get(clazz);
        if (cached != null) return cached;
        if (Boolean.TRUE.equals(EXECUTE_METHOD_MISSING.get(clazz))) return null;

        try {
            final Method m = clazz.getMethod(name, params);
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            EXECUTE_METHOD_CACHE.put(clazz, m);
            return m;
        } catch (NoSuchMethodException ignored) {
            // Try declared methods in hierarchy.
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                try {
                    final Method m = c.getDeclaredMethod(name, params);
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored2) {
                    }
                    EXECUTE_METHOD_CACHE.put(clazz, m);
                    return m;
                } catch (NoSuchMethodException ignored2) {
                    // keep searching
                } catch (Throwable t) {
                    break;
                }
            }
            EXECUTE_METHOD_MISSING.putIfAbsent(clazz, Boolean.TRUE);
            return null;
        } catch (Throwable t) {
            EXECUTE_METHOD_MISSING.putIfAbsent(clazz, Boolean.TRUE);
            return null;
        }
    }
}
