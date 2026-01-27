package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.logging.Level;

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

    @Nullable
    public static int[] getComponentIds(@Nonnull final UIComponentList list) {
        try {
            if (COMPONENT_IDS_FIELD == null) return null;
            return (int[]) COMPONENT_IDS_FIELD.get(list);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setComponentIds(@Nonnull final UIComponentList list, @Nonnull final int[] ids) {
        try {
            if (COMPONENT_IDS_FIELD == null) return;
            COMPONENT_IDS_FIELD.set(list, ids);
        } catch (Throwable ignored) {
        }
    }
}
