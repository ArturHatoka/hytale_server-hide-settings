package com.example.hideenemyhealth.systems.hideentityui;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityUIType;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Caches entity UI component IDs for quick classification.
 *
 * <p>We scan {@link EntityUIComponent} assets and build two ID sets:
 * <ul>
 *   <li>{@code CombatText} components (damage/heal numbers)</li>
 *   <li>{@code EntityStat(Health)} components (HP bar)</li>
 * </ul>
 *
 * <p>Membership checks are done via boolean arrays to avoid boxing and Set.contains overhead.</p>
 */
public final class UiComponentCache {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Optional sets (handy for debugging / logging). Membership checks use boolean flags (no boxing).
    private static volatile Set<Integer> COMBAT_TEXT_IDS = Set.of();
    private static volatile Set<Integer> HEALTH_STAT_IDS = Set.of();

    private static volatile boolean[] COMBAT_TEXT_FLAGS = new boolean[0];
    private static volatile boolean[] HEALTH_STAT_FLAGS = new boolean[0];

    private static volatile boolean CACHE_READY = false;

    private UiComponentCache() {
    }

    /**
     * Drop cached data so it will be rebuilt on next {@link #ensureCache()}.
     */
    public static void resetCache() {
        CACHE_READY = false;
        COMBAT_TEXT_IDS = Set.of();
        HEALTH_STAT_IDS = Set.of();
        COMBAT_TEXT_FLAGS = new boolean[0];
        HEALTH_STAT_FLAGS = new boolean[0];
    }

    /**
     * Ensure the cache is built.
     *
     * @return true if cache is ready; false if assets could not be read
     */
    public static boolean ensureCache() {
        if (CACHE_READY) return true;

        try {
            // Ensure stat indices are initialized.
            DefaultEntityStatTypes.update();
            final int healthIndex = DefaultEntityStatTypes.getHealth();

            final IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap = EntityUIComponent.getAssetMap();
            if (assetMap == null) return false;

            final int nextIndex = assetMap.getNextIndex();
            final HashSet<Integer> combat = new HashSet<>();
            final HashSet<Integer> health = new HashSet<>();

            final boolean[] combatFlags = new boolean[Math.max(0, nextIndex)];
            final boolean[] healthFlags = new boolean[Math.max(0, nextIndex)];

            for (int i = 0; i < nextIndex; i++) {
                final Object raw = assetMap.getAsset(i);
                if (!(raw instanceof EntityUIComponent uiAsset)) continue;

                final com.hypixel.hytale.protocol.EntityUIComponent packet = uiAsset.toPacket();
                if (packet == null) continue;

                final EntityUIType type = packet.type;
                if (type == null) continue;

                if (type == EntityUIType.CombatText) {
                    combat.add(i);
                    combatFlags[i] = true;
                } else if (type == EntityUIType.EntityStat && packet.entityStatIndex == healthIndex) {
                    health.add(i);
                    healthFlags[i] = true;
                }
            }

            COMBAT_TEXT_IDS = Collections.unmodifiableSet(combat);
            HEALTH_STAT_IDS = Collections.unmodifiableSet(health);
            COMBAT_TEXT_FLAGS = combatFlags;
            HEALTH_STAT_FLAGS = healthFlags;
            CACHE_READY = true;

            LOGGER.at(Level.INFO).log(
                    "[ServerHideSettings] UI component cache ready (health=%d, combat=%d)",
                    health.size(), combat.size()
            );
            return true;

        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[ServerHideSettings] Failed to build UI component cache");
            return false;
        }
    }

    /**
     * Fast membership test: is the given UI component ID a CombatText component.
     */
    public static boolean isCombatTextId(final int id) {
        final boolean[] flags = COMBAT_TEXT_FLAGS;
        if (id < 0 || id >= flags.length) {
            // If assets expanded after initial cache build, mark stale and rebuild on next ensureCache().
            if (CACHE_READY) CACHE_READY = false;
            return false;
        }
        return flags[id];
    }

    /**
     * Fast membership test: is the given UI component ID a Health EntityStat component.
     */
    public static boolean isHealthStatId(final int id) {
        final boolean[] flags = HEALTH_STAT_FLAGS;
        if (id < 0 || id >= flags.length) {
            if (CACHE_READY) CACHE_READY = false;
            return false;
        }
        return flags[id];
    }

    /**
     * @return immutable set of CombatText IDs (debugging/diagnostics)
     */
    public static Set<Integer> getCombatTextIds() {
        return COMBAT_TEXT_IDS;
    }

    /**
     * @return immutable set of Health EntityStat IDs (debugging/diagnostics)
     */
    public static Set<Integer> getHealthStatIds() {
        return HEALTH_STAT_IDS;
    }
}
