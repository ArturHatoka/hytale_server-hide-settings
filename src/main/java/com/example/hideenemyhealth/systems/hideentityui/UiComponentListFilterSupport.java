package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Pure helpers for computing the final {@code componentIds} list.
 *
 * <p>Important invariant: we only ever <b>remove</b> IDs from the provided source list.
 * We never introduce new IDs.</p>
 */
final class UiComponentListFilterSupport {

    private UiComponentListFilterSupport() {
    }

    /**
     * Compute final componentIds based on the current entity list and current hide settings.
     *
     * <p>We intentionally compute from the <b>current</b> list instead of a baseline snapshot.
     * Some UI IDs (e.g., combat text) may be introduced later in an entity's lifetime.
     * Using a baseline would unintentionally drop such IDs even when they should remain visible.
     * This keeps "hide HP" and "hide damage" independent.</p>
     */
    @Nonnull
    static int[] computeDesiredIds(@Nonnull final int[] currentIds,
                                   @Nonnull final HideEnemyHealthConfig.TargetSettings settings) {

        final boolean hideCombat = settings.hideDamageNumbers;
        final boolean hideHealth = settings.hideHealthBar;

        final int[] out = new int[currentIds.length];
        int count = 0;

        for (int id : currentIds) {
            if (hideCombat && UiComponentCache.isCombatTextId(id)) continue;
            if (hideHealth && UiComponentCache.isHealthStatId(id)) continue;
            out[count++] = id;
        }

        // Always return a new array (do not leak the source array into the entity component).
        return (count == out.length) ? currentIds.clone() : Arrays.copyOf(out, count);
    }

    
    /**
     * Append missing IDs from a baseline list, but only those matching {@code selector}.
     *
     * <p>This is used to keep "hide HP" and "hide damage" independent when the current entity list
     * temporarily omits one category (some builds only attach CombatText UI after first hit).
     * We never append IDs that were not present in the entity's baseline snapshot.</p>
     *
     * @param desired    current desired list (usually derived from currentIds by removing hidden IDs)
     * @param baseline   baseline IDs captured before the plugin first modified the entity
     * @param selector   selects which baseline IDs are eligible to be appended
     * @return desired list with missing selected baseline IDs appended (order preserved for appended items)
     */
    @Nonnull
    static int[] appendMissingFromBaseline(@Nonnull final int[] desired,
                                          @Nonnull final int[] baseline,
                                          @Nonnull final IntPredicate selector) {
        if (baseline.length == 0) return desired;

        int extra = 0;
        for (int id : baseline) {
            if (!selector.test(id)) continue;
            boolean present = false;
            for (int d : desired) {
                if (d == id) { present = true; break; }
            }
            if (!present) extra++;
        }

        if (extra == 0) return desired;

        final int[] out = Arrays.copyOf(desired, desired.length + extra);
        int w = desired.length;

        for (int id : baseline) {
            if (!selector.test(id)) continue;
            boolean present = false;
            for (int d : desired) {
                if (d == id) { present = true; break; }
            }
            if (!present) out[w++] = id;
        }

        return out;
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
