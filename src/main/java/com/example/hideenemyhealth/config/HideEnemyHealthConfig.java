package com.example.hideenemyhealth.config;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nonnull;

/**
 * Runtime configuration for HideEnemyHealth.
 *
 * <p>Admins change these values via in-game UI; the plugin persists them to disk.</p>
 */
public class HideEnemyHealthConfig {

    /** Global on/off switch. */
    @SerializedName("enabled")
    public boolean enabled = true;

    /** Settings applied to player entities. */
    @SerializedName("players")
    public TargetSettings players = TargetSettings.defaultsForPlayers();

    /** Settings applied to NPC entities. */
    @SerializedName("npcs")
    public TargetSettings npcs = TargetSettings.defaultsForNpcs();

    /** World map / minimap related settings. */
    @SerializedName("map")
    public MapSettings map = new MapSettings();

    /** Optional debug / maintenance settings (disabled by default). */
    @SerializedName("debug")
    public DebugSettings debug = new DebugSettings();

    /**
     * @return non-null player settings (creates defaults if missing)
     */
    @Nonnull
    public TargetSettings getPlayers() {
        if (players == null) players = TargetSettings.defaultsForPlayers();
        return players;
    }

    /**
     * @return non-null NPC settings (creates defaults if missing)
     */
    @Nonnull
    public TargetSettings getNpcs() {
        if (npcs == null) npcs = TargetSettings.defaultsForNpcs();
        return npcs;
    }

    /**
     * @return non-null map settings (creates defaults if missing)
     */
    @Nonnull
    public MapSettings getMap() {
        if (map == null) map = new MapSettings();
        return map;
    }

    /**
     * Normalize config after load: ensure nested objects are never null.
     */
    public void normalize() {
        if (players == null) players = TargetSettings.defaultsForPlayers();
        if (npcs == null) npcs = TargetSettings.defaultsForNpcs();
        if (map == null) map = new MapSettings();

        if (debug == null) debug = new DebugSettings();
        debug.normalize();
    }

    /**
     * Debug / maintenance settings.
     *
     * <p>These options are intentionally grouped to avoid cluttering the main config.
     * They are safe to keep disabled in production.</p>
     */
    public static final class DebugSettings {

        /**
         * If true, logs how many entities were visited/changed during explicit refresh passes
         * (triggered by UI toggles or /hid reload).
         */
        @SerializedName("logRefreshStats")
        public boolean logRefreshStats = false;

        /**
         * Optional periodic baseline cache garbage-collection.
         *
         * <p>Baseline entries are normally dropped on {@code onEntityRemove}. Some server builds may
         * occasionally skip remove hooks in edge cases (world unload, desync). When enabled, the plugin
         * periodically scans currently loaded entities and drops baseline entries that refer to entities
         * that are no longer present.</p>
         */
        @SerializedName("baselineGc")
        public BaselineGcSettings baselineGc = new BaselineGcSettings();

        /** Ensure nested objects and bounds are valid. */
        public void normalize() {
            if (baselineGc == null) baselineGc = new BaselineGcSettings();
            baselineGc.normalize();
        }
    }

    /**
     * Settings for periodic baseline cache GC.
     */
    public static final class BaselineGcSettings {

        /** Enable periodic GC (off by default). */
        @SerializedName("enabled")
        public boolean enabled = false;

        /** How often to run the sweep (seconds). */
        @SerializedName("intervalSeconds")
        public int intervalSeconds = 300;

        /** Clamp interval to a safe minimum. */
        public void normalize() {
            if (intervalSeconds < 30) intervalSeconds = 30;
        }
    }

    /**
     * World map (minimap / map screen) related settings.
     */
    public static final class MapSettings {

        /**
         * If true, hides player markers (icons) on the world map by overriding the built-in
         * {@code "playerIcons"} marker provider.
         *
         * <p>This does not affect objective markers or other map layers.</p>
         */
        @SerializedName("hidePlayerMarkers")
        public boolean hidePlayerMarkers = false;
    }

    /**
     * Target-specific settings (players or NPCs).
     */
    public static final class TargetSettings {

        /**
         * If true, remove {@code EntityStat(Health)} UI component(s) from entity UI component list.
         * This hides the floating HP bar rendered above entities.
         */
        @SerializedName("hideHealthBar")
        public boolean hideHealthBar = true;

        /**
         * If true, remove {@code CombatText} UI component(s) (damage/heal numbers).
         */
        @SerializedName("hideDamageNumbers")
        public boolean hideDamageNumbers = false;

        /**
         * Default settings for players.
         */
        @Nonnull
        public static TargetSettings defaultsForPlayers() {
            TargetSettings s = new TargetSettings();
            s.hideHealthBar = true;          // hide HP bar by default
            s.hideDamageNumbers = false;     // keep damage numbers by default
            return s;
        }

        /**
         * Default settings for NPCs.
         */
        @Nonnull
        public static TargetSettings defaultsForNpcs() {
            TargetSettings s = new TargetSettings();
            s.hideHealthBar = true;
            s.hideDamageNumbers = false;
            return s;
        }
    }
}
