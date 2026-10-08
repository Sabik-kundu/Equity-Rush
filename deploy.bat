@echo off
setlocal
cd /d "%~dp0"
title Bullrun launcher

rem ====== CHANGE THESE BEFORE GOING PUBLIC ======
set BULLRUN_ADMIN=ChangeMe-Admin-Key-123
set BULLRUN_GRAPH=ChangeMe-Graph-Key
set PORT=8080
rem ==============================================

rem ---- 1. Java JDK (javac is needed, not just java) ----
where javac >nul 2>nul
if errorlevel 1 (
  echo JDK not found. Installing Temurin JDK 21 with winget...
  winget install -e --id EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements
  echo.
  echo Done. CLOSE this window and double-click deploy.bat again.
  pause
  exit /b 1
)

rem ---- 2. SSH client (built into Windows 10/11) ----
where ssh >nul 2>nul
if errorlevel 1 (
  echo OpenSSH Client is missing.
  echo Enable it: Settings ^> Apps ^> Optional features ^> Add a feature ^> OpenSSH Client
  pause
  exit /b 1
)

rem ---- 3. Compile ----
if not exist out mkdir out
echo Compiling...
javac -d out src\bullrun\*.java
if errorlevel 1 (
  echo Compile failed.
  pause
  exit /b 1
)

rem ---- 4. Start the game server in its own window ----
echo Starting Bullrun on port %PORT%...
start "Bullrun server (Ctrl+C here to stop and save)" cmd /k "java -cp out bullrun.Main"
timeout /t 4 >nul

rem ---- 5. Public tunnel ----
echo.
echo ================================================================
echo  Look below for a line with your public URL, like:
echo      https://something.lhr.life
echo.
echo  Game  : that URL
echo  Admin : that URL + /admin     (key: %BULLRUN_ADMIN%)
echo  Graphs: that URL + /graph     (key: %BULLRUN_GRAPH%)
echo.
echo  Keep THIS window open. Closing it takes the site offline.
echo ================================================================
echo.
ssh -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -R 80:localhost:%PORT% nokey@localhost.run

echo.
echo Tunnel closed. You can close the server window too (Ctrl+C first so it saves).
pause
