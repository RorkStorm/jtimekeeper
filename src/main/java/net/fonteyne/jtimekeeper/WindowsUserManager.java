
package net.fonteyne.jtimekeeper;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Wtsapi32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

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
     * In debug mode, this method only logs the action without performing the actual logout.
     * In production mode, it executes a batch script to lock the workstation.
     * </p>
     *
     * @param sessionId the ID of the Windows session to logout
     * @param debug     if true, only logs the action; if false, performs the actual logout
     * @return true if the logout operation was successful (or simulated in debug mode), false otherwise
     */
    public static boolean forceLogout(int sessionId, boolean debug) {
        if (debug) {
            return simulateLogout(sessionId);
        }
        
        return executeLockScript();
    }

    /**
     * Simulates a logout operation for debugging purposes.
     *
     * @param sessionId the session ID to log
     * @return always returns true
     */
    private static boolean simulateLogout(int sessionId) {
        logger.info("DEBUG MODE: Simulating logout for session {}", sessionId);
        return true;
    }

    /**
     * Executes the lock script to logout the user.
     *
     * @return true if the script executed successfully, false otherwise
     */
    private static boolean executeLockScript() {
        try {
            File scriptDirectory = getScriptDirectory();
            ProcessBuilder processBuilder = createLockProcessBuilder(scriptDirectory);
            
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            boolean success = exitCode == 0;
            if (success) {
                logger.info("Lock script executed successfully");
            } else {
                logger.warn("Lock script exited with code: {}", exitCode);
            }
            
            return success;
            
        } catch (IOException ex) {
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
        return new File(jarPath);
    }

    /**
     * Creates a ProcessBuilder configured to execute the lock script.
     *
     * @param directory the directory containing the script
     * @return a configured ProcessBuilder
     */
    private static ProcessBuilder createLockProcessBuilder(File directory) {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", LOCK_SCRIPT_NAME);
        processBuilder.directory(directory);
        return processBuilder;
    }

    /**
     * Locks the workstation directly using the Windows User32 API.
     * <p>
     * This is a more direct alternative to {@link #forceLogout(int, boolean)}
     * that doesn't require a batch script.
     * </p>
     *
     * @return true if the lock was successful, false otherwise
     */
    public static boolean lockWorkstation() {
        try {
            boolean success = User32.INSTANCE.LockWorkStation().booleanValue();
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
