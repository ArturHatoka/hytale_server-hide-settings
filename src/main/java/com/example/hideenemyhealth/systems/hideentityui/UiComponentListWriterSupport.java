package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Writing helpers for {@link UIComponentList}.
 */
final class UiComponentListWriterSupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean WARNED_DIRECT_WRITE_COPY_FAILED = new AtomicBoolean(false);

    private UiComponentListWriterSupport() {
    }

    /**
     * Writes an updated {@link UIComponentList} back to the ECS store.
     *
     * <p>If we are inside an ECS chunk callback, we must use the provided {@link CommandBuffer}.</p>
     *
 * <p>For explicit refresh passes we may optionally force a client-side UI rebuild by doing
 * a component-level recreate (remove + add/put). This is used only for <b>hot hide</b>
 * scenarios (removing IDs) to make changes visible without client relog.</p>
     */
    static void putComponent(@Nonnull final Ref<EntityStore> entityRef,
                             @Nonnull final Store<EntityStore> store,
                             @Nullable final CommandBuffer<EntityStore> buffer,
                             @Nonnull final UIComponentList list,
                             final boolean forceClientRebuild) {
        if (buffer != null) {
            if (forceClientRebuild && tryRecreate(buffer, entityRef, list)) {
                return;
            }
            buffer.putComponent(entityRef, UIComponentList.getComponentType(), list);
            return;
        }

        // Outside ECS iteration we can only do a direct put; client-side UI update may be limited.
        store.putComponent(entityRef, UIComponentList.getComponentType(), list);
    }

    /**
     * Prepares a {@link UIComponentList} instance that is safe to write back to the store.
     *
     * <p>When we refresh from commands / UI (outside ECS iteration), we write via
     * {@link Store#putComponent}. Some builds may use identity-based change detection for direct
     * store writes. To maximize compatibility, we attempt to clone/copy the component so the store
     * sees a distinct instance.</p>
     */
    @Nonnull
    static UIComponentList prepareUiListForWrite(@Nonnull final UIComponentList current,
                                                 @Nullable final CommandBuffer<EntityStore> buffer) {
        // Inside ECS iteration, CommandBuffer writes are tracked; we can safely reuse the instance.
        if (buffer != null) return current;

        final @Nullable UIComponentList cloned = tryCloneOrCopy(current);
        if (cloned != null) return cloned;

        if (WARNED_DIRECT_WRITE_COPY_FAILED.compareAndSet(false, true)) {
            LOGGER.at(Level.WARNING).log(
                    "%s Could not clone/copy UIComponentList for direct Store.putComponent writes. " +
                            "If your server build relies on identity-based change tracking, refreshing UI " +
                            "for already-spawned entities may not work.",
                    HideEnemyHealthPlugin.LOG_PREFIX
            );
        }
        return current;
    }

    /**
     * Best-effort deep-ish copy for {@link UIComponentList}.
     *
     * <p>We first try the ECS {@link Component#clone()} contract.
     * If that fails, we attempt reflective {@code clone()} and then a no-arg ctor + shallow field copy.</p>
     */
    @Nullable
    private static UIComponentList tryCloneOrCopy(@Nonnull final UIComponentList current) {
        // 1) ECS contract: components are cloneable (preferred).
        try {
            if (current instanceof Component<?>) {
                final Object cloned = ((Component<?>) current).clone();
                if (cloned instanceof UIComponentList ui && ui != current) {
                    return ui;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) Reflective clone() (covers cases where Component is shaded / classloader oddities).
        try {
            final Method m = current.getClass().getDeclaredMethod("clone");
            m.setAccessible(true);
            final Object cloned = m.invoke(current);
            if (cloned instanceof UIComponentList ui && ui != current) {
                return ui;
            }
        } catch (Throwable ignored) {
        }

        // 3) Last resort: no-arg ctor + shallow copy of non-static fields.
        try {
            final Constructor<?> ctor = current.getClass().getDeclaredConstructor();
            ctor.setAccessible(true);
            final Object created = ctor.newInstance();
            if (!(created instanceof UIComponentList ui)) return null;

            Class<?> c = current.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    f.set(ui, f.get(current));
                }
                c = c.getSuperclass();
            }
            return ui;
        } catch (Throwable ignored) {
        }

        return null;
    }

    // --- Component recreate support (best-effort, reflection based) ---

    private static volatile boolean RECREATE_LOOKED_UP = false;
    private static volatile Method REMOVE_COMPONENT;
    private static volatile Method ADD_COMPONENT;
    private static final AtomicBoolean WARNED_RECREATE_UNAVAILABLE = new AtomicBoolean(false);
    private static final AtomicBoolean WARNED_RECREATE_FAILED = new AtomicBoolean(false);

    /**
     * Best-effort recreate: remove UIComponentList, then add (or put) updated list.
     *
     * <p>We use reflection because CommandBuffer API availability differs between server builds.
     * If removeComponent/addComponent are not present, we fall back to a plain putComponent.</p>
     */
    private static boolean tryRecreate(@Nonnull final CommandBuffer<EntityStore> buffer,
                                       @Nonnull final Ref<EntityStore> ref,
                                       @Nonnull final UIComponentList list) {

        ensureRecreateMethods(buffer.getClass());

        final Method remove = REMOVE_COMPONENT;
        if (remove == null) {
            if (WARNED_RECREATE_UNAVAILABLE.compareAndSet(false, true)) {
                LOGGER.at(Level.INFO).log(
                        "%s CommandBuffer.removeComponent(...) not available; using putComponent only.",
                        HideEnemyHealthPlugin.LOG_PREFIX
                );
            }
            return false;
        }

        try {
            // Important: some builds expose addComponent(...) but do not preserve the provided component instance
            // (or may initialize it with defaults). To avoid losing unrelated UI entries, we always re-apply the
            // updated component via putComponent after the remove.
            remove.invoke(buffer, ref, UIComponentList.getComponentType());

            // Re-add via putComponent so the provided componentIds are preserved.
            buffer.putComponent(ref, UIComponentList.getComponentType(), list);
            return true;


        } catch (Throwable t) {
            // Any failure: fall back to normal put. Log once so we can diagnose API mismatches.
            if (WARNED_RECREATE_FAILED.compareAndSet(false, true)) {
                LOGGER.at(Level.INFO).withCause(t).log(
                        "%s UIComponentList recreate failed; falling back to putComponent only.",
                        HideEnemyHealthPlugin.LOG_PREFIX
                );
            }
            return false;
        }
    }

    private static void ensureRecreateMethods(@Nonnull final Class<?> bufferClass) {
        if (RECREATE_LOOKED_UP) return;

        synchronized (UiComponentListWriterSupport.class) {
            if (RECREATE_LOOKED_UP) return;

            final Object componentType = UIComponentList.getComponentType();
            final Class<?> componentTypeClass = componentType.getClass();

            Method bestRemove = null;
            int bestRemoveScore = -1;

            Method bestAdd = null;
            int bestAddScore = -1;

            // Scan both public and non-public methods across the class hierarchy.
            Class<?> c = bufferClass;
            while (c != null) {
                for (Method m : c.getDeclaredMethods()) {
                    final int score = scoreCandidate(m, componentTypeClass);
                    if (score > 0) {
                        if ("removeComponent".equals(m.getName()) && m.getParameterCount() == 2) {
                            if (score > bestRemoveScore) {
                                bestRemove = m;
                                bestRemoveScore = score;
                            }
                        } else if ("addComponent".equals(m.getName()) && m.getParameterCount() == 3) {
                            if (score > bestAddScore) {
                                bestAdd = m;
                                bestAddScore = score;
                            }
                        }
                    }
                }
                c = c.getSuperclass();
            }

            // Public methods too (covers interfaces / inherited public API).
            for (Method m : bufferClass.getMethods()) {
                final int score = scoreCandidate(m, componentTypeClass);
                if (score > 0) {
                    if ("removeComponent".equals(m.getName()) && m.getParameterCount() == 2) {
                        if (score > bestRemoveScore) {
                            bestRemove = m;
                            bestRemoveScore = score;
                        }
                    } else if ("addComponent".equals(m.getName()) && m.getParameterCount() == 3) {
                        if (score > bestAddScore) {
                            bestAdd = m;
                            bestAddScore = score;
                        }
                    }
                }
            }

            if (bestRemove != null) {
                try { bestRemove.setAccessible(true); } catch (Throwable ignored) {}
            }
            if (bestAdd != null) {
                try { bestAdd.setAccessible(true); } catch (Throwable ignored) {}
            }

            REMOVE_COMPONENT = bestRemove;
            ADD_COMPONENT = bestAdd;
            RECREATE_LOOKED_UP = true;
        }
    }

    /**
     * Scores a CommandBuffer method candidate for remove/add component operations.
     * We intentionally use a permissive type match (API varies between builds), but we still
     * require argument compatibility with (Ref, ComponentType, UIComponentList).
     */
    private static int scoreCandidate(@Nonnull final Method m, @Nonnull final Class<?> componentTypeClass) {
        final String name = m.getName();
        final int pc = m.getParameterCount();

        if (!("removeComponent".equals(name) && pc == 2) && !("addComponent".equals(name) && pc == 3)) {
            return -1;
        }

        final Class<?>[] pt = m.getParameterTypes();
        if (pt.length != pc) return -1;

        // Param 0 must accept Ref
        if (!pt[0].isAssignableFrom(Ref.class)) return -1;

        // Param 1 must accept UIComponentList.getComponentType()
        if (!pt[1].isAssignableFrom(componentTypeClass)) return -1;

        // For addComponent, param 2 must accept UIComponentList
        if (pc == 3 && !pt[2].isAssignableFrom(UIComponentList.class)) return -1;

        int score = 1;
        if (pt[0] == Ref.class) score += 2;
        if (pt[1].getName().toLowerCase().contains("componenttype")) score += 2;
        if (pc == 3 && pt[2] == UIComponentList.class) score += 2;
        if ((m.getModifiers() & Modifier.PUBLIC) != 0) score += 1;
        return score;
    }
}
