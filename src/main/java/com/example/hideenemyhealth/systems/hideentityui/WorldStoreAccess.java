package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Best-effort access to a world's ECS {@link Store}.
 *
 * <p>This helper is shared across multiple systems (e.g. hideentityui, hidenameplate).
 * It must therefore be {@code public}.</p>
 */
public final class WorldStoreAccess {

    private WorldStoreAccess() {
    }

    /**
     * Try to obtain the ECS {@link Store} for a world.
     */
    @Nullable
    public static Store<EntityStore> tryGetWorldStore(@Nonnull final World world) {
        try {
            final EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) return null;
            return entityStore.getStore();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
