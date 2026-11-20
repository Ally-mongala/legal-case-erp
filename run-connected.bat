@echo off
REM LegalCase ERP - FULL SYSTEM (Windows).
REM Starts the backend REST API, then the JavaFX client in CONNECTED mode.
REM Close the client, then press any key here to stop the backend.
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

echo == Building all modules ==
call mvn -q install -DskipTests
if errorlevel 1 ( pause & exit /b 1 )

echo == Starting backend ==
start "LegalCase ERP backend" /min cmd /c "java -jar backend\target\legal-backend.jar"
echo   waiting for backend...
timeout /t 8 /nobreak >nul

echo == Starting JavaFX client (CONNECTED mode) ==
echo   Log in with ally@mongalalaw.co.za / lawyer123 (lawyer) or thabo@example.com / client123 (client)
set LCE_BACKEND_URL=http://localhost:8080
call mvn -pl frontend org.openjfx:javafx-maven-plugin:0.0.8:run

echo   Closing the backend window will stop the server.
pause
