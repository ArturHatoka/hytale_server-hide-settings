package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Baseline snapshot + restoration helpers.
 */
final class UiComponentListBaselineSupport {

    private UiComponentListBaselineSupport() {
    }

    /**
     * Restore an entity's UI list from baseline (if present) and then drop the cached baseline.
     *
     * <p>We remove the baseline entry after restoring to avoid unbounded cache growth.
     * If the entity gets modified again later, a fresh baseline snapshot can be rebuilt from its current state.</p>
     *
     * @return true if we wrote a restored list to the store
     */
    static boolean restoreBaselineIfPresent(final long key,
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
            final UIComponentList writable = UiComponentListWriterSupport.prepareUiListForWrite(list, buffer);
            UiComponentFieldAccessor.setComponentIds(writable, baseline.clone());
            UiComponentListWriterSupport.putComponent(entityRef, store, buffer, writable);
            EntityUiBaselineCache.remove(key);
            return true;
        } catch (Throwable ignored) {
            // Keep baseline in cache if restore failed.
            return false;
        }
    }
}
