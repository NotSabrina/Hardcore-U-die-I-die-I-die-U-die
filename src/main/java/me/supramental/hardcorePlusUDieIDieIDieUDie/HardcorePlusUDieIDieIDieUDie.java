package me.supramental.hardcorePlusUDieIDieIDieUDie;

import me.supramental.hardcorePlusUDieIDieIDieUDie.commands.HPUDIDIDUDCommand;
import me.supramental.hardcorePlusUDieIDieIDieUDie.listeners.DeathListener;
import me.supramental.hardcorePlusUDieIDieIDieUDie.managers.ConfigManager;
import me.supramental.hardcorePlusUDieIDieIDieUDie.managers.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HardcorePlusUDieIDieIDieUDie extends JavaPlugin {

    private ConfigManager configManager;
    private TeamManager teamManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        teamManager = new TeamManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new DeathListener(this, teamManager, configManager), this);

        // Register command
        HPUDIDIDUDCommand cmd = new HPUDIDIDUDCommand(this, teamManager, configManager);
        getCommand("HPUDIDIDUD").setExecutor(cmd);
        getCommand("HPUDIDIDUD").setTabCompleter(cmd);

        getLogger().info("Hardcore+ -UDieIDie plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveTeams();
        }
        getLogger().info("Hardcore+ -UDieIDie plugin disabled!");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public TeamManager getTeamManager()     { return teamManager; }
}
