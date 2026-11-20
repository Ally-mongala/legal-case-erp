@echo off
REM ===================================================================
REM  LegalCase ERP - one-time Google Calendar authorisation helper
REM
REM  Gets the refresh token needed for real Google Calendar sync.
REM  Requirements: JDK 21 on PATH, and an OAuth client (Desktop app)
REM  created in Google Cloud Console. See docs/GOOGLE_CALENDAR_SETUP.md
REM
REM  Run:  get-google-refresh-token.bat
REM ===================================================================
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java not found on PATH. Install JDK 21 from https://adoptium.net
    pause & exit /b 1
)

echo.
echo Starting the Google authorisation helper...
echo.
java tools\GoogleCalendarAuth.java
echo.
pause
