package net.fonteyne.jtimekeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d’entrée de l’application console.
 * Démarre une fenêtre cachée qui s’enregistre aux notifications de session Windows
 * et journalise les évènements (logon/logoff/lock/unlock).
 */
public class ServiceApp {
  private static final Logger log = LoggerFactory.getLogger(ServiceApp.class);

  public static void main(String[] args) {
    log.info("Starting Windows SessionChange listener...");
    HiddenSessionWindow window = new HiddenSessionWindow();

    // Démarre la boucle de messages (thread dédié)
    window.start();

    // Hook d’arrêt propre
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown requested. Stopping hidden window...");
      window.stop();
      log.info("Stopped.");
    }));

    // Boucle d’attente simple pour garder le processus vivant
    try {
      // Le thread de la fenêtre vit indéfiniment ; le thread principal dort.
      while (window.isRunning()) {
        Thread.sleep(1000L);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
