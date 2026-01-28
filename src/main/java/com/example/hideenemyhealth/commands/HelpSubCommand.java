package com.example.hideenemyhealth.commands;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * /hid help - show available commands.
 */
public class HelpSubCommand extends CommandBase {

    /**
     * Create the help subcommand.
     */
    public HelpSubCommand() {
        super("help", "Show available commands");
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
     * Print command usage (runs synchronously).
     */
    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        commandContext.sendMessage(Message.raw(HideEnemyHealthPlugin.DISPLAY_NAME + " commands:"));
        commandContext.sendMessage(Message.raw("  /hid info - показать текущие настройки"));
        commandContext.sendMessage(Message.raw("  /hid ui - открыть меню настроек (admin, permission: " + HideEnemyHealthPlugin.ADMIN_PERMISSION + ")"));
        commandContext.sendMessage(Message.raw("  /hid reload - перечитать config (admin)"));
    }
}
