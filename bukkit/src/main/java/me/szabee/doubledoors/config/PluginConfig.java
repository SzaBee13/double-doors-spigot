package me.szabee.doubledoors.config;

import java.util.ArrayList;
import java.util.List;

import me.szabee.doubledoors.DoubleDoors;
import me.szabee.doubledoors.util.SchedulerBridge;

/**
 * Configuration wrapper for DoubleDoors.
 */
public final class PluginConfig {
  private final DoubleDoors plugin;
  private volatile ConfigSnapshot snapshot = ConfigSnapshot.defaults();

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
    boolean enableRecursiveOpening = plugin.getConfig().getBoolean("enableRecursiveOpening", true);
    int recursiveOpeningMaxBlocksDistance = plugin.getConfig().getInt("recursiveOpeningMaxBlocksDistance", 10);
    if (recursiveOpeningMaxBlocksDistance < 1) {
      recursiveOpeningMaxBlocksDistance = 1;
    }
    if (recursiveOpeningMaxBlocksDistance > 32) {
      recursiveOpeningMaxBlocksDistance = 32;
    }

    boolean enableDoors = plugin.getConfig().getBoolean("enableDoors", true);
    boolean enableFenceGates = plugin.getConfig().getBoolean("enableFenceGates", true);
    boolean enableTrapdoors = plugin.getConfig().getBoolean("enableTrapdoors", true);
    boolean enableVillagerLinkedDoors = plugin.getConfig().getBoolean("enableVillagerLinkedDoors", true);
    boolean serverWideEnabled = plugin.getConfig().getBoolean("serverWideEnabled", true);
    boolean enableAnonymousTracking = plugin.getConfig().getBoolean("enableAnonymousTracking", true);
    boolean enableExtendedAnonymousTracking = plugin.getConfig().getBoolean("enableExtendedAnonymousTracking", false);

    List<String> configuredCountries = plugin.getConfig().getStringList("trackingCountries");
    List<String> trackingCountries = new ArrayList<>();
    for (String country : configuredCountries) {
      if (country != null && !country.isBlank()) {
        trackingCountries.add(country.trim());
      }
    }

    String trackingServerLocation = plugin.getConfig().getString("trackingServerLocation", "");
    if (trackingServerLocation == null) {
      trackingServerLocation = "";
    }
    trackingServerLocation = trackingServerLocation.trim();

    boolean sqlEnabled = plugin.getConfig().getBoolean("sql.enabled", false);
    String sqlJdbcUrl = plugin.getConfig().getString("sql.jdbcUrl", "jdbc:sqlite:plugins/DoubleDoors/doubledoors.db");
    if (sqlJdbcUrl == null || sqlJdbcUrl.isBlank()) {
      sqlJdbcUrl = "jdbc:sqlite:plugins/DoubleDoors/doubledoors.db";
    }
    String sqlUsername = plugin.getConfig().getString("sql.username", "");
    if (sqlUsername == null) {
      sqlUsername = "";
    }
    String sqlPassword = plugin.getConfig().getString("sql.password", "");
    if (sqlPassword == null) {
      sqlPassword = "";
    }
    boolean migrateYamlToSql = plugin.getConfig().getBoolean("sql.migrateFromYaml", true);
    long heartbeatSeconds = plugin.getConfig().getLong("sql.proxyHeartbeatMaxAgeSeconds", 180L);
    if (heartbeatSeconds < 15L) {
      heartbeatSeconds = 15L;
    }
    long proxyHeartbeatMaxAgeMillis = heartbeatSeconds * 1000L;

    String configuredLanguage = plugin.getConfig().getString("language", "en_US");
    if (configuredLanguage == null || configuredLanguage.isBlank()) {
      configuredLanguage = "en_US";
    }
    String language = configuredLanguage.trim();

    snapshot = new ConfigSnapshot(
        enableRecursiveOpening,
        recursiveOpeningMaxBlocksDistance,
        enableDoors,
        enableFenceGates,
        enableTrapdoors,
        enableVillagerLinkedDoors,
        serverWideEnabled,
        enableAnonymousTracking,
        enableExtendedAnonymousTracking,
        List.copyOf(trackingCountries),
        trackingServerLocation,
        language,
        sqlEnabled,
        sqlJdbcUrl,
        sqlUsername,
        sqlPassword,
        migrateYamlToSql,
        proxyHeartbeatMaxAgeMillis);
  }

  /**
   * Gets whether recursive opening is enabled.
   *
   * @return true when recursive opening is enabled
   */
  public boolean isEnableRecursiveOpening() {
    return snapshot.enableRecursiveOpening();
  }

  /**
   * Gets the recursive max distance.
   *
   * @return max recursive distance between 1 and 32
   */
  public int getRecursiveOpeningMaxBlocksDistance() {
    return snapshot.recursiveOpeningMaxBlocksDistance();
  }

  /**
   * Gets whether door support is enabled.
   *
   * @return true when door support is enabled
   */
  public boolean isEnableDoors() {
    return snapshot.enableDoors();
  }

  /**
   * Gets whether fence gate support is enabled.
   *
   * @return true when fence gate support is enabled
   */
  public boolean isEnableFenceGates() {
    return snapshot.enableFenceGates();
  }

  /**
   * Gets whether trapdoor support is enabled.
   *
   * @return true when trapdoor support is enabled
   */
  public boolean isEnableTrapdoors() {
    return snapshot.enableTrapdoors();
  }

  /**
   * Gets whether villager-triggered linked-door behavior is enabled.
   *
   * @return true when villager linked-door behavior is enabled
   */
  public boolean isEnableVillagerLinkedDoors() {
    return snapshot.enableVillagerLinkedDoors();
  }

  /**
   * Gets whether linked opening is enabled globally for the server.
   *
   * @return true when server-wide behavior is enabled
   */
  public boolean isServerWideEnabled() {
    return snapshot.serverWideEnabled();
  }

  /**
   * Gets whether anonymous tracking is enabled.
   *
   * @return true when FastStats tracking is enabled
   */
  public boolean isEnableAnonymousTracking() {
    return snapshot.enableAnonymousTracking();
  }

  /**
   * Gets whether extended anonymous tracking is enabled.
   *
   * @return true when extra telemetry should be sent
   */
  public boolean isEnableExtendedAnonymousTracking() {
    return snapshot.enableExtendedAnonymousTracking();
  }

  /**
   * Gets the configured countries associated with this server.
   *
   * @return immutable list of configured country codes
   */
  public List<String> getTrackingCountries() {
    return snapshot.trackingCountries();
  }

  /**
   * Gets the configured server location label.
   *
   * @return trimmed location label, or empty string
   */
  public String getTrackingServerLocation() {
    return snapshot.trackingServerLocation();
  }

  /**
   * Gets the configured plugin language code.
   *
   * @return language code such as {@code en_US}
   */
  public String getLanguage() {
    return snapshot.language();
  }

  /**
   * Gets whether SQL-backed storage is enabled.
   *
   * @return true when SQL storage should be used
   */
  public boolean isSqlEnabled() {
    return snapshot.sqlEnabled();
  }

  /**
   * Gets the JDBC URL used by both Bukkit and proxy modules.
   *
   * @return JDBC URL
   */
  public String getSqlJdbcUrl() {
    return snapshot.sqlJdbcUrl();
  }

  /**
   * Gets the SQL username.
   *
   * @return SQL username, or empty string
   */
  public String getSqlUsername() {
    return snapshot.sqlUsername();
  }

  /**
   * Gets the SQL password.
   *
   * @return SQL password, or empty string
   */
  public String getSqlPassword() {
    return snapshot.sqlPassword();
  }

  /**
   * Gets whether YAML data should be migrated to SQL on startup.
   *
   * @return true when one-time migration should run if needed
   */
  public boolean isMigrateYamlToSql() {
    return snapshot.migrateYamlToSql();
  }

  /**
   * Gets the maximum accepted age for proxy heartbeats in milliseconds.
   *
   * @return heartbeat max age in milliseconds
   */
  public long getProxyHeartbeatMaxAgeMillis() {
    return snapshot.proxyHeartbeatMaxAgeMillis();
  }

  /**
   * Sets and persists whether linked opening is enabled globally for the server.
   *
   * @param enabled true to enable globally, false to disable globally
   */
  public void setServerWideEnabled(boolean enabled) {
    snapshot = snapshot.withServerWideEnabled(enabled);
    plugin.getConfig().set("serverWideEnabled", enabled);
    SchedulerBridge.runAsync(plugin, plugin::saveConfig);
  }

  private record ConfigSnapshot(
      boolean enableRecursiveOpening,
      int recursiveOpeningMaxBlocksDistance,
      boolean enableDoors,
      boolean enableFenceGates,
      boolean enableTrapdoors,
      boolean enableVillagerLinkedDoors,
      boolean serverWideEnabled,
      boolean enableAnonymousTracking,
      boolean enableExtendedAnonymousTracking,
      List<String> trackingCountries,
      String trackingServerLocation,
      String language,
      boolean sqlEnabled,
      String sqlJdbcUrl,
      String sqlUsername,
      String sqlPassword,
      boolean migrateYamlToSql,
      long proxyHeartbeatMaxAgeMillis
  ) {
    private static ConfigSnapshot defaults() {
      return new ConfigSnapshot(
          true,
          10,
          true,
          true,
          true,
          true,
          true,
          true,
          false,
          List.of(),
          "",
          "en_US",
          false,
          "jdbc:sqlite:plugins/DoubleDoors/doubledoors.db",
          "",
          "",
          true,
          180_000L);
    }

    private ConfigSnapshot withServerWideEnabled(boolean enabled) {
      return new ConfigSnapshot(
          enableRecursiveOpening,
          recursiveOpeningMaxBlocksDistance,
          enableDoors,
          enableFenceGates,
          enableTrapdoors,
          enableVillagerLinkedDoors,
          enabled,
          enableAnonymousTracking,
          enableExtendedAnonymousTracking,
          trackingCountries,
          trackingServerLocation,
          language,
          sqlEnabled,
          sqlJdbcUrl,
          sqlUsername,
          sqlPassword,
          migrateYamlToSql,
          proxyHeartbeatMaxAgeMillis);
    }
  }
}

