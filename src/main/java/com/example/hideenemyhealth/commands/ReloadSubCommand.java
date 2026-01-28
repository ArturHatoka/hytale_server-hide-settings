package com.example.hideenemyhealth.commands;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
import com.example.hideenemyhealth.worldmap.PlayerMapMarkerController;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * /hid reload - reload config from disk and re-apply to loaded entities (admin).
 */
public class ReloadSubCommand extends CommandBase {

    /**
     * Create reload subcommand.
     */
    public ReloadSubCommand() {
        super("reload", "Reload plugin configuration (admin)");
        this.setPermissionGroup(null);
    }

    /**
     * No auto-generated permission node.
     */
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Execute reload (runs synchronously).
     */
    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        if (!commandContext.sender().hasPermission(HideEnemyHealthPlugin.ADMIN_PERMISSION)) {
            commandContext.sendMessage(Message.raw("Нет прав: " + HideEnemyHealthPlugin.ADMIN_PERMISSION));
            return;
        }

        HideEnemyHealthPlugin.getInstance().reloadConfig();
        HideEntityUiSystem.refreshLoadedEntities();
        commandContext.sendMessage(Message.raw("HideEnemyHealth: конфиг перезагружен и применён."));
    }
}
