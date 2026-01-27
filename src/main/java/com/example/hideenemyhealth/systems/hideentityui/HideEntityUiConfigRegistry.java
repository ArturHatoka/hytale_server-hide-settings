package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public final class HideEntityUiConfigRegistry {

    private static final AtomicReference<HideEnemyHealthConfig> CONFIG_REF =
            new AtomicReference<>(new HideEnemyHealthConfig());

    private HideEntityUiConfigRegistry() {
    }

    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        cfg.normalize();
        CONFIG_REF.set(cfg);
    }

    @Nonnull
    public static HideEnemyHealthConfig getConfig() {
        return CONFIG_REF.get();
    }
}
