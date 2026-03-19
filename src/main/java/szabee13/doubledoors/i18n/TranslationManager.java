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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
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
  private static final String DEFAULTS_RESOURCE_PATH = "lang/defaults.json";
  private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

  private final DoubleDoors plugin;
  private final PluginConfig pluginConfig;
  private final Map<String, String> defaultTranslations = new HashMap<>();
  private final Map<String, String> activeTranslations = new HashMap<>();
  private final Map<String, String> languageAliases = new HashMap<>();
  private final Map<String, String> bundledLanguagePaths = new HashMap<>();

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
    loadDefaultsMetadata();
    ensureLangFolder();

    defaultTranslations.clear();
    activeTranslations.clear();

    defaultTranslations.putAll(loadBundledLanguage(DEFAULT_LANGUAGE));
    activeTranslations.putAll(defaultTranslations);

    String requestedLanguage = resolveLanguageCode(sanitizeLanguageCode(pluginConfig.getLanguage()));
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

    String defaultRelativePath = bundledLanguagePaths.getOrDefault(DEFAULT_LANGUAGE, DEFAULT_LANGUAGE + ".json");
    File defaultFile = new File(langFolder, defaultRelativePath);
    if (!defaultFile.exists()) {
      plugin.saveResource("lang/" + defaultRelativePath, false);
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

  private String resolveLanguageCode(String requestedLanguage) {
    String normalized = normalizeLanguageCode(requestedLanguage);
    String aliasMatch = languageAliases.get(normalized);
    if (aliasMatch != null && !aliasMatch.isBlank()) {
      return aliasMatch;
    }
    return requestedLanguage;
  }

  private String normalizeLanguageCode(String languageCode) {
    return languageCode.toLowerCase().replace('-', '_');
  }

  private Map<String, String> loadRequestedLanguage(String languageCode) {
    File customFile = resolveCustomLanguageFile(languageCode);
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
    for (String resourcePath : getBundledResourceCandidates(languageCode)) {
      try (InputStream stream = plugin.getResource(resourcePath)) {
        if (stream == null) {
          continue;
        }
        return parseJson(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), resourcePath);
      } catch (IOException e) {
        plugin.getLogger().warning("Failed to read bundled language file " + resourcePath + ": " + e.getMessage());
        return Map.of();
      }
    }
    return Map.of();
  }

  private Map<String, String> loadFromFile(File file) {
    try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      return parseJson(reader, file.getAbsolutePath());
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to read language file " + file.getAbsolutePath() + ": " + e.getMessage());
      return Map.of();
    }
  }

  private File resolveCustomLanguageFile(String languageCode) {
    File langFolder = new File(plugin.getDataFolder(), "lang");
    List<File> candidates = new ArrayList<>();
    candidates.add(new File(langFolder, languageCode + ".json"));

    String relativeBundledPath = bundledLanguagePaths.get(languageCode);
    if (relativeBundledPath != null && !relativeBundledPath.isBlank()) {
      candidates.add(new File(langFolder, relativeBundledPath));
    }

    for (File candidate : candidates) {
      if (candidate.exists() && candidate.isFile()) {
        return candidate;
      }
    }

    try {
      Path langPath = langFolder.toPath();
      if (!Files.exists(langPath)) {
        return candidates.getFirst();
      }

      try (var paths = Files.walk(langPath)) {
        return paths
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().equalsIgnoreCase(languageCode + ".json"))
            .map(Path::toFile)
            .findFirst()
            .orElse(candidates.getFirst());
      }
    } catch (IOException ex) {
      plugin.getLogger().warning("Failed to scan language folder: " + ex.getMessage());
      return candidates.getFirst();
    }
  }

  private List<String> getBundledResourceCandidates(String languageCode) {
    List<String> candidates = new ArrayList<>();
    candidates.add("lang/" + languageCode + ".json");

    String relativeBundledPath = bundledLanguagePaths.get(languageCode);
    if (relativeBundledPath != null && !relativeBundledPath.isBlank()) {
      String normalizedPath = relativeBundledPath.replace('\\', '/');
      if (!normalizedPath.startsWith("lang/")) {
        normalizedPath = "lang/" + normalizedPath;
      }
      candidates.add(normalizedPath);
    }

    return candidates;
  }

  private void loadDefaultsMetadata() {
    languageAliases.clear();
    bundledLanguagePaths.clear();

    // Keep critical aliases available even if defaults.json is missing.
    languageAliases.put("en", DEFAULT_LANGUAGE);

    try (InputStream stream = plugin.getResource(DEFAULTS_RESOURCE_PATH)) {
      if (stream == null) {
        return;
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        JsonElement rootElement = JsonParser.parseReader(reader);
        if (!rootElement.isJsonObject()) {
          return;
        }

        JsonObject root = rootElement.getAsJsonObject();
        loadStringMap(root, "languageAliases", languageAliases, true);
        loadStringMap(root, "bundledLanguagePaths", bundledLanguagePaths, false);
      }
    } catch (IOException | JsonParseException | IllegalStateException ex) {
      plugin.getLogger().warning("Failed to load language defaults metadata: " + ex.getMessage());
    }
  }

  private void loadStringMap(
      JsonObject root,
      String propertyName,
      Map<String, String> target,
      boolean normalizeKeys
  ) {
    JsonElement section = root.get(propertyName);
    if (section == null || !section.isJsonObject()) {
      return;
    }

    for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject().entrySet()) {
      JsonElement value = entry.getValue();
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        continue;
      }

      String key = normalizeKeys ? normalizeLanguageCode(entry.getKey()) : entry.getKey();
      String mapValue = value.getAsString();
      if (!mapValue.isBlank()) {
        target.put(key, mapValue);
      }
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
