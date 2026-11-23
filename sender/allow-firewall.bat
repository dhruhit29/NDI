@echo off
rem Run ONCE on the program PC: right-click -> "Run as administrator".
rem Lets the TVs reach the relay on TCP port 8554 (RTSP).
rem Port 8890/UDP (SRT) is only needed if OBS runs on a different PC from the relay.
netsh advfirewall firewall add rule name="Mandir TV relay - RTSP" dir=in action=allow protocol=TCP localport=8554
netsh advfirewall firewall add rule name="Mandir TV relay - SRT" dir=in action=allow protocol=UDP localport=8890
echo.
echo Done.
pause
