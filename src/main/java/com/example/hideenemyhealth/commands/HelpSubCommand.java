package com.example.hideenemyhealth.commands;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

/**
 * /hid help - Show available commands
 */
public class HelpSubCommand extends CommandBase {

    public HelpSubCommand() {
        super("help", "Show available commands");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        commandContext.sendMessage(Message.raw("HideEnemyHealth commands:"));
        commandContext.sendMessage(Message.raw("  /hid info - показать текущие настройки"));
        commandContext.sendMessage(Message.raw("  /hid ui - открыть меню настроек (admin, permission: " + HideEnemyHealthPlugin.ADMIN_PERMISSION + ")"));
        commandContext.sendMessage(Message.raw("  /hid reload - перечитать config (admin)"));
    }
}
