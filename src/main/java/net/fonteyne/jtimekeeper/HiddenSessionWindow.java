
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

public class HiddenSessionWindow implements WindowProc {
  private static final Logger log = LoggerFactory.getLogger(HiddenSessionWindow.class);

  private volatile boolean running = false;
  private Thread pumpThread;
  private HWND hWnd;
  private WString windowClass = new WString("HiddenSessionWindowClass");
  private ServiceHelper svcHelper = new ServiceHelper();

  public boolean isRunning() { return running; }

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

  public void stop() {
    try {
      if (hWnd != null) {
        Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hWnd);

        INSTANCE.DestroyWindow(hWnd);

        INSTANCE.PostQuitMessage(0);
      }
    } catch (Throwable t) {
      log.warn("Error while stopping hidden window: {}", t.getMessage());
    } finally {
      running = false;
    }
  }

  private void messageLoop() {
    try {

      HMODULE hInst = Kernel32.INSTANCE.GetModuleHandle("");
      WNDCLASSEX wClass = new WNDCLASSEX();
      wClass.hInstance = hInst;
      wClass.lpfnWndProc = this;
      wClass.lpszClassName = String.valueOf(windowClass);
      if (INSTANCE.RegisterClassEx(wClass).intValue() == 0) {
        log.error("RegisterClassEx failed");
        running = false; return;
      }

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

      boolean ok = Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hWnd, Wtsapi32.NOTIFY_FOR_ALL_SESSIONS);
      if (!ok) {
        log.error("WTSRegisterSessionNotification failed");
      } else {
        log.info("Hidden window created and registered for session notifications.");
      }

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

    return INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
  }

}
