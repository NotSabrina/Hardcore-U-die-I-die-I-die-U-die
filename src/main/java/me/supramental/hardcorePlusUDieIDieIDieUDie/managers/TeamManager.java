package me.supramental.hardcorePlusUDieIDieIDieUDie.managers;

import me.supramental.hardcorePlusUDieIDieIDieUDie.HardcorePlusUDieIDieIDieUDie;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages team assignments and pending team requests.
 *
 * Storage layout (teams.yml):
 *   teams:
 *     <worldName>:
 *       team-size: <int>         # How many players per team
 *       last-mode: <lastadd|lastalone>
 *       groups:
 *         0:
 *           - <uuid>
 *           - <uuid>
 *         1:
 *           - <uuid>
 *   solo:           # Players with no team (lastalone remainder)
 *     <worldName>:
 *       - <uuid>
 */
public class TeamManager {

    private final HardcorePlusUDieIDieIDieUDie plugin;

    // worldName -> list of teams (each team = list of UUIDs)
    private final Map<String, List<List<UUID>>> worldTeams = new HashMap<>();

    // worldName -> team size used for splitting
    private final Map<String, Integer> worldTeamSize = new HashMap<>();

    // worldName -> lastadd or lastalone
    private final Map<String, String> worldLastMode = new HashMap<>();

    // worldName -> solo (no-team) players (lastalone mode)
    private final Map<String, Set<UUID>> worldSolo = new HashMap<>();

    // Pending "choose" requests: requesterUUID -> targetUUID
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();

    // Reverse: targetUUID -> set of requesterUUIDs (so target can see all requests)
    private final Map<UUID, Set<UUID>> incomingRequests = new HashMap<>();

    private File teamsFile;
    private YamlConfiguration teamsConfig;

    public TeamManager(HardcorePlusUDieIDieIDieUDie plugin) {
        this.plugin = plugin;
        teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        loadTeams();
    }

    // ─── Persistence ────────────────────────────────────────────────────────────

    public void loadTeams() {
        if (!teamsFile.exists()) return;
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);

        // Load world teams
        if (teamsConfig.isConfigurationSection("teams")) {
            for (String worldName : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
                String base = "teams." + worldName;
                int size = teamsConfig.getInt(base + ".team-size", 2);
                String lastMode = teamsConfig.getString(base + ".last-mode", "lastadd");
                worldTeamSize.put(worldName, size);
                worldLastMode.put(worldName, lastMode);

                List<List<UUID>> groups = new ArrayList<>();
                if (teamsConfig.isConfigurationSection(base + ".groups")) {
                    for (String groupKey : teamsConfig.getConfigurationSection(base + ".groups").getKeys(false)) {
                        List<String> uuidStrings = teamsConfig.getStringList(base + ".groups." + groupKey);
                        List<UUID> group = uuidStrings.stream()
                                .map(UUID::fromString).collect(Collectors.toList());
                        groups.add(group);
                    }
                }
                worldTeams.put(worldName, groups);
            }
        }

        // Load solo players
        if (teamsConfig.isConfigurationSection("solo")) {
            for (String worldName : teamsConfig.getConfigurationSection("solo").getKeys(false)) {
                List<String> uuidStrings = teamsConfig.getStringList("solo." + worldName);
                Set<UUID> soloSet = uuidStrings.stream()
                        .map(UUID::fromString).collect(Collectors.toCollection(HashSet::new));
                worldSolo.put(worldName, soloSet);
            }
        }
    }

    public void saveTeams() {
        teamsConfig = new YamlConfiguration();

        for (Map.Entry<String, List<List<UUID>>> entry : worldTeams.entrySet()) {
            String worldName = entry.getKey();
            String base = "teams." + worldName;
            teamsConfig.set(base + ".team-size", worldTeamSize.getOrDefault(worldName, 2));
            teamsConfig.set(base + ".last-mode", worldLastMode.getOrDefault(worldName, "lastadd"));

            List<List<UUID>> groups = entry.getValue();
            for (int i = 0; i < groups.size(); i++) {
                List<String> uuids = groups.get(i).stream()
                        .map(UUID::toString).collect(Collectors.toList());
                teamsConfig.set(base + ".groups." + i, uuids);
            }
        }

        for (Map.Entry<String, Set<UUID>> entry : worldSolo.entrySet()) {
            List<String> uuids = entry.getValue().stream()
                    .map(UUID::toString).collect(Collectors.toList());
            teamsConfig.set("solo." + entry.getKey(), uuids);
        }

        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save teams.yml: " + e.getMessage());
        }
    }

    // ─── Split (random) ──────────────────────────────────────────────────────────

    /**
     * Randomly split all online players in the world into teams.
     * @return A human-readable summary of the split.
     */
    public String splitRandom(World world, int teamSize, String lastMode) {
        List<Player> players = new ArrayList<>(world.getPlayers());
        Collections.shuffle(players);
        return applySplit(world.getName(), players, teamSize, lastMode);
    }

    /**
     * Apply the final split once all choose-mode requests are resolved.
     * Called after the "choose" phase times out or all players are paired.
     */
    public String applySplit(String worldName, List<Player> players, int teamSize, String lastMode) {
        worldTeamSize.put(worldName, teamSize);
        worldLastMode.put(worldName, lastMode);

        List<List<UUID>> groups = new ArrayList<>();
        List<UUID> soloPlayers = new ArrayList<>();

        // Build teams greedily
        List<UUID> currentGroup = new ArrayList<>();
        for (Player p : players) {
            currentGroup.add(p.getUniqueId());
            if (currentGroup.size() == teamSize) {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
            }
        }

        // Handle remainder
        if (!currentGroup.isEmpty()) {
            if (lastMode.equalsIgnoreCase("lastalone")) {
                soloPlayers.addAll(currentGroup);
            } else { // lastadd
                if (!groups.isEmpty()) {
                    groups.get(groups.size() - 1).addAll(currentGroup);
                } else {
                    groups.add(new ArrayList<>(currentGroup));
                }
            }
        }

        worldTeams.put(worldName, groups);
        worldSolo.put(worldName, new HashSet<>(soloPlayers));
        saveTeams();

        return buildSummary(worldName, groups, soloPlayers);
    }

    private String buildSummary(String worldName, List<List<UUID>> groups, List<UUID> soloPlayers) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6[HPUDIDIDUD] §eSplit complete for world §f").append(worldName).append("§e:\n");
        for (int i = 0; i < groups.size(); i++) {
            sb.append("  §7Team ").append(i + 1).append(": §f");
            sb.append(groups.get(i).stream()
                    .map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        return p != null ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                    })
                    .collect(Collectors.joining("§7, §f")));
            sb.append("\n");
        }
        if (!soloPlayers.isEmpty()) {
            sb.append("  §cNo team (lastalone): §f");
            sb.append(soloPlayers.stream()
                    .map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        return p != null ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                    })
                    .collect(Collectors.joining("§c, §f")));
        }
        return sb.toString();
    }

    // ─── Choose-mode requests ────────────────────────────────────────────────────

    public void sendTeamRequest(UUID requester, UUID target) {
        pendingRequests.put(requester, target);
        incomingRequests.computeIfAbsent(target, k -> new HashSet<>()).add(requester);
    }

    public boolean hasPendingRequest(UUID requester) {
        return pendingRequests.containsKey(requester);
    }

    public UUID getRequestTarget(UUID requester) {
        return pendingRequests.get(requester);
    }

    public Set<UUID> getIncomingRequests(UUID target) {
        return incomingRequests.getOrDefault(target, Collections.emptySet());
    }

    public void acceptRequest(UUID target, UUID requester) {
        pendingRequests.remove(requester);
        Set<UUID> incoming = incomingRequests.get(target);
        if (incoming != null) incoming.remove(requester);
    }

    public void declineRequest(UUID target, UUID requester) {
        pendingRequests.remove(requester);
        Set<UUID> incoming = incomingRequests.get(target);
        if (incoming != null) incoming.remove(requester);
    }

    public void clearRequests(UUID player) {
        UUID target = pendingRequests.remove(player);
        if (target != null) {
            Set<UUID> inc = incomingRequests.get(target);
            if (inc != null) inc.remove(player);
        }
        incomingRequests.remove(player);
    }

    // ─── Team Queries ────────────────────────────────────────────────────────────

    /**
     * Returns all team-mates of this UUID in the given world (excludes the player themselves).
     * Returns empty list if no team found.
     */
    public List<UUID> getTeammates(String worldName, UUID player) {
        List<List<UUID>> groups = worldTeams.get(worldName);
        if (groups == null) return Collections.emptyList();
        for (List<UUID> group : groups) {
            if (group.contains(player)) {
                List<UUID> teammates = new ArrayList<>(group);
                teammates.remove(player);
                return teammates;
            }
        }
        return Collections.emptyList();
    }

    /** Returns true if split mode is active for this world (i.e. teams exist). */
    public boolean isSplitActive(String worldName) {
        List<List<UUID>> groups = worldTeams.get(worldName);
        return groups != null && !groups.isEmpty();
    }

    /** Clear all team data for a world (reset). */
    public void clearTeams(String worldName) {
        worldTeams.remove(worldName);
        worldSolo.remove(worldName);
        worldTeamSize.remove(worldName);
        worldLastMode.remove(worldName);
        saveTeams();
    }

    /** Clear all teams across all worlds. */
    public void clearAllTeams() {
        worldTeams.clear();
        worldSolo.clear();
        worldTeamSize.clear();
        worldLastMode.clear();
        saveTeams();
    }

    public Map<String, List<List<UUID>>> getAllTeams() { return worldTeams; }
    public Map<String, Set<UUID>> getAllSolo()         { return worldSolo; }
    public int getTeamSize(String worldName)           { return worldTeamSize.getOrDefault(worldName, 2); }
    public String getLastMode(String worldName)        { return worldLastMode.getOrDefault(worldName, "lastadd"); }
}
