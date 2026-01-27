package com.example.hideenemyhealth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Small JSON persistence layer for HideEnemyHealthConfig.
 *
 * The user doesn't need to edit files manually; it's controlled via in-game UI,
 * but we still persist values to survive restarts.
 */
public final class ConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    @Nonnull
    public HideEnemyHealthConfig loadOrCreate(@Nonnull final File file) {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
                save(file, cfg);
                return cfg;
            }

            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                HideEnemyHealthConfig cfg = gson.fromJson(r, HideEnemyHealthConfig.class);
                if (cfg == null) cfg = new HideEnemyHealthConfig();
                cfg.normalize();
                return cfg;
            }
        } catch (JsonSyntaxException jse) {
            LOGGER.at(Level.WARNING).withCause(jse).log("[HideEnemyHealth] Config JSON is malformed. Resetting to defaults: %s", file.getAbsolutePath());
            HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
            save(file, cfg);
            return cfg;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HideEnemyHealth] Failed to load config: %s. Using defaults (not persisted).", file.getAbsolutePath());
            HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
            cfg.normalize();
            return cfg;
        }
    }

    public void save(@Nonnull final File file, @Nonnull final HideEnemyHealthConfig cfg) {
        try {
            file.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(cfg, w);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HideEnemyHealth] Failed to save config: %s", file.getAbsolutePath());
        }
    }
}
