
package net.fonteyne.jtimekeeper;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Wtsapi32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;

/**
 * Manages Windows user session operations including retrieving user information and locking sessions.
 * <p>
 * This class provides utility methods for interacting with Windows Terminal Services API (WTS)
 * and User32 API through JNA (Java Native Access).
 * </p>
 */
public class WindowsUserManager {
    private static final Logger logger = LoggerFactory.getLogger(WindowsUserManager.class);
    private static final String DEFAULT_USERNAME = "SYSTEM";
    private static final String LOCK_SCRIPT_NAME = "Lock.bat";
    private static final String DOMAIN_SEPARATOR = "\\";
    private static WorkstationLocker locker = new User32WorkstationLocker();

    // Setter pour injection de mock (tests uniquement)
    static void setLocker(WorkstationLocker locker) {
        WindowsUserManager.locker = locker;
    }
    
    /**
     * Retrieves the username associated with a Windows session.
     *
     * @param sessionId     the ID of the Windows session
     * @param prependDomain if true, prefixes the name with the domain (DOMAIN\\username)
     * @return the username, or "SYSTEM" if not found
     */
    public static String getUsernameBySessionId(int sessionId, boolean prependDomain) {
        String username = queryUsername(sessionId);
        
        if (prependDomain) {
            String domain = queryDomain(sessionId);
            if (domain != null && !domain.isEmpty()) {
                username = domain + DOMAIN_SEPARATOR + username;
            }
        }
        
        return username;
    }

    /**
     * Queries the username for a given session ID.
     *
     * @param sessionId the Windows session ID
     * @return the username, or "SYSTEM" if not found
     */
    private static String queryUsername(int sessionId) {
        return querySessionInformation(
            sessionId,
            Wtsapi32.WTS_INFO_CLASS.WTSUserName,
            DEFAULT_USERNAME,
            "username"
        );
    }

    /**
     * Queries the domain name for a given session ID.
     *
     * @param sessionId the Windows session ID
     * @return the domain name, or null if not found
     */
    private static String queryDomain(int sessionId) {
        return querySessionInformation(
            sessionId,
            Wtsapi32.WTS_INFO_CLASS.WTSDomainName,
            null,
            "domain"
        );
    }

    /**
     * Queries session information from the Windows Terminal Services API.
     *
     * @param sessionId    the Windows session ID
     * @param infoClass    the type of information to query
     * @param defaultValue the default value to return if query fails
     * @param infoType     the type of information being queried (for logging)
     * @return the queried information, or defaultValue if not found
     */
    private static String querySessionInformation(
            int sessionId,
            int infoClass,
            String defaultValue,
            String infoType) {
        
        PointerByReference bufferRef = new PointerByReference();
        IntByReference bytesReturned = new IntByReference();
        
        try {
            boolean success = Wtsapi32.INSTANCE.WTSQuerySessionInformation(
                Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                sessionId,
                infoClass,
                bufferRef,
                bytesReturned
            );

            if (success && hasValidData(bufferRef, bytesReturned)) {
                String value = extractWideString(bufferRef);
                if (isValidString(value)) {
                    return value;
                }
            }
            
            return defaultValue;
            
        } catch (Exception ex) {
            logger.error("Error getting {} for session {}: {}", 
                        infoType, sessionId, ex.getMessage(), ex);
            return defaultValue;
        } finally {
            freeWtsMemory(bufferRef, infoType);
        }
    }

    /**
     * Checks if the WTS query returned valid data.
     *
     * @param bufferRef      the pointer reference to the buffer
     * @param bytesReturned  the number of bytes returned
     * @return true if data is valid, false otherwise
     */
    private static boolean hasValidData(PointerByReference bufferRef, IntByReference bytesReturned) {
        Pointer pointer = bufferRef.getValue();
        return pointer != null && bytesReturned.getValue() > 0;
    }

    /**
     * Extracts a wide string from a pointer reference.
     *
     * @param bufferRef the pointer reference containing the string
     * @return the extracted string, or null if extraction fails
     */
    private static String extractWideString(PointerByReference bufferRef) {
        try {
            Pointer pointer = bufferRef.getValue();
            return pointer != null ? pointer.getWideString(0) : null;
        } catch (Exception ex) {
            logger.warn("Failed to extract wide string: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Checks if a string is valid (not null and not empty).
     *
     * @param value the string to check
     * @return true if the string is valid, false otherwise
     */
    private static boolean isValidString(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * Frees memory allocated by WTS API.
     *
     * @param bufferRef the pointer reference to free
     * @param infoType  the type of information (for logging)
     */
    private static void freeWtsMemory(PointerByReference bufferRef, String infoType) {
        try {
            Pointer pointer = bufferRef.getValue();
            if (pointer != null) {
                Wtsapi32.INSTANCE.WTSFreeMemory(pointer);
            }
        } catch (Throwable t) {
            logger.warn("Failed to free WTS memory for {}: {}", infoType, t.getMessage(), t);
        }
    }

    /**
     * Forces a logout for a Windows session.
     * <p>
     * Executes a batch script to lock the workstation.
     * </p>
     *
     * @return true if the logout operation was successful, false otherwise
     */
    public static boolean forceLogout() {
        return executeLockScript();
    }

    /**
     * Executes the lock script to logout the user.
     *
     * @return true if the script executed successfully, false otherwise
     */
    private static boolean executeLockScript() {
        try {
            File scriptDirectory = getScriptDirectory();
            File lockScript = new File(scriptDirectory, LOCK_SCRIPT_NAME);

            // Validate script path to prevent path traversal and ensure script exists
            if (!lockScript.getCanonicalPath().startsWith(scriptDirectory.getCanonicalPath() + File.separator)) {
                logger.error("Lock script path is outside the expected directory: {}", lockScript.getAbsolutePath());
                return false;
            }
            if (!lockScript.exists() || !lockScript.isFile()) {
                logger.error("Lock script not found: {}", lockScript.getAbsolutePath());
                return false;
            }
            if (!lockScript.canExecute()) {
                logger.error("Lock script is not executable: {}", lockScript.getAbsolutePath());
                return false;
            }

            ProcessBuilder processBuilder = createLockProcessBuilder(scriptDirectory);

            // Set a minimal environment for the process
            processBuilder.environment().clear();
            processBuilder.environment().put("PATH", System.getenv("PATH"));

            Process process = processBuilder.start();

            // Wait for process to finish with a timeout to avoid hanging
            final int timeoutSeconds = 10;
            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("Lock script timed out after {} seconds", timeoutSeconds);
                return false;
            }

            int exitCode = process.exitValue();

            boolean success = exitCode == 0;
            if (success) {
                logger.info("Lock script executed successfully");
            } else {
                logger.warn("Lock script exited with code: {}", exitCode);
            }

            return success;

        } catch (InvalidPathException | IOException ex) {
            logger.error("Failed to execute lock script: {}", ex.getMessage(), ex);
            return false;
        } catch (InterruptedException ex) {
            logger.error("Lock process interrupted: {}", ex.getMessage(), ex);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            logger.error("Unexpected error during logout: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Gets the directory where the lock script is located.
     *
     * @return the script directory
     */
    private static File getScriptDirectory() {
        ServiceHelper helper = new ServiceHelper();
        String jarPath = helper.getJarDirectory();
        if (jarPath == null || jarPath.isBlank()) {
            logger.warn("JAR directory could not be determined, using current working directory.");
            jarPath = System.getProperty("user.dir");
        }
        return new File(jarPath);
    }

    /**
     * Creates a ProcessBuilder configured to execute the lock script.
     *
     * @param directory the directory containing the script
     * @return a configured ProcessBuilder
     */
    private static ProcessBuilder createLockProcessBuilder(File directory) {
        // Defensive: Use absolute path to avoid ambiguity
        File lockScript = new File(directory, LOCK_SCRIPT_NAME);
        return new ProcessBuilder("cmd.exe", "/c", lockScript.getAbsolutePath())
                .directory(directory);
    }

    /**
     * Locks the workstation directly using the Windows User32 API.
     * <p>
     * This is a more direct alternative to {@link #forceLogout()}
     * that doesn't require a batch script.
     * </p>
     *
     * @return true if the lock was successful, false otherwise
     */
    public static boolean lockWorkstation() {
        try {
            boolean success = locker.lock();
            if (success) {
                logger.info("Workstation locked successfully via User32 API");
            } else {
                logger.warn("Failed to lock workstation via User32 API");
            }
            return success;
        } catch (Exception ex) {
            logger.error("Error locking workstation: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
