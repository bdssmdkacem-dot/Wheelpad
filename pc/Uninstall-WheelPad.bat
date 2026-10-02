@echo off
setlocal
title WheelPad PC Uninstaller

net session >nul 2>&1
if not "%errorlevel%"=="0" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

taskkill /IM WheelPadServer.exe /F >nul 2>&1
del /Q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\WheelPadServer.lnk" >nul 2>&1
netsh advfirewall firewall delete rule name="WheelPad UDP 5005" >nul 2>&1
rmdir /S /Q "%ProgramFiles%\WheelPad" >nul 2>&1

echo WheelPad PC removed.
pause
