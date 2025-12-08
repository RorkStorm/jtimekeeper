package net.fonteyne.jtimekeeper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ServiceHelper {
    private static final Logger logger = LoggerFactory.getLogger(ServiceHelper.class);
    private final Map<String, TimeCounter> users = new HashMap<>();

    public void configureUsers() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Obtenir le répertoire où se trouve le JAR
            String jarDir = getJarDirectory();
            Path configPath = Paths.get(jarDir, "appsettings.json");
            File configFile = configPath.toFile();
            
            if (!configFile.exists()) {
                throw new FileNotFoundException("Configuration file not found: " + configFile.getAbsolutePath());
            }

            logger.info("Loading configuration from: {}", configFile.getAbsolutePath());
            JsonNode root = mapper.readTree(configFile);
            JsonNode timeCountersSection = root.path("TimeCounters");

            if (timeCountersSection.isMissingNode()) {
                logger.warn("TimeCounters section not found in configuration");
                return;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = timeCountersSection.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                try {
                    int defaultMinutes = value.asInt();
                    TimeCounter counter = new TimeCounter();
                    counter.setDay(LocalDate.now());
                    counter.setMinutes(defaultMinutes);
                    counter.setDefaultMinutes(defaultMinutes);
                    counter.setLastLogOn(LocalDateTime.now());

                    users.put(key, counter);
                    logger.debug("Loading User: {} with Values: {}", key, users.get(key));
                } catch (NumberFormatException ex) {
                    logger.warn("Invalid number format for user {}: {}", key, value.asText());
                }
            }
            
            logger.info("Loaded {} user(s) from configuration", users.size());
        } catch (FileNotFoundException ex) {
            logger.error("Configuration file not found.", ex);
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            logger.error("Error reading configuration file.", ex);
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            logger.error("Error configuring users.", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Obtient le répertoire où se trouve le JAR en cours d'exécution
     */
    private String getJarDirectory() {
        try {
            String jarPath = ServiceHelper.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            
            File jarFile = new File(jarPath);
            
            // Si c'est un JAR, retourner le répertoire parent
            if (jarFile.isFile()) {
                return jarFile.getParent();
            }
            
            // Si on est en développement (classes dans target/classes)
            return jarFile.getAbsolutePath();
        } catch (Exception e) {
            logger.warn("Could not determine JAR directory, using current directory", e);
            return System.getProperty("user.dir");
        }
    }

    public Map<String, TimeCounter> getUsers() {
        return users;
    }
}
