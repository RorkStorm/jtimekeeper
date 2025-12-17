
package net.fonteyne.jtimekeeper;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Wtsapi32;
import com.sun.jna.platform.win32.WinDef.ATOM;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class HiddenSessionWindowTest {

    @Test
    public void testStartSuccessfully() throws InterruptedException {

        HMODULE mockModuleHandle = mock(HMODULE.class);
        
        Kernel32 mockKernel32 = mock(Kernel32.class);
        User32 mockUser32 = mock(User32.class);
        Wtsapi32 mockWtsapi32 = mock(Wtsapi32.class);

        when(mockKernel32.GetModuleHandle(anyString())).thenReturn(mockModuleHandle);
        when(mockUser32.RegisterClassEx(any())).thenReturn(new ATOM(1));

        HiddenSessionWindow window = new HiddenSessionWindow(mockKernel32, mockUser32, mockWtsapi32);
        
        assertFalse(window.isRunning());
        
        window.start();
        
        // Give the message loop thread time to start
        Thread.sleep(500);
        
        assertTrue(window.isRunning());
        
        window.stop();
        
        // Give the message loop thread time to stop
        Thread.sleep(500);
        
        assertFalse(window.isRunning());
    }
}
