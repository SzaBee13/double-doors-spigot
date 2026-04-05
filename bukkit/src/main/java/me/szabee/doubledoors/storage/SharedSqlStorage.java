package me.szabee.doubledoors.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.szabee.doubledoors.DoubleDoors;
import me.szabee.doubledoors.config.PluginConfig;

/**
 * Shared SQL storage used by Bukkit and proxy components.
 */
public final class SharedSqlStorage {

  private final DoubleDoors plugin;
  private final String jdbcUrl;
  private final String username;
  private final String password;

  /**
   * Creates an SQL storage wrapper from plugin config.
   *
   * @param plugin the plugin instance
   * @param config the plugin config
   */
  public SharedSqlStorage(DoubleDoors plugin, PluginConfig config) {
    this.plugin = plugin;
    this.jdbcUrl = config.getSqlJdbcUrl();
    this.username = config.getSqlUsername();
    this.password = config.getSqlPassword();
  }

  /**
   * Creates required tables if they do not already exist.
   */
  public void initializeSchema() {
    try {
      executeStatement("CREATE TABLE IF NOT EXISTS dd_player_preferences ("
        + "player_uuid VARCHAR(36) PRIMARY KEY,"
        + "enabled BOOLEAN NOT NULL,"
        + "enable_doors BOOLEAN NOT NULL,"
        + "enable_fence_gates BOOLEAN NOT NULL,"
        + "enable_trapdoors BOOLEAN NOT NULL"
        + ")");

      executeStatement("CREATE TABLE IF NOT EXISTS dd_claim_settings ("
        + "claim_id BIGINT PRIMARY KEY,"
        + "villagers_blocked BOOLEAN NOT NULL"
        + ")");

      executeStatement("CREATE TABLE IF NOT EXISTS dd_proxy_presence ("
        + "proxy_id VARCHAR(128) PRIMARY KEY,"
        + "platform VARCHAR(32) NOT NULL,"
        + "last_seen_epoch_ms BIGINT NOT NULL"
        + ")");

      executeStatement("CREATE TABLE IF NOT EXISTS dd_meta ("
        + "meta_key VARCHAR(128) PRIMARY KEY,"
        + "meta_value VARCHAR(255) NOT NULL"
        + ")");
    } catch (SQLException e) {
      throw new IllegalStateException("Could not initialize SQL schema", e);
    }
  }

  /**
   * Loads all player preferences from SQL.
   *
   * @return map keyed by player UUID
   */
  public Map<UUID, SqlPlayerPref> loadAllPlayerPreferences() {
    Map<UUID, SqlPlayerPref> result = new HashMap<>();
    String sql = "SELECT player_uuid, enabled, enable_doors, enable_fence_gates, enable_trapdoors FROM dd_player_preferences";
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        String rawUuid = rs.getString("player_uuid");
        try {
          UUID uuid = UUID.fromString(rawUuid);
          result.put(uuid, new SqlPlayerPref(
              rs.getBoolean("enabled"),
              rs.getBoolean("enable_doors"),
              rs.getBoolean("enable_fence_gates"),
              rs.getBoolean("enable_trapdoors")));
        } catch (IllegalArgumentException ignored) {
          // Ignore malformed UUID rows so startup keeps working.
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not load player preferences from SQL: %s", e.getMessage()));
    }
    return result;
  }

  /**
   * Saves one player preference row.
   *
   * @param uuid the player UUID
   * @param pref the preference values
   */
  public boolean savePlayerPreference(UUID uuid, SqlPlayerPref pref) {
    String updateSql = "UPDATE dd_player_preferences SET enabled=?, enable_doors=?, enable_fence_gates=?, "
        + "enable_trapdoors=? WHERE player_uuid=?";
    try (Connection connection = openConnection();
         PreparedStatement update = connection.prepareStatement(updateSql)) {
      update.setBoolean(1, pref.enabled());
      update.setBoolean(2, pref.enableDoors());
      update.setBoolean(3, pref.enableFenceGates());
      update.setBoolean(4, pref.enableTrapdoors());
      update.setString(5, uuid.toString());
      int changed = update.executeUpdate();
      if (changed == 0) {
        String insertSql = "INSERT INTO dd_player_preferences (player_uuid, enabled, enable_doors, "
            + "enable_fence_gates, enable_trapdoors) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
          insert.setString(1, uuid.toString());
          insert.setBoolean(2, pref.enabled());
          insert.setBoolean(3, pref.enableDoors());
          insert.setBoolean(4, pref.enableFenceGates());
          insert.setBoolean(5, pref.enableTrapdoors());
          insert.executeUpdate();
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not save player preference to SQL: %s", e.getMessage()));
      return false;
    }
    return true;
  }

  /**
   * Loads all claim IDs where villagers are blocked.
   *
   * @return blocked claim IDs
   */
  public Set<Long> loadVillagersBlockedClaims() {
    Set<Long> blocked = new HashSet<>();
    String sql = "SELECT claim_id FROM dd_claim_settings WHERE villagers_blocked=?";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setBoolean(1, true);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          blocked.add(rs.getLong("claim_id"));
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not load claim settings from SQL: %s", e.getMessage()));
    }
    return blocked;
  }

  /**
   * Persists villager-blocked flag for one claim.
   *
   * @param claimId claim ID
   * @param blocked true to block villagers in this claim
   */
  public boolean setVillagersBlocked(long claimId, boolean blocked) {
    String updateSql = "UPDATE dd_claim_settings SET villagers_blocked=? WHERE claim_id=?";
    try (Connection connection = openConnection();
         PreparedStatement update = connection.prepareStatement(updateSql)) {
      update.setBoolean(1, blocked);
      update.setLong(2, claimId);
      int changed = update.executeUpdate();
      if (changed == 0) {
        String insertSql = "INSERT INTO dd_claim_settings (claim_id, villagers_blocked) VALUES (?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
          insert.setLong(1, claimId);
          insert.setBoolean(2, blocked);
          insert.executeUpdate();
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not save claim setting to SQL: %s", e.getMessage()));
      return false;
    }
    return true;
  }

  /**
   * Returns whether a migration key has been marked as completed.
   *
   * @param migrationKey migration identifier
   * @return true if migration is already marked complete
   */
  public boolean isMigrationDone(String migrationKey) {
    String sql = "SELECT meta_value FROM dd_meta WHERE meta_key=?";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, migrationKey);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return false;
        }
        return "done".equalsIgnoreCase(rs.getString("meta_value"));
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not read SQL migration metadata: %s", e.getMessage()));
      return false;
    }
  }

  /**
   * Marks a migration key as completed.
   *
   * @param migrationKey migration identifier
   */
  public void markMigrationDone(String migrationKey) {
    String updateSql = "UPDATE dd_meta SET meta_value='done' WHERE meta_key=?";
    try (Connection connection = openConnection();
         PreparedStatement update = connection.prepareStatement(updateSql)) {
      update.setString(1, migrationKey);
      int changed = update.executeUpdate();
      if (changed == 0) {
        String insertSql = "INSERT INTO dd_meta (meta_key, meta_value) VALUES (?, 'done')";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
          insert.setString(1, migrationKey);
          insert.executeUpdate();
        }
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not write SQL migration metadata: %s", e.getMessage()));
    }
  }

  /**
   * Returns whether a proxy heartbeat has been seen recently enough.
   *
   * @param maxAgeMillis max accepted age in milliseconds
   * @return true when a recent proxy heartbeat exists
   */
  public boolean hasRecentProxyHeartbeat(long maxAgeMillis) {
    long threshold = System.currentTimeMillis() - maxAgeMillis;
    String sql = "SELECT 1 FROM dd_proxy_presence WHERE last_seen_epoch_ms >= ? LIMIT 1";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, threshold);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException e) {
      plugin.getLogger().warning(String.format("Could not read proxy heartbeat from SQL: %s", e.getMessage()));
    }
    return false;
  }

  private Connection openConnection() throws SQLException {
    if (username.isBlank()) {
      return DriverManager.getConnection(jdbcUrl);
    }
    return DriverManager.getConnection(jdbcUrl, username, password);
  }

  private void executeStatement(String sql) throws SQLException {
    try (Connection connection = openConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  /**
   * SQL row model for per-player preference values.
   *
   * @param enabled global toggle
   * @param enableDoors door toggle
   * @param enableFenceGates fence gate toggle
   * @param enableTrapdoors trapdoor toggle
   */
  public record SqlPlayerPref(
      boolean enabled,
      boolean enableDoors,
      boolean enableFenceGates,
      boolean enableTrapdoors
  ) {
  }
}
