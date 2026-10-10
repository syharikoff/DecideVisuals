@echo off
cd /d "%~dp0"
setlocal
rem UTF-8 console: game logs/chat are UTF-8, otherwise cyrillic turns into mojibake
chcp 65001 >nul

rem =========================================================================
rem  DecideVisuals - launcher
rem    PLAYER : offline nick name
rem    MEM    : java heap size (4G / 6G / 8G ...)
rem  extra args are passed to launch.ps1, e.g.
rem    start.bat -PrepareOnly     (only download/repair files, no game)
rem =========================================================================
rem loader.ps1 passes its values through the environment
if not "%PLAYER_OVERRIDE%"=="" set "PLAYER=%PLAYER_OVERRIDE%"
if not "%MEM_OVERRIDE%"=="" set "MEM=%MEM_OVERRIDE%"
if not "%PLAYER%"=="" goto :have_player
set "PLAYER=Dev"
:have_player
if not "%MEM%"=="" goto :have_mem
set "MEM=4G"
:have_mem

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1" -Player "%PLAYER%" -Mem "%MEM%" %*
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
    echo.
    echo [start] exited with code %RC%
    pause
)

endlocal
