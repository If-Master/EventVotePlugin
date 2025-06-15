package me.kanuunankuulasplugineventvote.eventVotePlugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.sql.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EventVotePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final Map<String, Vote> activeVotes = new ConcurrentHashMap<>();
    private final Set<String> legitCache = new HashSet<>();
    private File votesFile;
    private boolean isFolia;
    private boolean isPaper;
    private UniversalScheduler scheduler;

    private Connection connection;
    private boolean useMySQL;

    @Override
    public void onEnable() {
        detectServerType();
        scheduler = new UniversalScheduler(this, isFolia);

        saveDefaultConfig();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        votesFile = new File(getDataFolder(), "votes.dat");

        useMySQL = getConfig().getBoolean("storage.use-mysql", false);

        if (useMySQL) {
            initializeMySQL();
        }

        loadVotes();

        if (getCommand("eventvote") != null) {
            getCommand("eventvote").setExecutor(this);
            getCommand("eventvote").setTabCompleter(this);
        } else {
            getLogger().severe("Could not register 'Eventvote' command! Check your plugin.yml");
            setEnabled(false);
            return;
        }

        scheduler.runTimer(() -> checkExpiredVotes(), 20L, 20L * 60);

        getLogger().info("EventVotePlugin enabled successfully!");
    }

    private void detectServerType() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Detected Folia server");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                isPaper = true;
                getLogger().info("Detected Paper server");
            } catch (ClassNotFoundException ex) {
                isPaper = false;
                getLogger().info("Detected Bukkit/Spigot server");
            }
        }
    }

    private void broadcastMessage(String message) {
        if (isFolia) {
            scheduler.runSync(() -> Bukkit.broadcastMessage(message));
        } else {
            Bukkit.broadcastMessage(message);
        }
    }

    @Override
    public void onDisable() {
        if (votesFile != null) {
            saveVotes();
        }

        if (useMySQL && connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Failed to close MySQL connection", e);
            }
        }
        getLogger().info("EventVotePlugin disabled, votes saved.");
    }

    private String getPlayerIP(Player player) {
        String ip = player.getAddress().getAddress().getHostAddress();
        if (ip.contains(":") && !ip.startsWith("[")) {
            ip = ip.split(":")[0];
        }
        ip = ip.replaceAll("[\\[\\]]", "");
        return ip;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used in-game.");
            return true;
        }

        Player player = (Player) sender;
        String actualIP = getPlayerIP(player);
        String uuid = player.getUniqueId().toString();

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                return handleCreate(player, args);
            case "length":
                return handleLength(player, args);
            case "start":
                return handleStart(player, args);
            case "end":
                return handleEnd(player, args);
            case "results":
                return handleResults(player, args);
            case "list":
                return handleList(player);
            case "delete":
                return handleDelete(player, args);
            case "help":
                showHelp(player);
                return true;
            default:
                return handleVote(player, sub, actualIP, uuid);
        }
    }

    private void initializeMySQL() {
        try {
            String host = getConfig().getString("storage.mysql.host", "localhost");
            int port = getConfig().getInt("storage.mysql.port", 3306);
            String database = getConfig().getString("storage.mysql.database", "minecraft");
            String username = getConfig().getString("storage.mysql.username", "root");
            String password = getConfig().getString("storage.mysql.password", "");

            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, database);

            connection = DriverManager.getConnection(url, username, password);
            createTables();
            getLogger().info("MySQL connection established successfully!");

        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to connect to MySQL, falling back to file storage", e);
            useMySQL = false;
        }
    }

    private void createTables() throws SQLException {
        String createVotesTable = """
        CREATE TABLE IF NOT EXISTS vote_data (
            id VARCHAR(255) PRIMARY KEY,
            vote_data LONGTEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """;

        try (PreparedStatement stmt = connection.prepareStatement(createVotesTable)) {
            stmt.executeUpdate();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> allOptions = Stream.concat(
                            Arrays.asList("create", "start", "length", "end", "results", "list", "delete", "help").stream(),
                            getVoteOptions().stream()
                    ).filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());

            return allOptions;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("results") || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("end")) {
                return activeVotes.keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("length")) {
                return Arrays.asList("1m", "5m", "10m", "30m", "1h", "2h", "12h", "1d", "1w").stream()
                        .filter(duration -> duration.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }

    private void processVote(Player player, String option, String ip, String uuid, Vote vote) {
        Boolean canVote = vote.checkvotestage(ip, uuid);
        if (canVote) {
            vote.castVote(option, ip, uuid);
            player.sendMessage("§aYour vote for §e" + option + "§a has been recorded!");
        } else {
            player.sendMessage("§cYou have already voted or your IP matches another voter!");
        }

        if (getConfig().getBoolean("broadcast-votes", false)) {
            int totalVotes = vote.getResults().values().stream().mapToInt(Integer::intValue).sum();
            broadcastMessage("§7[Vote Update] Total votes: " + totalVotes);
        }
    }

    private boolean handleCreate(Player player, String[] args) {
        if (!player.hasPermission("eventvote.create")) {
            player.sendMessage("§4 Missing permissions ");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /eventvote create <name> <option1> <option2> [option3] ...");
            return true;
        }

        String name = args[1];
        if (activeVotes.containsKey(name)) {
            player.sendMessage("§cA vote with that name already exists!");
            return true;
        }

        List<String> options = Arrays.asList(args).subList(2, args.length);
        if (options.size() < 2) {
            player.sendMessage("§cYou need at least 2 options for a vote!");
            return true;
        }

        activeVotes.put(name, new Vote(name, options, player.getName()));
        player.sendMessage("§aVote created: §e" + name);
        player.sendMessage("§7Options: §f" + String.join(", ", options));
        player.sendMessage("§7Use §e/eventvote length <duration>§7 to set duration, then §e/eventvote start§7 to begin.");
        return true;
    }

    private boolean handleLength(Player player, String[] args) {
        if (!player.hasPermission("eventvote.create")) {
            player.sendMessage("§4 Missing permissions ");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("§cUsage: /eventvote length <1m|5m|30m|1h|12h|1d|1w>");
            return true;
        }

        Vote vote = getLatestVote();
        if (vote == null) {
            player.sendMessage("§cNo vote created. Use §e/eventvote create§c first.");
            return true;
        }

        if (vote.isStarted()) {
            player.sendMessage("§cCannot change duration of a started vote!");
            return true;
        }

        long duration = parseDuration(args[1]);
        if (duration == -1) {
            player.sendMessage("§cInvalid duration. Use formats like: 1m, 30m, 1h, 1d, 1w");
            return true;
        }

        vote.setDuration(duration);
        player.sendMessage("§aVote duration set to §e" + args[1]);
        return true;
    }

    private boolean handleStart(Player player, String[] args) {
        if (!player.hasPermission("eventvote.create")) {
            player.sendMessage("§4 Missing permissions ");
            return true;
        }

        Vote vote = getLatestVote();
        if (vote == null) {
            player.sendMessage("§cNo vote created. Use §e/eventvote create§c first.");
            return true;
        }

        if (vote.getDuration() == 0) {
            player.sendMessage("§cSet duration first with §e/eventvote length <duration>");
            return true;
        }

        if (vote.isStarted()) {
            player.sendMessage("§cVote is already started!");
            return true;
        }

        vote.start();
        player.sendMessage("§aVote §e" + vote.getName() + "§a started!");

        broadcastMessage("§6§l[VOTE] §a" + player.getName() + " started a vote: §e" + vote.getName());
        broadcastMessage("§7Options: §f" + String.join("§7, §f", vote.getOptions()));
        broadcastMessage("§7Use §e/eventvote <option>§7 to cast your vote!");

        return true;
    }

    private boolean handleEnd(Player player, String[] args) {
        if (!player.hasPermission("eventvote.create")) {
            player.sendMessage("§4 Missing permissions ");
            return true;
        }

        if (args.length == 1) {
            Vote activeVote = getActiveVote();
            if (activeVote == null) {
                player.sendMessage("§cNo active vote to end.");
                return true;
            }

            if (!player.hasPermission("eventvote.admin") && !activeVote.getCreator().equals(player.getName())) {
                player.sendMessage("§cYou can only end votes you created!");
                return true;
            }

            activeVote.end();
            player.sendMessage("§aVote §e" + activeVote.getName() + "§a has been ended!");
            broadcastMessage("§6§l[VOTE] §cVote ended: §e" + activeVote.getName());

            showVoteResults(activeVote);

            return true;
        } else if (args.length == 2) {
            Vote vote = activeVotes.get(args[1]);
            if (vote == null) {
                player.sendMessage("§cNo vote found with name: " + args[1]);
                return true;
            }

            if (!player.hasPermission("eventvote.admin") && !vote.getCreator().equals(player.getName())) {
                player.sendMessage("§cYou can only end votes you created!");
                return true;
            }

            if (!vote.isStarted()) {
                player.sendMessage("§cVote hasn't started yet!");
                return true;
            }

            if (vote.isExpired()) {
                player.sendMessage("§cVote has already expired!");
                return true;
            }

            vote.end();
            player.sendMessage("§aVote §e" + vote.getName() + "§a has been ended!");
            broadcastMessage("§6§l[VOTE] §cVote ended: §e" + vote.getName());

            showVoteResults(vote);

            return true;
        } else {
            player.sendMessage("§cUsage: /eventvote end [name]");
            return true;
        }
    }

    private void showVoteResults(Vote vote) {
        Map<String, Integer> results = vote.getResults();
        int totalVotes = results.values().stream().mapToInt(Integer::intValue).sum();

        broadcastMessage("§6§l=== Final Results for: " + vote.getName() + " ===");

        if (totalVotes == 0) {
            broadcastMessage("§7No votes were cast.");
        } else {
            broadcastMessage("§7Total votes: §f" + totalVotes);
            results.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        int count = entry.getValue();
                        double percentage = (count * 100.0) / totalVotes;
                        broadcastMessage(String.format("§e%s: §f%d §7(%.1f%%)",
                                entry.getKey(), count, percentage));
                    });
        }
    }

    private boolean handleResults(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUsage: /eventvote results <name>");
            return true;
        }

        Vote vote = activeVotes.get(args[1]);
        if (vote == null) {
            player.sendMessage("§cNo vote found with name: " + args[1]);
            return true;
        }

        player.sendMessage("§6§l=== Results for: " + vote.getName() + " ===");
        player.sendMessage("§7Created by: §f" + vote.getCreator());
        player.sendMessage("§7Status: " + (vote.isRunning() ? "§aActive" :
                vote.isStarted() ? "§cExpired" : "§eNot Started"));

        Map<String, Integer> results = vote.getResults();
        int totalVotes = results.values().stream().mapToInt(Integer::intValue).sum();

        if (totalVotes == 0) {
            player.sendMessage("§7No votes cast yet.");
        } else {
            player.sendMessage("§7Total votes: §f" + totalVotes);
            results.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        int count = entry.getValue();
                        double percentage = (count * 100.0) / totalVotes;
                        player.sendMessage(String.format("§e%s: §f%d §7(%.1f%%)",
                                entry.getKey(), count, percentage));
                    });
        }

        if (vote.isRunning()) {
            long timeLeft = vote.getTimeLeft();
            player.sendMessage("§7Time remaining: §f" + formatTime(timeLeft));
        }

        return true;
    }

    private boolean handleList(Player player) {
        if (activeVotes.isEmpty()) {
            player.sendMessage("§7No active votes.");
            return true;
        }

        player.sendMessage("§6§l=== Active Votes ===");
        activeVotes.values().forEach(vote -> {
            String status = vote.isRunning() ? "§aRunning" :
                    vote.isStarted() ? "§cExpired" : "§eWaiting";
            player.sendMessage("§e" + vote.getName() + " §7- " + status +
                    " §7(by " + vote.getCreator() + ")");
        });
        return true;
    }

    private boolean handleDelete(Player player, String[] args) {
        if (!player.hasPermission("eventvote.create")) {
            player.sendMessage("§4 Missing permissions ");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("§cUsage: /eventvote delete <name>");
            return true;
        }

        Vote vote = activeVotes.get(args[1]);
        if (vote == null) {
            player.sendMessage("§cNo vote found with name: " + args[1]);
            return true;
        }

        if (!player.hasPermission("eventvote.admin") && !vote.getCreator().equals(player.getName())) {
            player.sendMessage("§cYou can only delete votes you created!");
            return true;
        }

        activeVotes.remove(args[1]);
        player.sendMessage("§aVote §e" + args[1] + "§a deleted.");
        return true;
    }

    private boolean handleVote(Player player, String option, String ip, String uuid) {
        Vote vote = getActiveVote();
        if (vote == null) {
            player.sendMessage("§cNo active vote running. Use §e/eventvote list§c to see available votes.");
            return true;
        }

        String matchedOption = null;
        for (String voteOption : vote.getOptions()) {
            if (voteOption.equalsIgnoreCase(option)) {
                matchedOption = voteOption;
                break;
            }
        }

        if (matchedOption == null) {
            player.sendMessage("§cInvalid option. Available options: §e" +
                    String.join("§c, §e", vote.getOptions()));
            return true;
        }

        processVote(player, matchedOption, ip, uuid, vote);
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l=== Vote Plugin Help ===");
        player.sendMessage("§e/poll | /eventvote | /voting");
        player.sendMessage("§e/poll create <name> <option1> <option2> ...§7 - Create a new vote");
        player.sendMessage("§e/poll length <duration>§7 - Set vote duration (1m, 1h, 1d, etc.)");
        player.sendMessage("§e/poll start§7 - Start the vote");
        player.sendMessage("§e/poll end [name]§7 - End the active vote or specific vote");
        player.sendMessage("§e/poll <option>§7 - Cast your vote");
        player.sendMessage("§e/poll results <name>§7 - View results");
        player.sendMessage("§e/poll list§7 - List all votes");
        player.sendMessage("§e/poll delete <name>§7 - Delete a vote (creator only)");
    }

    private Vote getLatestVote() {
        return activeVotes.values().stream()
                .sorted(Comparator.comparingLong(v -> -v.getCreationTime()))
                .findFirst().orElse(null);
    }

    private Vote getActiveVote() {
        return activeVotes.values().stream()
                .filter(Vote::isRunning)
                .findFirst().orElse(null);
    }

    private List<String> getVoteOptions() {
        Vote activeVote = getActiveVote();
        return activeVote != null ? activeVote.getOptions() : new ArrayList<>();
    }

    private long parseDuration(String s) {
        try {
            int val = Integer.parseInt(s.substring(0, s.length() - 1));
            char unit = s.charAt(s.length() - 1);
            switch (unit) {
                case 'm': return val * 60_000L;
                case 'h': return val * 3_600_000L;
                case 'd': return val * 86_400_000L;
                case 'w': return val * 604_800_000L;
                default: return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private void checkExpiredVotes() {
        Vote mostRecentlyExpired = null;
        long mostRecentExpiredTime = 0;

        for (Vote vote : activeVotes.values()) {
            if (vote.isStarted() && vote.isExpired() && !vote.wasExpiredNotified()) {
                long expiredTime = vote.getStartTime() + vote.getDuration();
                if (expiredTime > mostRecentExpiredTime) {
                    mostRecentExpiredTime = expiredTime;
                    mostRecentlyExpired = vote;
                }
            }
        }

        if (mostRecentlyExpired != null) {
            mostRecentlyExpired.setExpiredNotified(true);

            broadcastMessage("§6§l[VOTE] §c⏰ Time's up! Vote expired: §e" + mostRecentlyExpired.getName());
            showVoteResults(mostRecentlyExpired);
        }

        for (Vote vote : activeVotes.values()) {
            if (vote.isStarted() && vote.isExpired() && !vote.wasExpiredNotified()) {
                vote.setExpiredNotified(true);
            }
        }

        if (getConfig().getBoolean("auto-remove-expired", false)) {
            activeVotes.values().removeIf(vote -> vote.isExpired() && vote.isStarted());
        }
    }

    private void saveVotes() {
        if (useMySQL) {
            scheduler.runAsync(this::saveToDB);
        } else {
            saveToFile();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadVotes() {
        if (useMySQL) {
            scheduler.runAsync(this::loadFromDB);
        } else {
            loadFromFile();
        }
    }

    private void saveToDB() {
        if (connection == null) return;

        try {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM vote_data")) {
                stmt.executeUpdate();
            }

            String insertSQL = "INSERT INTO vote_data (id, vote_data) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertSQL)) {
                for (Map.Entry<String, Vote> entry : activeVotes.entrySet()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                        oos.writeObject(entry.getValue());

                        stmt.setString(1, entry.getKey());
                        stmt.setString(2, Base64.getEncoder().encodeToString(baos.toByteArray()));
                        stmt.executeUpdate();
                    }
                }
            }


        } catch (SQLException | IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save votes to MySQL", e);
        }
    }

    private void loadFromDB() {
        if (connection == null) return;

        try {
            String selectSQL = "SELECT id, vote_data FROM vote_data";
            try (PreparedStatement stmt = connection.prepareStatement(selectSQL);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String id = rs.getString("id");
                    String voteData = rs.getString("vote_data");

                    byte[] data = Base64.getDecoder().decode(voteData);
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                         ObjectInputStream ois = new ObjectInputStream(bais)) {

                        Vote vote = (Vote) ois.readObject();
                        activeVotes.put(id, vote);
                    }
                }
            }


        } catch (SQLException | IOException | ClassNotFoundException e) {
        }
    }

    private void saveToFile() {
        if (votesFile == null) {
            return;
        }

        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(votesFile))) {
                oos.writeObject(activeVotes);
            }
        } catch (IOException e) {
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        if (votesFile == null || !votesFile.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(votesFile))) {
            Map<String, Vote> loaded = (Map<String, Vote>) ois.readObject();
            activeVotes.putAll(loaded);
        } catch (Exception e) {
        }
    }
}

class Vote implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<String> options;
    private final String creator;
    private final long creationTime;
    private final Map<String, Integer> results;
    private final Set<String> votedIPs;
    private final Set<String> votedUUIDs;

    private long duration;
    private long startTime;
    private boolean manuallyEnded = false;
    private boolean expiredNotified = false;

    public Vote(String name, List<String> options, String creator) {
        this.name = name;
        this.options = new ArrayList<>(options);
        this.creator = creator;
        this.creationTime = System.currentTimeMillis();
        this.results = new ConcurrentHashMap<>();
        this.votedIPs = new HashSet<>();
        this.votedUUIDs = new HashSet<>();

        options.forEach(option -> results.put(option, 0));
    }

    public void castVote(String option, String ip, String uuid) {
        if (options.contains(option) && !hasVoted(uuid) && !hasVotedIP(ip)) {
            results.put(option, results.get(option) + 1);
            votedIPs.add(ip);
            votedUUIDs.add(uuid);
        }
    }

    public boolean hasVoted(String uuid) {
        return votedUUIDs.contains(uuid);
    }

    public boolean hasVotedIP(String ip) {
        return votedIPs.contains(ip);
    }

    public boolean isRunning() {
        return isStarted() && !isExpired() && !manuallyEnded;
    }

    public boolean isStarted() {
        return startTime > 0;
    }

    public boolean isExpired() {
        return isStarted() && System.currentTimeMillis() > (startTime + duration);
    }

    public long getTimeLeft() {
        if (!isStarted()) return duration;
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, duration - elapsed);
    }

    public boolean checkvotestage(String ip, String uuid) {
        return !hasVoted(uuid) && !hasVotedIP(ip);
    }

    public boolean removeVote(String option, String ip, String uuid) {
        if (hasVoted(uuid) && hasVotedIP(ip) && results.containsKey(option)) {
            int currentCount = results.get(option);
            if (currentCount > 0) {
                results.put(option, currentCount - 1);
                votedIPs.remove(ip);
                votedUUIDs.remove(uuid);
                return true;
            }
        }
        return false;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.manuallyEnded = false;
    }

    public void end() {
        this.manuallyEnded = true;
    }

    public boolean wasExpiredNotified() {
        return expiredNotified;
    }

    public void setExpiredNotified(boolean notified) {
        this.expiredNotified = notified;
    }

    public String getName() { return name; }
    public List<String> getOptions() { return new ArrayList<>(options); }
    public String getCreator() { return creator; }
    public long getCreationTime() { return creationTime; }
    public Map<String, Integer> getResults() { return new HashMap<>(results); }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    public long getStartTime() { return startTime; }
}
