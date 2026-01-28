package com.example.hideenemyhealth.ui;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
import com.example.hideenemyhealth.worldmap.PlayerMapMarkerController;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;

/**
 * Admin dashboard UI for Server Hide Settings.
 *
 * <p>UI is driven by a .ui layout file and event bindings.
 * Any state changes are applied server-side to the config and then re-applied to currently loaded entities.</p>
 */
public class HideEnemyHealthDashboardUI extends InteractiveCustomUIPage<HideEnemyHealthDashboardUI.UIEventData> {

    /** Path to the UI layout asset. */
    public static final String LAYOUT = "serverhidesettings/Dashboard.ui";

    private final PlayerRef playerRef;

    /**
     * Create the dashboard page.
     *
     * @param playerRef owning player reference
     */
    public HideEnemyHealthDashboardUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
    }

    /**
     * Build the UI: load layout, bind events, and push initial state.
     */
    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        // Bind buttons to actions
        bind(evt, "#TogglePlayersHealthButton", "toggle_players_health");
        bind(evt, "#TogglePlayersDamageButton", "toggle_players_damage");
        bind(evt, "#ToggleNpcsHealthButton", "toggle_npcs_health");
        bind(evt, "#ToggleNpcsDamageButton", "toggle_npcs_damage");
        bind(evt, "#ToggleMapPlayersButton", "toggle_map_players");
        bind(evt, "#RefreshButton", "refresh");
        bind(evt, "#CloseButton", "close");

        // Set initial state
        syncUI(cmd);
    }

    /**
     * Create a UI event binding for a widget selector.
     */
    private void bind(@Nonnull UIEventBuilder evt, @Nonnull String selector, @Nonnull String action) {
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData().append("Action", action),
                false
        );
    }

    /**
     * Sync current config values into UI widget labels.
     */
    private void syncUI(@Nonnull UICommandBuilder cmd) {
        final HideEnemyHealthConfig cfg = HideEnemyHealthPlugin.getInstance().getConfig();

        cmd.set("#TogglePlayersHealthButton.Text", cfg.getPlayers().hideHealthBar ? "ON" : "OFF");
        cmd.set("#TogglePlayersDamageButton.Text", cfg.getPlayers().hideDamageNumbers ? "ON" : "OFF");

        cmd.set("#ToggleNpcsHealthButton.Text", cfg.getNpcs().hideHealthBar ? "ON" : "OFF");
        cmd.set("#ToggleNpcsDamageButton.Text", cfg.getNpcs().hideDamageNumbers ? "ON" : "OFF");

        cmd.set("#ToggleMapPlayersButton.Text", cfg.getMap().hidePlayerMarkers ? "ON" : "OFF");
    }

    /**
     * Handle a UI event sent from the client.
     *
     * <p>We enforce permissions server-side (fail closed): only admins may mutate config.</p>
     */
    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        if (data.action == null) return;

        // Server-side safety: only admins can mutate config
        try {
            final Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null || !player.hasPermission(HideEnemyHealthPlugin.ADMIN_PERMISSION)) {
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw(HideEnemyHealthPlugin.DISPLAY_NAME),
                        Message.raw("No permission (" + HideEnemyHealthPlugin.ADMIN_PERMISSION + ")"),
                        NotificationStyle.Warning
                );
                return;
            }
        } catch (Throwable ignored) {
            // If permission API changes, we fail closed (no changes).
            return;
        }

        final HideEnemyHealthPlugin plugin = HideEnemyHealthPlugin.getInstance();
        final HideEnemyHealthConfig cfg = plugin.getConfig();

        boolean changed = false;
        boolean refreshPlayers = false;
        boolean refreshNpcs = false;
        boolean refreshMap = false;

        switch (data.action) {
            case "toggle_players_health" -> {
                cfg.getPlayers().hideHealthBar = !cfg.getPlayers().hideHealthBar;
                changed = true;
                refreshPlayers = true;
            }
            case "toggle_players_damage" -> {
                cfg.getPlayers().hideDamageNumbers = !cfg.getPlayers().hideDamageNumbers;
                changed = true;
                refreshPlayers = true;
            }
            case "toggle_npcs_health" -> {
                cfg.getNpcs().hideHealthBar = !cfg.getNpcs().hideHealthBar;
                changed = true;
                refreshNpcs = true;
            }
            case "toggle_npcs_damage" -> {
                cfg.getNpcs().hideDamageNumbers = !cfg.getNpcs().hideDamageNumbers;
                changed = true;
                refreshNpcs = true;
            }
            case "toggle_map_players" -> {
                cfg.getMap().hidePlayerMarkers = !cfg.getMap().hidePlayerMarkers;
                changed = true;
                refreshMap = true;
            }
            case "refresh" -> {
                // no config change, just re-apply
                HideEntityUiSystem.setConfig(cfg);
                HideEntityUiSystem.refreshLoadedEntities();
                PlayerMapMarkerController.applyToAllLoadedWorlds(cfg);
                sendStatus("Applied.");
                return;
            }
            case "close" -> {
                this.close();
                return;
            }
            default -> {
                // Unknown action: ignore.
                return;
            }
        }

        if (!changed) return;

        // Persist + publish updated config
        cfg.normalize();
        plugin.saveConfig();
        HideEntityUiSystem.setConfig(cfg);

        // Apply map marker changes if requested.
        if (refreshMap) {
            PlayerMapMarkerController.applyToAllLoadedWorlds(cfg);
        }

        // Refresh only what changed (players or NPCs). If both flags are false for some reason, refresh all.
        if (refreshPlayers && !refreshNpcs) {
            HideEntityUiSystem.refreshLoadedPlayers();
        } else if (refreshNpcs && !refreshPlayers) {
            HideEntityUiSystem.refreshLoadedNpcs();
        } else {
            HideEntityUiSystem.refreshLoadedEntities();
        }

        // Update UI widgets
        final UICommandBuilder cmd = new UICommandBuilder();
        syncUI(cmd);
        cmd.set("#StatusText.Text", "Saved and applied.");
        this.sendUpdate(cmd, false);

        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(HideEnemyHealthPlugin.DISPLAY_NAME),
                Message.raw("Saved and applied."),
                NotificationStyle.Success
        );
    }

    /**
     * Update the status text in the UI.
     */
    private void sendStatus(@Nonnull String text) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#StatusText.Text", text);
        this.sendUpdate(cmd, false);
    }

    /**
     * UI event payload codec.
     */
    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(
                        UIEventData.class, UIEventData::new
                )
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (e, v) -> e.action = v,
                        e -> e.action)
                .add()
                .build();

        private String action;

        public UIEventData() {
        }
    }
}
