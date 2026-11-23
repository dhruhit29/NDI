@echo off
rem ------------------------------------------------------------------
rem  Mandir TV - install / update the app on one Fire TV from a Windows PC.
rem
rem  Usage:  install-on-firetv.bat <TV-IP> <PROGRAM-PC-IP>
rem  Example: install-on-firetv.bat 192.168.1.101 192.168.1.50
rem
rem  Before the first run:
rem   1. Put MandirTV.apk next to this file.
rem   2. Put Google's "platform-tools" folder (adb.exe) next to this file:
rem      https://developer.android.com/tools/releases/platform-tools
rem   3. On the TV: Settings > My Fire TV / Device > Developer options >
rem      turn ON "ADB debugging" (see README for how to show Developer options).
rem ------------------------------------------------------------------
setlocal
if "%~2"=="" (
  echo Usage: %~nx0 ^<TV-IP^> ^<PROGRAM-PC-IP^>
  echo Example: %~nx0 192.168.1.101 192.168.1.50
  exit /b 1
)
set "TV=%~1:5555"
set "PC=%~2"
set "APK=%~dp0MandirTV.apk"
set "ADB=%~dp0platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"
set "PKG=org.mandir.tvreceiver"

if not exist "%APK%" (
  echo Cannot find %APK%
  exit /b 1
)

echo Connecting to the TV at %TV% ...
"%ADB%" connect %TV%
echo.
echo If the TV now asks "Allow USB debugging?", tick "Always allow from this computer"
echo and press OK on the remote. Then come back here.
pause

echo Installing the app ...
"%ADB%" -s %TV% install -r "%APK%"
if errorlevel 1 (
  echo.
  echo Install FAILED. Check that ADB debugging is on and you allowed this PC on the TV.
  exit /b 1
)

echo Allowing the app to open itself when the TV is switched on ...
"%ADB%" -s %TV% shell appops set %PKG% SYSTEM_ALERT_WINDOW allow

echo Setting the program PC address and starting the app ...
"%ADB%" -s %TV% shell am start -n %PKG%/.PlayerActivity -e url rtsp://%PC%:8554/program

"%ADB%" disconnect %TV% >nul
echo.
echo Done. The TV should now show the program feed.
exit /b 0
