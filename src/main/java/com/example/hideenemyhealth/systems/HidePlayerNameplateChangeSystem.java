package com.example.hideenemyhealth.systems;

import com.example.hideenemyhealth.systems.hidenameplate.HidePlayerNameplateApplier;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reacts to {@link Nameplate} writes so the hide-nameplates feature stays applied even if the server or
 * other plugins set/replace player nameplates after spawn.
 */
public final class HidePlayerNameplateChangeSystem extends RefChangeSystem<EntityStore, Nameplate> {

    @Nonnull
    @Override
    public ComponentType<EntityStore, Nameplate> componentType() {
        return Nameplate.getComponentType();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Nameplate.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull final Ref<EntityStore> ref,
                                 @Nonnull final Nameplate nameplate,
                                 @Nonnull final Store<EntityStore> store,
                                 @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        applyIfPlayer(ref, store, commandBuffer);
    }

    @Override
    public void onComponentSet(@Nonnull final Ref<EntityStore> ref,
                               @Nullable final Nameplate oldComponent,
                               @Nonnull final Nameplate newComponent,
                               @Nonnull final Store<EntityStore> store,
                               @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        applyIfPlayer(ref, store, commandBuffer);
    }

    @Override
    public void onComponentRemoved(@Nonnull final Ref<EntityStore> ref,
                                   @Nonnull final Nameplate nameplate,
                                   @Nonnull final Store<EntityStore> store,
                                   @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        // Do NOT clear baselines here: removal can be caused by us when hiding.
    }

    private static void applyIfPlayer(@Nonnull final Ref<EntityStore> ref,
                                      @Nonnull final Store<EntityStore> store,
                                      @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        try {
            if (store.getComponent(ref, Player.getComponentType()) == null) return;
        } catch (Throwable ignored) {
            return;
        }
        HidePlayerNameplateApplier.applyForRefAndReport(ref, store, commandBuffer, false);
    }
}
