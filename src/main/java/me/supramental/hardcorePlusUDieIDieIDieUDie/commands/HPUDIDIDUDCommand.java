package me.supramental.hardcorePlusUDieIDieIDieUDie.commands;

import me.supramental.hardcorePlusUDieIDieIDieUDie.HardcorePlusUDieIDieIDieUDie;
import me.supramental.hardcorePlusUDieIDieIDieUDie.managers.ConfigManager;
import me.supramental.hardcorePlusUDieIDieIDieUDie.managers.TeamManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /HPUDIDIDUD <subcommand>
 *
 * Subcommands:
 *   help                                       – list all commands
 *   reload                                     – reload config (op)
 *   split <teamSize> <random|choose> [lastadd|lastalone]
 *   reset [world]                              – clear team data (op)
 *   teams [world]                              – show current teams
 *   request <player>                           – (choose mode) request to team with player
 *   accept <player>                            – accept incoming team request
 *   decline <player>                           – decline incoming team request
 */
public class HPUDIDIDUDCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX  = "§6[HPUDIDIDUD] §r";
    private static final String ERR     = "§c";
    private static final String OK      = "§a";

    private final HardcorePlusUDieIDieIDieUDie plugin;
    private final TeamManager  teamManager;
    private final ConfigManager configManager;

    // choose-mode state: worldName -> list of pre-built pairs (UUID pairs accepted so far)
    // and the players still waiting
    private final Map<String, ChooseSession> chooseSessions = new HashMap<>();

    public HPUDIDIDUDCommand(HardcorePlusUDieIDieIDieUDie plugin,
                              TeamManager teamManager,
                              ConfigManager configManager) {
        this.plugin = plugin;
        this.teamManager  = teamManager;
        this.configManager = configManager;
    }

    // ─── Execute ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload":
                return handleReload(sender);

            case "split":
                return handleSplit(sender, args);

            case "reset":
                return handleReset(sender, args);

            case "teams":
                return handleTeams(sender, args);

            case "request":
                return handleRequest(sender, args);

            case "accept":
                return handleAccept(sender, args);

            case "decline":
                return handleDecline(sender, args);

            default:
                sender.sendMessage(PREFIX + ERR + "Unknown subcommand. Use /HPUDIDIDUD help");
                return true;
        }
    }

    // ─── Help ────────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━ HardcorePlus Commands ━━━━━");
        sendHelpLine(sender, "/HPUDIDIDUD help",
                "Show this help menu", null);
        sendHelpLine(sender, "/HPUDIDIDUD reload",
                "Reload the plugin config [OP]", null);
        sendHelpLine(sender, "/HPUDIDIDUD split <size> <random|choose> [lastadd|lastalone]",
                "Divide online players into teams.\n"
                + "  §esize§7: players per team\n"
                + "  §erandom§7: auto-pair players randomly\n"
                + "  §echoose§7: players send team requests\n"
                + "  §elastadd§7: leftover player joins last team (default)\n"
                + "  §elastalone§7: leftover player stays solo",
                "/HPUDIDIDUD split ");
        sendHelpLine(sender, "/HPUDIDIDUD request <player>",
                "Request to team up with a player (choose mode only)", null);
        sendHelpLine(sender, "/HPUDIDIDUD accept <player>",
                "Accept an incoming team request", null);
        sendHelpLine(sender, "/HPUDIDIDUD decline <player>",
                "Decline an incoming team request", null);
        sendHelpLine(sender, "/HPUDIDIDUD teams [world]",
                "Show current team assignments", null);
        sendHelpLine(sender, "/HPUDIDIDUD reset [world]",
                "Clear team data for a world (or all worlds) [OP]", null);
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void sendHelpLine(CommandSender sender, String cmd, String tooltip, String suggestion) {
        if (sender instanceof Player) {
            TextComponent comp = new TextComponent("  §e" + cmd);
            comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7" + tooltip).create()));
            if (suggestion != null) {
                comp.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion));
            }
            ((Player) sender).spigot().sendMessage(comp);
        } else {
            sender.sendMessage("  §e" + cmd + " §7- " + tooltip.replace("\n", " "));
        }
    }

    // ─── Reload ──────────────────────────────────────────────────────────────────

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("HPUDIDIDUD.reload")) {
            sender.sendMessage(PREFIX + ERR + "You don't have permission.");
            return true;
        }
        configManager.loadConfig();
        sender.sendMessage(PREFIX + OK + "Config reloaded.");
        return true;
    }

    // ─── Split ───────────────────────────────────────────────────────────────────

    private boolean handleSplit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("HPUDIDIDUD.split")) {
            sender.sendMessage(PREFIX + ERR + "You don't have permission.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ERR + "Only players can use split (world context needed).");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(PREFIX + ERR
                    + "Usage: /HPUDIDIDUD split <teamSize> <random|choose> [lastadd|lastalone]");
            return true;
        }

        int teamSize;
        try {
            teamSize = Integer.parseInt(args[1]);
            if (teamSize < 2) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ERR + "Team size must be an integer ≥ 2.");
            return true;
        }

        String mode = args[2].toLowerCase();
        if (!mode.equals("random") && !mode.equals("choose")) {
            sender.sendMessage(PREFIX + ERR + "Mode must be §frandom §cor §fchoose§c.");
            return true;
        }

        String lastMode = "lastadd";
        if (args.length >= 4) {
            lastMode = args[3].toLowerCase();
            if (!lastMode.equals("lastadd") && !lastMode.equals("lastalone")) {
                sender.sendMessage(PREFIX + ERR + "Last-mode must be §flastadd §cor §flastalone§c.");
                return true;
            }
        }

        Player player = (Player) sender;
        World  world  = player.getWorld();
        String worldName = world.getName();

        List<Player> online = new ArrayList<>(world.getPlayers());
        if (online.size() < 2) {
            sender.sendMessage(PREFIX + ERR + "Not enough players online in this world to split.");
            return true;
        }

        if (mode.equals("random")) {
            String summary = teamManager.splitRandom(world, teamSize, lastMode);
            Bukkit.broadcastMessage(summary);
        } else {
            // ── Choose mode ────────────────────────────────────────────────────
            // Start a session; players send /HPUDIDIDUD request <player>
            // After the timeout OR when no unmatched players remain, finalize.
            ChooseSession session = new ChooseSession(teamSize, lastMode, new ArrayList<>(online));
            chooseSessions.put(worldName, session);

            Bukkit.broadcastMessage(PREFIX
                    + "§eTeam selection started in §f" + worldName + "§e! "
                    + "You have §f" + configManager.getTeamRequestTimeout()
                    + "s §eto pick your team-mate(s) with §f/HPUDIDIDUD request <player>§e. "
                    + "Unmatched players will be auto-assigned.");

            int timeoutTicks = configManager.getTeamRequestTimeout() * 20;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ChooseSession s = chooseSessions.remove(worldName);
                if (s == null) return; // already finalized
                finalizeChooseSession(worldName, s);
            }, timeoutTicks);
        }
        return true;
    }

    // ─── Request ─────────────────────────────────────────────────────────────────

    private boolean handleRequest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ERR + "Only players can use this.");
            return true;
        }
        Player requester = (Player) sender;
        String worldName = requester.getWorld().getName();

        ChooseSession session = chooseSessions.get(worldName);
        if (session == null) {
            requester.sendMessage(PREFIX + ERR + "No active team-selection session in this world.");
            return true;
        }

        if (args.length < 2) {
            requester.sendMessage(PREFIX + ERR + "Usage: /HPUDIDIDUD request <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.getWorld().getName().equals(worldName)) {
            requester.sendMessage(PREFIX + ERR + "Player §f" + args[1] + " §cis not online in this world.");
            return true;
        }

        if (target.equals(requester)) {
            requester.sendMessage(PREFIX + ERR + "You cannot request yourself.");
            return true;
        }

        if (session.isMatched(requester.getUniqueId())) {
            requester.sendMessage(PREFIX + ERR + "You are already in a team.");
            return true;
        }

        if (session.isMatched(target.getUniqueId())) {
            requester.sendMessage(PREFIX + ERR + target.getName() + " is already in a team.");
            return true;
        }

        // Store request
        teamManager.sendTeamRequest(requester.getUniqueId(), target.getUniqueId());
        requester.sendMessage(PREFIX + "§eTeam request sent to §f" + target.getName() + "§e.");

        // Notify target with clickable accept/decline buttons
        if (target.isOnline()) {
            target.sendMessage(PREFIX + "§f" + requester.getName()
                    + " §ewants to team up with you!");
            sendClickableRequestButtons(target, requester.getName());
        }
        return true;
    }

    private void sendClickableRequestButtons(Player target, String requesterName) {
        TextComponent accept = new TextComponent("  §a[✔ Accept]");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/HPUDIDIDUD accept " + requesterName));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§aAccept team request from §f" + requesterName).create()));

        TextComponent space = new TextComponent("  ");

        TextComponent decline = new TextComponent("§c[✘ Decline]");
        decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/HPUDIDIDUD decline " + requesterName));
        decline.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§cDecline team request from §f" + requesterName).create()));

        target.spigot().sendMessage(accept, space, decline);
    }

    // ─── Accept ──────────────────────────────────────────────────────────────────

    private boolean handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player target = (Player) sender;
        String worldName = target.getWorld().getName();

        if (args.length < 2) {
            target.sendMessage(PREFIX + ERR + "Usage: /HPUDIDIDUD accept <player>");
            return true;
        }

        Player requester = Bukkit.getPlayerExact(args[1]);
        UUID requesterUUID = requester != null ? requester.getUniqueId() : null;

        // Check incoming requests
        Set<UUID> incoming = teamManager.getIncomingRequests(target.getUniqueId());
        if (requesterUUID == null || !incoming.contains(requesterUUID)) {
            target.sendMessage(PREFIX + ERR + "No pending request from §f" + args[1] + "§c.");
            return true;
        }

        ChooseSession session = chooseSessions.get(worldName);
        if (session == null) {
            target.sendMessage(PREFIX + ERR + "The selection session has ended.");
            teamManager.declineRequest(target.getUniqueId(), requesterUUID);
            return true;
        }

        // Match them
        session.match(requesterUUID, target.getUniqueId());
        teamManager.acceptRequest(target.getUniqueId(), requesterUUID);

        target.sendMessage(PREFIX + OK + "You are now teamed with §f"
                + (requester != null ? requester.getName() : args[1]) + "§a.");
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(PREFIX + OK + target.getName() + " §aaccepted your team request!");
        }

        // Check if session is complete (all matched or no unmatched pairs possible)
        tryFinalizeIfDone(worldName, session);
        return true;
    }

    // ─── Decline ─────────────────────────────────────────────────────────────────

    private boolean handleDecline(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player target = (Player) sender;

        if (args.length < 2) {
            target.sendMessage(PREFIX + ERR + "Usage: /HPUDIDIDUD decline <player>");
            return true;
        }

        Player requester = Bukkit.getPlayerExact(args[1]);
        UUID requesterUUID = requester != null ? requester.getUniqueId() : null;

        Set<UUID> incoming = teamManager.getIncomingRequests(target.getUniqueId());
        if (requesterUUID == null || !incoming.contains(requesterUUID)) {
            target.sendMessage(PREFIX + ERR + "No pending request from §f" + args[1] + "§c.");
            return true;
        }

        teamManager.declineRequest(target.getUniqueId(), requesterUUID);

        target.sendMessage(PREFIX + "§7Request from §f"
                + (requester != null ? requester.getName() : args[1]) + " §7declined.");
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(PREFIX + ERR + target.getName()
                    + " §cdeclined your team request. Try another player.");
        }
        return true;
    }

    // ─── Teams ───────────────────────────────────────────────────────────────────

    private boolean handleTeams(CommandSender sender, String[] args) {
        String worldName = null;
        if (args.length >= 2) {
            worldName = args[1];
        } else if (sender instanceof Player) {
            worldName = ((Player) sender).getWorld().getName();
        }

        if (worldName == null) {
            // Show all worlds
            Map<String, List<List<UUID>>> all = teamManager.getAllTeams();
            if (all.isEmpty()) {
                sender.sendMessage(PREFIX + "§7No teams are currently active.");
                return true;
            }
            for (String wn : all.keySet()) {
                printTeams(sender, wn);
            }
        } else {
            printTeams(sender, worldName);
        }
        return true;
    }

    private void printTeams(CommandSender sender, String worldName) {
        if (!teamManager.isSplitActive(worldName)) {
            sender.sendMessage(PREFIX + "§7No teams in world §f" + worldName + "§7 (default: all-linked).");
            return;
        }
        sender.sendMessage("§6§lTeams in §f" + worldName + "§6§l (size="
                + teamManager.getTeamSize(worldName) + ", "
                + teamManager.getLastMode(worldName) + "):");

        Map<String, List<List<UUID>>> all = teamManager.getAllTeams();
        List<List<UUID>> groups = all.get(worldName);
        for (int i = 0; i < groups.size(); i++) {
            String members = groups.get(i).stream()
                    .map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) return "§a" + p.getName();
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        return "§7" + (name != null ? name : uuid.toString());
                    })
                    .collect(Collectors.joining("§r, "));
            sender.sendMessage("  §eTeam " + (i + 1) + "§r: " + members);
        }

        Set<UUID> solo = teamManager.getAllSolo().get(worldName);
        if (solo != null && !solo.isEmpty()) {
            String soloList = solo.stream()
                    .map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        return p != null ? "§c" + p.getName()
                                : "§7" + Bukkit.getOfflinePlayer(uuid).getName();
                    })
                    .collect(Collectors.joining("§r, "));
            sender.sendMessage("  §cNo team (solo): §r" + soloList);
        }
    }

    // ─── Reset ───────────────────────────────────────────────────────────────────

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("HPUDIDIDUD.reset")) {
            sender.sendMessage(PREFIX + ERR + "You don't have permission.");
            return true;
        }
        if (args.length >= 2) {
            teamManager.clearTeams(args[1]);
            sender.sendMessage(PREFIX + OK + "Teams cleared for world §f" + args[1] + "§a.");
        } else {
            teamManager.clearAllTeams();
            sender.sendMessage(PREFIX + OK + "All team data cleared.");
        }
        return true;
    }

    // ─── Choose session finalization ─────────────────────────────────────────────

    private void tryFinalizeIfDone(String worldName, ChooseSession session) {
        List<UUID> unmatched = session.getUnmatched();
        if (unmatched.size() <= 1) {
            chooseSessions.remove(worldName);
            finalizeChooseSession(worldName, session);
        }
    }

    private void finalizeChooseSession(String worldName, ChooseSession session) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Build full player list from matched pairs + unmatched remainder
        // Matched pairs are already in session.pairs
        // Unmatched players need to be auto-assigned like random

        // Collect all players in order: matched first, unmatched last
        List<Player> orderedPlayers = new ArrayList<>();
        for (List<UUID> pair : session.getPairs()) {
            for (UUID uuid : pair) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) orderedPlayers.add(p);
            }
        }
        for (UUID uuid : session.getUnmatched()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) orderedPlayers.add(p);
        }

        String summary = teamManager.applySplit(worldName, orderedPlayers,
                session.getTeamSize(), session.getLastMode());
        Bukkit.broadcastMessage(PREFIX + "§eTeam selection ended!\n" + summary);
    }

    // ─── Tab Completion ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("help", "reload", "split", "reset", "teams",
                    "request", "accept", "decline");
            return filterPrefix(subs, args[0]);
        }

        switch (args[0].toLowerCase()) {
            case "split":
                if (args.length == 2) return Collections.singletonList("<teamSize>");
                if (args.length == 3) return filterPrefix(Arrays.asList("random", "choose"), args[2]);
                if (args.length == 4) return filterPrefix(Arrays.asList("lastadd", "lastalone"), args[3]);
                break;

            case "reset":
            case "teams":
                if (args.length == 2) {
                    return Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                break;

            case "request":
            case "accept":
            case "decline":
                if (args.length == 2) {
                    String worldName = sender instanceof Player
                            ? ((Player) sender).getWorld().getName() : null;
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.equals(sender))
                            .filter(p -> worldName == null || p.getWorld().getName().equals(worldName))
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                break;
        }

        return completions;
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    // ─── Inner: ChooseSession ────────────────────────────────────────────────────

    private static class ChooseSession {
        private final int teamSize;
        private final String lastMode;
        private final List<UUID> remaining; // not yet matched
        private final List<List<UUID>> pairs = new ArrayList<>();
        private final Set<UUID> matchedSet = new HashSet<>();

        ChooseSession(int teamSize, String lastMode, List<Player> players) {
            this.teamSize = teamSize;
            this.lastMode = lastMode;
            this.remaining = players.stream().map(Player::getUniqueId).collect(Collectors.toList());
        }

        void match(UUID a, UUID b) {
            matchedSet.add(a);
            matchedSet.add(b);
            remaining.remove(a);
            remaining.remove(b);

            // Find if either is already in a partial pair (teams > 2)
            for (List<UUID> pair : pairs) {
                if (pair.contains(a) || pair.contains(b)) {
                    if (!pair.contains(a)) pair.add(a);
                    if (!pair.contains(b)) pair.add(b);
                    return;
                }
            }
            pairs.add(new ArrayList<>(Arrays.asList(a, b)));
        }

        boolean isMatched(UUID uuid) { return matchedSet.contains(uuid); }
        List<UUID> getUnmatched()    { return remaining; }
        List<List<UUID>> getPairs()  { return pairs; }
        int getTeamSize()            { return teamSize; }
        String getLastMode()         { return lastMode; }
    }
}
