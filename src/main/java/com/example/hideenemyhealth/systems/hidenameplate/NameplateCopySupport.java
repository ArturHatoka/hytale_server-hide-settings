package com.example.hideenemyhealth.systems.hidenameplate;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Best-effort cloning/copying utilities for {@link Nameplate}.
 */
public final class NameplateCopySupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean WARNED_COPY_FAILED = new AtomicBoolean(false);

    private NameplateCopySupport() {
    }

    /**
     * Best-effort copy of a {@link Nameplate} component.
     *
     * <p>We prefer the ECS {@link Component#clone()} contract, but fall back to reflection and shallow field copy.
     * Returning the original instance is allowed as a last resort.</p>
     */
    @Nonnull
    public static Nameplate copy(@Nonnull final Nameplate current) {
        // 1) ECS contract: components are cloneable (preferred).
        try {
            if (current instanceof Component<?>) {
                final Object cloned = ((Component<?>) current).clone();
                if (cloned instanceof Nameplate np && np != current) {
                    return np;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) Reflective clone()
        try {
            final Method m = current.getClass().getDeclaredMethod("clone");
            m.setAccessible(true);
            final Object cloned = m.invoke(current);
            if (cloned instanceof Nameplate np && np != current) {
                return np;
            }
        } catch (Throwable ignored) {
        }

        // 3) No-arg ctor + shallow field copy
        final @Nullable Nameplate copied = tryShallowCopy(current);
        if (copied != null) {
            return copied;
        }

        if (WARNED_COPY_FAILED.compareAndSet(false, true)) {
            LOGGER.at(Level.WARNING).log(
                    "[ServerHideSettings] Could not clone/copy Nameplate; restore may be limited on some builds."
            );
        }

        return current;
    }

    @Nullable
    private static Nameplate tryShallowCopy(@Nonnull final Nameplate current) {
        try {
            final Constructor<?> ctor = current.getClass().getDeclaredConstructor();
            ctor.setAccessible(true);
            final Object created = ctor.newInstance();
            if (!(created instanceof Nameplate np)) return null;

            Class<?> c = current.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    f.set(np, f.get(current));
                }
                c = c.getSuperclass();
            }

            return np;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
