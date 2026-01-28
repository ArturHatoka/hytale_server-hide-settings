package com.example.hideenemyhealth.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Root command for Server Hide Settings plugin.
 *
 * <p>Usage:
 * <ul>
 *   <li>/hid help</li>
 *   <li>/hid info</li>
 *   <li>/hid reload</li>
 *   <li>/hid ui</li>
 * </ul>
 * </p>
 */
public class HideEnemyHealthPluginCommand extends AbstractCommandCollection {

    /**
     * Create command collection and register subcommands.
     */
    public HideEnemyHealthPluginCommand() {
        super("hid", "Server Hide Settings plugin commands");

        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
    }

    /**
     * Base command itself does not auto-generate a permission node.
     */
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}
