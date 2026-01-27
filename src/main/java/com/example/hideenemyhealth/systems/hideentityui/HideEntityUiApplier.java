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
 * Applies the HideEnemyHealth configuration to an entity by rewriting its {@link UIComponentList}.
 *
 * <p>Implementation rules:
 * <ul>
 *   <li>We take a baseline snapshot the first time we touch an entity, and only ever remove IDs from that baseline.</li>
 *   <li>"Unhide" restores the exact baseline (no extra UI components are introduced).</li>
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
     * @param entityRef entity reference (must be valid)
     * @param store     entity store
     * @param buffer    command buffer if called from ECS callback (preferred), otherwise null
     * @param forceNpc  optional hint: TRUE = treat as NPC, FALSE = treat as player, null = auto-detect
     */
    public static void applyForRef(@Nonnull final Ref<EntityStore> entityRef,
                                   @Nonnull final Store<EntityStore> store,
                                   @Nullable final CommandBuffer<EntityStore> buffer,
                                   @Nullable final Boolean forceNpc) {

        if (!entityRef.isValid()) return;

        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();

        final UIComponentList list = store.getComponent(entityRef, UIComponentList.getComponentType());
        if (list == null) return;

        final int[] currentIds = UiComponentFieldAccessor.getComponentIds(list);
        if (currentIds == null) return;

        final long key = EntityUiBaselineCache.entityKey(entityRef);

        // If disabled globally -> restore baseline (if any) and exit.
        if (!cfg.enabled) {
            final int[] baseline = EntityUiBaselineCache.getBaseline(key);
            if (baseline != null && !Arrays.equals(currentIds, baseline)) {
                UiComponentFieldAccessor.setComponentIds(list, baseline.clone());
                putComponent(entityRef, store, buffer, list);
            }
            return;
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
            return;
        }

        final HideEnemyHealthConfig.TargetSettings settings =
                isPlayer ? cfg.getPlayers() : (isNpc ? cfg.getNpcs() : null);

        if (settings == null) return;

        final boolean hideNone = !settings.hideDamageNumbers && !settings.hideHealthBar;

        // If nothing should be hidden, we only restore if we have a baseline and the entity was modified before.
        // This avoids creating baseline entries for entities when the feature is effectively off for them.
        if (hideNone) {
            final int[] baseline = EntityUiBaselineCache.getBaseline(key);
            if (baseline != null && !Arrays.equals(currentIds, baseline)) {
                UiComponentFieldAccessor.setComponentIds(list, baseline.clone());
                putComponent(entityRef, store, buffer, list);
            }
            return;
        }

        // Baseline must reflect the entity's original UI list (before we modify it).
        final int[] baseline = getOrBuildBaseline(key, currentIds);

        if (!UiComponentCache.ensureCache()) return;

        final int[] desired = computeDesiredIds(baseline, settings);
        if (Arrays.equals(currentIds, desired)) return;

        UiComponentFieldAccessor.setComponentIds(list, desired);
        putComponent(entityRef, store, buffer, list);
    }

    /**
     * Write the updated UIComponentList back to the ECS store.
     *
     * <p>If we're inside an ECS callback, we must use {@link CommandBuffer}.</p>
     */
    private static void putComponent(@Nonnull final Ref<EntityStore> entityRef,
                                     @Nonnull final Store<EntityStore> store,
                                     @Nullable final CommandBuffer<EntityStore> buffer,
                                     @Nonnull final UIComponentList list) {
        if (buffer != null) {
            buffer.putComponent(entityRef, UIComponentList.getComponentType(), list);
        } else {
            store.putComponent(entityRef, UIComponentList.getComponentType(), list);
        }
    }

    /**
     * Compute final componentIds based on baseline (original list) and current hide settings.
     *
     * <p>Important: We only <b>remove</b> IDs from the baseline. We never add IDs that were not present
     * originally, so "unhide" restores exactly what the entity had before the plugin touched it.</p>
     */
    @Nonnull
    private static int[] computeDesiredIds(@Nonnull final int[] baselineIds,
                                          @Nonnull final HideEnemyHealthConfig.TargetSettings settings) {

        final boolean hideCombat = settings.hideDamageNumbers;
        final boolean hideHealth = settings.hideHealthBar;

        final int[] out = new int[baselineIds.length];
        int count = 0;

        for (int id : baselineIds) {
            if (hideCombat && UiComponentCache.isCombatTextId(id)) continue;
            if (hideHealth && UiComponentCache.isHealthStatId(id)) continue;
            out[count++] = id;
        }

        // Always return a new array (do not leak baseline array into entity component).
        return (count == out.length) ? baselineIds.clone() : Arrays.copyOf(out, count);
    }

    /**
     * Get existing baseline if present; otherwise build and store a baseline snapshot.
     */
    @Nonnull
    private static int[] getOrBuildBaseline(final long key, @Nonnull final int[] currentIds) {
        final int[] existing = EntityUiBaselineCache.getBaseline(key);
        if (existing != null) {
            return existing;
        }
        final int[] baseline = buildBaselineFromCurrent(currentIds);
        return EntityUiBaselineCache.putBaselineIfAbsent(key, baseline);
    }

    /**
     * Baseline must be a pure snapshot of the current list (deduped, preserving order).
     */
    @Nonnull
    private static int[] buildBaselineFromCurrent(@Nonnull final int[] currentIds) {
        if (currentIds.length <= 1) return currentIds.clone();

        final int[] out = new int[currentIds.length];
        int count = 0;

        outer:
        for (int id : currentIds) {
            for (int i = 0; i < count; i++) {
                if (out[i] == id) continue outer;
            }
            out[count++] = id;
        }

        return (count == out.length) ? currentIds.clone() : Arrays.copyOf(out, count);
    }
}
