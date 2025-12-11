package net.fonteyne.jtimekeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceApp {
  private static final Logger log = LoggerFactory.getLogger(ServiceApp.class);

  public static void main(String[] args) {
    log.info("Starting Windows SessionChange listener...");
    HiddenSessionWindow window = new HiddenSessionWindow();

    window.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown requested. Stopping hidden window...");
      window.stop();
      log.info("Stopped.");
    }));

    try {
      while (window.isRunning()) {
        Thread.sleep(1000L);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
