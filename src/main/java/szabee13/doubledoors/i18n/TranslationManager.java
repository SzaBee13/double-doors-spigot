package szabee13.doubledoors.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.regex.Pattern;
import szabee13.doubledoors.DoubleDoors;
import szabee13.doubledoors.config.PluginConfig;

/**
 * Loads and resolves localized message strings for DoubleDoors.
 *
 * <p>Translations are read from JSON files with a flat key/value object shape.
 * Built-in files live under {@code /lang} inside the plugin jar. Server owners can
 * override or add language files by placing JSON files in
 * {@code plugins/DoubleDoors/lang}.</p>
 */
public final class TranslationManager {
  private static final String DEFAULT_LANGUAGE = "en_US";
  private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

  private final DoubleDoors plugin;
  private final PluginConfig pluginConfig;
  private final Map<String, String> defaultTranslations = new HashMap<>();
  private final Map<String, String> activeTranslations = new HashMap<>();

  private String activeLanguage = DEFAULT_LANGUAGE;

  /**
   * Creates a translation manager.
   *
   * @param plugin the plugin instance
   * @param pluginConfig the configuration wrapper
   */
  public TranslationManager(DoubleDoors plugin, PluginConfig pluginConfig) {
    this.plugin = plugin;
    this.pluginConfig = pluginConfig;
  }

  /**
   * Reloads translations from bundled and custom language files.
   */
  public void reload() {
    ensureLangFolder();

    defaultTranslations.clear();
    activeTranslations.clear();

    defaultTranslations.putAll(loadBundledLanguage(DEFAULT_LANGUAGE));
    activeTranslations.putAll(defaultTranslations);

    String requestedLanguage = sanitizeLanguageCode(pluginConfig.getLanguage());
    Map<String, String> requestedTranslations = loadRequestedLanguage(requestedLanguage);
    if (!requestedTranslations.isEmpty()) {
      activeTranslations.putAll(requestedTranslations);
      activeLanguage = requestedLanguage;
      return;
    }

    if (!DEFAULT_LANGUAGE.equals(requestedLanguage)) {
      plugin.getLogger().warning(
          "Language '" + requestedLanguage + "' was not found. Falling back to " + DEFAULT_LANGUAGE + ".");
    }
    activeLanguage = DEFAULT_LANGUAGE;
  }

  /**
   * Resolves a translation key into a message.
   *
   * @param key the translation key
   * @param args optional {@link String#format(String, Object...)} arguments
   * @return resolved message or the key itself if missing
   */
  public String tr(String key, Object... args) {
    String raw = activeTranslations.getOrDefault(key, defaultTranslations.getOrDefault(key, key));
    if (args.length == 0) {
      return raw;
    }
    try {
      return String.format(raw, args);
    } catch (IllegalFormatException ignored) {
      return raw;
    }
  }

  /**
   * Gets the currently active language code.
   *
   * @return active language code
   */
  public String getActiveLanguage() {
    return activeLanguage;
  }

  private void ensureLangFolder() {
    File dataFolder = plugin.getDataFolder();
    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
      plugin.getLogger().warning("Could not create plugin data folder: " + dataFolder.getAbsolutePath());
      return;
    }

    File langFolder = new File(dataFolder, "lang");
    if (!langFolder.exists() && !langFolder.mkdirs()) {
      plugin.getLogger().warning("Could not create language folder: " + langFolder.getAbsolutePath());
      return;
    }

    File defaultFile = new File(langFolder, DEFAULT_LANGUAGE + ".json");
    if (!defaultFile.exists()) {
      plugin.saveResource("lang/" + DEFAULT_LANGUAGE + ".json", false);
    }
  }

  private String sanitizeLanguageCode(String configured) {
    if (configured == null || configured.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    String trimmed = configured.trim();
    if (!LANGUAGE_PATTERN.matcher(trimmed).matches()) {
      return DEFAULT_LANGUAGE;
    }
    return trimmed;
  }

  private Map<String, String> loadRequestedLanguage(String languageCode) {
    File customFile = new File(new File(plugin.getDataFolder(), "lang"), languageCode + ".json");
    if (customFile.exists()) {
      Map<String, String> customTranslations = loadFromFile(customFile);
      if (!customTranslations.isEmpty()) {
        return customTranslations;
      }
      plugin.getLogger().warning("Language file exists but is empty/invalid: " + customFile.getAbsolutePath());
    }

    return loadBundledLanguage(languageCode);
  }

  private Map<String, String> loadBundledLanguage(String languageCode) {
    String resourcePath = "lang/" + languageCode + ".json";
    try (InputStream stream = plugin.getResource(resourcePath)) {
      if (stream == null) {
        return Map.of();
      }
      return parseJson(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), resourcePath);
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to read bundled language file " + resourcePath + ": " + e.getMessage());
      return Map.of();
    }
  }

  private Map<String, String> loadFromFile(File file) {
    try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      return parseJson(reader, file.getAbsolutePath());
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to read language file " + file.getAbsolutePath() + ": " + e.getMessage());
      return Map.of();
    }
  }

  private Map<String, String> parseJson(BufferedReader reader, String sourceName) {
    try {
      JsonElement rootElement = JsonParser.parseReader(reader);
      if (!rootElement.isJsonObject()) {
        plugin.getLogger().warning("Language source " + sourceName + " does not contain a JSON object root.");
        return Map.of();
      }

      JsonObject object = rootElement.getAsJsonObject();
      Map<String, String> result = new HashMap<>();
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
        JsonElement value = entry.getValue();
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
          result.put(entry.getKey(), value.getAsString());
        }
      }
      return result;
    } catch (JsonParseException | IllegalStateException ex) {
      plugin.getLogger().warning("Invalid JSON in language source " + sourceName + ": " + ex.getMessage());
      return Map.of();
    }
  }
}
