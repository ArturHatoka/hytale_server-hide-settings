package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.util.WorldThreadExecutor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Utilities to force-refresh already loaded entities (players / NPCs) across all loaded worlds.
 *
 * <p>Important thread-safety rule:
 * any direct access to entity components must happen in the world's execution context.</p>
 */
public final class HideEntityUiWorldRefresher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Debounced refresh scheduler:
    // UI actions can trigger multiple refresh calls in quick succession. We coalesce them so we don't
    // repeatedly iterate worlds and enqueue redundant world.execute() tasks.
    private static final int REFRESH_PLAYERS_MASK = 1;
    private static final int REFRESH_NPCS_MASK = 2;
    private static final int BASELINE_GC_MASK = 4;

    private static final AtomicInteger PENDING_REFRESH_MASK = new AtomicInteger(0);
    private static final AtomicBoolean REFRESH_LOOP_RUNNING = new AtomicBoolean(false);

    private HideEntityUiWorldRefresher() {
    }

    /**
     * Refresh players and NPCs in all currently loaded worlds.
     */
    public static void refreshLoadedEntities() {
        requestRefresh(REFRESH_PLAYERS_MASK | REFRESH_NPCS_MASK);
    }

    /**
     * Refresh players only in all currently loaded worlds.
     */
    public static void refreshLoadedPlayers() {
        requestRefresh(REFRESH_PLAYERS_MASK);
    }

    /**
     * Refresh NPCs only in all currently loaded worlds.
     */
    public static void refreshLoadedNpcs() {
        requestRefresh(REFRESH_NPCS_MASK);
    }

    /**
     * Trigger a defensive baseline-cache GC sweep.
     */
    public static void gcBaselineCache() {
        requestRefresh(BASELINE_GC_MASK);
    }

    /**
     * Request a refresh pass. Multiple requests are coalesced into a single world iteration.
     */
    private static void requestRefresh(final int mask) {
        if (mask != 0) {
            PENDING_REFRESH_MASK.getAndUpdate(prev -> prev | mask);
        }

        // If a refresh loop is already running, it will pick up our pending mask.
        if (!REFRESH_LOOP_RUNNING.compareAndSet(false, true)) {
            return;
        }

        try {
            while (true) {
                final int pendingMask = PENDING_REFRESH_MASK.getAndSet(0);
                if (pendingMask == 0) break;

                final boolean players = (pendingMask & REFRESH_PLAYERS_MASK) != 0;
                final boolean npcs = (pendingMask & REFRESH_NPCS_MASK) != 0;
                final boolean gc = (pendingMask & BASELINE_GC_MASK) != 0;

                if (players && npcs && gc) {
                    forEachWorldOnWorldThread(world -> {
                        refreshWorld(world);
                        HideEntityUiBaselineGcPass.baselineGcSweepWorld(world);
                    });
                } else if (players && npcs) {
                    forEachWorldOnWorldThread(HideEntityUiWorldRefresher::refreshWorld);
                } else if (players && gc) {
                    forEachWorldOnWorldThread(world -> {
                        HideEntityUiPlayerRefreshPass.refreshPlayers(world);
                        HideEntityUiBaselineGcPass.baselineGcSweepWorld(world);
                    });
                } else if (npcs && gc) {
                    forEachWorldOnWorldThread(world -> {
                        HideEntityUiNpcRefreshPass.refreshNpcs(world);
                        HideEntityUiBaselineGcPass.baselineGcSweepWorld(world);
                    });
                } else if (players) {
                    forEachWorldOnWorldThread(HideEntityUiPlayerRefreshPass::refreshPlayers);
                } else if (npcs) {
                    forEachWorldOnWorldThread(HideEntityUiNpcRefreshPass::refreshNpcs);
                } else if (gc) {
                    forEachWorldOnWorldThread(HideEntityUiBaselineGcPass::baselineGcSweepWorld);
                }
            }
        } finally {
            REFRESH_LOOP_RUNNING.set(false);

            // Race window: if another thread requested a refresh after we stopped, run once more.
            if (PENDING_REFRESH_MASK.get() != 0) {
                requestRefresh(0);
            }
        }
    }

    /**
     * Iterate all loaded worlds and run a task on each world thread.
     */
    private static void forEachWorldOnWorldThread(@Nonnull final Consumer<World> perWorldTask) {
        try {
            final Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null || worlds.isEmpty()) return;

            for (World world : worlds.values()) {
                if (world == null) continue;
                WorldThreadExecutor.runStrict(world, () -> perWorldTask.accept(world), LOGGER);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[ServerHideSettings] World iteration failed");
        }
    }

    /**
     * Refresh both players and NPCs for a single world.
     */
    private static void refreshWorld(@Nonnull final World world) {
        HideEntityUiPlayerRefreshPass.refreshPlayers(world);
        HideEntityUiNpcRefreshPass.refreshNpcs(world);
    }
}
