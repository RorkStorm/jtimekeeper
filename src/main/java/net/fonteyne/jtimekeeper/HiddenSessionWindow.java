
package net.fonteyne.jtimekeeper;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
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
 */
public class HiddenSessionWindow implements WindowProc {
    private static final Logger logger = LoggerFactory.getLogger(HiddenSessionWindow.class);
    
    private static final String WINDOW_CLASS_NAME = "HiddenSessionWindowClass";
    private static final String WINDOW_TITLE = "Hidden helper window for WM_WTSSESSION_CHANGE";
    private static final String MESSAGE_LOOP_THREAD_NAME = "win32-msg-loop";
    private static final int SUCCESS_RESULT = 0;
    
    private volatile boolean running = false;
    private Thread messageLoopThread;
    private HWND windowHandle;
    private final WString windowClassName = new WString(WINDOW_CLASS_NAME);
    private final ServiceHelper serviceHelper = new ServiceHelper();

    /**
     * Checks if the message loop is currently running.
     *
     * @return true if the window is running, false otherwise
     */
    public boolean isRunning() {
        return running;
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
     */
    public void start() {
        if (running) {
            logger.warn("Hidden window is already running");
            return;
        }
        
        running = true;
        serviceHelper.configureUsers();
        startMessageLoopThread();
        
        logger.info("Hidden session window started successfully");

        //To simulate the first LOGON -> to convert into an Integration Test
        serviceHelper.handleSessionChange("efn", WTSSessionCodes.WTS_SESSION_LOGON, 0);
    }

    /**
     * Creates and starts the message loop thread.
     */
    private void startMessageLoopThread() {
        messageLoopThread = new Thread(this::runMessageLoop, MESSAGE_LOOP_THREAD_NAME);
        messageLoopThread.setDaemon(false);
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
     */
    public void stop() {
        try {
            if (windowHandle != null) {
                unregisterSessionNotifications();
                destroyWindow();
                postQuitMessage();
            }
            logger.info("Hidden session window stopped successfully");
        } catch (Throwable t) {
            logger.warn("Error while stopping hidden window: {}", t.getMessage(), t);
        } finally {
            running = false;
        }
    }

    /**
     * Unregisters WTS session change notifications.
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
            running = false;
            logger.info("Message loop terminated");
        }
    }

    /**
     * Registers the window class with Windows.
     *
     * @return true if registration succeeded, false otherwise
     */
    private boolean registerWindowClass() {
        try {
            HMODULE moduleHandle = Kernel32.INSTANCE.GetModuleHandle("");
            WNDCLASSEX windowClass = createWindowClass(moduleHandle);
            
            if (INSTANCE.RegisterClassEx(windowClass).intValue() == 0) {
                logger.error("Failed to register window class");
                running = false;
                return false;
            }
            
            logger.debug("Window class registered successfully");
            return true;
        } catch (Exception ex) {
            logger.error("Error registering window class: {}", ex.getMessage(), ex);
            running = false;
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
     */
    private boolean createHiddenWindow() {
        try {
            HMODULE moduleHandle = Kernel32.INSTANCE.GetModuleHandle("");
            
            windowHandle = INSTANCE.CreateWindowEx(
                WS_EX_TOPMOST,
                windowClassName.toString(),
                WINDOW_TITLE,
                0, // style
                0, 0, 0, 0,
                null, null, moduleHandle, null
            );
            
            if (windowHandle == null) {
                logger.error("Failed to create hidden window");
                running = false;
                return false;
            }
            
            logger.debug("Hidden window created successfully");
            return true;
        } catch (Exception ex) {
            logger.error("Error creating hidden window: {}", ex.getMessage(), ex);
            running = false;
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
            logger.error("Error registering for session notifications: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Processes Windows messages in a loop until the window is stopped.
     */
    private void processMessages() {
        MSG message = new MSG();
        while (running && INSTANCE.GetMessage(message, windowHandle, 0, 0) != 0) {
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
     */
    @Override
    public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        if (uMsg == WTSSessionCodes.WM_WTSSESSION_CHANGE) {
            return handleSessionChangeMessage(wParam, lParam);
        }
        
        return INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    /**
     * Handles WM_WTSSESSION_CHANGE messages.
     *
     * @param wParam contains the session change code
     * @param lParam contains the session ID
     * @return an LRESULT with value 0
     */
    private LRESULT handleSessionChangeMessage(WPARAM wParam, LPARAM lParam) {
        int eventCode = wParam.intValue();
        int sessionId = lParam.intValue();
        
        String username = WindowsUserManager.getUsernameBySessionId(sessionId, false);
        logger.info("Session change detected - User: '{}', Session ID: {}, Event: {}", 
                   username, sessionId, getEventName(eventCode));
        
        processSessionEvent(username, eventCode, sessionId);
        
        return new LRESULT(SUCCESS_RESULT);
    }

    /**
     * Processes a session event based on the event code.
     *
     * @param username  the username associated with the session
     * @param eventCode the WTS session event code
     * @param sessionId the Windows session ID
     */
    private void processSessionEvent(String username, int eventCode, int sessionId) {
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
    }

    /**
     * Gets a human-readable name for a WTS session event code.
     *
     * @param eventCode the WTS session event code
     * @return the event name
     */
    private String getEventName(int eventCode) {
        switch (eventCode) {
            case WTSSessionCodes.WTS_SESSION_LOGON:
                return "WTS_SESSION_LOGON";
            case WTSSessionCodes.WTS_SESSION_LOGOFF:
                return "WTS_SESSION_LOGOFF";
            case WTSSessionCodes.WTS_SESSION_LOCK:
                return "WTS_SESSION_LOCK";
            case WTSSessionCodes.WTS_SESSION_UNLOCK:
                return "WTS_SESSION_UNLOCK";
            case WTSSessionCodes.WTS_CONSOLE_CONNECT:
                return "WTS_CONSOLE_CONNECT";
            case WTSSessionCodes.WTS_CONSOLE_DISCONNECT:
                return "WTS_CONSOLE_DISCONNECT";
            case WTSSessionCodes.WTS_REMOTE_CONNECT:
                return "WTS_REMOTE_CONNECT";
            case WTSSessionCodes.WTS_REMOTE_DISCONNECT:
                return "WTS_REMOTE_DISCONNECT";
            case WTSSessionCodes.WTS_SESSION_REMOTE_CONTROL:
                return "WTS_SESSION_REMOTE_CONTROL";
            default:
                return "UNKNOWN_EVENT_" + eventCode;
        }
    }
}
