package com.example.hideenemyhealth.config;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nonnull;

/**
 * Runtime configuration for HideEnemyHealth.
 *
 * Admins change these values via in-game UI.
 * The plugin persists them automatically to disk.
 */
public class HideEnemyHealthConfig {

    /** Global on/off switch. */
    @SerializedName("enabled")
    public boolean enabled = true;

    /** Settings applied to Player entities. */
    @SerializedName("players")
    public TargetSettings players = TargetSettings.defaultsForPlayers();

    /** Settings applied to NPC entities. */
    @SerializedName("npcs")
    public TargetSettings npcs = TargetSettings.defaultsForNpcs();

    @Nonnull
    public TargetSettings getPlayers() {
        if (players == null) players = TargetSettings.defaultsForPlayers();
        return players;
    }

    @Nonnull
    public TargetSettings getNpcs() {
        if (npcs == null) npcs = TargetSettings.defaultsForNpcs();
        return npcs;
    }

    public void normalize() {
        if (players == null) players = TargetSettings.defaultsForPlayers();
        if (npcs == null) npcs = TargetSettings.defaultsForNpcs();
    }

    public static final class TargetSettings {

        /**
         * If true - removes "EntityStat(Health)" UI component(s) from entity UI component list.
         * This hides the floating HP bar rendered above entities.
         */
        @SerializedName("hideHealthBar")
        public boolean hideHealthBar = true;

        /**
         * If true - removes CombatText UI component(s) (damage/heal numbers).
         */
        @SerializedName("hideDamageNumbers")
        public boolean hideDamageNumbers = false;

        @Nonnull
        public static TargetSettings defaultsForPlayers() {
            TargetSettings s = new TargetSettings();
            s.hideHealthBar = true;          // requested feature
            s.hideDamageNumbers = false;     // keep damage numbers by default
            return s;
        }

        @Nonnull
        public static TargetSettings defaultsForNpcs() {
            TargetSettings s = new TargetSettings();
            s.hideHealthBar = true;
            s.hideDamageNumbers = false;
            return s;
        }
    }
}
