package com.example.hideenemyhealth.systems;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.hideentityui.EntityUiBaselineCache;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiApplier;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiConfigRegistry;
import com.example.hideenemyhealth.systems.hideentityui.HideEntityUiWorldRefresher;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS system that reacts to entities gaining {@link UIComponentList} and rewrites its internal component ID list
 * to hide selected overhead UI elements.
 *
 * <p>Performance note: prefer using the {@link Target#PLAYER} and {@link Target#NPC} variants so the ECS query
 * pre-filters entities and avoids per-entity component checks.</p>
 */
public final class HideEntityUiSystem extends RefSystem<EntityStore> {

    /**
     * Defines which category of entities this system instance targets.
     *
     * <ul>
     *   <li>{@link #PLAYER}: entities that have both {@link UIComponentList} and {@link Player}</li>
     *   <li>{@link #NPC}: entities that have both {@link UIComponentList} and {@link NPCEntity}</li>
     *   <li>{@link #ALL}: any entity with {@link UIComponentList} (legacy / fallback)</li>
     * </ul>
     */
    public enum Target {
        ALL,
        PLAYER,
        NPC
    }

    private final Target target;
    private final Query<EntityStore> query;

    /**
     * Legacy constructor that targets all entities with {@link UIComponentList}.
     *
     * <p>Prefer {@link #HideEntityUiSystem(Target)} with {@link Target#PLAYER}/{@link Target#NPC}.</p>
     */
    public HideEntityUiSystem() {
        this(Target.ALL);
    }

    /**
     * Creates a system instance with a query tailored to the given target.
     *
     * @param target entity category that should be matched by {@link #getQuery()}.
     */
    public HideEntityUiSystem(@Nonnull final Target target) {
        this.target = target;
        this.query = buildQuery(target);
    }

    /**
     * Returns the target category for this system instance.
     */
    @Nonnull
    public Target getTarget() {
        return target;
    }

    /**
     * ECS query used by the scheduler to select matching entities.
     */
    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    /**
     * Update active config (shared across all system instances).
     */
    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        HideEntityUiConfigRegistry.setConfig(cfg);
    }

    /**
     * Read current active config.
     */
    @Nonnull
    public static HideEnemyHealthConfig getConfig() {
        return HideEntityUiConfigRegistry.getConfig();
    }

    /**
     * Force-update already loaded entities (players + NPCs if API provides refs).
     */
    public static void refreshLoadedEntities() {
        HideEntityUiWorldRefresher.refreshLoadedEntities();
    }

    /**
     * Force-update already loaded players (does not touch NPCs).
     */
    public static void refreshLoadedPlayers() {
        HideEntityUiWorldRefresher.refreshLoadedPlayers();
    }

    /**
     * Force-update already loaded NPCs (does not touch players).
     */
    public static void refreshLoadedNpcs() {
        HideEntityUiWorldRefresher.refreshLoadedNpcs();
    }

    /**
     * Called by ECS when an entity matching {@link #getQuery()} appears or changes archetype.
     */
    @Override
    public void onEntityAdded(@Nonnull final Ref<EntityStore> entityRef,
                              @Nonnull final AddReason addReason,
                              @Nonnull final Store<EntityStore> store,
                              @Nonnull final CommandBuffer<EntityStore> buffer) {

        final @Nullable Boolean forceNpc = switch (target) {
            case PLAYER -> Boolean.FALSE;
            case NPC -> Boolean.TRUE;
            case ALL -> null;
        };

        HideEntityUiApplier.applyForRef(entityRef, store, buffer, forceNpc);
    }

    /**
     * Called by ECS when the entity stops matching the query or is removed.
     * We use this to drop baseline data and prevent cache growth.
     */
    @Override
    public void onEntityRemove(@Nonnull final Ref<EntityStore> ref,
                               @Nonnull final RemoveReason reason,
                               @Nonnull final Store<EntityStore> store,
                               @Nonnull final CommandBuffer<EntityStore> buffer) {
        EntityUiBaselineCache.remove(ref);
    }

    /**
     * Build a query appropriate for a target category.
     */
    @Nonnull
    private static Query<EntityStore> buildQuery(@Nonnull final Target target) {
        return switch (target) {
            case PLAYER -> Query.and(UIComponentList.getComponentType(), Player.getComponentType());
            case NPC -> Query.and(UIComponentList.getComponentType(), NPCEntity.getComponentType());
            case ALL -> Query.and(UIComponentList.getComponentType());
        };
    }
}
