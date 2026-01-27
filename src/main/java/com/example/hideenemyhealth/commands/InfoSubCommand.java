package com.example.hideenemyhealth.commands;

import com.example.hideenemyhealth.HideEnemyHealthPlugin;
import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * /hid info - print current plugin settings.
 */
public class InfoSubCommand extends CommandBase {

    /**
     * Create the info subcommand.
     */
    public InfoSubCommand() {
        super("info", "Show plugin information");
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
     * Print current runtime config (runs synchronously).
     */
    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        final HideEnemyHealthConfig cfg = HideEnemyHealthPlugin.getInstance().getConfig();

        commandContext.sendMessage(Message.raw("HideEnemyHealth"));
        commandContext.sendMessage(Message.raw("  enabled: " + cfg.enabled));
        commandContext.sendMessage(Message.raw("  players.hideHealthBar: " + cfg.getPlayers().hideHealthBar));
        commandContext.sendMessage(Message.raw("  players.hideDamageNumbers: " + cfg.getPlayers().hideDamageNumbers));
        commandContext.sendMessage(Message.raw("  npcs.hideHealthBar: " + cfg.getNpcs().hideHealthBar));
        commandContext.sendMessage(Message.raw("  npcs.hideDamageNumbers: " + cfg.getNpcs().hideDamageNumbers));
        commandContext.sendMessage(Message.raw("  admin permission: " + HideEnemyHealthPlugin.ADMIN_PERMISSION));
        commandContext.sendMessage(Message.raw("  open UI: /hid ui"));
    }
}
