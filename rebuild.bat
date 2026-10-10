@echo off
cd /d "%~dp0"
setlocal
rem UTF-8 console: game logs/chat are UTF-8, otherwise cyrillic turns into mojibake
chcp 65001 >nul

rem =========================================================================
rem  DecideVisuals - build + deploy + launch in one step
rem    1. gradlew build
rem    2. copy jar to run\mods\DecideVisuals-1.0-SNAPSHOT.jar
rem    3. free disk space (dev jar is not needed to play)
rem    4. start.bat  (game)
rem    gradlew.bat build --console=plain   (pass extra gradle args)
rem
rem  NOTE: keep every comment here ASCII-only. cmd.exe reads .bat byte by byte
rem  using the OEM codepage, so cyrillic inside a rem line gets split into
rem  garbage commands ("'y' is not recognized as an internal command").
rem =========================================================================

echo [rebuild] building...
call gradlew.bat build --console=plain
if errorlevel 1 goto :build_failed

echo [rebuild] deploying jar...
copy /y "build\libs\decide-1.0-SNAPSHOT.jar" "run\mods\DecideVisuals-1.0-SNAPSHOT.jar" >nul
if errorlevel 1 goto :copy_failed

rem The build\libs copy is no longer needed - the same jar now lives in run\mods.
rem Deleting it right away frees 138 MB on an already cramped disk.
del /q "build\libs\decide-1.0-SNAPSHOT.jar" 2>nul

rem dev-jar is 130+ MB and is not used by the game, but is rebuilt every build
rem and has repeatedly filled the disk (gradle then fails with
rem "Could not write cache value").
echo [rebuild] freeing disk space...
if exist "build\devlibs" rmdir /s /q "build\devlibs"

rem A failed build leaves a 132 MB copy of the jar behind in C:\TMP. Those files
rem are never cleaned up by loom, so the disk slowly fills to zero and every
rem following build fails the same way. "if exist" does not expand wildcards,
rem so use del directly (it is a no-op when nothing matches).
del /q "C:\TMP\loom-remapJar-*.jar" 2>nul
del /q "C:\TMP\fabric-loom-src*" 2>nul

rem A running client keeps run\logs\latest.log locked. The next client then
rem fails with "Unable to delete file latest.log" and quits right after start,
rem so always stop the old one first (see stop-client.ps1).
echo [rebuild] stopping previous client...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-client.ps1"

echo [rebuild] launching game...
call start.bat
exit /b %ERRORLEVEL%

:build_failed
echo.
echo [rebuild] BUILD FAILED - jar not updated, game NOT started.
pause
exit /b 1

:copy_failed
echo.
echo [rebuild] COPY FAILED - game NOT started.
pause
exit /b 1
