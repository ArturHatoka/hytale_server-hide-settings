package com.example.hideenemyhealth.commands;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.ui.HideEnemyHealthDashboardUI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /hid ui - open the plugin dashboard UI (admins only).
 */
public class UISubCommand extends AbstractPlayerCommand {

    /**
     * Create the subcommand.
     */
    public UISubCommand() {
        super("ui", "Open the plugin dashboard (admin)");
        this.addAliases(new String[]{"dashboard", "gui"});
        this.setPermissionGroup(null);
    }

    /**
     * Subcommand handles its own permission check.
     */
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Execute on the player thread context (provided by {@link AbstractPlayerCommand}).
     *
     * @param context   command context
     * @param store     world ECS store
     * @param ref       player entity ref
     * @param playerRef player reference
     * @param world     player world
     */
    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!context.sender().hasPermission(HideEnemyHealthPlugin.ADMIN_PERMISSION)) {
            context.sendMessage(Message.raw("Нет прав: " + HideEnemyHealthPlugin.ADMIN_PERMISSION));
            return;
        }

        try {
            final Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Ошибка: Player component не найден."));
                return;
            }

            final HideEnemyHealthDashboardUI page = new HideEnemyHealthDashboardUI(playerRef);
            player.getPageManager().openCustomPage(ref, store, page);

        } catch (Exception e) {
            context.sendMessage(Message.raw("Ошибка открытия UI: " + e.getMessage()));
        }
    }
}
