
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
 * Creates a hidden window and registers for WTS (session change) notifications.
 * Receives WM_WTSSESSION_CHANGE and logs the events.
 *
 * Microsoft Reference: WTSRegisterSessionNotification (window) and WM_WTSSESSION_CHANGE
 * https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsregistersessionnotification
 */
public class HiddenSessionWindow implements WindowProc {
  private static final Logger log = LoggerFactory.getLogger(HiddenSessionWindow.class);

  private volatile boolean running = false;
  private Thread pumpThread;
  private HWND hWnd;
  private WString windowClass = new WString("HiddenSessionWindowClass");
  private ServiceHelper svcHelper = new ServiceHelper();

  public boolean isRunning() { return running; }

    /**
   * Starts the hidden window message loop and initializes session monitoring.
   * <p>
   * This method creates and starts a non-daemon thread that runs the Windows message loop
   * to receive WM_WTSSESSION_CHANGE notifications. It also configures users and simulates
   * an initial logon event for testing purposes.
   * </p>
   * <p>
   * If the window is already running, this method returns immediately without taking any action.
   * </p>
   * <p>
   * Note: The simulated logon event for user "Eric" is intended to be converted into an
   * integration test in the future.
   * </p>
   */
  public void start() {
    if (running) return;
    running = true;
    pumpThread = new Thread(this::messageLoop, "win32-msg-loop");
    pumpThread.setDaemon(false);
    pumpThread.start();

    svcHelper.configureUsers();

    //To simulate the first LOGON -> to convert into an Integration Test
    svcHelper.handleSessionChange("Eric", WTSSessionCodes.WTS_SESSION_LOGON, 0);
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
   * If the window handle is null or if any errors occur during cleanup, they are logged
   * as warnings but do not prevent the method from completing. The running flag is always
   * set to false in the finally block to ensure proper state management.
   * </p>
   * <p>
   * This method is safe to call multiple times or when the window is not running.
   * </p>
   */
  public void stop() {
    try {
      if (hWnd != null) {
        // Unregister WTS notifications
        Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hWnd);
        // Destroy the window
        INSTANCE.DestroyWindow(hWnd);
        // Post a WM_QUIT to exit GetMessage
        INSTANCE.PostQuitMessage(0);
      }
    } catch (Throwable t) {
      log.warn("Error while stopping hidden window: {}", t.getMessage());
    } finally {
      running = false;
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
   * <p>
   * The message loop continues running until either the {@code running} flag is set to false
   * or a WM_QUIT message is received. All Windows messages are translated and dispatched to
   * the window procedure callback.
   * </p>
   * <p>
   * If any errors occur during window registration, creation, or notification registration,
   * they are logged and the {@code running} flag is set to false. The {@code running} flag
   * is always set to false in the finally block when the message loop terminates.
   * </p>
   * <p>
   * This method is intended to run on a dedicated thread and should not be called directly
   * from the main application thread.
   * </p>
   */
  private void messageLoop() {
    try {
      // Register the window class
      HMODULE hInst = Kernel32.INSTANCE.GetModuleHandle("");
      WNDCLASSEX wClass = new WNDCLASSEX();
      wClass.hInstance = hInst;
      wClass.lpfnWndProc = this;
      wClass.lpszClassName = String.valueOf(windowClass);
      if (INSTANCE.RegisterClassEx(wClass).intValue() == 0) {
        log.error("RegisterClassEx failed");
        running = false; return;
      }

      // Create the hidden window (0x80000000 WS_EX_NOREDIRECTIONBITMAP or simply topmost)
      hWnd = INSTANCE.CreateWindowEx(
          WS_EX_TOPMOST,
          String.valueOf(windowClass),
          "Hidden helper window for WM_WTSSESSION_CHANGE",
          0, // style
          0, 0, 0, 0,
          null, null, hInst, null);
      if (hWnd == null) {
        log.error("CreateWindowEx failed");
        running = false; return;
      }

      // Register for session notifications for all sessions
      boolean ok = Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hWnd, Wtsapi32.NOTIFY_FOR_ALL_SESSIONS);
      if (!ok) {
        log.error("WTSRegisterSessionNotification failed");
      } else {
        log.info("Hidden window created and registered for session notifications.");
      }

      // Message loop
      MSG msg = new MSG();
      while (running && INSTANCE.GetMessage(msg, hWnd, 0, 0) != 0) {
        INSTANCE.TranslateMessage(msg);
        INSTANCE.DispatchMessage(msg);
      }

    } catch (Throwable t) {
      log.error("Message loop error", t);
    } finally {
      running = false;
      log.info("Message loop terminated.");
    }
  }

    /**
   * Windows message callback procedure that processes messages sent to the hidden window.
   * <p>
   * This method is invoked by the Windows message loop to handle window messages. It specifically
   * processes WM_WTSSESSION_CHANGE messages to detect and respond to Windows Terminal Services
   * session state changes (logon, logoff, lock, unlock, etc.). For all other messages, it delegates
   * to the default Windows message handler.
   * </p>
   * <p>
   * When a WM_WTSSESSION_CHANGE message is received, this method:
   * <ul>
   *   <li>Extracts the session change code and session ID from the message parameters</li>
   *   <li>Retrieves the username associated with the session ID</li>
   *   <li>Logs the session change event</li>
   *   <li>Delegates to {@link ServiceHelper#handleSessionChange} for logon, logoff, lock, and unlock events</li>
   * </ul>
   * </p>
   *
   * @param hwnd the handle to the window receiving the message
   * @param uMsg the message identifier (e.g., WM_WTSSESSION_CHANGE)
   * @param wParam additional message-specific information; for WM_WTSSESSION_CHANGE, contains the session change code
   * @param lParam additional message-specific information; for WM_WTSSESSION_CHANGE, contains the session ID
   * @return an LRESULT containing 0 for WM_WTSSESSION_CHANGE messages, or the result of DefWindowProc for all other messages
   */
  @Override
  public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
    if (uMsg == WTSSessionCodes.WM_WTSSESSION_CHANGE) {
      int code = wParam.intValue();
      int sessionID = lParam.intValue();

      String currentUser = WindowsUserManager.getUsernameBySessionId(sessionID, false);
      log.info("Session change detected for user '{}', session ID {}", currentUser, sessionID);

      switch (code) {
        case WTSSessionCodes.WTS_SESSION_LOGON:
          log.info("WTS_SESSION_LOGON");
          svcHelper.handleSessionChange(currentUser, code, sessionID);
          break;
        case WTSSessionCodes.WTS_SESSION_LOGOFF:
          log.info("WTS_SESSION_LOGOFF");
            svcHelper.handleSessionChange(currentUser, code, sessionID);
          break;
        case WTSSessionCodes.WTS_SESSION_LOCK:
          log.info("WTS_SESSION_LOCK");
            svcHelper.handleSessionChange(currentUser, code, sessionID);
          break;
        case WTSSessionCodes.WTS_SESSION_UNLOCK:
          log.info("WTS_SESSION_UNLOCK");
            svcHelper.handleSessionChange(currentUser, code, sessionID);
          break;
        case WTSSessionCodes.WTS_CONSOLE_CONNECT:    log.info("WTS_CONSOLE_CONNECT"); break;
        case WTSSessionCodes.WTS_CONSOLE_DISCONNECT: log.info("WTS_CONSOLE_DISCONNECT"); break;
        case WTSSessionCodes.WTS_REMOTE_CONNECT:     log.info("WTS_REMOTE_CONNECT"); break;
        case WTSSessionCodes.WTS_REMOTE_DISCONNECT:  log.info("WTS_REMOTE_DISCONNECT"); break;
        case WTSSessionCodes.WTS_SESSION_REMOTE_CONTROL: log.info("WTS_SESSION_REMOTE_CONTROL"); break;
        default: log.info("WM_WTSSESSION_CHANGE code={}", code);
      }
      return new LRESULT(0);
    }
    // Default behavior
    return INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
  }

}
