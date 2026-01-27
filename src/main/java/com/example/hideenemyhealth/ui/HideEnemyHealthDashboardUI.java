package com.example.hideenemyhealth.ui;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
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
 * Admin UI for HideEnemyHealth.
 */
public class HideEnemyHealthDashboardUI extends InteractiveCustomUIPage<HideEnemyHealthDashboardUI.UIEventData> {

    public static final String LAYOUT = "hideenemyhealth/Dashboard.ui";

    private final PlayerRef playerRef;

    public HideEnemyHealthDashboardUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
    }

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
        bind(evt, "#RefreshButton", "refresh");
        bind(evt, "#CloseButton", "close");

        // Set initial state
        syncUI(cmd);
    }

    private void bind(@Nonnull UIEventBuilder evt, @Nonnull String selector, @Nonnull String action) {
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData().append("Action", action),
                false
        );
    }

    private void syncUI(@Nonnull UICommandBuilder cmd) {
        HideEnemyHealthConfig cfg = HideEnemyHealthPlugin.getInstance().getConfig();

        cmd.set("#TogglePlayersHealthButton.Text", cfg.getPlayers().hideHealthBar ? "ON" : "OFF");
        cmd.set("#TogglePlayersDamageButton.Text", cfg.getPlayers().hideDamageNumbers ? "ON" : "OFF");

        cmd.set("#ToggleNpcsHealthButton.Text", cfg.getNpcs().hideHealthBar ? "ON" : "OFF");
        cmd.set("#ToggleNpcsDamageButton.Text", cfg.getNpcs().hideDamageNumbers ? "ON" : "OFF");
    }

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
                        Message.raw("HideEnemyHealth"),
                        Message.raw("Нет прав (" + HideEnemyHealthPlugin.ADMIN_PERMISSION + ")"),
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

        switch (data.action) {
            case "toggle_players_health" -> {
                cfg.getPlayers().hideHealthBar = !cfg.getPlayers().hideHealthBar;
                changed = true;
            }
            case "toggle_players_damage" -> {
                cfg.getPlayers().hideDamageNumbers = !cfg.getPlayers().hideDamageNumbers;
                changed = true;
            }
            case "toggle_npcs_health" -> {
                cfg.getNpcs().hideHealthBar = !cfg.getNpcs().hideHealthBar;
                changed = true;
            }
            case "toggle_npcs_damage" -> {
                cfg.getNpcs().hideDamageNumbers = !cfg.getNpcs().hideDamageNumbers;
                changed = true;
            }
            case "refresh" -> {
                // no config change, just re-apply
                HideEntityUiSystem.setConfig(cfg);
                HideEntityUiSystem.refreshLoadedEntities();
                sendStatus("Применено.");
                return;
            }
            case "close" -> {
                this.close();
                return;
            }
        }

        if (changed) {
            // Persist + apply
            cfg.normalize();
            plugin.saveConfig();
            HideEntityUiSystem.setConfig(cfg);
            HideEntityUiSystem.refreshLoadedEntities();

            UICommandBuilder cmd = new UICommandBuilder();
            syncUI(cmd);
            cmd.set("#StatusText.Text", "Сохранено и применено.");
            this.sendUpdate(cmd, false);

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw("HideEnemyHealth"),
                    Message.raw("Сохранено и применено."),
                    NotificationStyle.Success
            );
        }
    }

    private void sendStatus(@Nonnull String text) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#StatusText.Text", text);
        this.sendUpdate(cmd, false);
    }

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
