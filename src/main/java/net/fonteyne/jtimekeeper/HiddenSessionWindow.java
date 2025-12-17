
package net.fonteyne.jtimekeeper;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.platform.win32.Wtsapi32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.sun.jna.platform.win32.User32.*;

/**
 * Creates a hidden window and registers for Windows Terminal Services session change notifications.
 * <p>
 * This class implements a Windows message loop to receive WM_WTSSESSION_CHANGE events,
 * which notify the application of user session state changes (logon, logoff, lock, unlock, etc.).
 * </p>
 * <p>
 * Microsoft Reference: 
 * <a href="https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsregistersessionnotification">
 * WTSRegisterSessionNotification
 * </a>
 * </p>
 * 
 * <p><b>Security Considerations:</b></p>
 * <ul>
 *   <li>Thread-safe using AtomicBoolean for state management</li>
 *   <li>Input validation on session IDs and event codes</li>
 *   <li>Proper resource cleanup to prevent memory leaks</li>
 *   <li>Username sanitization to prevent injection attacks</li>
 * </ul>
 */
public class HiddenSessionWindow implements WindowProc {
    private static final Logger logger = LoggerFactory.getLogger(HiddenSessionWindow.class);
    
    private static final String WINDOW_CLASS_NAME = "HiddenSessionWindowClass";
    private static final String WINDOW_TITLE = "Hidden helper window for WM_WTSSESSION_CHANGE";
    private static final String MESSAGE_LOOP_THREAD_NAME = "win32-msg-loop";
    private static final int SUCCESS_RESULT = 0;
    
    // Security: Maximum allowed session ID to prevent integer overflow attacks
    private static final int MAX_SESSION_ID = 65535;
    
    // Security: Maximum username length to prevent buffer overflow
    private static final int MAX_USERNAME_LENGTH = 256;
    
    // Security: Pattern to validate usernames (alphanumeric, underscore, hyphen, backslash for domain)
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\\\]{1," + MAX_USERNAME_LENGTH + "}$");
    
    // Security: Use AtomicBoolean for thread-safe state management
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private volatile Thread messageLoopThread;
    private volatile HWND windowHandle;
    private final WString windowClassName = new WString(WINDOW_CLASS_NAME);
    private final ServiceHelper serviceHelper = new ServiceHelper();

    private final Kernel32 kernel32;
    private final User32 user32;
    private final Wtsapi32 wtsapi32;

    // Constructor for production use
    public HiddenSessionWindow() {
        this(Kernel32.INSTANCE, INSTANCE, Wtsapi32.INSTANCE);
    }

    // Constructor for testing (package-private)
    HiddenSessionWindow(Kernel32 kernel32, User32 user32, Wtsapi32 wtsapi32) {
        this.kernel32 = kernel32;
        this.user32 = user32;
        this.wtsapi32 = wtsapi32;
    }

    /**
     * Checks if the message loop is currently running.
     *
     * @return true if the window is running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts the hidden window message loop and initializes session monitoring.
     * <p>
     * This method creates and starts a non-daemon thread that runs the Windows message loop
     * to receive WM_WTSSESSION_CHANGE notifications. It also configures users from the
     * configuration file.
     * </p>
     * <p>
     * If the window is already running, this method returns immediately without taking any action.
     * </p>
     * 
     * <p><b>Security:</b> Uses atomic compare-and-set to prevent race conditions.</p>
     */
    public void start() {
        // Security: Atomic check-and-set to prevent race conditions
        if (!running.compareAndSet(false, true)) {
            logger.warn("Hidden window is already running");
            return;
        }
        
        try {
            serviceHelper.configureUsers();
            startMessageLoopThread();
            
            logger.info("Hidden session window started successfully");
        } catch (Exception ex) {
            logger.error("Failed to start hidden session window", ex);
            running.set(false);
            throw new IllegalStateException("Failed to start session window", ex);
        }
    }

    /**
     * Creates and starts the message loop thread.
     * 
     * <p><b>Security:</b> Thread is created with controlled name and daemon status.</p>
     */
    private void startMessageLoopThread() {
        messageLoopThread = new Thread(this::runMessageLoop, MESSAGE_LOOP_THREAD_NAME);
        messageLoopThread.setDaemon(false);
        
        // Security: Set uncaught exception handler
        messageLoopThread.setUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in message loop thread", throwable);
            running.set(false);
        });
        
        messageLoopThread.start();
    }

    /**
     * Stops the hidden window message loop and cleans up session monitoring resources.
     * <p>
     * This method performs the following cleanup operations:
     * <ul>
     *   <li>Unregisters WTS session change notifications</li>
     *   <li>Destroys the hidden window</li>
     *   <li>Posts a WM_QUIT message to terminate the message loop</li>
     *   <li>Sets the running flag to false</li>
     * </ul>
     * </p>
     * <p>
     * This method is safe to call multiple times or when the window is not running.
     * </p>
     * 
     * <p><b>Security:</b> Ensures proper cleanup even in case of exceptions.</p>
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            logger.debug("Hidden window is not running, nothing to stop");
            return;
        }
        
        try {
            if (windowHandle != null) {
                unregisterSessionNotifications();
                destroyWindow();
                postQuitMessage();
            }
            
            // Security: Wait for thread to terminate with timeout
            if (messageLoopThread != null && messageLoopThread.isAlive()) {
                try {
                    messageLoopThread.join(5000); // 5 second timeout
                    if (messageLoopThread.isAlive()) {
                        logger.warn("Message loop thread did not terminate within timeout");
                    }
                } catch (InterruptedException ex) {
                    logger.warn("Interrupted while waiting for message loop thread to terminate");
                    Thread.currentThread().interrupt();
                }
            }
            
            logger.info("Hidden session window stopped successfully");
        } catch (Throwable t) {
            logger.error("Error while stopping hidden window", t);
        } finally {
            // Security: Ensure running flag is always set to false
            running.set(false);
            windowHandle = null;
            messageLoopThread = null;
        }
    }

    /**
     * Unregisters WTS session change notifications.
     * 
     * <p><b>Security:</b> Handles exceptions to prevent resource leaks.</p>
     */
    private void unregisterSessionNotifications() {
        try {
            Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(windowHandle);
            logger.debug("Unregistered WTS session notifications");
        } catch (Exception ex) {
            logger.warn("Failed to unregister session notifications: {}", ex.getMessage());
        }
    }

    /**
     * Destroys the hidden window.
     * 
     * <p><b>Security:</b> Handles exceptions to prevent resource leaks.</p>
     */
    private void destroyWindow() {
        try {
            INSTANCE.DestroyWindow(windowHandle);
            logger.debug("Window destroyed");
        } catch (Exception ex) {
            logger.warn("Failed to destroy window: {}", ex.getMessage());
        }
    }

    /**
     * Posts a WM_QUIT message to exit the message loop.
     * 
     * <p><b>Security:</b> Handles exceptions gracefully.</p>
     */
    private void postQuitMessage() {
        try {
            INSTANCE.PostQuitMessage(SUCCESS_RESULT);
            logger.debug("Posted quit message");
        } catch (Exception ex) {
            logger.warn("Failed to post quit message: {}", ex.getMessage());
        }
    }

    /**
     * Runs the Windows message loop for the hidden window.
     * <p>
     * This method performs the following operations:
     * <ul>
     *   <li>Registers a window class with the Windows API</li>
     *   <li>Creates a hidden topmost window to receive session change notifications</li>
     *   <li>Registers the window for WTS session change notifications for all sessions</li>
     *   <li>Enters a message loop to process Windows messages until the window is stopped</li>
     * </ul>
     * </p>
     * 
     * <p><b>Security:</b> Comprehensive error handling and resource cleanup.</p>
     */
    private void runMessageLoop() {
        try {
            if (!registerWindowClass()) {
                return;
            }
            
            if (!createHiddenWindow()) {
                return;
            }
            
            if (!registerForSessionNotifications()) {
                logger.warn("Failed to register for session notifications, but continuing...");
            }
            
            processMessages();
            
        } catch (Throwable t) {
            logger.error("Message loop error", t);
        } finally {
            running.set(false);
            logger.info("Message loop terminated");
        }
    }

    /**
     * Registers the window class with Windows.
     *
     * @return true if registration succeeded, false otherwise
     * 
     * <p><b>Security:</b> Validates module handle before use.</p>
     */
    private boolean registerWindowClass() {
        try {
            HMODULE moduleHandle = kernel32.GetModuleHandle("");
            
            // Security: Validate module handle
            if (moduleHandle == null) {
                logger.error("Failed to get module handle");
                running.set(false);
                return false;
            }
            
            WNDCLASSEX windowClass = createWindowClass(moduleHandle);
            
            if (INSTANCE.RegisterClassEx(windowClass).intValue() == 0) {
                logger.error("Failed to register window class");
                running.set(false);
                return false;
            }
            
            logger.debug("Window class registered successfully");
            return true;
        } catch (Exception ex) {
            logger.error("Error registering window class", ex);
            running.set(false);
            return false;
        }
    }

    /**
     * Creates a WNDCLASSEX structure for the window class.
     *
     * @param moduleHandle the module handle
     * @return the configured WNDCLASSEX
     */
    private WNDCLASSEX createWindowClass(HMODULE moduleHandle) {
        WNDCLASSEX windowClass = new WNDCLASSEX();
        windowClass.hInstance = moduleHandle;
        windowClass.lpfnWndProc = this;
        windowClass.lpszClassName = windowClassName.toString();
        return windowClass;
    }

    /**
     * Creates the hidden window.
     *
     * @return true if creation succeeded, false otherwise
     * 
     * <p><b>Security:</b> Validates module handle and window handle.</p>
     */
    private boolean createHiddenWindow() {
        try {
            HMODULE moduleHandle = kernel32.GetModuleHandle("");
            
            // Security: Validate module handle
            if (moduleHandle == null) {
                logger.error("Failed to get module handle for window creation");
                running.set(false);
                return false;
            }
            
            windowHandle = INSTANCE.CreateWindowEx(
                WS_EX_TOPMOST,
                windowClassName.toString(),
                WINDOW_TITLE,
                0, // style
                0, 0, 0, 0,
                null, null, moduleHandle, null
            );
            
            // Security: Validate window handle
            if (windowHandle == null) {
                logger.error("Failed to create hidden window");
                running.set(false);
                return false;
            }
            
            logger.debug("Hidden window created successfully");
            return true;
        } catch (Exception ex) {
            logger.error("Error creating hidden window", ex);
            running.set(false);
            return false;
        }
    }

    /**
     * Registers the window for WTS session notifications.
     *
     * @return true if registration succeeded, false otherwise
     */
    private boolean registerForSessionNotifications() {
        try {
            boolean success = Wtsapi32.INSTANCE.WTSRegisterSessionNotification(
                windowHandle, 
                Wtsapi32.NOTIFY_FOR_ALL_SESSIONS
            );
            
            if (!success) {
                logger.error("WTSRegisterSessionNotification failed");
                return false;
            }
            
            logger.info("Registered for WTS session notifications (all sessions)");
            return true;
        } catch (Exception ex) {
            logger.error("Error registering for session notifications", ex);
            return false;
        }
    }

    /**
     * Processes Windows messages in a loop until the window is stopped.
     * 
     * <p><b>Security:</b> Checks running state to prevent infinite loops.</p>
     */
    private void processMessages() {
        MSG message = new MSG();
        while (running.get() && INSTANCE.GetMessage(message, windowHandle, 0, 0) != 0) {
            INSTANCE.TranslateMessage(message);
            INSTANCE.DispatchMessage(message);
        }
    }

    /**
     * Windows message callback procedure that processes messages sent to the hidden window.
     * <p>
     * This method is invoked by the Windows message loop to handle window messages. It specifically
     * processes WM_WTSSESSION_CHANGE messages to detect and respond to Windows Terminal Services
     * session state changes.
     * </p>
     *
     * @param hwnd   the handle to the window receiving the message
     * @param uMsg   the message identifier
     * @param wParam additional message-specific information (session change code for WM_WTSSESSION_CHANGE)
     * @param lParam additional message-specific information (session ID for WM_WTSSESSION_CHANGE)
     * @return an LRESULT containing 0 for WM_WTSSESSION_CHANGE, or DefWindowProc result for other messages
     * 
     * <p><b>Security:</b> Validates input parameters and handles exceptions.</p>
     */
    @Override
    public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        try {
            if (uMsg == WTSSessionCodes.WM_WTSSESSION_CHANGE) {
                return handleSessionChangeMessage(wParam, lParam);
            }
            
            return INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
        } catch (Exception ex) {
            logger.error("Error in window callback", ex);
            return new LRESULT(SUCCESS_RESULT);
        }
    }

    /**
     * Handles WM_WTSSESSION_CHANGE messages.
     *
     * @param wParam contains the session change code
     * @param lParam contains the session ID
     * @return an LRESULT with value 0
     * 
     * <p><b>Security:</b> Validates session ID and sanitizes username.</p>
     */
    private LRESULT handleSessionChangeMessage(WPARAM wParam, LPARAM lParam) {
        int eventCode = wParam.intValue();
        int sessionId = lParam.intValue();
        
        // Security: Validate session ID to prevent integer overflow
        if (!isValidSessionId(sessionId)) {
            logger.warn("Invalid session ID received: {}", sessionId);
            return new LRESULT(SUCCESS_RESULT);
        }
        
        // Security: Validate event code
        if (!isValidEventCode(eventCode)) {
            logger.warn("Invalid event code received: {}", eventCode);
            return new LRESULT(SUCCESS_RESULT);
        }
        
        String username = WindowsUserManager.getUsernameBySessionId(sessionId, false);
        
        // Security: Sanitize username to prevent injection attacks
        String sanitizedUsername = sanitizeUsername(username);
        
        // Security: Use parameterized logging to prevent log injection
        logger.info("Session change detected - User: '{}', Session ID: {}, Event: {}", 
                   sanitizedUsername, sessionId, getEventName(eventCode));
        
        processSessionEvent(sanitizedUsername, eventCode, sessionId);
        
        return new LRESULT(SUCCESS_RESULT);
    }

    /**
     * Validates a session ID.
     * 
     * @param sessionId the session ID to validate
     * @return true if valid, false otherwise
     * 
     * <p><b>Security:</b> Prevents integer overflow and negative values.</p>
     */
    private boolean isValidSessionId(int sessionId) {
        return sessionId >= 0 && sessionId <= MAX_SESSION_ID;
    }

    /**
     * Validates an event code.
     * 
     * @param eventCode the event code to validate
     * @return true if valid, false otherwise
     * 
     * <p><b>Security:</b> Ensures event code is within expected range.</p>
     */
    private boolean isValidEventCode(int eventCode) {
        return eventCode >= WTSSessionCodes.WTS_CONSOLE_CONNECT && 
               eventCode <= WTSSessionCodes.WTS_SESSION_REMOTE_CONTROL;
    }

    /**
     * Sanitizes a username to prevent injection attacks.
     * 
     * @param username the username to sanitize
     * @return the sanitized username, or "INVALID" if validation fails
     * 
     * <p><b>Security:</b> Validates against whitelist pattern and length limits.</p>
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Security: Enforce maximum length
        if (username.length() > MAX_USERNAME_LENGTH) {
            logger.warn("Username exceeds maximum length: {}", username.length());
            return "INVALID_LENGTH";
        }
        
        // Security: Validate against whitelist pattern
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            logger.warn("Username contains invalid characters");
            return "INVALID_CHARS";
        }
        
        return username;
    }

    /**
     * Processes a session event based on the event code.
     *
     * @param username  the username associated with the session
     * @param eventCode the WTS session event code
     * @param sessionId the Windows session ID
     * 
     * <p><b>Security:</b> Handles exceptions to prevent service disruption.</p>
     */
    private void processSessionEvent(String username, int eventCode, int sessionId) {
        try {
            switch (eventCode) {
                case WTSSessionCodes.WTS_SESSION_LOGON:
                case WTSSessionCodes.WTS_SESSION_LOGOFF:
                case WTSSessionCodes.WTS_SESSION_LOCK:
                case WTSSessionCodes.WTS_SESSION_UNLOCK:
                    serviceHelper.handleSessionChange(username, eventCode, sessionId);
                    break;
                    
                case WTSSessionCodes.WTS_CONSOLE_CONNECT:
                case WTSSessionCodes.WTS_CONSOLE_DISCONNECT:
                case WTSSessionCodes.WTS_REMOTE_CONNECT:
                case WTSSessionCodes.WTS_REMOTE_DISCONNECT:
                case WTSSessionCodes.WTS_SESSION_REMOTE_CONTROL:
                    logger.debug("Informational event: {}", getEventName(eventCode));
                    break;
                    
                default:
                    logger.debug("Unknown WM_WTSSESSION_CHANGE event code: {}", eventCode);
            }
        } catch (Exception ex) {
            logger.error("Error processing session event for user '{}', event: {}", 
                        username, getEventName(eventCode), ex);
        }
    }

    /**
     * Gets a human-readable name for a WTS session event code.
     *
     * @param eventCode the WTS session event code
     * @return the event name
     */
    private String getEventName(int eventCode) {
        return switch (eventCode) {
            case WTSSessionCodes.WTS_SESSION_LOGON -> "WTS_SESSION_LOGON";
            case WTSSessionCodes.WTS_SESSION_LOGOFF -> "WTS_SESSION_LOGOFF";
            case WTSSessionCodes.WTS_SESSION_LOCK -> "WTS_SESSION_LOCK";
            case WTSSessionCodes.WTS_SESSION_UNLOCK -> "WTS_SESSION_UNLOCK";
            case WTSSessionCodes.WTS_CONSOLE_CONNECT -> "WTS_CONSOLE_CONNECT";
            case WTSSessionCodes.WTS_CONSOLE_DISCONNECT -> "WTS_CONSOLE_DISCONNECT";
            case WTSSessionCodes.WTS_REMOTE_CONNECT -> "WTS_REMOTE_CONNECT";
            case WTSSessionCodes.WTS_REMOTE_DISCONNECT -> "WTS_REMOTE_DISCONNECT";
            case WTSSessionCodes.WTS_SESSION_REMOTE_CONTROL -> "WTS_SESSION_REMOTE_CONTROL";
            default -> "UNKNOWN_EVENT_" + eventCode;
        };
    }
}
