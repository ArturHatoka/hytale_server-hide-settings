package com.example.hideenemyhealth.worldmap;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.util.WorldThreadExecutor;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Controls visibility of player markers (icons) on the world map by overriding the built-in
 * marker provider under the {@code "playerIcons"} key.
 */
public final class PlayerMapMarkerController {

    /** The built-in key used by the server for the player marker provider. */
    public static final String PLAYER_ICONS_KEY = "playerIcons";

    /** No-op provider that suppresses markers by never emitting player icons. */
    private static final WorldMapManager.MarkerProvider NOOP_PROVIDER = new NoopPlayerIconsProvider();

    /**
     * World -> original provider mapping.
     *
     * <p>We intentionally use {@link WeakHashMap} so worlds can be GC'd if unloaded and not referenced elsewhere.</p>
     */
    private static final Map<World, WorldMapManager.MarkerProvider> ORIGINAL_PROVIDERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Tracks whether we captured the original provider for a given world (even if it was null). */
    private static final java.util.Set<World> ORIGINAL_CAPTURED =
            java.util.Collections.newSetFromMap(java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));

    private PlayerMapMarkerController() {
    }

    /**
     * Register world hooks that apply map-marker settings for newly added worlds.
     */
    public static void register(@Nonnull EventRegistry registry) {
        registry.registerGlobal(AddWorldEvent.class, event -> {
            try {
                applyToWorld(event.getWorld(), HideEnemyHealthPlugin.getInstance().getConfig());
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Apply current map-marker settings to all loaded worlds.
     */
    public static void applyToAllLoadedWorlds(@Nonnull HideEnemyHealthConfig cfg) {
        final Map<String, World> worlds = Universe.get().getWorlds();
        if (worlds == null || worlds.isEmpty()) return;

        for (World world : worlds.values()) {
            if (world == null) continue;
            applyToWorld(world, cfg);
        }
    }

    /**
     * Best-effort restore original player icon provider for all currently loaded worlds.
     */
    public static void restoreAllLoadedWorlds() {
        final Map<String, World> worlds = Universe.get().getWorlds();
        if (worlds == null || worlds.isEmpty()) return;

        for (World world : worlds.values()) {
            if (world == null) continue;
            WorldThreadExecutor.runQuiet(world, () -> restoreOnWorldThread(world));
        }
    }

    /**
     * Apply the configured state for a single world.
     */
    private static void applyToWorld(@Nonnull final World world, @Nullable final HideEnemyHealthConfig cfg) {
        // Capture booleans now to avoid races.
        final boolean hide = cfg != null
                && cfg.enabled
                && cfg.getMap() != null
                && cfg.getMap().hidePlayerMarkers;

        WorldThreadExecutor.runQuiet(world, () -> applyOnWorldThread(world, hide));
    }

    /**
     * World-thread application: capture original provider and then override/restore based on flag.
     */
    private static void applyOnWorldThread(@Nonnull World world, boolean hide) {
        final WorldMapManager manager;
        try {
            manager = world.getWorldMapManager();
        } catch (Throwable t) {
            return;
        }
        if (manager == null) return;

        final WorldMapProviderAccessor.Accessor accessor = WorldMapProviderAccessor.resolve(manager);

        // Capture original provider once per world (even if it is null).
        captureOriginalIfNeeded(world, accessor, manager);

        if (hide) {
            accessor.setProvider(manager, PLAYER_ICONS_KEY, NOOP_PROVIDER);
        } else {
            restoreOnWorldThread(world);
        }
    }

    /**
     * Capture and remember the original provider for this world, exactly once.
     */
    private static void captureOriginalIfNeeded(@Nonnull World world,
                                                @Nonnull WorldMapProviderAccessor.Accessor accessor,
                                                @Nonnull WorldMapManager manager) {
        synchronized (ORIGINAL_PROVIDERS) {
            if (ORIGINAL_CAPTURED.contains(world)) return;
            ORIGINAL_CAPTURED.add(world);
            ORIGINAL_PROVIDERS.put(world, accessor.getProvider(manager, PLAYER_ICONS_KEY)); // may be null
        }
    }

    /**
     * Restore original provider for the given world (world thread).
     */
    private static void restoreOnWorldThread(@Nonnull World world) {
        final WorldMapManager manager;
        try {
            manager = world.getWorldMapManager();
        } catch (Throwable t) {
            return;
        }
        if (manager == null) return;

        final WorldMapProviderAccessor.Accessor accessor = WorldMapProviderAccessor.resolve(manager);

        final WorldMapManager.MarkerProvider original;
        final boolean captured;

        synchronized (ORIGINAL_PROVIDERS) {
            captured = ORIGINAL_CAPTURED.contains(world);
            original = ORIGINAL_PROVIDERS.get(world);
        }

        // If we never overrode this world, do not touch anything.
        if (!captured) return;

        if (original != null) {
            accessor.setProvider(manager, PLAYER_ICONS_KEY, original);
        } else {
            accessor.removeProvider(manager, PLAYER_ICONS_KEY);
        }
    }
}
