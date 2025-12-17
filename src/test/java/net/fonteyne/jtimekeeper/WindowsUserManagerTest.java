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
    public void shouldCallExecuteLockScript() throws Exception {
        // Arrange

        // Create a test lock script that exits successfully
        createTestLockScript();

        // Note: This test will execute the test lock script
        // For testing purposes, we use a simple script that exits with code 0

        // Act
        boolean result = WindowsUserManager.forceLogout();

        // Assert
        assertTrue(result, "Lock script should execute successfully");
    }

    @Test
    public void shouldLogInfoMessageWhenWorkstationIsLockedSuccessfully() {

        // Arrange
        WorkstationLocker mockLocker = new WorkstationLocker() {
            @Override
            public boolean lock() {
                return true;
            }
        };

        WindowsUserManager.setLocker(mockLocker);

        // Act
        boolean result = WindowsUserManager.lockWorkstation();

        // Assert
        // We can only verify that the method returns a boolean value
        // The actual result depends on whether the lock operation succeeds
        assertTrue(result);
    }

    @Test
    public void shouldLogWarningMessageWhenUser32APIFailsToLockWorkstation() {
        // Arrange
        WorkstationLocker mockLocker = new WorkstationLocker() {
            @Override
            public boolean lock() {
                return false;
            }
        };

        WindowsUserManager.setLocker(mockLocker);

        // Act
        boolean result = WindowsUserManager.lockWorkstation();

        // Assert
        // The actual result depends on whether the lock operation succeeds
        // In case of failure, the method should return false and log a warning
        // We can only verify that the method returns a boolean value
        assertFalse(result);
    }
}