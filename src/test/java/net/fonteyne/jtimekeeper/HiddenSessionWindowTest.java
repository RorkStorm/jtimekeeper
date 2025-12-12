
package net.fonteyne.jtimekeeper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HiddenSessionWindowTest {

    @Test
    public void testStartSuccessfully() throws InterruptedException {
        HiddenSessionWindow window = new HiddenSessionWindow();
        
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
