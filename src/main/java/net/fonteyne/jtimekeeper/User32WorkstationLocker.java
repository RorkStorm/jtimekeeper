package net.fonteyne.jtimekeeper;

import com.sun.jna.platform.win32.User32;

public class User32WorkstationLocker implements WorkstationLocker {
    @Override
    public boolean lock() {
        return User32.INSTANCE.LockWorkStation().booleanValue();
    }
}
