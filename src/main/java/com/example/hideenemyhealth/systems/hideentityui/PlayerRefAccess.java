package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Best-effort extraction of ECS refs from public player references.
 */
final class PlayerRefAccess {

    private PlayerRefAccess() {
    }

    /**
     * Extract an ECS ref from {@link PlayerRef}, if API provides {@code getReference()}.
     */
    @Nullable
    static Ref<EntityStore> safeGetPlayerEntityRef(@Nonnull final PlayerRef playerRef) {
        try {
            @SuppressWarnings("unchecked")
            final Ref<EntityStore> ref = (Ref<EntityStore>) playerRef.getReference();
            return ref;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
