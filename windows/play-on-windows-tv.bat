@echo off
rem ------------------------------------------------------------------
rem  HPCC TV - player for a TV that is connected to a Windows PC.
rem
rem  Usage:  play-on-windows-tv.bat 192.168.1.50
rem          (the IP address of the program PC)
rem
rem  Needs mpv (free): put mpv.exe in a folder named "mpv" next to this
rem  file, or install it so "mpv" works from a command prompt.
rem  Close the window (or press Q in the video) to stop.
rem ------------------------------------------------------------------
setlocal
set "PC=%~1"
if "%PC%"=="" set /p PC=Program PC IP address: 
set "URL=%PC%"
echo %URL% | find "://" >nul || set "URL=rtsp://%PC%:8554/program"

set "MPV=%~dp0mpv\mpv.exe"
if not exist "%MPV%" set "MPV=mpv"

title HPCC TV player - %URL%
:loop
"%MPV%" "%URL%" --profile=low-latency --rtsp-transport=tcp --hwdec=auto-safe --fullscreen --no-osc --osd-level=0 --cursor-autohide=always --force-window=immediate --network-timeout=8 --keep-open=no
echo.
echo Feed stopped or not available. Retrying in 3 seconds... (close this window to stop)
timeout /t 3 /nobreak >nul
goto loop
