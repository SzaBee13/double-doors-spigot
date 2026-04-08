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

import dev.faststats.core.Metrics;
import dev.faststats.core.data.Metric;
import dev.faststats.velocity.VelocityMetrics;

/**
 * Velocity-side component that reports proxy heartbeat into shared SQL storage.
 */
@Plugin(
    id = "doubledoors-proxy",
    name = "DoubleDoorsProxy",
    version = "1.3.2",
    description = "Proxy companion plugin for DoubleDoors shared SQL detection",
    authors = {"SzaBee13"}
)
public final class DoubleDoorsProxy {
  private static final String FASTSTATS_TOKEN_PATTERN = "[a-z0-9]{32}";
  private static final String FASTSTATS_PROJECT_TOKEN = "883c734d766f7078fa4525e9c573c8af"; // This should be public since it only identifies the project, not individual servers.

  private final ProxyServer proxyServer;
  private final Logger logger;
  private final Path dataDirectory;
  private final VelocityMetrics.Factory metricsFactory;

  private ProxySqlClient sqlClient;
  private String proxyId;
  private boolean heartbeatEnabled;
  private Metrics metrics;

  /**
   * Creates the Velocity plugin instance.
   *
   * @param proxyServer Velocity proxy server
   * @param logger plugin logger
   * @param dataDirectory plugin data directory
   * @param metricsFactory FastStats metrics factory
   */
  @Inject
  public DoubleDoorsProxy(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory,
      VelocityMetrics.Factory metricsFactory) {
    this.proxyServer = proxyServer;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
    this.metricsFactory = metricsFactory;
  }

  /**
   * Initializes SQL heartbeat reporting once Velocity starts.
   *
   * @param event initialize event
   */
  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    Properties config = loadConfig();
    boolean anonymousTrackingEnabled = Boolean.parseBoolean(config.getProperty("enableAnonymousTracking", "true"));
    if (anonymousTrackingEnabled) {
      String token = normalizeFastStatsToken(FASTSTATS_PROJECT_TOKEN);
      if (token == null) {
        metrics = null;
        logger.warn("DoubleDoorsProxy anonymous tracking is enabled, but the built-in FastStats token is invalid;"
            + " metrics are disabled.");
      }

      VelocityMetrics.Factory factory = metricsFactory;
      if (Boolean.parseBoolean(config.getProperty("enableExtendedAnonymousTracking", "false"))) {
        factory = factory
            .addMetric(Metric.string("server_location", () -> getConfigValue(config, "trackingServerLocation")))
            .addMetric(Metric.stringArray("countries", () -> getCountries(config)))
            .addMetric(Metric.string("java_version", () -> System.getProperty("java.version", "unknown")))
            .addMetric(Metric.stringArray("system_statistics", this::getSystemStatistics));
      }

      if (token != null) {
        try {
          metrics = factory.token(token).create(this);
        } catch (RuntimeException e) {
          metrics = null;
          logger.warn("DoubleDoorsProxy FastStats could not be initialized; continuing without metrics.", e);
        }
      }
    } else {
      metrics = null;
      logger.info("DoubleDoorsProxy anonymous tracking is disabled by config.");
    }

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
    if (heartbeatEnabled && sqlClient != null) {
      try {
        writeHeartbeat();
      } finally {
        sqlClient.close();
      }
    }

    if (metrics != null) {
      metrics.shutdown();
      metrics = null;
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

  private String[] getCountries(Properties config) {
    String rawCountries = config.getProperty("trackingCountries", "").trim();
    if (rawCountries.isEmpty()) {
      return new String[0];
    }

    return java.util.Arrays.stream(rawCountries.split(","))
        .map(String::trim)
        .filter(country -> !country.isEmpty())
        .toArray(String[]::new);
  }

  private String getConfigValue(Properties config, String key) {
    String value = config.getProperty(key, "");
    return value == null ? "" : value.trim();
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
