package net.fonteyne.jtimekeeper;

/**
 * Contains constants for Windows Terminal Services (WTS) session change notifications.
 * <p>
 * This class defines the message code and event types used to monitor and respond to
 * Windows session state changes, such as user logon/logoff, lock/unlock, and console
 * or remote desktop connections/disconnections.
 * </p>
 */
public class WTSSessionCodes {

    // This is a constant representing the session change message code in Windows Terminal Services (WTS)
    public static final int WM_WTSSESSION_CHANGE = 0x2B1;   // 689

    // These are constants representing different types of session events
    public static final int WTS_CONSOLE_CONNECT = 0x1;
    public static final int WTS_CONSOLE_DISCONNECT = 0x2;
    public static final int WTS_REMOTE_CONNECT = 0x3;
    public static final int WTS_REMOTE_DISCONNECT = 0x4;
    public static final int WTS_SESSION_LOGON = 0x5;
    public static final int WTS_SESSION_LOGOFF = 0x6;
    public static final int WTS_SESSION_LOCK = 0x7;
    public static final int WTS_SESSION_UNLOCK = 0x8;
    public static final int WTS_SESSION_REMOTE_CONTROL = 0x9;
}