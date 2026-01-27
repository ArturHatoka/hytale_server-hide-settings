package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for the active {@link HideEnemyHealthConfig}.
 *
 * <p>ECS systems and UI code access config through this registry to avoid holding plugin references and to
 * allow hot-reload / config reload to update the active view immediately.</p>
 */
public final class HideEntityUiConfigRegistry {

    private static final AtomicReference<HideEnemyHealthConfig> CONFIG_REF =
            new AtomicReference<>(new HideEnemyHealthConfig());

    private HideEntityUiConfigRegistry() {
    }

    /**
     * Publish config to all systems.
     *
     * @param cfg new config (will be normalized)
     */
    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        cfg.normalize();
        CONFIG_REF.set(cfg);
    }

    /**
     * @return current active config (always non-null)
     */
    @Nonnull
    public static HideEnemyHealthConfig getConfig() {
        return CONFIG_REF.get();
    }
}
