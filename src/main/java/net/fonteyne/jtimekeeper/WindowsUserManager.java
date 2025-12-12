
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
 * This class provides utility methods for interacting with Windows Terminal Services API (WTS)
 * and User32 API through JNA.
 */
public class WindowsUserManager {
    private static final Logger logger = LoggerFactory.getLogger(WindowsUserManager.class);

    /**
     * Retrieves the username associated with a Windows session
     *
     * @param sessionId ID of the Windows session
     * @param prependDomain If true, prefixes the name with the domain (DOMAIN\\username)
     * @return The username or "SYSTEM" if not found
     */
    public static String getUsernameBySessionId(int sessionId, boolean prependDomain) {
        PointerByReference bufferRef = new PointerByReference();
        IntByReference bytesReturned = new IntByReference();
        String username = "SYSTEM";

        Pointer p = null;
        try {
            boolean success = Wtsapi32.INSTANCE.WTSQuerySessionInformation(
                    Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                    sessionId,
                    Wtsapi32.WTS_INFO_CLASS.WTSUserName,
                    bufferRef,
                    bytesReturned
            );

            p = bufferRef.getValue();
            if (success && p != null && bytesReturned.getValue() > 0) {
                // WTS returns wchar_t*, read as Unicode
                String user = p.getWideString(0);
                if (user != null && !user.isEmpty()) {
                    username = user;
                }
            }
        } catch (Exception ex) {
            logger.error("Error getting username for session {}: {}", sessionId, ex.getMessage(), ex);
        } finally {
            // Always free if we received a non-null pointer
            try {
                Pointer got = bufferRef.getValue();
                if (got != null) {
                    Wtsapi32.INSTANCE.WTSFreeMemory(got);
                }
            } catch (Throwable t) {
                logger.warn("Failed to free WTS memory: {}", t.getMessage(), t);
            }
        }

        if (prependDomain) {
            // Retrieve the domain and prefix if possible
            PointerByReference domainRef = new PointerByReference();
            IntByReference domainBytes = new IntByReference();
            Pointer pd = null;
            try {
                boolean success = Wtsapi32.INSTANCE.WTSQuerySessionInformation(
                        Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                        sessionId,
                        Wtsapi32.WTS_INFO_CLASS.WTSDomainName,
                        domainRef,
                        domainBytes
                );
                pd = domainRef.getValue();
                if (success && pd != null && domainBytes.getValue() > 0) {
                    String domain = pd.getWideString(0);
                    if (domain != null && !domain.isEmpty()) {
                        username = domain + "\\" + username;
                    }
                }
            } catch (Exception ex) {
                logger.error("Error getting domain for session {}: {}", sessionId, ex.getMessage(), ex);
            } finally {
                try {
                    Pointer got = domainRef.getValue();
                    if (got != null) {
                        Wtsapi32.INSTANCE.WTSFreeMemory(got);
                    }
                } catch (Throwable t) {
                    logger.warn("Failed to free WTS memory for domain: {}", t.getMessage(), t);
                }
            }
        }

        return username;
    }

    /**
     * Forces a Windows session to lock by executing a batch script.
     * In debug mode, this method only logs the action without performing the actual lock.
     * When not in debug mode, it attempts to execute a Lock.bat file located in the JAR directory.
     *
     * @param sessionId The ID of the Windows session to lock
     * @param debug If true, only logs the lock action without executing it; if false, performs the actual lock
     * @return true if the lock operation was successful (or simulated successfully in debug mode), false otherwise
     */
    public static boolean forceLogout(int sessionId, boolean debug) {

        if (debug) {
            logger.info("Forcing logout for session {}", sessionId);
            return true;
        }
        else {

            try {

                ServiceHelper helper = new ServiceHelper();
                String jarPath = helper.getJarDirectory();

                // Option 1: Execute Lock.bat
                ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "Lock.bat");
                processBuilder.directory(new File(jarPath));
                Process process = processBuilder.start();

                int exitCode = process.waitFor();
                return exitCode == 0;

                // Option 2 (commented): Use rundll32 to lock
                // ProcessBuilder processBuilder = new ProcessBuilder(
                //     "rundll32.exe",
                //     "user32.dll,LockWorkStation"
                // );
                // processBuilder.directory(systemDirectory);
                // Process process = processBuilder.start();
                // return true;

            } catch (IOException ex) {
                logger.debug("Failed to lock session: {}", ex.getMessage());
            } catch (InterruptedException ex) {
                logger.debug("Lock process interrupted: {}", ex.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.debug("Failed to lock session: {}", ex.getMessage());
            }
        }
        
        return false;
    }

    /**
     * Locks the workstation directly using the Windows API
     * (More direct alternative to forceLogout)
     *
     * @return true if the lock was successful
     */
    public static boolean lockWorkstation() {
        try {
            return User32.INSTANCE.LockWorkStation().booleanValue();
        } catch (Exception ex) {
            logger.error("Failed to lock workstation: {}", ex.getMessage());
            return false;
        }
    }
}