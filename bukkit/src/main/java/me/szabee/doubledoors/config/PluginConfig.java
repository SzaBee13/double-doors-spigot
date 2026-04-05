package me.szabee.doubledoors.config;

import me.szabee.doubledoors.DoubleDoors;

/**
 * Configuration wrapper for DoubleDoors.
 */
public final class PluginConfig {
  private final DoubleDoors plugin;

  private boolean enableRecursiveOpening;
  private int recursiveOpeningMaxBlocksDistance;
  private boolean enableDoors;
  private boolean enableFenceGates;
  private boolean enableTrapdoors;
  private boolean enableVillagerLinkedDoors;
  private boolean serverWideEnabled;
  private String language;
  private boolean sqlEnabled;
  private String sqlJdbcUrl;
  private String sqlUsername;
  private String sqlPassword;
  private boolean migrateYamlToSql;
  private long proxyHeartbeatMaxAgeMillis;

  /**
   * Creates and loads a plugin config wrapper.
   *
   * @param plugin the plugin instance
   */
  public PluginConfig(DoubleDoors plugin) {
    this.plugin = plugin;
    reload();
  }

  /**
   * Reloads config values from config.yml.
   */
  public void reload() {
    enableRecursiveOpening = plugin.getConfig().getBoolean("enableRecursiveOpening", true);
    recursiveOpeningMaxBlocksDistance = plugin.getConfig().getInt("recursiveOpeningMaxBlocksDistance", 10);
    if (recursiveOpeningMaxBlocksDistance < 1) {
      recursiveOpeningMaxBlocksDistance = 1;
    }
    if (recursiveOpeningMaxBlocksDistance > 32) {
      recursiveOpeningMaxBlocksDistance = 32;
    }

    enableDoors = plugin.getConfig().getBoolean("enableDoors", true);
    enableFenceGates = plugin.getConfig().getBoolean("enableFenceGates", true);
    enableTrapdoors = plugin.getConfig().getBoolean("enableTrapdoors", true);
    enableVillagerLinkedDoors = plugin.getConfig().getBoolean("enableVillagerLinkedDoors", true);
    serverWideEnabled = plugin.getConfig().getBoolean("serverWideEnabled", true);

    sqlEnabled = plugin.getConfig().getBoolean("sql.enabled", false);
    sqlJdbcUrl = plugin.getConfig().getString("sql.jdbcUrl", "jdbc:sqlite:plugins/DoubleDoors/doubledoors.db");
    if (sqlJdbcUrl == null || sqlJdbcUrl.isBlank()) {
      sqlJdbcUrl = "jdbc:sqlite:plugins/DoubleDoors/doubledoors.db";
    }
    sqlUsername = plugin.getConfig().getString("sql.username", "");
    if (sqlUsername == null) {
      sqlUsername = "";
    }
    sqlPassword = plugin.getConfig().getString("sql.password", "");
    if (sqlPassword == null) {
      sqlPassword = "";
    }
    migrateYamlToSql = plugin.getConfig().getBoolean("sql.migrateFromYaml", true);
    long heartbeatSeconds = plugin.getConfig().getLong("sql.proxyHeartbeatMaxAgeSeconds", 180L);
    if (heartbeatSeconds < 15L) {
      heartbeatSeconds = 15L;
    }
    proxyHeartbeatMaxAgeMillis = heartbeatSeconds * 1000L;

    String configuredLanguage = plugin.getConfig().getString("language", "en_US");
    if (configuredLanguage == null || configuredLanguage.isBlank()) {
      configuredLanguage = "en_US";
    }
    language = configuredLanguage.trim();
  }

  /**
   * Gets whether recursive opening is enabled.
   *
   * @return true when recursive opening is enabled
   */
  public boolean isEnableRecursiveOpening() {
    return enableRecursiveOpening;
  }

  /**
   * Gets the recursive max distance.
   *
   * @return max recursive distance between 1 and 32
   */
  public int getRecursiveOpeningMaxBlocksDistance() {
    return recursiveOpeningMaxBlocksDistance;
  }

  /**
   * Gets whether door support is enabled.
   *
   * @return true when door support is enabled
   */
  public boolean isEnableDoors() {
    return enableDoors;
  }

  /**
   * Gets whether fence gate support is enabled.
   *
   * @return true when fence gate support is enabled
   */
  public boolean isEnableFenceGates() {
    return enableFenceGates;
  }

  /**
   * Gets whether trapdoor support is enabled.
   *
   * @return true when trapdoor support is enabled
   */
  public boolean isEnableTrapdoors() {
    return enableTrapdoors;
  }

  /**
   * Gets whether villager-triggered linked-door behavior is enabled.
   *
   * @return true when villager linked-door behavior is enabled
   */
  public boolean isEnableVillagerLinkedDoors() {
    return enableVillagerLinkedDoors;
  }

  /**
   * Gets whether linked opening is enabled globally for the server.
   *
   * @return true when server-wide behavior is enabled
   */
  public boolean isServerWideEnabled() {
    return serverWideEnabled;
  }

  /**
   * Gets the configured plugin language code.
   *
   * @return language code such as {@code en_US}
   */
  public String getLanguage() {
    return language;
  }

  /**
   * Gets whether SQL-backed storage is enabled.
   *
   * @return true when SQL storage should be used
   */
  public boolean isSqlEnabled() {
    return sqlEnabled;
  }

  /**
   * Gets the JDBC URL used by both Bukkit and proxy modules.
   *
   * @return JDBC URL
   */
  public String getSqlJdbcUrl() {
    return sqlJdbcUrl;
  }

  /**
   * Gets the SQL username.
   *
   * @return SQL username, or empty string
   */
  public String getSqlUsername() {
    return sqlUsername;
  }

  /**
   * Gets the SQL password.
   *
   * @return SQL password, or empty string
   */
  public String getSqlPassword() {
    return sqlPassword;
  }

  /**
   * Gets whether YAML data should be migrated to SQL on startup.
   *
   * @return true when one-time migration should run if needed
   */
  public boolean isMigrateYamlToSql() {
    return migrateYamlToSql;
  }

  /**
   * Gets the maximum accepted age for proxy heartbeats in milliseconds.
   *
   * @return heartbeat max age in milliseconds
   */
  public long getProxyHeartbeatMaxAgeMillis() {
    return proxyHeartbeatMaxAgeMillis;
  }

  /**
   * Sets and persists whether linked opening is enabled globally for the server.
   *
   * @param enabled true to enable globally, false to disable globally
   */
  public void setServerWideEnabled(boolean enabled) {
    serverWideEnabled = enabled;
    plugin.getConfig().set("serverWideEnabled", enabled);
    plugin.saveConfig();
  }
}

