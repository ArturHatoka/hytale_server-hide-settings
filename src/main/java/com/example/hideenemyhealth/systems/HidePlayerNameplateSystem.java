package com.example.hideenemyhealth.systems;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.hidenameplate.HideNameplateConfigRegistry;
import com.example.hideenemyhealth.systems.hidenameplate.HideNameplateWorldRefresher;
import com.example.hideenemyhealth.systems.hidenameplate.HidePlayerNameplateApplier;
import com.example.hideenemyhealth.systems.hidenameplate.NameplateBaselineCache;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * ECS system that applies the "Players: Hide nameplates" setting.
 */
public final class HidePlayerNameplateSystem extends RefSystem<EntityStore> {

    /** Publish config reference for the nameplate feature. */
    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        HideNameplateConfigRegistry.setConfig(cfg);
    }

    /** Apply current config across already-loaded players. */
    public static void refreshLoadedPlayers() {
        HideNameplateWorldRefresher.refreshLoadedPlayers();
    }

    /** Force restore of baselines across already-loaded players (shutdown safety). */
    public static void forceRestoreLoadedPlayers() {
        HideNameplateWorldRefresher.forceRestoreLoadedPlayers();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Track player entities for lifecycle (spawn/despawn). We do NOT include Nameplate in the query,
        // because we may remove it and we must keep baseline state to support restore.
        return Query.and(Player.getComponentType());
    }

    @Override
    public void onEntityAdded(@Nonnull final Ref<EntityStore> entityRef,
                              @Nonnull final AddReason addReason,
                              @Nonnull final Store<EntityStore> store,
                              @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        // Apply to new players as they appear. Nameplate may be attached later; a separate change system handles that.
        HidePlayerNameplateApplier.applyForRefAndReport(entityRef, store, commandBuffer, false);
    }

    @Override
    public void onEntityRemove(@Nonnull final Ref<EntityStore> entityRef,
                               @Nonnull final RemoveReason reason,
                               @Nonnull final Store<EntityStore> store,
                               @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        // Entity despawn: cleanup baseline entry.
        try {
            NameplateBaselineCache.remove(entityRef);
        } catch (Throwable ignored) {
        }
    }
}
