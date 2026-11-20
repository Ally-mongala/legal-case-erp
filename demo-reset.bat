@echo off
REM ============================================================================
REM  demo-reset.bat  -  return the system to a clean, freshly seeded state
REM
REM  Deletes the data folder (database + encrypted documents) and restarts the
REM  backend, which recreates and reseeds everything automatically.
REM ============================================================================
setlocal
cd /d "%~dp0"

echo.
echo == 1/3  Stopping any running backend ==
taskkill /F /IM java.exe >nul 2>&1
if errorlevel 1 (echo    No java process was running.) else (echo    Stopped java.exe)
timeout /t 2 >nul

echo.
echo == 2/3  Deleting the data folder ==
if exist "data" (
  rmdir /S /Q "data"
  echo    data folder removed.
) else (
  echo    data folder did not exist.
)

echo.
echo == 3/3  Starting the backend ==
if not exist "backend\target\legal-backend.jar" (
  echo    ERROR: backend\target\legal-backend.jar not found.
  echo    Build it first:  mvn clean install -DskipTests
  pause
  exit /b 1
)
echo    Starting... open http://localhost:8080 once it says "Listening on"
java -jar "backend\target\legal-backend.jar"
