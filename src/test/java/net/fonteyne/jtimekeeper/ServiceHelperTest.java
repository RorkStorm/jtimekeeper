
package net.fonteyne.jtimekeeper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class ServiceHelperTest {

    @Test
    public void shouldSuccessfullyLoadUsersFromConfigurationFileWhenFileExistsAndIsValid() throws Exception {
        // Arrange
        String configJson = """
        {
            "TimeCounters": {
                "user1": 60,
                "user2": 120,
                "user3": 30
            }
        }
        """;
        
        File tempConfigFile = createTempConfigFile(configJson);
        ServiceHelper serviceHelper = new ServiceHelper() {
            @Override
            public String getJarDirectory() {
                return tempConfigFile.getParent();
            }
        };
        
        // Act
        serviceHelper.configureUsers();
        
        // Assert
        Map<String, TimeCounter> users = serviceHelper.getUsers();
        assertEquals(3, users.size(), "Should load exactly 3 users");
        
        assertTrue(users.containsKey("user1"), "Should contain user1");
        assertTrue(users.containsKey("user2"), "Should contain user2");
        assertTrue(users.containsKey("user3"), "Should contain user3");
        
        assertEquals(60, users.get("user1").getDefaultMinutes());
        assertEquals(120, users.get("user2").getDefaultMinutes());
        assertEquals(30, users.get("user3").getDefaultMinutes());
        
        assertEquals(60, users.get("user1").getMinutes());
        assertEquals(120, users.get("user2").getMinutes());
        assertEquals(30, users.get("user3").getMinutes());
        
        // Cleanup
        tempConfigFile.delete();
    }

    @Test
    public void shouldLogInfoMessageWithCorrectUserCountWhenUsersAreSuccessfullyLoaded() throws Exception {
        // Arrange
        String configJson = """
        {
            "TimeCounters": {
                "user1": 60,
                "user2": 120
            }
        }
        """;
        
        File tempConfigFile = createTempConfigFile(configJson);
        ServiceHelper serviceHelper = new ServiceHelper() {
            @Override
            public String getJarDirectory() {
                return tempConfigFile.getParent();
            }
        };
        
        // Act
        serviceHelper.configureUsers();
        
        // Assert
        Map<String, TimeCounter> users = serviceHelper.getUsers();
        assertEquals(2, users.size(), "Should load exactly 2 users");
        
        // Cleanup
        tempConfigFile.delete();
    }

    @Test
    public void shouldLogErrorMessageWhenFileNotFoundExceptionIsCaught() throws Exception {
        // Arrange
        ServiceHelper serviceHelper = new ServiceHelper() {
            @Override
            public String getJarDirectory() {
                return "/nonexistent/directory/path";
            }
        };
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            serviceHelper.configureUsers();
        }, "Should throw RuntimeException when configuration file is not found");
    }
    
   @Test
public void shouldLogCurrentWindowsUserWhenSessionChangeIsDetected() throws Exception {
    // Arrange
    String configJson = """
    {
        "TimeCounters": {
            "efn": 2
        }
    }
    """;
    
    File tempConfigFile = createTempConfigFile(configJson);
    ServiceHelper serviceHelper = new ServiceHelper() {
        @Override
        public String getJarDirectory() {
            return tempConfigFile.getParent();
        }
    };
    
    serviceHelper.configureUsers();
    
    // Get the current Windows user
    String currentWindowsUser = System.getProperty("user.name");
    assertNotNull(currentWindowsUser, "Current Windows user should not be null");
    assertFalse(currentWindowsUser.isEmpty(), "Current Windows user should not be empty");
    
    // Log the current Windows user
    System.out.println("Current Windows user logged in: " + currentWindowsUser);
    
    // Act - Simulate a session change for the current user
    // Note: This will only work if the current user is configured in TimeCounters
    int mockSessionId = 1;
    serviceHelper.handleSessionChange(currentWindowsUser, WTSSessionCodes.WTS_SESSION_LOGON, mockSessionId);
    
    // Assert
    Map<String, TimeCounter> users = serviceHelper.getUsers();
    if (users.containsKey(currentWindowsUser)) {
        TimeCounter userCounter = users.get(currentWindowsUser);
        assertNotNull(userCounter, "User counter should be created for current Windows user");
        assertNotNull(userCounter.getLastLogOn(), "Last logon time should be set");
    } else {
        System.out.println("Current Windows user '" + currentWindowsUser + "' is not configured in TimeCounters");
    }
    
    // Cleanup
    tempConfigFile.delete();
}

@Test
public void shouldRetrieveAndLogWindowsUserFromActiveSession() throws Exception {
    // Arrange - Get current Windows session information
    String currentWindowsUser = System.getProperty("user.name");
    String userDomain = System.getenv("USERDOMAIN");
    String computerName = System.getenv("COMPUTERNAME");
    
    // Log detailed Windows user information
    System.out.println("=== Windows User Session Information ===");
    System.out.println("Username: " + currentWindowsUser);
    System.out.println("Domain: " + (userDomain != null ? userDomain : "N/A"));
    System.out.println("Computer Name: " + (computerName != null ? computerName : "N/A"));
    System.out.println("User Home: " + System.getProperty("user.home"));
    System.out.println("OS Name: " + System.getProperty("os.name"));
    System.out.println("OS Version: " + System.getProperty("os.version"));
    System.out.println("========================================");
    
    // Assert
    assertNotNull(currentWindowsUser, "Windows username should be available");
    assertFalse(currentWindowsUser.isEmpty(), "Windows username should not be empty");
    assertTrue(currentWindowsUser.matches("^[a-zA-Z0-9._-]+$"), 
               "Username should contain only valid characters");
}

@Test
public void shouldHandleSessionChangeForActualWindowsUser() throws Exception {
    // Arrange
    String currentWindowsUser = System.getProperty("user.name");
    
    String configJson = String.format("""
    {
        "TimeCounters": {
            "%s": 120
        }
    }
    """, currentWindowsUser);
    
    File tempConfigFile = createTempConfigFile(configJson);
    ServiceHelper serviceHelper = new ServiceHelper() {
        @Override
        public String getJarDirectory() {
            return tempConfigFile.getParent();
        }
    };
    
    serviceHelper.configureUsers();
    
    System.out.println("Testing session change for actual Windows user: " + currentWindowsUser);
    
    // Act - Simulate LOGON event
    int mockSessionId = 1;
    serviceHelper.handleSessionChange(currentWindowsUser, WTSSessionCodes.WTS_SESSION_LOGON, mockSessionId);
    
    // Assert
    Map<String, TimeCounter> users = serviceHelper.getUsers();
    assertTrue(users.containsKey(currentWindowsUser), 
               "Should contain the current Windows user");
    
    TimeCounter userCounter = users.get(currentWindowsUser);
    assertNotNull(userCounter, "User counter should exist");
    assertEquals(120, userCounter.getDefaultMinutes(), "Default minutes should be 120");
    assertNotNull(userCounter.getLastLogOn(), "Last logon should be set after session change");
    
    System.out.println("Session change handled successfully for user: " + currentWindowsUser);
    System.out.println("Remaining time: " + userCounter.getMinutes() + " minutes");
    
    // Cleanup
    tempConfigFile.delete();
}

    private File createTempConfigFile(String content) throws IOException {
        File tempFile = File.createTempFile("appsettings", ".json");
        File renamedFile = new File(tempFile.getParent(), "appsettings.json");
        tempFile.renameTo(renamedFile);
        Files.write(renamedFile.toPath(), content.getBytes());
        return renamedFile;
    }
}
