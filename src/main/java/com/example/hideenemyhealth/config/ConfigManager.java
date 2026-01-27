package com.example.hideenemyhealth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;

/**
 * Small JSON persistence layer for {@link HideEnemyHealthConfig}.
 *
 * <p>We intentionally keep this simple and self-contained. The server docs also show a codec-based config approach;
 * you can migrate to that later if you prefer, but this manager is perfectly fine for a small plugin.</p>
 */
public final class ConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Load config from disk, or create a default config if the file doesn't exist.
     *
     * @param filePath config file path
     * @return loaded (and normalized) config
     */
    @Nonnull
    public HideEnemyHealthConfig loadOrCreate(@Nonnull final java.io.File filePath) {
        final Path path = filePath.toPath();

        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                final HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
                cfg.normalize();
                save(filePath, cfg);
                return cfg;
            }

            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                HideEnemyHealthConfig cfg = gson.fromJson(r, HideEnemyHealthConfig.class);
                if (cfg == null) cfg = new HideEnemyHealthConfig();
                cfg.normalize();
                return cfg;
            }

        } catch (JsonSyntaxException jse) {
            LOGGER.at(Level.WARNING).withCause(jse)
                    .log("[HideEnemyHealth] Config JSON is malformed. Resetting to defaults: %s", path.toAbsolutePath());
            final HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
            cfg.normalize();
            save(filePath, cfg);
            return cfg;

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[HideEnemyHealth] Failed to load config: %s. Using defaults (not persisted).", path.toAbsolutePath());
            final HideEnemyHealthConfig cfg = new HideEnemyHealthConfig();
            cfg.normalize();
            return cfg;
        }
    }

    /**
     * Save config to disk using an atomic write pattern:
     * write to a temp file and then move into place.
     *
     * @param filePath config file path
     * @param cfg      config to persist
     */
    public void save(@Nonnull final java.io.File filePath, @Nonnull final HideEnemyHealthConfig cfg) {
        final Path path = filePath.toPath();

        try {
            Files.createDirectories(path.getParent());

            final Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");

            try (Writer w = Files.newBufferedWriter(
                    tmp,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                gson.toJson(cfg, w);
            }

            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                // Some file systems don't support atomic moves (e.g., cross-device or certain mounts).
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[HideEnemyHealth] Failed to save config: %s", path.toAbsolutePath());
        }
    }
}
