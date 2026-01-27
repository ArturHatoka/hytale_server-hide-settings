package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Reflection helper around {@link UIComponentList}.
 *
 * <p>At the time of writing, the component IDs are stored in a private field named {@code componentIds}.
 * If the server API changes, this accessor returns null and the plugin will fail closed (do nothing) instead
 * of crashing the server.</p>
 */
public final class UiComponentFieldAccessor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Field COMPONENT_IDS_FIELD;

    static {
        Field f = null;
        try {
            f = UIComponentList.class.getDeclaredField("componentIds");
            f.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] Failed to access UIComponentList.componentIds via reflection. Plugin will be limited.");
        }
        COMPONENT_IDS_FIELD = f;
    }

    private UiComponentFieldAccessor() {
    }

    /**
     * Read the raw {@code componentIds} array from {@link UIComponentList}.
     *
     * @param list UI component list
     * @return internal array instance, or null if reflection is unavailable
     */
    @Nullable
    public static int[] getComponentIds(@Nonnull final UIComponentList list) {
        try {
            if (COMPONENT_IDS_FIELD == null) return null;
            return (int[]) COMPONENT_IDS_FIELD.get(list);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Replace the raw {@code componentIds} array inside {@link UIComponentList}.
     *
     * @param list UI component list
     * @param ids  new IDs array
     */
    public static void setComponentIds(@Nonnull final UIComponentList list, @Nonnull final int[] ids) {
        try {
            if (COMPONENT_IDS_FIELD == null) return;
            COMPONENT_IDS_FIELD.set(list, ids);
        } catch (Throwable ignored) {
        }
    }
}
