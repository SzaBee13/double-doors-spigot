package me.szabee.doubledoors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.data.Metric;
import me.szabee.doubledoors.config.ClaimSettings;
import me.szabee.doubledoors.config.PlayerPreferences;
import me.szabee.doubledoors.config.PluginConfig;
import me.szabee.doubledoors.i18n.TranslationManager;
import me.szabee.doubledoors.listeners.DoorInteractListener;
import me.szabee.doubledoors.listeners.RedstoneListener;
import me.szabee.doubledoors.migration.YamlToSqlMigrator;
import me.szabee.doubledoors.storage.SharedSqlStorage;
import me.szabee.doubledoors.util.ProtectionCompat;

/**
 * Main plugin class for DoubleDoors.
 */
public final class DoubleDoors extends JavaPlugin implements CommandExecutor, TabCompleter {
  private static final String FASTSTATS_TOKEN_PATTERN = "[a-z0-9]{32}";
  private static final String FASTSTATS_PROJECT_TOKEN = "2dc619de-5e43-4289-8df6-16e9671697c9";

  private PluginConfig pluginConfig;
  private PlayerPreferences playerPreferences;
  private ClaimSettings claimSettings;
  private TranslationManager translationManager;
  private SharedSqlStorage sqlStorage;
  private BukkitMetrics metrics;

  /**
   * Gets the plugin configuration wrapper.
   *
   * @return the active plugin config
   */
  public PluginConfig getPluginConfig() {
    return pluginConfig;
  }

  /**
   * Gets the per-player preferences manager.
   *
   * @return the player preferences instance
   */
  public PlayerPreferences getPlayerPreferences() {
    return playerPreferences;
  }

  /**
   * Gets the per-claim settings manager.
   *
   * @return the claim settings instance
   */
  public ClaimSettings getClaimSettings() {
    return claimSettings;
  }

  /**
   * Gets the translation manager.
   *
   * @return the active translation manager
   */
  public TranslationManager getTranslationManager() {
    return translationManager;
  }

  /**
   * Gets the shared SQL storage (null when SQL mode is disabled).
   *
   * @return SQL storage or null
   */
  public SharedSqlStorage getSqlStorage() {
    return sqlStorage;
  }

  /**
   * Checks whether the player can interact with a linked door block according to
   * active protection plugins.
   *
   * @param player the interacting player
   * @param linkedBlock the linked block to toggle
   * @return true if interaction should be allowed
   */
  public boolean canOpenLinkedDoor(Player player, Block linkedBlock) {
    return ProtectionCompat.canOpenLinkedDoor(this, player, linkedBlock);
  }

  /**
   * Checks whether double-door logic is globally enabled for a given player.
   *
   * @param player the player to check
   * @return true if behavior is enabled for the player
   */
  public boolean isEnabledForPlayer(Player player) {
    return playerPreferences.isEnabled(player.getUniqueId());
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    pluginConfig = new PluginConfig(this);

    initializeFastStats();

    sqlStorage = null;
    translationManager = new TranslationManager(this, pluginConfig);
    translationManager.reload();
    playerPreferences = new PlayerPreferences(this);
    claimSettings = new ClaimSettings(this);
    initializeSqlIfEnabledAsync();

    getServer().getPluginManager().registerEvents(new DoorInteractListener(this), this);
    getServer().getPluginManager().registerEvents(new RedstoneListener(this), this);

    if (getCommand("doubledoors") != null) {
      getCommand("doubledoors").setExecutor(this);
      getCommand("doubledoors").setTabCompleter(this);
    }

    PluginManager pluginManager = getServer().getPluginManager();
    if (pluginManager.isPluginEnabled("LuckPerms")) {
      getLogger().info(t("log.luckperms_detected"));
    }
    if (pluginManager.isPluginEnabled("GriefPrevention")) {
      getLogger().info(t("log.griefprevention_detected"));
    }
    boolean hasLocalGeyserBridge = hasAnyPluginEnabled(pluginManager,
        "Geyser-Spigot",
        "Geyser",
        "floodgate",
        "floodgate-bukkit");
    boolean hasProxyHeartbeat = sqlStorage != null
      && sqlStorage.hasRecentProxyHeartbeat(pluginConfig.getProxyHeartbeatMaxAgeMillis());
    if (hasLocalGeyserBridge || hasProxyHeartbeat) {
      getLogger().info(t("log.geyser_detected"));
    }

    getLogger().info(t("log.enabled"));
  }

  @Override
  public void onDisable() {
    if (metrics != null) {
      metrics.shutdown();
      metrics = null;
    }

    if (playerPreferences != null) {
      playerPreferences.save();
    }
    getLogger().info(t("log.disabled"));
  }

  private String t(String key, Object... args) {
    if (translationManager == null) {
      return key;
    }
    return translationManager.tr(key, args);
  }

  private static boolean hasAnyPluginEnabled(PluginManager pluginManager, String... pluginNames) {
    for (Plugin plugin : pluginManager.getPlugins()) {
      String installedName = plugin.getName();
      for (String candidate : pluginNames) {
        if (installedName.equalsIgnoreCase(candidate)) {
          return true;
        }
      }
    }
    return false;
  }

  private void initializeSqlIfEnabledAsync() {
    sqlStorage = null;
    if (!pluginConfig.isSqlEnabled()) {
      return;
    }

    SharedSqlStorage storage = new SharedSqlStorage(this, pluginConfig);
    getServer().getScheduler().runTaskAsynchronously(this, () -> {
      try {
        storage.initializeSchema();
        if (pluginConfig.isMigrateYamlToSql()) {
          YamlToSqlMigrator.migrateIfNeeded(this, storage);
        }
        getServer().getScheduler().runTask(this, () -> {
          sqlStorage = storage;
          playerPreferences = new PlayerPreferences(this);
          claimSettings = new ClaimSettings(this);
        });
      } catch (RuntimeException e) {
        getLogger().log(Level.SEVERE, "Could not initialize SQL storage; continuing with YAML persistence.", e);
        getServer().getScheduler().runTask(this, () -> {
          sqlStorage = null;
          playerPreferences = new PlayerPreferences(this);
          claimSettings = new ClaimSettings(this);
        });
      }
    });
  }

  private void initializeFastStats() {
    if (!pluginConfig.isEnableAnonymousTracking()) {
      metrics = null;
      getLogger().info("Anonymous tracking is disabled by config.");
      return;
    }

    String tokenSource = pluginConfig.getFastStatsToken().isBlank()
        ? FASTSTATS_PROJECT_TOKEN
        : pluginConfig.getFastStatsToken();
    String token = normalizeFastStatsToken(tokenSource);
    if (token == null) {
      metrics = null;
      getLogger().warning("Anonymous tracking is enabled, but FastStats token is invalid; metrics are disabled."
          + " Please set fastStatsToken in config.yml.");
      return;
    }

    BukkitMetrics.Factory factory = BukkitMetrics.factory();
    if (pluginConfig.isEnableExtendedAnonymousTracking()) {
      factory = factory
          .addMetric(Metric.string("server_location", pluginConfig::getTrackingServerLocation))
          .addMetric(Metric.stringArray("countries", () -> pluginConfig.getTrackingCountries().toArray(String[]::new)))
          .addMetric(Metric.string("java_version", () -> System.getProperty("java.version", "unknown")))
          .addMetric(Metric.stringArray("system_statistics", this::getSystemStatistics));
    }

    try {
      metrics = factory.token(token).create(this);
      metrics.ready();
    } catch (RuntimeException e) {
      metrics = null;
      getLogger().log(Level.WARNING, "FastStats could not be initialized; continuing without metrics.", e);
    }
  }

  private String normalizeFastStatsToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return null;
    }

    String normalized = rawToken.trim().toLowerCase().replace("-", "");
    if (!normalized.matches(FASTSTATS_TOKEN_PATTERN)) {
      return null;
    }
    return normalized;
  }

  private String[] getSystemStatistics() {
    Runtime runtime = Runtime.getRuntime();
    return new String[] {
        "os=" + System.getProperty("os.name", "unknown"),
        "os_version=" + System.getProperty("os.version", "unknown"),
        "arch=" + System.getProperty("os.arch", "unknown"),
        "java_version=" + System.getProperty("java.version", "unknown"),
        "cores=" + runtime.availableProcessors(),
        "max_memory_mb=" + (runtime.maxMemory() / (1024L * 1024L))
    };
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("doubledoors")) {
      return false;
    }

    if (args.length == 0) {
      sender.sendMessage(t("cmd.usage.main", label));
      return true;
    }

    if (args[0].equalsIgnoreCase("reload")) {
      if (!sender.hasPermission("doubledoors.reload")) {
        sender.sendMessage(t("cmd.no_permission"));
        return true;
      }

      reloadConfig();
      pluginConfig.reload();
      sqlStorage = null;
      playerPreferences = new PlayerPreferences(this);
      claimSettings = new ClaimSettings(this);
      initializeSqlIfEnabledAsync();
      translationManager.reload();
      sender.sendMessage(t("cmd.reload.success", translationManager.getActiveLanguage()));
      return true;
    }

    if (args[0].equalsIgnoreCase("toggle")) {
      if (!(sender instanceof Player player)) {
        sender.sendMessage(t("cmd.only_players.toggle", label));
        return true;
      }

      if (!sender.hasPermission("doubledoors.toggle")) {
        sender.sendMessage(t("cmd.no_permission"));
        return true;
      }

      // /doubledoors toggle [doors|gates|trapdoors]
      if (args.length >= 2) {
        UUID uuid = player.getUniqueId();
        switch (args[1].toLowerCase()) {
          case "doors" -> {
            boolean next = playerPreferences.toggleDoors(uuid);
            sender.sendMessage(next ? t("cmd.toggle.doors.enabled") : t("cmd.toggle.doors.disabled"));
          }
          case "gates" -> {
            boolean next = playerPreferences.toggleFenceGates(uuid);
            sender.sendMessage(next ? t("cmd.toggle.gates.enabled") : t("cmd.toggle.gates.disabled"));
          }
          case "trapdoors" -> {
            boolean next = playerPreferences.toggleTrapdoors(uuid);
            sender.sendMessage(next ? t("cmd.toggle.trapdoors.enabled") : t("cmd.toggle.trapdoors.disabled"));
          }
          default -> sender.sendMessage(t("cmd.usage.toggle", label));
        }
        return true;
      }

      boolean enabled = playerPreferences.toggleAll(player.getUniqueId());
      sender.sendMessage(enabled ? t("cmd.toggle.all.enabled") : t("cmd.toggle.all.disabled"));
      return true;
    }

    if (args[0].equalsIgnoreCase("server-toggle")) {
      if (!sender.hasPermission("doubledoors.server-toggle")) {
        sender.sendMessage(t("cmd.no_permission"));
        return true;
      }

      boolean nextState = !pluginConfig.isServerWideEnabled();
      pluginConfig.setServerWideEnabled(nextState);
      sender.sendMessage(nextState
          ? t("cmd.server_toggle.enabled")
          : t("cmd.server_toggle.disabled"));
      return true;
    }

    if (args[0].equalsIgnoreCase("grief")) {
      if (!(sender instanceof Player player)) {
        sender.sendMessage(t("cmd.only_players.grief", label));
        return true;
      }

      if (!sender.hasPermission("doubledoors.grief")) {
        sender.sendMessage(t("cmd.no_permission"));
        return true;
      }

      if (args.length < 2 || !args[1].equalsIgnoreCase("villagers")) {
        sender.sendMessage(t("cmd.usage.grief", label));
        return true;
      }

      Block standingBlock = player.getLocation().getBlock();
      long claimId = ProtectionCompat.getClaimIdAt(this, standingBlock);
      if (claimId < 0) {
        sender.sendMessage(t("cmd.grief.no_claim"));
        return true;
      }

      if (!ProtectionCompat.isClaimManagerAt(this, player, standingBlock)) {
        sender.sendMessage(t("cmd.grief.no_manage_permission"));
        return true;
      }

      boolean blocked = claimSettings.toggleVillagersBlocked(claimId);
      sender.sendMessage(blocked
          ? t("cmd.grief.villagers.blocked")
          : t("cmd.grief.villagers.allowed"));
      return true;
    }

    sender.sendMessage(t("cmd.usage.main", label));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> completions = new ArrayList<>();
    if (!command.getName().equalsIgnoreCase("doubledoors")) {
      return completions;
    }

    if (args.length == 1) {
      for (String sub : List.of("reload", "toggle", "server-toggle", "grief")) {
        if (sub.startsWith(args[0].toLowerCase())) {
          completions.add(sub);
        }
      }
    } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
      for (String sub : List.of("doors", "gates", "trapdoors")) {
        if (sub.startsWith(args[1].toLowerCase())) {
          completions.add(sub);
        }
      }
    } else if (args.length == 2 && args[0].equalsIgnoreCase("grief")) {
      if ("villagers".startsWith(args[1].toLowerCase())) {
        completions.add("villagers");
      }
    }
    return completions;
  }
}

