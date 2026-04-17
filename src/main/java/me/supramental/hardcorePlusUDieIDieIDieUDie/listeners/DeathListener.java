package me.supramental.hardcorePlusUDieIDieIDieUDie.listeners;

import me.supramental.hardcorePlusUDieIDieIDieUDie.HardcorePlusUDieIDieIDieUDie;
import me.supramental.hardcorePlusUDieIDieIDieUDie.managers.ConfigManager;
import me.supramental.hardcorePlusUDieIDieIDieUDie.managers.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

public class DeathListener implements Listener {

    private final HardcorePlusUDieIDieIDieUDie plugin;
    private final TeamManager teamManager;
    private final ConfigManager configManager;

    // Track which UUIDs are "world-banned" (kicked from the world, not the server)
    // worldName -> set of banned UUIDs
    private final Map<String, Set<UUID>> worldBanned = new HashMap<>();

    public DeathListener(HardcorePlusUDieIDieIDieUDie plugin,
                         TeamManager teamManager,
                         ConfigManager configManager) {
        this.plugin  = plugin;
        this.teamManager  = teamManager;
        this.configManager = configManager;
    }

    private final Set<UUID> processingDeaths = new HashSet<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        UUID deadUUID = deadPlayer.getUniqueId();

        if (processingDeaths.contains(deadUUID)) return;

        processingDeaths.add(deadUUID);

        World world = deadPlayer.getWorld();
        String worldName = world.getName();

        if (configManager.isWorldBlacklisted(worldName)) return;
        if (configManager.isRequireHardcore() && !world.isHardcore()) return;

        if (!teamManager.isSplitActive(worldName)) {
            for (Player p : world.getPlayers()) {
                if (!p.equals(deadPlayer)) {
                    p.damage(9999);
                }
            }
        } else {
            List<UUID> teammates = teamManager.getTeammates(worldName, deadUUID);

            if (teammates.isEmpty()) return;

            worldBanPlayer(worldName, deadUUID, deadPlayer);

            for (UUID uuid : teammates) {
                Player mate = Bukkit.getPlayer(uuid);
                if (mate != null && mate.isOnline()) {
                    mate.damage(9999);
                }
                worldBanPlayer(worldName, uuid, mate);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processingDeaths.remove(deadUUID);
        }, 20L);
    }

    /**
     * World-ban: tracks the UUID as banned from this specific world.
     * If the player is online, teleport them away to the default world spawn.
     * This does NOT ban them from the server.
     */
    private void worldBanPlayer(String worldName, UUID uuid, Player player) {
        worldBanned.computeIfAbsent(worldName, k -> new HashSet<>()).add(uuid);

        // Teleport away on the next tick (they may be dead right now)
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    World defaultWorld = Bukkit.getWorlds().get(0);
                    if (!defaultWorld.getName().equals(worldName)) {
                        player.teleport(defaultWorld.getSpawnLocation());
                        player.sendMessage("§c[HardcorePlus] You have been removed from world §f"
                                + worldName + "§c.");
                    }
                }
            }, 2L);
        }
    }

    /**
     * When a player joins, check if they are world-banned from the world they are in.
     * If so, teleport them to the default world.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        Set<UUID> banned = worldBanned.get(worldName);
        if (banned != null && banned.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage("§c[HardcorePlus] You are world-banned from §f" + worldName
                        + "§c. You cannot return there.");
                World defaultWorld = Bukkit.getWorlds().get(0);
                if (!defaultWorld.getName().equals(worldName)) {
                    player.teleport(defaultWorld.getSpawnLocation());
                }
            }, 2L);
        }
    }

    /** Expose for command use (allow ops to un-ban a player from a world). */
    public void removeWorldBan(String worldName, UUID uuid) {
        Set<UUID> banned = worldBanned.get(worldName);
        if (banned != null) banned.remove(uuid);
    }

    public Map<String, Set<UUID>> getWorldBanned() { return worldBanned; }
}
