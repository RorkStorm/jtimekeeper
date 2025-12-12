
package net.fonteyne.jtimekeeper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service helper class that manages user session time tracking and enforcement.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Loading user time limit configurations from appsettings.json</li>
 *   <li>Tracking active user sessions and their remaining time</li>
 *   <li>Handling Windows session change events (logon, logoff, lock, unlock)</li>
 *   <li>Enforcing time limits by triggering automatic logout when time expires</li>
 * </ul>
 */
public class ServiceHelper {
    private static final Logger logger = LoggerFactory.getLogger(ServiceHelper.class);
    private static final String CONFIG_FILE_NAME = "appsettings.json";
    private static final String TIME_COUNTERS_SECTION = "TimeCounters";
    private static final long MILLISECONDS_PER_MINUTE = 60_000L;
    
    private final Map<String, TimeCounter> users = new HashMap<>();
    private final Map<String, Timer> activeTimers = new HashMap<>();

    /**
     * Loads user configurations from the appsettings.json file.
     *
     * @throws RuntimeException if the configuration file is not found, cannot be read,
     *                          or contains invalid data
     */
    public void configureUsers() {
        try {
            File configFile = getConfigurationFile();
            loadUsersFromFile(configFile);
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

    private File getConfigurationFile() throws FileNotFoundException {
        String jarDir = getJarDirectory();
        Path configPath = Paths.get(jarDir, CONFIG_FILE_NAME);
        File configFile = configPath.toFile();
        
        if (!configFile.exists()) {
            throw new FileNotFoundException("Configuration file not found: " + configFile.getAbsolutePath());
        }
        
        logger.info("Loading configuration from: {}", configFile.getAbsolutePath());
        return configFile;
    }

    private void loadUsersFromFile(File configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configFile);
        JsonNode timeCountersSection = root.path(TIME_COUNTERS_SECTION);

        if (timeCountersSection.isMissingNode()) {
            logger.warn("{} section not found in configuration", TIME_COUNTERS_SECTION);
            return;
        }

        parseTimeCounters(timeCountersSection);
    }

    private void parseTimeCounters(JsonNode timeCountersSection) {
        Iterator<Map.Entry<String, JsonNode>> fields = timeCountersSection.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String username = entry.getKey();
            JsonNode minutesNode = entry.getValue();

            try {
                int defaultMinutes = minutesNode.asInt();
                TimeCounter counter = createTimeCounter(defaultMinutes);
                users.put(username, counter);
                logger.info("Loaded user: {} with time limit: {} minutes", username, defaultMinutes);
            } catch (NumberFormatException ex) {
                logger.warn("Invalid number format for user {}: {}", username, minutesNode.asText());
            }
        }
    }

    private TimeCounter createTimeCounter(int defaultMinutes) {
        TimeCounter counter = new TimeCounter();
        counter.setDay(LocalDate.now());
        counter.setMinutes(defaultMinutes);
        counter.setDefaultMinutes(defaultMinutes);
        counter.setLastLogOn(LocalDateTime.now());
        return counter;
    }

    /**
     * Gets the directory where the running JAR is located.
     *
     * @return the absolute path to the JAR directory
     */
    public String getJarDirectory() {
        try {
            return determineJarDirectory();
        } catch (Exception e) {
            logger.warn("Could not determine JAR directory, using current directory", e);
            return System.getProperty("user.dir");
        }
    }

    private String determineJarDirectory() throws URISyntaxException {
        String jarPath = ServiceHelper.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI()
            .getPath();
        
        File jarFile = new File(jarPath);
        
        // If it's a JAR, return the parent directory
        if (jarFile.isFile()) {
            return jarFile.getParent();
        }
        
        // If we're in development mode (classes in target/classes)
        return jarFile.getAbsolutePath();
    }

    /**
     * Gets the map of configured users and their time counters.
     *
     * @return the map of users to their TimeCounter instances
     */
    public Map<String, TimeCounter> getUsers() {
        return users;
    }

    /**
     * Handles Windows Terminal Services session change events.
     *
     * @param username   the username of the session owner
     * @param eventCode  the Windows Terminal Services session event code
     * @param sessionId  the Windows session ID
     */
    public void handleSessionChange(String username, int eventCode, int sessionId) {
        logger.info("Processing session change for user: {} - Event: {} - Session: {}", 
                    username, eventCode, sessionId);

        if (isSessionStartEvent(eventCode)) {
            handleSessionStart(username, sessionId);
        } else if (isSessionEndEvent(eventCode)) {
            handleSessionEnd(username);
        }
    }

    private boolean isSessionStartEvent(int eventCode) {
        return eventCode == WTSSessionCodes.WTS_SESSION_LOGON || 
               eventCode == WTSSessionCodes.WTS_SESSION_UNLOCK;
    }

    private boolean isSessionEndEvent(int eventCode) {
        return eventCode == WTSSessionCodes.WTS_SESSION_LOGOFF || 
               eventCode == WTSSessionCodes.WTS_SESSION_LOCK;
    }

    private void handleSessionStart(String username, int sessionId) {
        if (!users.containsKey(username)) {
            logger.debug("User {} not configured for time tracking", username);
            return;
        }

        logger.info("Session started for user: {}", username);
        
        TimeCounter counter = users.get(username);
        resetDailyAllowanceIfNeeded(counter);
        counter.setLastLogOn(LocalDateTime.now());

        if (hasRemainingTime(counter)) {
            scheduleAutomaticLogout(username, sessionId, counter);
        } else {
            executeImmediateLogout(username, sessionId);
        }
    }

    private void resetDailyAllowanceIfNeeded(TimeCounter counter) {
        if (!counter.getDay().equals(LocalDate.now())) {
            logger.info("New day detected, resetting time allowance from {} to {}", 
                       counter.getDay(), LocalDate.now());
            counter.setDay(LocalDate.now());
            counter.setMinutes(counter.getDefaultMinutes());
        }
    }

    private boolean hasRemainingTime(TimeCounter counter) {
        return counter.getMinutes() > 0;
    }

    private void scheduleAutomaticLogout(String username, int sessionId, TimeCounter counter) {
        logger.info("Scheduling automatic logout for user: {} in {} minutes", 
                   username, counter.getMinutes());

        cancelExistingTimer(username);
        
        Timer timer = new Timer(true);
        long delayMs = counter.getMinutes() * MILLISECONDS_PER_MINUTE;
        
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                executeLogout(username, sessionId);
            }
        }, delayMs);
        
        activeTimers.put(username, timer);
    }

    private void executeImmediateLogout(String username, int sessionId) {
        logger.info("No remaining time for user: {}, forcing immediate logout", username);
        executeLogout(username, sessionId);
    }

    private void handleSessionEnd(String username) {
        if (!users.containsKey(username)) {
            return;
        }

        logger.info("Session ended for user: {}", username);
        
        TimeCounter counter = users.get(username);
        int remainingMinutes = calculateRemainingMinutes(counter);
        counter.setMinutes(remainingMinutes);
        
        logger.info("Saved remaining time for user {}: {} minutes", username, remainingMinutes);
        
        cancelExistingTimer(username);
    }

    private void cancelExistingTimer(String username) {
        Timer existingTimer = activeTimers.remove(username);
        if (existingTimer != null) {
            existingTimer.cancel();
            logger.debug("Cancelled existing timer for user: {}", username);
        }
    }

    private void executeLogout(String username, int sessionId) {
        logger.info("Executing logout for user: {} - Session: {}", username, sessionId);
        
        TimeCounter counter = users.get(username);
        counter.setMinutes(0);

        boolean success = WindowsUserManager.forceLogout(sessionId, true);
        logger.info("Logout operation status for user {}: {}", username, success);
        
        cancelExistingTimer(username);
    }

    /**
     * Calculates the remaining minutes for a user based on their last logon time
     * and allocated time allowance.
     *
     * @param timeCounter the TimeCounter instance containing user session data
     * @return the number of remaining minutes, or 0 if time has expired
     */
    public static int calculateRemainingMinutes(TimeCounter timeCounter) {
        LocalDateTime expiry = timeCounter.getLastLogOn()
                                          .plusMinutes(timeCounter.getMinutes());

        long remainingMinutes = Duration.between(LocalDateTime.now(), expiry)
                                       .toMinutes();

        logger.debug("Expiry: {} - Remaining minutes: {}", expiry, remainingMinutes);

        return (int) Math.max(0, remainingMinutes);
    }
}
