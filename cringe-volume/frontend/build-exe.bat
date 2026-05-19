@echo off
REM ============================================================
REM  Build Cringe Volume Player -> standalone .exe installer
REM  Output: target\installer\Cringe Volume Player-1.0.0.exe
REM ============================================================

REM === CONFIG ===
SET "JAVAFX_JMODS=C:\Users\viplu\Desktop\javafx-jmods-17.0.19"
SET "APP_VERSION=1.0.1"
SET "BACKEND_URL=https://pay.vernovpn.com"
SET "MVN=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd"

REM Add WiX to PATH immediately (path contains parentheses, unsafe inside if-blocks)
SET "PATH=C:\Program Files (x86)\WiX Toolset v3.14\bin;%PATH%"

REM === CHECKS ===
echo.
echo ========== Environment check ==========

if not exist "%JAVAFX_JMODS%\javafx.base.jmod" (
    echo [X] JAVAFX_JMODS not found: %JAVAFX_JMODS%
    pause
    exit /b 1
)
echo [OK] JavaFX jmods

where jpackage >nul 2>&1
if errorlevel 1 (
    echo [X] jpackage not found. Install JDK 17+.
    pause
    exit /b 1
)
echo [OK] jpackage

where candle >nul 2>&1
if errorlevel 1 (
    echo [X] WiX not in PATH.
    pause
    exit /b 1
)
echo [OK] WiX Toolset
echo [OK] Backend: %BACKEND_URL%
echo ========================================
echo.

REM === PRE-CLEAN: delete old installer if it exists (may be locked) ===
if exist "target\installer" (
    del /f /q "target\installer\*.exe" >nul 2>&1
    rmdir /s /q "target\installer" >nul 2>&1
)

REM === STEP 1: FAT-JAR (use 'package' not 'clean package' to avoid locked file issues) ===
echo [1/3] Maven build...
call "%MVN%" package -DskipTests -q
if errorlevel 1 (
    echo [X] Maven build failed.
    echo     Run manually: call "%MVN%" package -DskipTests
    pause
    exit /b 1
)

if not exist "target\volume-frontend-%APP_VERSION%-fat.jar" (
    echo [X] fat-jar not found
    pause
    exit /b 1
)
echo [OK] Fat-jar built
echo.

REM === STEP 2: PREPARE ===
echo [2/3] Preparing...
if exist target\app rmdir /s /q target\app
mkdir target\app
copy /Y "target\volume-frontend-%APP_VERSION%-fat.jar" "target\app\cringe-player.jar" >nul
echo [OK] Ready
echo.

REM === STEP 3: JPACKAGE ===
echo [3/3] jpackage: building installer (1-3 min)...

REM Output to a fresh directory (avoid conflict with locked old exe)
if exist "target\build-out" rmdir /s /q "target\build-out"

SET "ICON_OPT="
if exist "src\main\resources\icons\clown.ico" SET "ICON_OPT=--icon src\main\resources\icons\clown.ico"

jpackage --type exe --name "Cringe Volume Player" --app-version %APP_VERSION% --vendor "CringeWare" --description "Audio player where you pay to change volume" --input target\app --main-jar cringe-player.jar --main-class com.cringe.player.Launcher --module-path "%JAVAFX_JMODS%" --add-modules javafx.controls,javafx.fxml,javafx.media,java.net.http,jdk.crypto.ec --java-options "-DPUBLIC_BACKEND_URL=%BACKEND_URL%" --java-options "-Dfile.encoding=UTF-8" --win-dir-chooser --win-shortcut --win-menu --win-menu-group "CringeWare" %ICON_OPT% --dest target\build-out

if errorlevel 1 (
    echo.
    echo [X] jpackage failed!
    pause
    exit /b 1
)

echo.
echo ============================================
echo   DONE!
echo   Installer: target\build-out\Cringe Volume Player-%APP_VERSION%.exe
echo   Send this .exe to anyone. No Java needed.
echo ============================================
pause
