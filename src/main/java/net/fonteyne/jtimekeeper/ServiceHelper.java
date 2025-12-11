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

public class ServiceHelper {
    private static final Logger logger = LoggerFactory.getLogger(ServiceHelper.class);
    private final Map<String, TimeCounter> users = new HashMap<>();

    public void configureUsers() {

        try {
            ObjectMapper mapper = new ObjectMapper();

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

    public String getJarDirectory() {
        try {
            String jarPath = ServiceHelper.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            
            File jarFile = new File(jarPath);

            if (jarFile.isFile()) {
                return jarFile.getParent();
            }

            return jarFile.getAbsolutePath();
        } catch (Exception e) {
            logger.warn("Could not determine JAR directory, using current directory", e);
            return System.getProperty("user.dir");
        }
    }

    public Map<String, TimeCounter> getUsers() {
        return users;
    }

    public void handleSessionChange(String currentUser, int WTS_SESSION_EVENT, int sessionId) {

        logger.info("Loading User : {} with Values : {} and Event {}",currentUser, users.get(currentUser), WTS_SESSION_EVENT);
        Timer timer = new Timer(true);

        if ((WTS_SESSION_EVENT == WTSSessionCodes.WTS_SESSION_LOGON || WTS_SESSION_EVENT == WTSSessionCodes.WTS_SESSION_UNLOCK) && getUsers().containsKey(currentUser)) {
            logger.info("A new session has started.");

            if (!Objects.equals(getUsers().get(currentUser).getDay(), LocalDate.now())) {
                logger.info("The service started {} but we are now {} ->reset", getUsers().get(currentUser).getDay(), LocalDate.now());
                getUsers().get(currentUser).setDay(LocalDate.now());
                getUsers().get(currentUser).setMinutes(getUsers().get(currentUser).getDefaultMinutes());
            }

            getUsers().get(currentUser).setLastLogOn(LocalDateTime.now());

            if (checkForKidsLogon(currentUser)) {

                logger.info("Loading User : {} with Values : {}",currentUser, getUsers().get(currentUser));

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

    private boolean checkForKidsLogon(String currentUser)
    {
        if (getUsers().get(currentUser).getMinutes() == 0)
            return false;
        else
            return true;
    }

    private void forceLogout(String currentUser, int sessionId)
    {
        logger.info("Logout operation triggered for User {} and Session : {}", currentUser, sessionId);
        getUsers().get(currentUser).setMinutes(0);

        boolean result = WindowsUserManager.forceLogout(sessionId, true);

        logger.info("Logout operation status : {}", result);
    }

    public static int calculateRemainingMinutes(TimeCounter timeCounter) {

        LocalDateTime expiry = timeCounter.getLastLogOn().plusMinutes(timeCounter.getMinutes());

        long remainingMinutes = Duration.between(LocalDateTime.now(), expiry).toMinutes();

        logger.info("Expiry {} - Remaining minutes {}", expiry, remainingMinutes);

        return (int) Math.max(0, remainingMinutes);
    }
}
