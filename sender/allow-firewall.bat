@echo off
rem Run ONCE on the program PC: right-click -> "Run as administrator".
rem Lets the TVs reach the relay on TCP 8554 (RTSP) and TCP 8888 (HLS, the
rem automatic backup the app uses if RTSP does not work on a TV).
rem Port 8890/UDP (SRT) is only needed if OBS runs on a different PC from the relay.
netsh advfirewall firewall add rule name="Mandir TV relay - RTSP" dir=in action=allow protocol=TCP localport=8554
netsh advfirewall firewall add rule name="Mandir TV relay - HLS" dir=in action=allow protocol=TCP localport=8888
netsh advfirewall firewall add rule name="Mandir TV relay - SRT" dir=in action=allow protocol=UDP localport=8890
echo.
echo Done.
pause
