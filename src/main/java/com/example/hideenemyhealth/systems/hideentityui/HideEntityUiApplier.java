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
import java.util.LinkedHashSet;

public final class HideEntityUiApplier {

    private HideEntityUiApplier() {
    }

    public static void applyForRef(@Nonnull final Ref<EntityStore> entityRef,
                                   @Nonnull final Store<EntityStore> store,
                                   @Nullable final CommandBuffer<EntityStore> buffer,
                                   @Nullable final Boolean forceNpc) {

        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();

        final UIComponentList list = store.getComponent(entityRef, UIComponentList.getComponentType());
        if (list == null) return;

        final int[] currentIds = UiComponentFieldAccessor.getComponentIds(list);
        if (currentIds == null) return;

        final long key = EntityUiBaselineCache.entityKey(entityRef);

        if (!cfg.enabled) {
            final int[] baseline = EntityUiBaselineCache.getBaseline(key);
            if (baseline != null && !Arrays.equals(currentIds, baseline)) {
                UiComponentFieldAccessor.setComponentIds(list, baseline.clone());
                putComponent(entityRef, store, buffer, list);
            }
            return;
        }

        if (!UiComponentCache.ensureCache()) return;

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
                isPlayer ? cfg.getPlayers() :
                        (isNpc ? cfg.getNpcs() : null);

        if (settings == null) return;

        final int[] baseline = EntityUiBaselineCache.getOrCreateBaseline(key, currentIds);

        final int[] desired = computeDesiredIds(baseline, settings);
        if (Arrays.equals(currentIds, desired)) return;

        UiComponentFieldAccessor.setComponentIds(list, desired);
        putComponent(entityRef, store, buffer, list);
    }

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

    private static int[] computeDesiredIds(@Nonnull final int[] baselineIds,
                                          @Nonnull final HideEnemyHealthConfig.TargetSettings settings) {

        final LinkedHashSet<Integer> out = new LinkedHashSet<>(baselineIds.length + 8);

        for (int id : baselineIds) {
            if (settings.hideDamageNumbers && UiComponentCache.isCombatTextId(id)) continue;
            if (settings.hideHealthBar && UiComponentCache.isHealthStatId(id)) continue;
            out.add(id);
        }

        final int[] arr = new int[out.size()];
        int i = 0;
        for (Integer id : out) arr[i++] = id;
        return arr;
    }
}
