@echo off
REM ============================================================
REM  Сборка Cringe Volume Player в .exe через jpackage
REM
REM  Требования:
REM    - JDK 17+ (с jpackage в PATH)
REM    - Maven 3.8+
REM    - JavaFX jmods (скачать с https://gluonhq.com/products/javafx/)
REM    - WiX Toolset 3.x (только для .msi установщика)
REM
REM  Перед запуском:
REM    1. Скачайте JavaFX jmods для Windows:
REM       https://gluonhq.com/products/javafx/  -> "jmods" вариант
REM    2. Распакуйте и укажите путь ниже в JAVAFX_JMODS
REM    3. Положите clown.ico в src\main\resources\icons\
REM ============================================================

REM === НАСТРОЙКИ ===
SET JAVAFX_JMODS=C:\path\to\javafx-jmods-17.0.11
SET APP_VERSION=1.0.0
SET BACKEND_URL=https://pay.vernovpn.com

REM === СБОРКА JAR ===
echo [1/3] Сборка Maven...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo ОШИБКА: Maven сборка не удалась
    pause
    exit /b 1
)

REM === ОЧИСТКА ПРЕДЫДУЩЕЙ СБОРКИ ===
echo [2/3] Подготовка...
if exist target\installer rmdir /s /q target\installer

REM === JPACKAGE ===
echo [3/3] Создание .exe через jpackage...
jpackage ^
  --type app-image ^
  --name "Cringe Volume Player" ^
  --app-version %APP_VERSION% ^
  --vendor "CringeWare" ^
  --input target ^
  --main-jar volume-frontend-%APP_VERSION%.jar ^
  --main-class com.cringe.player.Launcher ^
  --module-path "%JAVAFX_JMODS%" ^
  --add-modules javafx.controls,javafx.fxml,javafx.media ^
  --java-options "-Dbackend.url=%BACKEND_URL%" ^
  --icon src\main\resources\icons\clown.ico ^
  --dest target\installer

if errorlevel 1 (
    echo ОШИБКА: jpackage не удался
    echo.
    echo Проверьте:
    echo   - Путь JAVAFX_JMODS указан верно
    echo   - JDK 17+ установлен и jpackage в PATH
    echo   - Файл clown.ico существует
    pause
    exit /b 1
)

echo.
echo ============================================
echo   ГОТОВО!
echo   .exe находится в: target\installer\Cringe Volume Player\
echo ============================================
pause
