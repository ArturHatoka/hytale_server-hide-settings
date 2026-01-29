package com.example.hideenemyhealth.systems;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.systems.hideentityui.EntityUiBaselineCache;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiApplier;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Applies ServerHideSettings UI filtering whenever {@link UIComponentList} is written to an entity.
 *
 * <p>This is intentionally implemented as a {@link RefChangeSystem} so we also react to
 * {@code putComponent}/ {@code replaceComponent} updates of {@link UIComponentList}.
 * That covers spawn pipelines where the vanilla server may overwrite UI components after an
 * entity first becomes visible to other systems.</p>
 */
public final class HideEntityUiComponentListChangeSystem extends RefChangeSystem<EntityStore, UIComponentList> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public ComponentType<EntityStore, UIComponentList> componentType() {
        return UIComponentList.getComponentType();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        // Listen for UIComponentList changes on any entity.
        return Query.and(UIComponentList.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull final Ref<EntityStore> ref,
                                 @Nonnull final UIComponentList uiComponentList,
                                 @Nonnull final Store<EntityStore> store,
                                 @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        // Spawn-time apply. We do NOT force a client rebuild here; the entity may not be streamed yet.
        apply(ref, store, commandBuffer);
    }

    @Override
    public void onComponentSet(@Nonnull final Ref<EntityStore> ref,
                               @Nullable final UIComponentList oldComponent,
                               @Nonnull final UIComponentList newComponent,
                               @Nonnull final Store<EntityStore> store,
                               @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        // Vanilla may overwrite UIComponentList during spawn or later updates.
        // Re-apply our filtering each time UIComponentList is set.
        apply(ref, store, commandBuffer);
    }

    @Override
    public void onComponentRemoved(@Nonnull final Ref<EntityStore> ref,
                                   @Nonnull final UIComponentList uiComponentList,
                                   @Nonnull final Store<EntityStore> store,
                                   @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        // Defensive cleanup (entity despawn / UI module removal).
        try {
            EntityUiBaselineCache.remove(ref);
        } catch (Throwable ignored) {
        }
    }

    private static void apply(@Nonnull final Ref<EntityStore> ref,
                              @Nonnull final Store<EntityStore> store,
                              @Nonnull final CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Auto-detect Player vs NPC target inside the applier.
            HideEntityUiApplier.applyForRefAndReport(ref, store, commandBuffer, null, false);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log(
                    "%s UIComponentList change apply failed", HideEnemyHealthPlugin.LOG_PREFIX
            );
        }
    }
}
