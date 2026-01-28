package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * Pure helpers for computing the final {@code componentIds} list.
 *
 * <p>Important invariant: we only ever <b>remove</b> IDs from a baseline snapshot.
 * We never introduce IDs that were not present originally.</p>
 */
final class UiComponentListFilterSupport {

    private UiComponentListFilterSupport() {
    }

    /**
     * Compute final componentIds based on baseline (original list) and current hide settings.
     */
    @Nonnull
    static int[] computeDesiredIds(@Nonnull final int[] baselineIds,
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
    static int[] buildBaselineFromCurrent(@Nonnull final int[] currentIds) {
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
