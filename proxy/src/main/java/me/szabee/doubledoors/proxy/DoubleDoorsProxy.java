package me.szabee.doubledoors.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

/**
 * Velocity-side component that reports proxy heartbeat into shared SQL storage.
 */
@Plugin(
    id = "doubledoors-proxy",
    name = "DoubleDoorsProxy",
    version = "1.3.0",
    description = "Proxy companion plugin for DoubleDoors shared SQL detection",
    authors = {"SzaBee13"}
)
public final class DoubleDoorsProxy {

  private final ProxyServer proxyServer;
  private final Logger logger;
  private final Path dataDirectory;

  private ProxySqlClient sqlClient;
  private String proxyId;
  private boolean heartbeatEnabled;

  /**
   * Creates the Velocity plugin instance.
   *
   * @param proxyServer Velocity proxy server
   * @param logger plugin logger
   * @param dataDirectory plugin data directory
   */
  @Inject
  public DoubleDoorsProxy(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
    this.proxyServer = proxyServer;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  /**
   * Initializes SQL heartbeat reporting once Velocity starts.
   *
   * @param event initialize event
   */
  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    Properties config = loadConfig();
    boolean sqlEnabled = Boolean.parseBoolean(config.getProperty("sql.enabled", "false"));
    if (!sqlEnabled) {
      logger.info("DoubleDoorsProxy SQL heartbeat is disabled by config.");
      return;
    }

    boolean geyserPresent = isPluginPresent("geyser", "geyser-velocity");
    boolean floodgatePresent = isPluginPresent("floodgate", "floodgate-velocity");
    if (!geyserPresent && !floodgatePresent) {
      logger.info("DoubleDoorsProxy did not detect Geyser/Floodgate on this proxy.");
      return;
    }

    String jdbcUrl = config.getProperty("sql.jdbcUrl", "jdbc:sqlite:plugins/DoubleDoors/doubledoors.db").trim();
    String username = config.getProperty("sql.username", "").trim();
    String password = config.getProperty("sql.password", "");
    proxyId = config.getProperty("sql.proxyId", "velocity-main").trim();
    if (proxyId.isEmpty()) {
      proxyId = "velocity-main";
    }

    long heartbeatSeconds;
    try {
      heartbeatSeconds = Long.parseLong(config.getProperty("sql.heartbeatSeconds", "30"));
    } catch (NumberFormatException ignored) {
      heartbeatSeconds = 30L;
    }
    if (heartbeatSeconds < 5L) {
      heartbeatSeconds = 5L;
    }

    sqlClient = new ProxySqlClient(jdbcUrl, username, password);
    long repeatSeconds = heartbeatSeconds;
    proxyServer.getScheduler().buildTask(this, () -> {
      try {
        sqlClient.initializeSchema();
        writeHeartbeat();
        heartbeatEnabled = true;
        proxyServer.getScheduler()
            .buildTask(this, this::writeHeartbeat)
            .repeat(repeatSeconds, TimeUnit.SECONDS)
            .schedule();
        logger.info("DoubleDoorsProxy heartbeat enabled for proxyId='{}' every {}s.", proxyId, repeatSeconds);
      } catch (SQLException e) {
        logger.warn("DoubleDoorsProxy could not initialize SQL heartbeat: {}", e.getMessage());
      }
    }).schedule();
  }

  /**
   * Writes a final heartbeat when the proxy shuts down and closes the connection pool.
   *
   * @param event shutdown event
   */
  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    if (!heartbeatEnabled || sqlClient == null) {
      return;
    }
    try {
      writeHeartbeat();
    } finally {
      sqlClient.close();
    }
  }

  private boolean isPluginPresent(String... ids) {
    for (String id : ids) {
      Optional<?> plugin = proxyServer.getPluginManager().getPlugin(id);
      if (plugin.isPresent()) {
        return true;
      }
    }
    return false;
  }

  private void writeHeartbeat() {
    if (sqlClient == null) {
      return;
    }
    try {
      sqlClient.upsertHeartbeat(proxyId, "velocity", System.currentTimeMillis());
    } catch (SQLException e) {
      logger.warn("DoubleDoorsProxy heartbeat write failed: {}", e.getMessage());
    }
  }

  private Properties loadConfig() {
    Properties properties = new Properties();
    try {
      Files.createDirectories(dataDirectory);
      Path configFile = dataDirectory.resolve("config.properties");
      if (Files.notExists(configFile)) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("proxy-config.properties")) {
          if (in != null) {
            try (OutputStream out = Files.newOutputStream(configFile)) {
              in.transferTo(out);
            }
          }
        }
      }

      if (Files.exists(configFile)) {
        try (InputStream in = Files.newInputStream(configFile)) {
          properties.load(in);
        }
      }
    } catch (IOException e) {
      logger.warn("DoubleDoorsProxy could not read config.properties: {}", e.getMessage());
    }
    return properties;
  }
}
