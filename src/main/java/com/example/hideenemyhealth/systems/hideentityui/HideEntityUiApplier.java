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
     * <p>This variant is used from ECS callbacks. It performs the mutation (if needed) and ignores
     * the "changed" signal.</p>
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
        applyForRefAndReport(entityRef, store, buffer, forceNpc);
    }

    /**
     * Apply current config to the given entity ref and report whether a store update was performed.
     *
     * <p>This is used by explicit refresh passes so we can optionally log how many entities were
     * actually changed.</p>
     *
     * @return true if the plugin wrote a new {@link UIComponentList} to the store for this entity
     */
    public static boolean applyForRefAndReport(@Nonnull final Ref<EntityStore> entityRef,
                                               @Nonnull final Store<EntityStore> store,
                                               @Nullable final CommandBuffer<EntityStore> buffer,
                                               @Nullable final Boolean forceNpc) {

        if (!entityRef.isValid()) return false;

        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();

        final UIComponentList list = store.getComponent(entityRef, UIComponentList.getComponentType());
        if (list == null) return false;

        final int[] currentIds = UiComponentFieldAccessor.getComponentIds(list);
        if (currentIds == null) return false;

        final long key = EntityUiBaselineCache.entityKey(entityRef);

        // If disabled globally -> restore baseline (if any) and drop cached baseline to avoid growth.
        if (!cfg.enabled) {
            return restoreBaselineIfPresent(key, currentIds, entityRef, store, buffer, list);
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

        final byte kind = isPlayer
                ? EntityUiBaselineCache.KIND_PLAYER
                : (isNpc ? EntityUiBaselineCache.KIND_NPC : EntityUiBaselineCache.KIND_UNKNOWN);

        final HideEnemyHealthConfig.TargetSettings settings =
                isPlayer ? cfg.getPlayers() : (isNpc ? cfg.getNpcs() : null);

        if (settings == null) return false;

        final boolean hideNone = !settings.hideDamageNumbers && !settings.hideHealthBar;

        // If nothing should be hidden, we only restore if we have a baseline and the entity was modified before.
        // This avoids creating baseline entries for entities when the feature is effectively off for them.
        if (hideNone) {
            return restoreBaselineIfPresent(key, currentIds, entityRef, store, buffer, list);
        }

        // Baseline must reflect the entity's original UI list (before we modify it).
        // We cache it only if we actually need to hide something for this entity.
        final int[] existingBaseline = EntityUiBaselineCache.getBaseline(key);
        final boolean baselineWasMissing = (existingBaseline == null);
        final int[] baseline = (existingBaseline != null)
                ? existingBaseline
                : EntityUiBaselineCache.putBaselineIfAbsent(key, buildBaselineFromCurrent(currentIds), kind);

        if (!UiComponentCache.ensureCache()) return false;

        final int[] desired = computeDesiredIds(baseline, settings);
        if (Arrays.equals(currentIds, desired)) {
            // If we created a baseline but ended up not changing anything, drop it to prevent cache growth.
            if (baselineWasMissing) {
                EntityUiBaselineCache.remove(key);
            }
            return false;
        }

        try {
            UiComponentFieldAccessor.setComponentIds(list, desired);
            putComponent(entityRef, store, buffer, list);
            return true;
        } catch (Throwable t) {
            // Failed to write (store may be in a transient state). Keep baseline.
            return false;
        }
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
     * Restore an entity's UI list from baseline (if present) and then drop the cached baseline.
     *
     * <p>This is used when:
     * <ul>
     *   <li>the plugin is disabled globally</li>
     *   <li>the relevant target settings specify "hide nothing"</li>
     * </ul>
     *
     * <p>We remove the baseline entry after restoring to avoid unbounded cache growth.
     * If the entity gets modified again later, a fresh baseline snapshot can be rebuilt from its current state.</p>
     *
     * @param key        entity key (see {@link EntityUiBaselineCache#entityKey(Ref)})
     * @param currentIds current UI component IDs currently present on the entity
     * @param entityRef  entity reference
     * @param store      entity store
     * @param buffer     optional command buffer (ECS callback)
     * @param list       UI component list component
     * @return true if we wrote a restored list to the store
     */
    private static boolean restoreBaselineIfPresent(final long key,
                                                   @Nonnull final int[] currentIds,
                                                   @Nonnull final Ref<EntityStore> entityRef,
                                                   @Nonnull final Store<EntityStore> store,
                                                   @Nullable final CommandBuffer<EntityStore> buffer,
                                                   @Nonnull final UIComponentList list) {
        final int[] baseline = EntityUiBaselineCache.getBaseline(key);
        if (baseline == null) return false;

        final boolean alreadyRestored = Arrays.equals(currentIds, baseline);
        if (alreadyRestored) {
            EntityUiBaselineCache.remove(key);
            return false;
        }

        try {
            UiComponentFieldAccessor.setComponentIds(list, baseline.clone());
            putComponent(entityRef, store, buffer, list);
            EntityUiBaselineCache.remove(key);
            return true;
        } catch (Throwable ignored) {
            // Keep baseline in cache if restore failed.
            return false;
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
