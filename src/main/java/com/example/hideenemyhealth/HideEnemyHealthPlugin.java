package com.example.hideenemyhealth;

import com.example.hideenemyhealth.commands.HideEnemyHealthPluginCommand;
import com.example.hideenemyhealth.config.ConfigManager;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
import com.example.hideenemyhealth.systems.HidePlayerNameplateChangeSystem;
import com.example.hideenemyhealth.systems.HidePlayerNameplateSystem;
import com.example.hideenemyhealth.systems.hideentityui.EntityUiBaselineCache;
import com.example.hideenemyhealth.systems.hideentityui.UiComponentCache;
import com.example.hideenemyhealth.worldmap.PlayerMapMarkerController;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Server Hide Settings - server-side plugin that hides overhead UI elements for entities (HP bars, damage numbers)
 * and optionally hides player markers on the world map.
 */
public final class HideEnemyHealthPlugin extends JavaPlugin {

    /** Display name shown in UI and notifications. */
    public static final String DISPLAY_NAME = "Server Hide Settings";

    /** Log prefix used for server logs (no spaces for easier filtering). */
    public static final String LOG_PREFIX = "[ServerHideSettings]";

    /** Permission required for admin UI and reload. */
    public static final String ADMIN_PERMISSION = "serverhidesettings.admin";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static HideEnemyHealthPlugin instance;

    private final ConfigManager configManager = new ConfigManager();

    /** Canonical config path for this plugin. */
    private final File configFile = new File("mods/ServerHideSettings/config.json");

    @Nullable
    private HideEnemyHealthConfig config;

    @Nullable
    private ScheduledExecutorService backgroundScheduler;

    @Nullable
    private ScheduledFuture<?> baselineGcFuture;

    /**
     * Standard Hytale plugin constructor.
     */
    public HideEnemyHealthPlugin(@Nonnull final JavaPluginInit init) {
        super(init);
    }

    /**
     * @return plugin singleton instance (available after {@link #setup()}).
     */
    @Nonnull
    public static HideEnemyHealthPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ServerHideSettings plugin not initialized yet");
        }
        return instance;
    }

    /**
     * @return current in-memory config. If not loaded yet, returns normalized defaults.
     */
    @Nonnull
    public HideEnemyHealthConfig getConfig() {
        if (config == null) {
            final HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
            cfg.normalize();
            config = cfg;
        }
        return config;
    }

    /**
     * Persist the current config to disk.
     */
    public void saveConfig() {
        final HideEnemyHealthConfig cfg = config;
        if (cfg == null) return;
        cfg.normalize();
        configManager.save(configFile, cfg);
    }

    /**
     * Load configuration from the canonical plugin config path.
     */
    @Nonnull
    private HideEnemyHealthConfig loadConfig() {
        try {
            return configManager.loadOrCreate(configFile);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("%s Failed to load config; using defaults (not persisted)", LOG_PREFIX);
            final HideEnemyHealthConfig fallback = new HideEnemyHealthConfig();
            fallback.normalize();
            return fallback;
        }
    }

    /**
     * Reload config from disk and publish it to ECS systems and world-map marker controller.
     */
    public void reloadConfig() {
        config = loadConfig();
        HideEntityUiSystem.setConfig(config);
        HidePlayerNameplateSystem.setConfig(config);

        // Apply map marker settings to loaded worlds.
        try {
            PlayerMapMarkerController.applyToAllLoadedWorlds(config);
        } catch (Throwable ignored) {
        }

        // Background jobs are config-driven (debug.baselineGc.enabled etc.).
        restartBackgroundJobs();
    }

    /**
     * Plugin setup phase: register systems, commands, and listeners.
     */
    @Override
    protected void setup() {
        instance = this;
        LOGGER.at(Level.INFO).log("%s Setting up...", LOG_PREFIX);

        // Config
        reloadConfig();

        // ECS systems
        registerSystems();

        // Commands
        registerCommands();

        // Listeners
        registerListeners();

        // Apply to already loaded entities
        HideEntityUiSystem.refreshLoadedEntities();

        // Apply player nameplate settings to already loaded players
        HidePlayerNameplateSystem.refreshLoadedPlayers();

        // Apply map marker settings to already loaded worlds
        try {
            PlayerMapMarkerController.applyToAllLoadedWorlds(getConfig());
        } catch (Throwable ignored) {
        }

        LOGGER.at(Level.INFO).log("%s Setup complete!", LOG_PREFIX);
    }

    /**
     * Plugin start phase.
     */
    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("%s Started!", LOG_PREFIX);
        LOGGER.at(Level.INFO).log("%s Use /hid ui (admin) to open the in-game menu", LOG_PREFIX);
    }

    /**
     * Plugin shutdown phase.
     */
    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("%s Shutting down...", LOG_PREFIX);

        // Stop background jobs early to avoid work during teardown.
        stopBackgroundJobs();

        // Best-effort save.
        try {
            saveConfig();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("%s Failed to persist config on shutdown", LOG_PREFIX);
        }

        // Best-effort restore map marker providers (avoid leaving overrides on hot reload).
        try {
            PlayerMapMarkerController.restoreAllLoadedWorlds();
        } catch (Throwable ignored) {
        }

        // Best-effort restore player nameplates (avoid leaving them hidden on hot reload).
        try {
            HidePlayerNameplateSystem.forceRestoreLoadedPlayers();
        } catch (Throwable ignored) {
        }

        // Defensive cleanup to avoid stale static state after hot reload.
        try {
            EntityUiBaselineCache.clearAll();
            com.example.hideenemyhealth.systems.hidenameplate.NameplateBaselineCache.clearAll();
            UiComponentCache.resetCache();
        } catch (Throwable ignored) {
        }

        instance = null;
    }

    /**
     * Start/stop background jobs depending on the active config.
     */
    private synchronized void restartBackgroundJobs() {
        stopBackgroundJobs();

        final HideEnemyHealthConfig cfg = getConfig();
        if (cfg.debug == null || cfg.debug.baselineGc == null || !cfg.debug.baselineGc.enabled) {
            return;
        }

        final int intervalSeconds = cfg.debug.baselineGc.intervalSeconds;

        backgroundScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "ServerHideSettings-Background");
            t.setDaemon(true);
            return t;
        });

        baselineGcFuture = backgroundScheduler.scheduleAtFixedRate(() -> {
            try {
                HideEntityUiSystem.gcBaselineCache();
            } catch (Throwable t) {
                LOGGER.at(Level.FINE).withCause(t).log("%s Baseline GC tick failed", LOG_PREFIX);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        LOGGER.at(Level.INFO).log("%s Baseline GC enabled (interval=%ds)", LOG_PREFIX, intervalSeconds);
    }

    /**
     * Stop background jobs and shutdown their executor (if any).
     */
    private synchronized void stopBackgroundJobs() {
        if (baselineGcFuture != null) {
            try {
                baselineGcFuture.cancel(false);
            } catch (Throwable ignored) {
            }
            baselineGcFuture = null;
        }

        if (backgroundScheduler != null) {
            try {
                backgroundScheduler.shutdownNow();
            } catch (Throwable ignored) {
            }
            backgroundScheduler = null;
        }
    }

    /**
     * Register ECS systems.
     */
    private void registerSystems() {
        // Correctness first: many mob/entity types do not carry the NPCEntity/Player marker components
        // on all server builds. Registering only PLAYER/NPC queries can therefore miss entities that still
        // have a UIComponentList (HP bars, overhead UI).
        //
        // The ALL query (UIComponentList only) matches exactly what we need and restores spawn-time behaviour
        // from the known-good 1.4.x builds.
        try {
            getEntityStoreRegistry().registerSystem(new HideEntityUiSystem(HideEntityUiSystem.Target.ALL));
            LOGGER.at(Level.INFO).log("%s Registered HideEntityUiSystem (ALL)", LOG_PREFIX);

            getEntityStoreRegistry().registerSystem(new HidePlayerNameplateSystem());
            getEntityStoreRegistry().registerSystem(new HidePlayerNameplateChangeSystem());
            LOGGER.at(Level.INFO).log("%s Registered HidePlayerNameplate systems", LOG_PREFIX);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("%s Failed to register ECS systems", LOG_PREFIX);
        }
    }

    /**
     * Register command collection (/hid ...).
     */
    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new HideEnemyHealthPluginCommand());
            LOGGER.at(Level.INFO).log("%s Registered /hid command", LOG_PREFIX);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("%s Failed to register commands", LOG_PREFIX);
        }
    }

    /**
     * Register optional event listeners / hooks.
     */
    private void registerListeners() {
        final EventRegistry eventBus = getEventRegistry();
        try {
            // World hooks: apply player map-marker settings for new worlds.
            PlayerMapMarkerController.register(eventBus);
            LOGGER.at(Level.FINE).log("%s Listeners registered", LOG_PREFIX);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("%s Failed to register listeners", LOG_PREFIX);
        }
    }
}
