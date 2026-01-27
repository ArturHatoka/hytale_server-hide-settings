package com.example.hideenemyhealth.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Main command for HideEnemyHealth plugin.
 *
 * Usage:
 * - /hid help - Show available commands
 * - /hid info - Show plugin information
 * - /hid reload - Reload plugin configuration
 * - /hid ui - Open the plugin dashboard
 */
public class HideEnemyHealthPluginCommand extends AbstractCommandCollection {

    public HideEnemyHealthPluginCommand() {
        super("hid", "HideEnemyHealth plugin commands");

        // Add subcommands
        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // No permission required for base command
    }
}