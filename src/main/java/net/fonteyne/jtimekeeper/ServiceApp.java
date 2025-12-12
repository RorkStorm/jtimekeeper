package net.fonteyne.jtimekeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the console application.
 * Starts a hidden window that registers for Windows session notifications
 * and logs events (logon/logoff/lock/unlock).
 */
public class ServiceApp {
  private static final Logger log = LoggerFactory.getLogger(ServiceApp.class);

  public static void main(String[] args) {
    log.info("Starting Windows SessionChange listener...");
    HiddenSessionWindow window = new HiddenSessionWindow();

    // Starts the message loop (dedicated thread)
    window.start();

    // Clean shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown requested. Stopping hidden window...");
      window.stop();
      log.info("Stopped.");
    }));

    // Simple wait loop to keep the process alive
    try {
      // The window thread lives indefinitely; the main thread sleeps.
      while (window.isRunning()) {
        Thread.sleep(1000L);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
