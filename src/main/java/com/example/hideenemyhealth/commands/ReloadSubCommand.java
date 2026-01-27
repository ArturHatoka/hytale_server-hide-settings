package com.example.hideenemyhealth.commands;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.systems.HideEntityUiSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

/**
 * /hid reload - Reload plugin configuration (admin)
 */
public class ReloadSubCommand extends CommandBase {

    public ReloadSubCommand() {
        super("reload", "Reload plugin configuration (admin)");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        if (!commandContext.sender().hasPermission(HideEnemyHealthPlugin.ADMIN_PERMISSION)) {
            commandContext.sendMessage(Message.raw("Нет прав: " + HideEnemyHealthPlugin.ADMIN_PERMISSION));
        }

        HideEnemyHealthPlugin.getInstance().reloadConfig();
        HideEntityUiSystem.refreshLoadedEntities();
        commandContext.sendMessage(Message.raw("HideEnemyHealth: конфиг перезагружен и применён."));
    }
}
