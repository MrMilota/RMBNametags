package me.vewa.rmbnametags;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bstats.bukkit.Metrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RMBNametags extends JavaPlugin implements Listener {

    private Scoreboard board;
    private Team hiddenNamesTeam;
    private int displayTime;
    private String nameFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getLogger().info("Config loaded: display-time=" + displayTime + "s, format=" + nameFormat);

        // BStats
        new Metrics(this, 22888);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("rmbnametags_reload").setExecutor(new ReloadCommand(this));
        getLogger().info("Events and command registered successfully.");

        board = Bukkit.getScoreboardManager().getNewScoreboard();
        hiddenNamesTeam = board.registerNewTeam("hiddenNames");
        hiddenNamesTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        getLogger().info("Custom scoreboard created, hiddenNames team registered.");

        for (Player player : Bukkit.getOnlinePlayers()) {
            hidePlayerName(player);
        }
    }

    @Override
    public void onDisable() {
        // Scoreboard создан самим плагином — ничего вручную чистить не нужно
    }

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        displayTime = config.getInt("display-time", 3);
        // Храним формат как есть; обработаем цвета (HEX и &-) при показе
        nameFormat = config.getString("name-format", "&6{PLAYER_NAME}");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        hidePlayerName(event.getPlayer());
    }

    private void hidePlayerName(Player player) {
        hiddenNamesTeam.addEntry(player.getName());
        player.setScoreboard(board);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }
        Player clickedPlayer = (Player) event.getRightClicked();
        if (clickedPlayer.isInvisible()) {
            return;
        }
        Player clickingPlayer = event.getPlayer();
        showPlayerNameInActionbar(clickingPlayer, clickedPlayer);
    }

    private void showPlayerNameInActionbar(Player clickingPlayer, Player clickedPlayer) {
        String formattedName = colorize(nameFormat.replace("{PLAYER_NAME}", clickedPlayer.getName()));

        clickingPlayer.sendActionBar(formattedName);

        new BukkitRunnable() {
            @Override
            public void run() {
                clickingPlayer.sendActionBar("");
            }
        }.runTaskLater(this, displayTime * 20L); // секунды → тики
    }

    // Поддержка HEX цветов (#RRGGBB) + стандартных &-кодов
    private String colorize(String input) {
        // HEX (#RRGGBB) → §x§R§R§G§G§B§B через Bungee ChatColor.of()
        Pattern hexPattern = Pattern.compile("#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + color).toString());
        }
        matcher.appendTail(buffer);

        // Затем переводим &-коды (&a, &l и т.д.)
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
