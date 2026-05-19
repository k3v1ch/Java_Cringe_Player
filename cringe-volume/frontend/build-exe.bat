@echo off
REM ============================================================
REM  Сборка Cringe Volume Player в .exe через jpackage
REM
REM  Требования:
REM    - JDK 17+ (с jpackage в PATH)
REM    - Maven (или встроенный в IntelliJ, путь в SET MVN ниже)
REM    - JavaFX jmods (скачать с https://gluonhq.com/products/javafx/)
REM    - WiX Toolset 3.x (только для --type exe / msi установщика)
REM
REM  Перед запуском:
REM    1. Скачайте JavaFX jmods для Windows: https://gluonhq.com/products/javafx/
REM    2. Распакуйте и укажите путь ниже в JAVAFX_JMODS
REM    3. (опционально) Положите clown.ico в src\main\resources\icons\
REM ============================================================

REM === НАСТРОЙКИ ===
SET JAVAFX_JMODS=C:\Users\viplu\Desktop\javafx-jmods-17.0.19
SET APP_VERSION=1.0.0
SET BACKEND_URL=https://pay.vernovpn.com
SET MVN="C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd"

REM === ПРОВЕРКИ ===
if not exist "%JAVAFX_JMODS%" (
    echo ОШИБКА: JAVAFX_JMODS не существует: %JAVAFX_JMODS%
    echo Скачайте jmods с https://gluonhq.com/products/javafx/
    pause
    exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
    echo ОШИБКА: jpackage не найден в PATH. Установите JDK 17+ или добавьте в PATH.
    pause
    exit /b 1
)

REM === СБОРКА JAR ===
echo [1/3] Сборка Maven (может занять несколько минут при первом запуске)...
call %MVN% clean package -DskipTests
if errorlevel 1 (
    echo ОШИБКА: Maven сборка не удалась
    pause
    exit /b 1
)

REM Проверим, что fat-jar собрался
if not exist target\volume-frontend-%APP_VERSION%-fat.jar (
    echo ОШИБКА: fat-jar не найден: target\volume-frontend-%APP_VERSION%-fat.jar
    echo Возможно, maven-shade-plugin не отработал. Проверьте pom.xml
    pause
    exit /b 1
)

REM === ОЧИСТКА ПРЕДЫДУЩЕЙ СБОРКИ ===
echo [2/3] Подготовка...
if exist target\installer rmdir /s /q target\installer

REM Готовим папку с одним fat-jar (jpackage заберёт всё из target\app)
if exist target\app rmdir /s /q target\app
mkdir target\app
copy /Y target\volume-frontend-%APP_VERSION%-fat.jar target\app\cringe-player.jar >nul

REM === JPACKAGE ===
echo [3/3] Создание .exe через jpackage (это занимает 1-3 минуты)...

SET ICON_ARG=
if exist src\main\resources\icons\clown.ico (
    SET ICON_ARG=--icon src\main\resources\icons\clown.ico
) else (
    echo [!] clown.ico не найден - будет использована иконка по умолчанию
)

jpackage ^
  --type app-image ^
  --name "Cringe Volume Player" ^
  --app-version %APP_VERSION% ^
  --vendor "CringeWare" ^
  --input target\app ^
  --main-jar cringe-player.jar ^
  --main-class com.cringe.player.Launcher ^
  --module-path "%JAVAFX_JMODS%" ^
  --add-modules javafx.controls,javafx.fxml,javafx.media ^
  --java-options "-Dbackend.url=%BACKEND_URL%" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  %ICON_ARG% ^
  --dest target\installer

if errorlevel 1 (
    echo.
    echo ОШИБКА: jpackage не удался
    echo.
    echo Проверьте:
    echo   - Путь JAVAFX_JMODS существует и содержит .jmod файлы
    echo   - JDK 17+ установлен и jpackage в PATH
    echo   - Если clown.ico указан - что это валидный 256x256 ICO
    pause
    exit /b 1
)

echo.
echo ============================================
echo   ГОТОВО!
echo   .exe находится в:
echo   %CD%\target\installer\Cringe Volume Player\Cringe Volume Player.exe
echo.
echo   Для распространения - копируйте всю папку
echo   "Cringe Volume Player" (с runtime внутри).
echo ============================================
pause
