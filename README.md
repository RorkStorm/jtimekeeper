# Windows Session Change Service

A Java-based Windows service that monitors and manages user session time limits using Windows Terminal Services (WTS) session change notifications.

## Overview

This application creates a hidden window that registers for Windows session change notifications and tracks user login time. It automatically enforces configurable time limits by logging users out when their allocated time expires.

## Features

- **Session Monitoring**: Tracks Windows session events (logon, logoff, lock, unlock)
- **Time Tracking**: Monitors active session time for configured users
- **Automatic Enforcement**: Forces logout when user time limit is reached
- **Daily Reset**: Time limits reset daily at midnight
- **Configurable**: User time limits defined in JSON configuration
- **Logging**: Comprehensive logging using SLF4J

## Architecture

### Core Components

#### `ServiceApp`
The main entry point of the application. It:
- Initializes the hidden window for session monitoring
- Sets up a graceful shutdown hook
- Keeps the application running until stopped

#### `HiddenSessionWindow`
Creates and manages a hidden Windows window that:
- Registers for WTS session change notifications via `WTSRegisterSessionNotification`
- Receives `WM_WTSSESSION_CHANGE` messages
- Processes session events (logon, logoff, lock, unlock)
- Delegates event handling to `ServiceHelper`

**Key Methods:**
- `start()`: Initializes the message loop and session monitoring
- `stop()`: Cleans up resources and unregisters notifications
- `callback()`: Windows message procedure for processing WTS events

#### `ServiceHelper`
The business logic layer that:
- Loads user configurations from `appsettings.json`
- Manages time counters for each user
- Handles session change events
- Calculates remaining time
- Triggers automatic logout when time expires

**Key Methods:**
- `configureUsers()`: Loads user time limits from configuration
- `handleSessionChange()`: Processes session events and enforces time limits
- `calculateRemainingMinutes()`: Computes remaining session time
- `forceLogout()`: Initiates user logout when time expires

#### `TimeCounter`
A data class that tracks:
- `Day`: The date for daily time reset
- `Minutes`: Remaining minutes for current session
- `DefaultMinutes`: Default daily time allowance
- `LastLogOn`: Timestamp of last logon event

#### `WindowsUserManager`
Provides Windows API integration through JNA:
- `getUsernameBySessionId()`: Retrieves username for a session ID
- `forceLogout()`: Executes logout operation
- `lockWorkstation()`: Locks the Windows workstation

#### `WTSSessionCodes`
Constants for Windows Terminal Services session change codes:
- `WM_WTSSESSION_CHANGE`: Message ID for session changes
- `WTS_SESSION_LOGON`: User logged on
- `WTS_SESSION_LOGOFF`: User logged off
- `WTS_SESSION_LOCK`: Session locked
- `WTS_SESSION_UNLOCK`: Session unlocked
- Additional console and remote session codes

## Configuration

Create an `appsettings.json` file in the same directory as the JAR file:

```json
{
  "TimeCounters": {
    "Eric": 480,
    "William": 360,
    "admin": 600
  }
}
```

**Configuration Details:**
- **Key**: Windows username (case-sensitive)
- **Value**: Daily time allowance in minutes
- **Location**: Must be in the same directory as the executable JAR

**Example Time Limits:**
- `480` minutes = 8 hours
- `360` minutes = 6 hours
- `600` minutes = 10 hours

## Building

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Windows operating system

### Build Commands

```bash
# Clean and compile
mvn clean compile

# Package (creates shaded JAR with all dependencies)
mvn package

# The output will be in target/windows-sessionchange-service-1.0.0.jar
```

### Build Artifacts

After building, you'll find:
- `target/windows-sessionchange-service-1.0.0.jar` - Executable JAR with all dependencies
- `target/appsettings.json` - Configuration file (copied automatically)

## Installation

1. **Build the project**:
   ```bash
   mvn package
   ```

2. **Copy files to deployment location**:
   ```bash
   cp target/windows-sessionchange-service-1.0.0.jar /path/to/deployment/
   cp target/appsettings.json /path/to/deployment/
   ```

3. **Configure users**: Edit `appsettings.json` with desired user time limits

4. **Run the application**:
   ```bash
   java -jar windows-sessionchange-service-1.0.0.jar
   ```

## Running

### Console Mode

Run directly from command line:
```bash
java -jar windows-sessionchange-service-1.0.0.jar
```

### As a Windows Service

To run as a Windows service, you can use tools like:
- **NSSM** (Non-Sucking Service Manager)
- **Apache Commons Daemon** (procrun)
- **WinSW** (Windows Service Wrapper)

Example with NSSM:
```bash
nssm install SessionChangeService "C:\path\to\java.exe" "-jar C:\path\to\windows-sessionchange-service-1.0.0.jar"
nssm start SessionChangeService
```

## How It Works

### Session Tracking Flow

1. **Initialization**:
   - Application starts and loads user configurations
   - Hidden window is created and registered for WTS notifications
   - Message loop begins listening for session events

2. **User Logon/Unlock**:
   - WTS sends `WM_WTSSESSION_CHANGE` with `WTS_SESSION_LOGON` or `WTS_SESSION_UNLOCK`
   - Application retrieves username via `getUsernameBySessionId()`
   - If user is configured:
     - Checks if it's a new day (resets time if needed)
     - Records logon timestamp
     - Schedules logout timer based on remaining minutes
   - If time is already expired, forces immediate logout

3. **User Logoff/Lock**:
   - WTS sends `WM_WTSSESSION_CHANGE` with `WTS_SESSION_LOGOFF` or `WTS_SESSION_LOCK`
   - Application calculates remaining time
   - Saves remaining minutes for next session
   - Cancels scheduled logout timer

4. **Time Expiration**:
   - When timer expires, `forceLogout()` is called
   - User is logged out automatically
   - Remaining time is set to 0

5. **Daily Reset**:
   - At first logon after midnight, time counter resets to default value
   - Allows full daily allowance again

### Time Calculation

```java
remaining = (lastLogon + allocatedMinutes) - currentTime
```

If result is negative, remaining time = 0 (user is logged out)

## Dependencies

### Runtime Dependencies

- **JNA (5.14.0)**: Java Native Access for Windows API calls
- **JNA Platform (5.14.0)**: Platform-specific JNA mappings
- **SLF4J API (2.0.12)**: Logging facade
- **SLF4J Simple (2.0.12)**: Simple logging implementation
- **Jackson Databind (2.17.0)**: JSON parsing for configuration

### Build Dependencies

- **Maven Compiler Plugin**: Java 17 compilation
- **Maven Shade Plugin**: Creates uber JAR with dependencies
- **Maven Resources Plugin**: Copies configuration files

## Logging

The application uses SLF4J with the simple logger implementation. Logs include:

- Session change events (logon, logoff, lock, unlock)
- Time tracking information
- User configuration loading
- Logout operations
- Error and warning messages

**Log Output**: Console (stdout/stderr)

**Log Level**: Controlled by SLF4J simple logger properties

## Security Considerations

1. **Windows API Access**: Requires appropriate permissions to query session information
2. **Force Logout**: Needs privileges to terminate user sessions
3. **Configuration File**: Should be protected with appropriate file permissions
4. **User Context**: Must run with sufficient privileges to monitor all sessions

## Limitations

- **Windows Only**: Uses Windows-specific APIs (WTS, User32)
- **Session Notifications**: Requires registration for WTS notifications (may need admin rights)
- **Time Granularity**: Tracks time in minutes (not seconds)
- **Single Machine**: Does not support cross-machine session tracking
- **No Database**: Time tracking is in-memory (resets on service restart)

## Troubleshooting

### Application doesn't detect sessions

- Ensure the application is running with appropriate privileges
- Check that WTS notifications are being received (check logs)
- Verify the hidden window was created successfully

### Configuration not loading

- Verify `appsettings.json` is in the same directory as the JAR
- Check JSON syntax is valid
- Review logs for configuration errors

### Logout not working

- Ensure `Lock.bat` exists in the JAR directory (if using batch script method)
- Verify permissions to terminate user sessions
- Check logs for logout operation status

### Time not resetting daily

- Verify system clock is correct
- Check that service is running continuously
- Review logs for day comparison logic

## Development

### Project Structure

```
src/main/java/net/fonteyne/jtimekeeper/
├── ServiceApp.java              # Main entry point
├── HiddenSessionWindow.java     # WTS message handling
├── ServiceHelper.java           # Business logic
├── TimeCounter.java             # Time tracking data
├── WindowsUserManager.java      # Windows API utilities
└── WTSSessionCodes.java         # WTS constants

src/main/resources/
└── appsettings.json             # Configuration template

pom.xml                          # Maven configuration
```

### Testing

While the project doesn't include automated tests yet, you can manually test:

1. **Configuration Loading**: Start app and check logs for loaded users
2. **Session Events**: Log in/out and observe event logging
3. **Time Tracking**: Monitor remaining time calculations
4. **Logout Enforcement**: Set a short time limit and verify automatic logout

**Note**: The code includes a simulated logon event in `HiddenSessionWindow.start()` which is intended to be converted into an integration test.

## Future Enhancements

- [ ] Add unit and integration tests
- [ ] Database persistence for time tracking
- [ ] Web UI for configuration and monitoring
- [ ] Email/notification alerts
- [ ] Support for time exceptions (holidays, special events)
- [ ] Detailed session history reporting
- [ ] Multiple time profiles per user (weekday vs weekend)
- [ ] Grace period warnings before logout

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](https://www.apache.org/licenses/LICENSE-2.0) for details.

## Technical References

### Windows API Documentation

- [WTSRegisterSessionNotification](https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsregistersessionnotification)
- [WM_WTSSESSION_CHANGE](https://learn.microsoft.com/en-us/windows/win32/termserv/wm-wtssession-change)
- [WTSQuerySessionInformation](https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsquerysessioninformationa)

### Libraries

- [JNA (Java Native Access)](https://github.com/java-native-access/jna)
- [SLF4J (Simple Logging Facade)](http://www.slf4j.org/)
- [Jackson JSON](https://github.com/FasterXML/jackson)

## Author

This project was developed to provide a simple time management solution for Windows user sessions using Java and native Windows APIs.

## Support

For issues, questions, or contributions, please refer to the project repository.
