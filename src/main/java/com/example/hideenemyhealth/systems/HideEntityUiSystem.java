package com.example.hideenemyhealth.systems;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.hideentityui.EntityUiBaselineCache;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiApplier;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiConfigRegistry;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiWorldRefresher;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Hides floating HP bars & damage numbers by manipulating entity UIComponentList on the server.
 *
 * Why this works:
 *  - Client renders overhead UI based on UIComponentList componentIds (EntityUI components).
 *  - We remove (or add back) IDs corresponding to:
 *      * EntityStat(Health)  -> HP bar
 *      * CombatText          -> damage/heal numbers
 *
 * No client mod required.
 */
public final class HideEntityUiSystem extends RefSystem<EntityStore> {

    private final Query<EntityStore> query;

    public HideEntityUiSystem() {
        this.query = Query.and(UIComponentList.getComponentType());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    /** Hot-swap config for all system instances. */
    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        HideEntityUiConfigRegistry.setConfig(cfg);
    }

    @Nonnull
    public static HideEnemyHealthConfig getConfig() {
        return HideEntityUiConfigRegistry.getConfig();
    }

    /** Force-update already loaded entities (players + NPCs if API provides refs). */
    public static void refreshLoadedEntities() {
        HideEntityUiWorldRefresher.refreshLoadedEntities();
    }

    @Override
    public void onEntityAdded(@Nonnull final Ref<EntityStore> entityRef,
                              @Nonnull final AddReason addReason,
                              @Nonnull final Store<EntityStore> store,
                              @Nonnull final CommandBuffer<EntityStore> buffer) {
        HideEntityUiApplier.applyForRef(entityRef, store, buffer, null);
    }

    @Override
    public void onEntityRemove(@Nonnull final Ref<EntityStore> ref,
                               @Nonnull final RemoveReason reason,
                               @Nonnull final Store<EntityStore> store,
                               @Nonnull final CommandBuffer<EntityStore> buffer) {
        EntityUiBaselineCache.remove(ref);
    }
}
