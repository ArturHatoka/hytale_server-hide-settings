package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Applies the Server Hide Settings configuration to an entity by rewriting its {@link UIComponentList}.
 *
 * <p>Implementation rules:
 * <ul>
 *   <li>We compute from the entity's <b>current</b> {@code componentIds} list and only ever remove IDs.</li>
 *   <li>Hot "unhide" (re-adding IDs) is intentionally not supported on all client builds without relog/re-stream.</li>
 *   <li>All operations are safe against invalid/stale refs (we check {@link Ref#isValid()}).</li>
 * </ul>
 * </p>
 */
public final class HideEntityUiApplier {

    private HideEntityUiApplier() {
    }

    /**
     * Apply current config to the given entity ref.
     *
     * <p>This variant is used from ECS callbacks (spawn/archetype changes). It performs the mutation (if needed)
     * and does not force a client-side UI rebuild.</p>
     *
     * @param entityRef entity reference (must be valid)
     * @param store     entity store
     * @param buffer    command buffer if called from ECS callback (preferred), otherwise null
     * @param forceNpc  optional hint: TRUE = treat as NPC, FALSE = treat as player, null = auto-detect
     */
    public static void applyForRef(@Nonnull final Ref<EntityStore> entityRef,
                                   @Nonnull final Store<EntityStore> store,
                                   @Nullable final CommandBuffer<EntityStore> buffer,
                                   @Nullable final Boolean forceNpc) {
        applyForRefAndReport(entityRef, store, buffer, forceNpc, false);
    }

    /**
     * Apply current config to the given entity ref and report whether a store update was performed.
     *
     * <p>This overload is kept for compatibility with older call sites. It does not force a client-side UI rebuild.</p>
     *
     * @return true if the plugin wrote a new {@link UIComponentList} to the store for this entity
     */
    public static boolean applyForRefAndReport(@Nonnull final Ref<EntityStore> entityRef,
                                               @Nonnull final Store<EntityStore> store,
                                               @Nullable final CommandBuffer<EntityStore> buffer,
                                               @Nullable final Boolean forceNpc) {
        return applyForRefAndReport(entityRef, store, buffer, forceNpc, false);
    }

    /**
     * Apply current config to the given entity ref and report whether a store update was performed.
     *
     * <p>When {@code forceClientRebuild} is true and a {@link CommandBuffer} is available, we attempt to
     * force a client-side UI rebuild by removing and re-adding the {@link UIComponentList} component.
     * This is used by explicit refresh passes to update already-spawned entities without requiring client relog.</p>
     *
     * @return true if the plugin wrote a new {@link UIComponentList} to the store for this entity
     */
    public static boolean applyForRefAndReport(@Nonnull final Ref<EntityStore> entityRef,
                                               @Nonnull final Store<EntityStore> store,
                                               @Nullable final CommandBuffer<EntityStore> buffer,
                                               @Nullable final Boolean forceNpc,
                                               final boolean forceClientRebuild) {

        if (!entityRef.isValid()) return false;

        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();

        final UIComponentList list = store.getComponent(entityRef, UIComponentList.getComponentType());
        if (list == null) return false;

        final int[] currentIds = UiComponentFieldAccessor.getComponentIds(list);
        if (currentIds == null) return false;

        // If disabled globally, do nothing.
        // We intentionally do not attempt to "unhide" for already-spawned entities.
        // Some client builds do not recreate overhead UI mid-session without re-stream/relog.
        if (!cfg.enabled) {
            return false;
        }

        final boolean isPlayer;
        final boolean isNpc;

        try {
            if (forceNpc != null) {
                isNpc = forceNpc;
                isPlayer = !forceNpc;
            } else {
                isPlayer = store.getComponent(entityRef, Player.getComponentType()) != null;
                isNpc = store.getComponent(entityRef, NPCEntity.getComponentType()) != null;
            }
        } catch (Throwable t) {
            // If the store throws for any reason, skip this entity.
            return false;
        }

        final HideEnemyHealthConfig.TargetSettings settings =
                isPlayer ? cfg.getPlayers() : (isNpc ? cfg.getNpcs() : null);

        if (settings == null) return false;

        final boolean hideNone = !settings.hideDamageNumbers && !settings.hideHealthBar;

        // If nothing should be hidden for this target, do nothing.
        // We intentionally avoid trying to re-add UI components mid-session (see comment above).
        if (hideNone) {
            return false;
        }

        if (!UiComponentCache.ensureCache()) return false;

        // Capture a baseline snapshot the first time we see this entity.
        // We use it defensively to preserve unrelated UI IDs when doing "hot hide" refreshes.
        final byte kind = isPlayer ? EntityUiBaselineCache.KIND_PLAYER
                : (isNpc ? EntityUiBaselineCache.KIND_NPC : EntityUiBaselineCache.KIND_UNKNOWN);
        final long key = EntityUiBaselineCache.entityKey(entityRef);
        final int[] baselineIds = EntityUiBaselineCache.putBaselineIfAbsent(key, currentIds, kind);

        // Compute from the CURRENT list so we never introduce new IDs by default.
        int[] desired = UiComponentListFilterSupport.computeDesiredIds(currentIds, settings);

        // If only one category is hidden, ensure the other category's IDs that were present in the baseline
        // remain present. Some builds omit certain UI IDs (e.g., combat text) until first use; without this,
        // toggling "Hide HP" may also effectively remove damage numbers.
        if (settings.hideHealthBar && !settings.hideDamageNumbers) {
            desired = UiComponentListFilterSupport.appendMissingFromBaseline(desired, baselineIds, UiComponentCache::isCombatTextId);
        } else if (settings.hideDamageNumbers && !settings.hideHealthBar) {
            desired = UiComponentListFilterSupport.appendMissingFromBaseline(desired, baselineIds, UiComponentCache::isHealthStatId);
        }

        if (Arrays.equals(currentIds, desired)) {
            return false;
        }

        try {
            final UIComponentList writable = UiComponentListWriterSupport.prepareUiListForWrite(list, buffer);
            UiComponentFieldAccessor.setComponentIds(writable, desired);

            // Force a client rebuild only for explicit refresh passes and only when we removed something.
            // We do not support hot "unhide".
            final boolean removedSomething = desired.length < currentIds.length;
            final boolean rebuild = forceClientRebuild && buffer != null && removedSomething;

            UiComponentListWriterSupport.putComponent(entityRef, store, buffer, writable, rebuild);
            return true;
        } catch (Throwable t) {
            // Failed to write (store may be in a transient state). Skip this entity.
            return false;
        }
    }
}
