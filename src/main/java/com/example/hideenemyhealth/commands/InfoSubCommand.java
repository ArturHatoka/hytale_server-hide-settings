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

        commandContext.sendMessage(Message.raw(HideEnemyHealthPlugin.DISPLAY_NAME));
        commandContext.sendMessage(Message.raw("  enabled: " + cfg.enabled));
        final boolean playersHide = cfg.getPlayers().hideHealthBar || cfg.getPlayers().hideDamageNumbers;
        final boolean npcsHide = cfg.getNpcs().hideHealthBar || cfg.getNpcs().hideDamageNumbers;
        commandContext.sendMessage(Message.raw("  players.hideOverheadUI: " + playersHide));
        commandContext.sendMessage(Message.raw("  npcs.hideOverheadUI: " + npcsHide));
        commandContext.sendMessage(Message.raw("  map.hidePlayerMarkers: " + cfg.getMap().hidePlayerMarkers));
        commandContext.sendMessage(Message.raw("  admin permission: " + HideEnemyHealthPlugin.ADMIN_PERMISSION));
        commandContext.sendMessage(Message.raw("  open UI: /hid ui"));
    }
}
