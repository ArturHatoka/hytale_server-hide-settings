package com.example.hideenemyhealth;

import com.example.hideenemyhealth.commands.HideEnemyHealthPluginCommand;
import com.example.hideenemyhealth.config.ConfigManager;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.logging.Level;

/**
 * HideEnemyHealth - server-side plugin that hides overhead HP bars (and optionally damage numbers).
 *
 * Управление - через in-game UI (доступ только админам).
 */
public class HideEnemyHealthPlugin extends JavaPlugin {

    public static final String ADMIN_PERMISSION = "hideenemyhealth.admin";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HideEnemyHealthPlugin instance;

    private final ConfigManager configManager = new ConfigManager();
    private final File configFile = new File("mods/HideEnemyHealth/config.json");

    private HideEnemyHealthConfig config;

    public HideEnemyHealthPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static HideEnemyHealthPlugin getInstance() {
        return instance;
    }

    @Nonnull
    public HideEnemyHealthConfig getConfig() {
        if (config == null) {
            config = new HideEnemyHealthConfig();
        }
        return config;
    }

    public void saveConfig() {
        if (config == null) return;
        configManager.save(configFile, config);
    }

    public void reloadConfig() {
        config = configManager.loadOrCreate(configFile);
        HideEntityUiSystem.setConfig(config);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Setting up...");

        // Config
        reloadConfig();

        // Systems
        try {
            getEntityStoreRegistry().registerSystem(new HideEntityUiSystem());
            LOGGER.at(Level.INFO).log("[HideEnemyHealth] Registered HideEntityUiSystem");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to register ECS system");
        }

        // Commands
        registerCommands();

        // Optional listeners (template left here, but not required for core feature)
        registerListeners();

        // Apply to already loaded entities
        HideEntityUiSystem.refreshLoadedEntities();

        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Setup complete!");
    }

    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new HideEnemyHealthPluginCommand());
            LOGGER.at(Level.INFO).log("[HideEnemyHealth] Registered /hid command");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HideEnemyHealth] Failed to register commands");
        }
    }

    private void registerListeners() {
        // You can remove PlayerListener if you don't need join/leave logs.
        EventRegistry eventBus = getEventRegistry();
        try {
            // Template listener is optional; leaving as-is.
            // new PlayerListener().register(eventBus);
            LOGGER.at(Level.FINE).log("[HideEnemyHealth] Listeners skipped (not required)");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HideEnemyHealth] Failed to register listeners");
        }
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Started!");
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Use /hid ui (admin) to open the in-game menu");
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[HideEnemyHealth] Shutting down...");
        saveConfig();
        instance = null;
    }
}
