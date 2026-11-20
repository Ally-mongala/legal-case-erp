@echo off
REM ============================================================
REM  LegalCase ERP - front-end launcher (Windows)
REM
REM  Usage:  run.bat              -> JavaFX client, DEMO mode (no backend)
REM          run-connected.bat    -> backend + client talking over REST
REM
REM  Requires: JDK 21 (https://adoptium.net) and Maven on your PATH.
REM  First run builds the project and downloads dependencies - be patient.
REM ============================================================
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java not found. Install JDK 21 from https://adoptium.net
    pause & exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven not found. Install Maven from https://maven.apache.org
    pause & exit /b 1
)

echo == Building all modules (shared + backend + frontend) ==
call mvn -q install -DskipTests
if errorlevel 1 (
    echo Build failed - scroll up to see the error.
    pause & exit /b 1
)

echo == Starting the JavaFX client in DEMO mode ==
echo    Log in with ally@mongalalaw.co.za / lawyer123 or thabo@example.com / client123
call mvn -pl frontend org.openjfx:javafx-maven-plugin:0.0.8:run
pause
