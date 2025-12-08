
# Windows SessionChange Java Service (Maven)

Ce projet Maven démarre une application **console Java** qui écoute les évènements Windows de **changement de session** (logon/logoff/lock/unlock…) via l’API **WTS** de Windows, en utilisant **JNA** pour interagir avec le Win32.

- Réception côté JVM de `WM_WTSSESSION_CHANGE` (évènements WTS) grâce à une **fenêtre cachée** dédiée (message loop).
- Peut être hébergé comme **service Windows** via **WinSW** (XML) ou **Apache Commons Daemon – Procrun**.

> Références Microsoft : `WTSRegisterSessionNotification` (enregistrement d’une fenêtre aux notifications de session) et la famille d’évènements `WM_WTSSESSION_CHANGE`. Pour un **service** pur, Microsoft recommande de traiter `SERVICE_CONTROL_SESSIONCHANGE` via **RegisterServiceCtrlHandlerEx** ; la présente implémentation repose sur une fenêtre cachée et fonctionne aussi depuis un processus de service. [[MS Learn]](https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsregistersessionnotification) [[Service Control Handler]](https://learn.microsoft.com/en-us/windows/win32/services/service-control-handler-function)

## Build & exécution

```bash
mvn -v        # Maven 3.x, JDK 17+
mvn clean package
java -jar target/windows-sessionchange-service-1.0.0.jar
```

Les évènements sont loggués sur la sortie standard (SLF4J Simple).

## Hébergement comme service Windows

### Option 1 — WinSW (simple)
1. Télécharge `WinSW.exe` (ou `WinSW-x64.exe`). [[WinSW README]](https://github.com/winsw/winsw)
2. Place l’exe et le fichier `winsw.xml` à côté du JAR (voir `dist/winsw.xml`).
3. Ouvre un terminal **Admin** dans le dossier et exécute :
   ```bat
   WinSW-x64.exe install
   WinSW-x64.exe start
   ```

### Option 2 — Apache Commons Daemon – Procrun
Procrun permet d’envelopper un JAR en service et propose un GUI `prunmgr`. [[Apache Procrun]](https://commons.apache.org/proper/commons-daemon/procrun.html)
Un script d’exemple `dist/install-procrun.bat` est fourni (à adapter).

## Notes techniques
- Cette implémentation crée une **fenêtre cachée** et s’enregistre via `WTSRegisterSessionNotification(hWnd, NOTIFY_FOR_ALL_SESSIONS)` ; le message `WM_WTSSESSION_CHANGE` est traité dans le **WindowProc** JNA. [[MS Learn]](https://learn.microsoft.com/en-us/windows/win32/api/wtsapi32/nf-wtsapi32-wtsregistersessionnotification)
- Pour un service « à la lettre », on peut implémenter un handler **SCM** via `RegisterServiceCtrlHandlerEx` pour recevoir `SERVICE_CONTROL_SESSIONCHANGE`. Voir la doc Microsoft. [[Service Control Handler]](https://learn.microsoft.com/en-us/windows/win32/services/service-control-handler-function)

## Structure
```
src/main/java/com/example/winservice/
  ServiceApp.java            # Point d’entrée console
  HiddenSessionWindow.java   # Fenêtre cachée + boucle messages + WTS

dist/
  winsw.xml                  # Config WinSW (exécute java -jar)
  install-procrun.bat        # Script Procrun (exemple)
```

## Licence
Apache-2.0 pour ce code d’exemple.
