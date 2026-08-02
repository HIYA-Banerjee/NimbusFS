package com.nimbusfs.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads configuration from a .properties file.
 *
 * Search order (first found wins):
 *  1. Path specified via system property: -Dnimbusfs.config=/path/to/file.properties
 *  2. Working directory: ./config/{filename}
 *  3. Classpath resource: /{filename}
 *
 * Example usage:
 *   ConfigLoader cfg = new ConfigLoader("master.properties");
 *   int port = cfg.getInt("master.port", 9000);
 */
public class ConfigLoader {

    private static final Logger log = LogManager.getLogger(ConfigLoader.class);

    private final Properties props = new Properties();

    public ConfigLoader(String filename) {
        load(filename);
    }

    private void load(String filename) {
        // 1. System property override
        String sysProp = System.getProperty("nimbusfs.config");
        if (sysProp != null) {
            Path p = Paths.get(sysProp);
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    props.load(is);
                    log.info("Loaded config from system property path: {}", p);
                    return;
                } catch (IOException e) {
                    log.warn("Failed to read config from {}: {}", p, e.getMessage());
                }
            }
        }

        // 2. Working directory
        Path workDir = Paths.get("config", filename);
        if (Files.exists(workDir)) {
            try (InputStream is = Files.newInputStream(workDir)) {
                props.load(is);
                log.info("Loaded config from working directory: {}", workDir);
                return;
            } catch (IOException e) {
                log.warn("Failed to read config from {}: {}", workDir, e.getMessage());
            }
        }

        // 3. Classpath
        try (InputStream is = getClass().getResourceAsStream("/" + filename)) {
            if (is != null) {
                props.load(is);
                log.info("Loaded config from classpath: /{}", filename);
                return;
            }
        } catch (IOException e) {
            log.warn("Failed to read classpath config /{}: {}", filename, e.getMessage());
        }

        log.warn("No config file found for '{}'. Using defaults.", filename);
    }

    public String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) {
            log.warn("Config key '{}' has non-integer value '{}'. Using default {}.", key, val, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try { return Long.parseLong(val.trim()); }
        catch (NumberFormatException e) {
            log.warn("Config key '{}' has non-long value '{}'. Using default {}.", key, val, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val.trim());
    }
}
