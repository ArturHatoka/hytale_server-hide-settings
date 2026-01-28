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
     * Write the updated UIComponentList back to the ECS store.
     *
     * <p>If we're inside an ECS callback, we must use {@link CommandBuffer}.</p>
     */
    static void putComponent(@Nonnull final Ref<EntityStore> entityRef,
                             @Nonnull final Store<EntityStore> store,
                             @Nullable final CommandBuffer<EntityStore> buffer,
                             @Nonnull final UIComponentList list) {
        if (buffer != null) {
            buffer.putComponent(entityRef, UIComponentList.getComponentType(), list);
        } else {
            store.putComponent(entityRef, UIComponentList.getComponentType(), list);
        }
    }

    /**
     * Prepare a {@link UIComponentList} instance that is safe to write back to the store.
     *
     * <p>When we refresh from commands / UI (outside ECS iteration), we write directly via
     * {@link Store#putComponent}. On some builds this can be sensitive to writing back the <b>same</b> component
     * instance we read (especially if the store uses identity-based change detection).
     *
     * <p>To keep refresh working for already-spawned entities, we attempt to clone/copy the component so the
     * store sees a distinct instance.
     */
    @Nonnull
    static UIComponentList prepareUiListForWrite(@Nonnull final UIComponentList current,
                                                 @Nullable final CommandBuffer<EntityStore> buffer) {
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
     * <p>We first try the standard ECS {@link Component#clone()} contract.
     * If that fails, we attempt reflective no-arg construction and shallow field copy.</p>
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
}
