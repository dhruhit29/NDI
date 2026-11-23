@echo off
rem ------------------------------------------------------------------
rem  Mandir TV - program feed relay (runs on the program PC)
rem
rem  Starts MediaMTX, which takes ONE stream from OBS and serves it to
rem  every TV. Keeps restarting it if it ever stops.
rem
rem  Put mediamtx.exe (and its mediamtx.yml) in a folder named "mediamtx"
rem  next to this file. Download: https://github.com/bluenviron/mediamtx/releases
rem  (file name ends in _windows_amd64.zip)
rem ------------------------------------------------------------------
title Mandir TV relay
cd /d "%~dp0mediamtx" || (
  echo Could not find the "mediamtx" folder next to this file.
  pause
  exit /b 1
)
:loop
mediamtx.exe
echo.
echo Relay stopped. Restarting in 3 seconds... (close this window to stop it)
timeout /t 3 /nobreak >nul
goto loop
