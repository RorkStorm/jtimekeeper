package net.fonteyne.jtimekeeper;

public class WTSSessionCodes {
    // Code du message envoyé par TS pour les changements de session
    public static final int WM_WTSSESSION_CHANGE = 0x2B1;  // 689

    // Codes WTS (wParam)
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

