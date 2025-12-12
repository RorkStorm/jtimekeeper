
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
    
    /**
     * Map of usernames to their corresponding TimeCounter instances.
     * Each TimeCounter tracks the daily time allowance and usage for a user.
     */
    private final Map<String, TimeCounter> users = new HashMap<>();

    /**
     * Loads user configurations from the appsettings.json file.
     * <p>
     * The configuration file should be located in the same directory as the JAR file
     * and contain a "TimeCounters" section with username-to-minutes mappings.
     * <p>
     * Example configuration:
     * <pre>
     * {
     *   "TimeCounters": {
     *     "username1": 60,
     *     "username2": 120
     *   }
     * }
     * </pre>
     *
     * @throws RuntimeException if the configuration file is not found, cannot be read,
     *                          or contains invalid data
     */
    public void configureUsers() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Get the directory where the JAR is located
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
                    logger.info("Loading User: {} with Values: {}", key, users.get(key));
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
     * Gets the directory where the running JAR is located.
     * <p>
     * This method handles both production (JAR file) and development (IDE) scenarios.
     * In production, it returns the parent directory of the JAR file.
     * In development, it returns the absolute path to the classes directory.
     *
     * @return the absolute path to the JAR directory, or the current working directory
     *         if the JAR location cannot be determined
     */
    public String getJarDirectory() {
        try {
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
        } catch (Exception e) {
            logger.warn("Could not determine JAR directory, using current directory", e);
            return System.getProperty("user.dir");
        }
    }

    /**
     * Gets the map of configured users and their time counters.
     *
     * @return an unmodifiable view would be safer, but returns the internal map
     *         for direct access to TimeCounter instances
     */
    public Map<String, TimeCounter> getUsers() {
        return users;
    }

    /**
     * Handles Windows Terminal Services session change events.
     * <p>
     * This method processes different session events:
     * <ul>
     *   <li><b>LOGON/UNLOCK:</b> Starts tracking time, resets daily allowance if needed,
     *       and schedules automatic logout when time expires</li>
     *   <li><b>LOGOFF/LOCK:</b> Calculates and saves remaining time for the user</li>
     * </ul>
     *
     * @param currentUser        the username of the session owner
     * @param WTS_SESSION_EVENT  the Windows Terminal Services session event code
     *                           (from {@link WTSSessionCodes})
     * @param sessionId          the Windows session ID
     */
    public void handleSessionChange(String currentUser, int WTS_SESSION_EVENT, int sessionId) {

        logger.info("Loading User : {} with Values : {} and Event {}",currentUser, users.get(currentUser), WTS_SESSION_EVENT);
        Timer timer = new Timer(true);

        if ((WTS_SESSION_EVENT == WTSSessionCodes.WTS_SESSION_LOGON || WTS_SESSION_EVENT == WTSSessionCodes.WTS_SESSION_UNLOCK) && getUsers().containsKey(currentUser)) {
            // A new session has started
            logger.info("A new session has started.");

            // If the session starts another day than the Svc startup date, we reset the time counter
            if (!Objects.equals(getUsers().get(currentUser).getDay(), LocalDate.now())) {
                logger.info("The service started {} but we are now {} ->reset", getUsers().get(currentUser).getDay(), LocalDate.now());
                getUsers().get(currentUser).setDay(LocalDate.now());
                getUsers().get(currentUser).setMinutes(getUsers().get(currentUser).getDefaultMinutes());
            }

            getUsers().get(currentUser).setLastLogOn(LocalDateTime.now());

            if (checkForKidsLogon(currentUser)) {

                logger.info("Loading User : {} with Values : {}",currentUser, getUsers().get(currentUser));

                 // daemon timer
                TimeCounter tc = users.get(currentUser);
                long delayMs = tc.getMinutes() * 60L * 1000L;
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        forceLogout(currentUser, sessionId);
                    }
                }, delayMs);
            } else {
                forceLogout(currentUser, sessionId);
            }
        }

        if (getUsers().containsKey(currentUser) && (WTS_SESSION_EVENT == WTSSessionCodes.WTS_SESSION_LOGOFF || WTS_SESSION_EVENT == WTSSessionCodes.WTS_SESSION_UNLOCK))
        {
            logger.info("A session is closed or locked");
            getUsers().get(currentUser).setMinutes(calculateRemainingMinutes(getUsers().get(currentUser)));
            logger.info("Remaining remaining minutes: {} for User {}", getUsers().get(currentUser).getMinutes(), currentUser);
            timer.cancel();
        }
    }

    /**
     * Checks if a user has remaining time allowance for the current session.
     * <p>
     * This method is used to determine whether to allow a user to log in or
     * immediately force logout if their time has expired.
     *
     * @param currentUser the username to check
     * @return {@code true} if the user has remaining minutes (> 0), {@code false} otherwise
     */
    private boolean checkForKidsLogon(String currentUser)
    {
        if (getUsers().get(currentUser).getMinutes() == 0)
            return false;
        else
            return true;
    }

    /**
     * Forces a user logout by setting their remaining time to zero and
     * invoking the Windows session logout mechanism.
     *
     * @param currentUser the username to log out
     * @param sessionId   the Windows session ID to terminate
     */
    private void forceLogout(String currentUser, int sessionId)
    {
        logger.info("Logout operation triggered for User {} and Session : {}", currentUser, sessionId);
        getUsers().get(currentUser).setMinutes(0);

        boolean result = WindowsUserManager.forceLogout(sessionId, true);

        logger.info("Logout operation status : {}", result);
    }

    /**
     * Calculates the remaining minutes for a user based on their last logon time
     * and allocated time allowance.
     * <p>
     * The calculation is: remaining = (lastLogon + allocatedMinutes) - currentTime
     * <p>
     * If the result is negative (time has expired), returns 0.
     *
     * @param timeCounter the TimeCounter instance containing user session data
     * @return the number of remaining minutes, or 0 if time has expired
     */
    public static int calculateRemainingMinutes(TimeCounter timeCounter) {

        // Expiry date/time = last logon + duration in minutes
        LocalDateTime expiry = timeCounter.getLastLogOn().plusMinutes(timeCounter.getMinutes());

        // Duration between now and expiry (can be negative)
        long remainingMinutes = Duration.between(LocalDateTime.now(), expiry).toMinutes();

        logger.info("Expiry {} - Remaining minutes {}", expiry, remainingMinutes);

        // Returns 0 if negative, otherwise the value in minutes
        return (int) Math.max(0, remainingMinutes);
    }
}
