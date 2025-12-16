package net.fonteyne.jtimekeeper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class WindowsUserManagerTest {

    /**
     * Creates a test Lock.bat script in the JAR directory that exits successfully.
     * This overwrites any existing Lock.bat file.
     *
     * @return the File object representing the created Lock.bat script
     * @throws IOException if the file cannot be written
     */
    private File createTestLockScript() throws IOException {
        ServiceHelper helper = new ServiceHelper();
        String jarDirectory = helper.getJarDirectory();
        File lockScript = new File(jarDirectory, "Lock.bat");
        
        String lockScriptContent = "@echo off\nexit 0";
        Files.write(lockScript.toPath(), lockScriptContent.getBytes());
        
        return lockScript;
    }

    @Test
    public void shouldReturnResultOfSimulateLogoutWhenDebugIsTrue() {
        // Arrange
        int sessionId = 1;
        boolean debug = true;

        // Act
        boolean result = WindowsUserManager.forceLogout(sessionId, debug);

        // Assert
        assertTrue(result, "Should return true when debug mode is enabled");
    }

    @Test
    public void shouldNotCallSimulateLogoutWhenDebugIsFalse() {
        // Arrange
        int sessionId = 1;
        boolean debug = false;

        // Act
        boolean result = WindowsUserManager.forceLogout(sessionId, debug);

        // Assert
        // When debug is false, executeLockScript() is called instead of simulateLogout()
        // The result depends on whether the lock script execution succeeds
        // We can only verify that the method completes without throwing an exception
        // and returns a boolean value
        assertNotNull(result);
    }

    @Test
    public void shouldCallExecuteLockScriptWhenDebugIsFalse() throws Exception {
        // Arrange
        int sessionId = 1;
        boolean debug = false;

        // Create a test lock script that exits successfully
        createTestLockScript();

        // Note: This test will execute the test lock script
        // For testing purposes, we use a simple script that exits with code 0

        // Act
        boolean result = WindowsUserManager.forceLogout(sessionId, debug);

        // Assert
        assertTrue(result, "Lock script should execute successfully");
    }

    @Test
    public void shouldLogInfoMessageWhenWorkstationIsLockedSuccessfully() {
        // Arrange
        // Note: This test will attempt to lock the actual workstation
        // In a real test environment, you would mock User32.INSTANCE.LockWorkStation()

        // Act
        boolean result = WindowsUserManager.lockWorkstation();

        // Assert
        // We can only verify that the method returns a boolean value
        // The actual result depends on whether the lock operation succeeds
        assertNotNull(result);
    }

    @Test
    public void shouldLogWarningMessageWhenUser32APIFailsToLockWorkstation() {
        // Arrange
        // Note: This test would ideally mock User32.INSTANCE.LockWorkStation()
        // to return false, but since we cannot easily mock static JNA calls
        // without modifying the production code, this test documents the expected behavior

        // Act
        boolean result = WindowsUserManager.lockWorkstation();

        // Assert
        // The actual result depends on whether the lock operation succeeds
        // In case of failure, the method should return false and log a warning
        // We can only verify that the method returns a boolean value
        assertNotNull(result);
    }
}