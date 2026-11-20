@echo off
REM ===================================================================
REM  LegalCase ERP - backend + web client launcher (Windows)
REM  Starts JWT auth + H2 database (.\data) + NLP pipeline
REM  + calendar sync + audit log, and serves the browser UI.
REM
REM  Default port 8080.  Override with:  set LCE_PORT=9090
REM ===================================================================
cd /d "%~dp0"

if "%LCE_PORT%"=="" set LCE_PORT=8080

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java not found on PATH.
    echo         Install JDK 21 from https://adoptium.net
    pause & exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven not found on PATH.
    echo         Install Maven from https://maven.apache.org/download.cgi
    pause & exit /b 1
)

echo.
echo == Java version in use ==
java -version
echo.

echo == Building all modules (first run downloads dependencies, be patient) ==
call mvn -q clean install -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed.
    echo         If you see "invalid target release" you are on the wrong JDK.
    echo         This project needs JDK 21.  Check with:  java -version
    pause & exit /b 1
)

echo.
echo ===================================================================
echo   LegalCase ERP is starting
echo   Open your browser at:   http://localhost:%LCE_PORT%
echo.
echo   Lawyer : ally@mongalalaw.co.za  / lawyer123
echo   Client : thabo@example.com      / client123
echo   Admin  : admin@mongalalaw.co.za / admin123
echo.
echo   Press Ctrl+C in this window to stop the server.
echo ===================================================================
echo.

java -jar backend\target\legal-backend.jar
pause
