package me.supramental.hardcorePlusUDieIDieIDieUDie.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private List<String> blacklistedWorlds;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        blacklistedWorlds = config.getStringList("blacklisted-worlds");

        plugin.getLogger().info("Configuration loaded. Blacklisted worlds: " + blacklistedWorlds);
    }

    public boolean isRequireHardcore() {
        return config.getBoolean("require-hardcore", true);
    }

    private void createDefaultConfig(File configFile) {
        try {
            config = new YamlConfiguration();
            config.set("blacklisted-worlds", Arrays.asList("spawn_world", "lobby"));
            config.set("plugin-settings.enable-broadcast", true);
            config.set("plugin-settings.broadcast-message",
                    "§c[HardcorePlus] All players in {world} have been eliminated!");
            config.set("plugin-settings.team-request-timeout-seconds", 60);
            config.save(configFile);
            plugin.getLogger().info("Default config.yml created!");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default config: " + e.getMessage());
        }
    }

    public boolean isWorldBlacklisted(String worldName) {
        return blacklistedWorlds.contains(worldName);
    }

    public List<String> getBlacklistedWorlds() { return blacklistedWorlds; }

    public boolean isBroadcastEnabled() {
        return config.getBoolean("plugin-settings.enable-broadcast", true);
    }

    public String getBroadcastMessage() {
        return config.getString("plugin-settings.broadcast-message",
                "§c[HardcorePlus] All players in {world} have been eliminated!");
    }

    public int getTeamRequestTimeout() {
        return config.getInt("plugin-settings.team-request-timeout-seconds", 60);
    }

    public void addBlacklistedWorld(String worldName) {
        if (!blacklistedWorlds.contains(worldName)) {
            blacklistedWorlds.add(worldName);
            saveConfig();
        }
    }

    public void removeBlacklistedWorld(String worldName) {
        blacklistedWorlds.remove(worldName);
        saveConfig();
    }

    private void saveConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            config.set("blacklisted-worlds", blacklistedWorlds);
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config: " + e.getMessage());
        }
    }
}
