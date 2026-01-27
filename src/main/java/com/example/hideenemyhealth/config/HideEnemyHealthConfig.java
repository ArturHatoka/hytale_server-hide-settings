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
     * Normalize config after load: ensure nested objects are never null.
     */
    public void normalize() {
        if (players == null) players = TargetSettings.defaultsForPlayers();
        if (npcs == null) npcs = TargetSettings.defaultsForNpcs();
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
