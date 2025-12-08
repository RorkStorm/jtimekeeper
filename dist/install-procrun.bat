
@echo off
setlocal ENABLEDELAYEDEXPANSION

REM Adapter les chemins ci-dessous
set SERVICE_NAME=WinSessionSvc
set BASE_DIR=C:\services\winsession
set PRUNSRV=%BASE_DIR%\prunsrv.exe
set JAR=%BASE_DIR%\windows-sessionchange-service-1.0.0.jar

"%PRUNSRV%" //IS//%SERVICE_NAME% ^
  --DisplayName="Windows SessionChange Java Service" ^
  --Startup=auto ^
  --Jvm=auto ^
  --Classpath="%JAR%" ^
  --StartMode=jvm --StopMode=jvm ^
  --StartClass=net.fonteyne.jtimekeeper.ServiceApp --StartMethod=main ^
  --StopClass=net.fonteyne.jtimekeeper.ServiceApp  --StopMethod=main ^
  --LogPath="%BASE_DIR%\logs" --LogLevel=Info

echo Installed %SERVICE_NAME%
