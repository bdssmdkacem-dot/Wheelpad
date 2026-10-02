@echo off
setlocal
title WheelPad PC Installer

echo.
echo ========================================
echo          WheelPad PC Installer
echo ========================================
echo.

net session >nul 2>&1
if not "%errorlevel%"=="0" (
  echo Requesting Administrator permission...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

set "INSTALL_DIR=%ProgramFiles%\WheelPad"
set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"

if not exist "%~dp0WheelPadServer.exe" (
  echo ERROR: WheelPadServer.exe was not found next to this installer.
  pause
  exit /b 1
)

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
copy /Y "%~dp0WheelPadServer.exe" "%INSTALL_DIR%\WheelPadServer.exe" >nul

netsh advfirewall firewall delete rule name="WheelPad UDP 5005" >nul 2>&1
netsh advfirewall firewall add rule name="WheelPad UDP 5005" dir=in action=allow protocol=UDP localport=5005 profile=private enable=yes >nul

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws=New-Object -ComObject WScript.Shell; $s=$ws.CreateShortcut([Environment]::GetFolderPath('Startup') + '\WheelPadServer.lnk'); $s.TargetPath='%INSTALL_DIR%\WheelPadServer.exe'; $s.WorkingDirectory='%INSTALL_DIR%'; $s.WindowStyle=1; $s.Save()"

echo.
echo WheelPad PC installed successfully.
echo Firewall: UDP 5005 allowed on Private networks.
echo Auto-start: enabled for the current Windows user.
echo.
echo Starting WheelPadServer...
start "" "%INSTALL_DIR%\WheelPadServer.exe"
exit /b 0
