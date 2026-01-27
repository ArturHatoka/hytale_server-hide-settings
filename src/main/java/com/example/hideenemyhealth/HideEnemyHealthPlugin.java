package com.example.hideenemyhealth;

import com.example.hideenemyhealth.commands.HideEnemyHealthPluginCommand;
import com.example.hideenemyhealth.config.ConfigManager;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
import com.example.hideenemyhealth.systems.hideentityui.EntityUiBaselineCache;
import com.example.hideenemyhealth.systems.hideentityui.UiComponentCache;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.logging.Level;

/**
 * HideEnemyHealth - server-side plugin that hides overhead HP bars (and optionally damage numbers).
 *
 * <p>Управление — через in-game UI и команды. Основная логика реализована на сервере через ECS:
 * мы переписываем {@code UIComponentList.componentIds} у сущностей.</p>
 */
public final class HideEnemyHealthPlugin extends JavaPlugin {

    /** Permission required for admin UI and reload. */
    public static final String ADMIN_PERMISSION = "hideenemyhealth.admin";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static HideEnemyHealthPlugin instance;

    private final ConfigManager configManager = new ConfigManager();
    private final File configFile = new File("mods/HideEnemyHealth/config.json");

    @Nullable
    private HideEnemyHealthConfig config;

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
        // If something calls this too early, fail loudly to catch ordering issues during dev.
        if (instance == null) throw new IllegalStateException("HideEnemyHealthPlugin not initialized yet");
        return instance;
    }

    /**
     * @return current in-memory config. If not loaded yet, returns defaults.
     */
    @Nonnull
    public HideEnemyHealthConfig getConfig() {
        if (config == null) {
            config = new HideEnemyHealthConfig();
            config.normalize();
        }
        return config;
    }

    /**
     * Persist current config to disk.
     */
    public void saveConfig() {
        final HideEnemyHealthConfig cfg = config;
        if (cfg == null) return;
        cfg.normalize();
        configManager.save(configFile, cfg);
    }

    /**
     * Reload config from disk and publish it to ECS systems.
     */
    public void reloadConfig() {
        config = configManager.loadOrCreate(configFile);
        HideEntityUiSystem.setConfig(config);
    }

    /**
     * Plugin setup phase: register systems, commands, and optional listeners.
     */
    @Override
    protected void setup() {
        instance = this;
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Setting up...");

        // Config
        reloadConfig();

        // ECS systems
        registerSystems();

        // Commands
        registerCommands();

        // Optional listeners
        registerListeners();

        // Apply to already loaded entities
        HideEntityUiSystem.refreshLoadedEntities();

        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Setup complete!");
    }

    /**
     * Plugin start phase.
     */
    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Started!");
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Use /hid ui (admin) to open the in-game menu");
    }

    /**
     * Plugin shutdown phase.
     */
    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Shutting down...");

        // Best-effort save.
        try {
            saveConfig();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to persist config on shutdown");
        }

        // Defensive cleanup to avoid stale static state after hot reload.
        try {
            EntityUiBaselineCache.clearAll();
            UiComponentCache.resetCache();
        } catch (Throwable ignored) {
        }

        instance = null;
    }

    /**
     * Register ECS systems.
     *
     * <p>For correctness across server builds, we keep a broad system registered that targets all entities
     * with {@code UIComponentList}. Player/NPC classification is handled inside the applier.</p>
     */
    private void registerSystems() {
        try {
            // IMPORTANT: keep the broad (ALL) system registered.
            // In some server builds, entity classification components can differ, and the narrower
            // PLAYER/NPC queries may miss entities that still have UIComponentList.
            // The applier itself performs Player/NPC detection to choose the right config branch.
            getEntityStoreRegistry().registerSystem(new HideEntityUiSystem(HideEntityUiSystem.Target.ALL));
            LOGGER.at(Level.INFO).log("[HideEnemyHealth] Registered HideEntityUiSystem (ALL)");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to register ECS systems");
        }
    }

    /**
     * Register command collection (/hid ...).
     */
    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new HideEnemyHealthPluginCommand());
            LOGGER.at(Level.INFO).log("[HideEnemyHealth] Registered /hid command");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to register commands");
        }
    }

    /**
     * Register optional event listeners.
     *
     * <p>Listener is not required for core functionality; the ECS systems handle live updates.
     * Enable only if you want connection logging or you later add join/leave logic.</p>
     */
    private void registerListeners() {
        final EventRegistry eventBus = getEventRegistry();
        try {
            // Uncomment if you need it:
            // new com.example.hideenemyhealth.listeners.PlayerListener().register(eventBus);
            LOGGER.at(Level.FINE).log("[HideEnemyHealth] Listeners skipped (not required)");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to register listeners");
        }
    }
}
