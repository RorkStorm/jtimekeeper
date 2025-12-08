package net.fonteyne.jtimekeeper;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.Wtsapi32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class WindowsUserManager {
    private static final Logger logger = LoggerFactory.getLogger(WindowsUserManager.class);

    /**
     * Récupère le nom d'utilisateur associé à une session Windows
     *
     * @param sessionId ID de la session Windows
     * @param prependDomain Si true, préfixe le nom avec le domaine (DOMAIN\\username)
     * @return Le nom d'utilisateur ou "SYSTEM" si non trouvé
     */
    public static String getUsernameBySessionId(int sessionId, boolean prependDomain) {
        PointerByReference bufferRef = new PointerByReference();
        IntByReference bytesReturned = new IntByReference();
        String username = "SYSTEM";

        try {
            // Récupérer le nom d'utilisateur
            boolean success = Wtsapi32.INSTANCE.WTSQuerySessionInformation(
                Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                sessionId,
                Wtsapi32.WTS_INFO_CLASS.WTSUserName,
                bufferRef,
                bytesReturned
            );

            if (success && bytesReturned.getValue() > 1) {
                username = bufferRef.getValue().getString(0);
                Wtsapi32.INSTANCE.WTSFreeMemory(bufferRef.getValue());

                // Si demandé, préfixer avec le domaine
                if (prependDomain) {
                    bufferRef = new PointerByReference();
                    bytesReturned = new IntByReference();
                    
                    success = Wtsapi32.INSTANCE.WTSQuerySessionInformation(
                        Wtsapi32.WTS_CURRENT_SERVER_HANDLE,
                        sessionId,
                        Wtsapi32.WTS_INFO_CLASS.WTSDomainName,
                        bufferRef,
                        bytesReturned
                    );

                    if (success && bytesReturned.getValue() > 1) {
                        String domain = bufferRef.getValue().getString(0);
                        username = domain + "\\" + username;
                        Wtsapi32.INSTANCE.WTSFreeMemory(bufferRef.getValue());
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error getting username for session {}: {}", sessionId, ex.getMessage());
        }

        return username;
    }

    /**
     * Force le verrouillage de la session
     *
     * @param sessionId ID de la session à verrouiller
     * @return true si le verrouillage a réussi, false sinon
     */
    public static boolean forceLogout(int sessionId) {
        try {
            // Définir le répertoire système comme répertoire courant
            String systemDir = System.getenv("SystemRoot") + "\\system32";
            File systemDirectory = new File(systemDir);
            
            if (!systemDirectory.exists()) {
                logger.error("System directory not found: {}", systemDir);
                return false;
            }

            // Option 1: Exécuter Lock.bat
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "Lock.bat");
            processBuilder.directory(systemDirectory);
            Process process = processBuilder.start();
            
            int exitCode = process.waitFor();
            return exitCode == 0;

            // Option 2 (commentée): Utiliser rundll32 pour verrouiller
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
        
        return false;
    }

    /**
     * Verrouille directement la station de travail en utilisant l'API Windows
     * (Alternative plus directe à forceLogout)
     *
     * @return true si le verrouillage a réussi
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
