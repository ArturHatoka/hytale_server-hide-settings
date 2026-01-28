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
            return UiComponentListBaselineSupport.restoreBaselineIfPresent(
                    key, currentIds, entityRef, store, buffer, list
            );
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
            return UiComponentListBaselineSupport.restoreBaselineIfPresent(
                    key, currentIds, entityRef, store, buffer, list
            );
        }

        // Baseline must reflect the entity's original UI list (before we modify it).
        // We cache it only if we actually need to hide something for this entity.
        final int[] existingBaseline = EntityUiBaselineCache.getBaseline(key);
        final boolean baselineWasMissing = (existingBaseline == null);
        final int[] baseline = (existingBaseline != null)
                ? existingBaseline
                : EntityUiBaselineCache.putBaselineIfAbsent(
                        key,
                        UiComponentListFilterSupport.buildBaselineFromCurrent(currentIds),
                        kind
                );

        if (!UiComponentCache.ensureCache()) return false;

        final int[] desired = UiComponentListFilterSupport.computeDesiredIds(baseline, settings);
        if (Arrays.equals(currentIds, desired)) {
            // If we created a baseline but ended up not changing anything, drop it to prevent cache growth.
            if (baselineWasMissing) {
                EntityUiBaselineCache.remove(key);
            }
            return false;
        }

        try {
            final UIComponentList writable = UiComponentListWriterSupport.prepareUiListForWrite(list, buffer);
            UiComponentFieldAccessor.setComponentIds(writable, desired);
            UiComponentListWriterSupport.putComponent(entityRef, store, buffer, writable);
            return true;
        } catch (Throwable t) {
            // Failed to write (store may be in a transient state). Keep baseline.
            return false;
        }
    }
}
