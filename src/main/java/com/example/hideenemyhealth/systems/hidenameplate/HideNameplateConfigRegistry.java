package com.example.hideenemyhealth.systems.hidenameplate;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;

import javax.annotation.Nonnull;

/**
 * Holds the active {@link HideEnemyHealthConfig} reference for the nameplate-hide feature.
 */
public final class HideNameplateConfigRegistry {

    private static volatile HideEnemyHealthConfig CONFIG;

    static {
        final HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
        cfg.normalize();
        CONFIG = cfg;
    }

    private HideNameplateConfigRegistry() {
    }

    /**
     * Publish the current runtime config.
     */
    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        CONFIG = cfg;
    }

    /**
     * @return currently active config (never null)
     */
    @Nonnull
    public static HideEnemyHealthConfig getConfig() {
        return CONFIG;
    }
}
