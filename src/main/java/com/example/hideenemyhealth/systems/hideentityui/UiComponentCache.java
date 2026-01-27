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

public final class UiComponentCache {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile Set<Integer> COMBAT_TEXT_IDS = Set.of();
    private static volatile Set<Integer> HEALTH_STAT_IDS = Set.of();
    private static volatile boolean CACHE_READY = false;

    private UiComponentCache() {
    }

    public static boolean ensureCache() {
        if (CACHE_READY) return true;

        try {
            DefaultEntityStatTypes.update();
            final int healthIndex = DefaultEntityStatTypes.getHealth();

            final IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap = EntityUIComponent.getAssetMap();
            if (assetMap == null) return false;

            final int nextIndex = assetMap.getNextIndex();
            final HashSet<Integer> combat = new HashSet<>();
            final HashSet<Integer> health = new HashSet<>();

            for (int i = 0; i < nextIndex; i++) {
                final Object raw = assetMap.getAsset(i);
                if (!(raw instanceof EntityUIComponent uiAsset)) continue;

                final com.hypixel.hytale.protocol.EntityUIComponent packet = uiAsset.toPacket();
                if (packet == null) continue;

                final EntityUIType type = packet.type;
                if (type == null) continue;

                if (type == EntityUIType.CombatText) {
                    combat.add(i);
                } else if (type == EntityUIType.EntityStat && packet.entityStatIndex == healthIndex) {
                    health.add(i);
                }
            }

            COMBAT_TEXT_IDS = Collections.unmodifiableSet(combat);
            HEALTH_STAT_IDS = Collections.unmodifiableSet(health);
            CACHE_READY = true;

            LOGGER.at(Level.INFO).log(
                    "[HideEnemyHealth] UI component cache ready (health=%d, combat=%d)",
                    health.size(), combat.size()
            );
            return true;

        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to build UI component cache");
            return false;
        }
    }

    public static boolean isCombatTextId(final int id) {
        return COMBAT_TEXT_IDS.contains(id);
    }

    public static boolean isHealthStatId(final int id) {
        return HEALTH_STAT_IDS.contains(id);
    }
}
