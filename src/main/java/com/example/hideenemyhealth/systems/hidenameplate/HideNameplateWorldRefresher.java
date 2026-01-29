package com.example.hideenemyhealth.systems.hidenameplate;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.util.WorldThreadExecutor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Applies nameplate hide/restore to currently loaded players across all worlds.
 */
public final class HideNameplateWorldRefresher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int APPLY_MASK = 1;
    private static final int RESTORE_MASK = 2;

    private static final AtomicInteger PENDING = new AtomicInteger(0);
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private HideNameplateWorldRefresher() {
    }

    /**
     * Apply current config (hide or restore) to all loaded players.
     */
    public static void refreshLoadedPlayers() {
        request(APPLY_MASK);
    }

    /**
     * Force restore baselines (used on plugin shutdown / hot-reload safety).
     */
    public static void forceRestoreLoadedPlayers() {
        request(RESTORE_MASK);
    }

    private static void request(final int mask) {
        PENDING.getAndUpdate(old -> old | mask);
        if (!RUNNING.compareAndSet(false, true)) return;

        try {
            while (true) {
                final int m = PENDING.getAndSet(0);
                if (m == 0) return;

                final boolean forceRestore = (m & RESTORE_MASK) != 0;
                refreshAllWorlds(forceRestore);
            }
        } finally {
            RUNNING.set(false);
            if (PENDING.get() != 0) {
                // Another request came in while we were finishing.
                if (RUNNING.compareAndSet(false, true)) {
                    try {
                        final int m = PENDING.getAndSet(0);
                        if (m != 0) {
                            refreshAllWorlds((m & RESTORE_MASK) != 0);
                        }
                    } finally {
                        RUNNING.set(false);
                    }
                }
            }
        }
    }

    private static void refreshAllWorlds(final boolean forceRestore) {
        try {
            final Universe universe = Universe.get();
            if (universe == null) return;

            // Universe API in this project exposes a map of loaded worlds.
            final Map<String, World> worlds = universe.getWorlds();
            if (worlds == null || worlds.isEmpty()) return;

            for (World world : worlds.values()) {
                if (world == null) continue;

                WorldThreadExecutor.runStrict(world, () -> {
                    try {
                        HideNameplatePlayerRefreshPass.refreshPlayers(world, forceRestore);
                    } catch (Throwable t) {
                        LOGGER.at(Level.WARNING).withCause(t)
                                .log("%s Nameplate refresh pass failed for world=%s", HideEnemyHealthPlugin.LOG_PREFIX, WorldThreadExecutor.safeWorldName(world));
                    }
                }, LOGGER);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("%s Failed to refresh player nameplates", HideEnemyHealthPlugin.LOG_PREFIX);
        }
    }
}
