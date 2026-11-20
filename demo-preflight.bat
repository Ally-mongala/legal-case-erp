@echo off
setlocal enabledelayedexpansion
REM ============================================================================
REM  demo-preflight.bat  -  run this 10 minutes before you present (Windows)
REM
REM  Checks everything the live demonstration needs and prints PASS or FAIL.
REM  Nothing is changed - it only looks. Requires curl (built into Windows 10/11).
REM ============================================================================

set "B=http://localhost:8080"
if not "%~1"=="" set "B=%~1"
set /a PASS=0
set /a FAIL=0

echo.
echo ==============================================================
echo   LegalCase ERP - pre-flight check
echo   Backend: %B%
echo   Time:    %DATE% %TIME%
echo ==============================================================

echo.
echo 1. Java version (must be 21)
java -version 2>&1 | findstr /C:"\"21" >nul
if errorlevel 1 (
  echo   FAIL  Java is not 21 - select JDK 21 and open a new window
  set /a FAIL+=1
) else (
  for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr /C:"version"') do echo   PASS  %%v
  set /a PASS+=1
)

echo.
echo 2. Backend is running
curl -s -m 5 "%B%/api/health" | findstr /C:"\"status\":\"ok\"" >nul
if errorlevel 1 (
  echo   FAIL  No answer from %B%/api/health - is the backend started?
  set /a FAIL+=1
) else (
  echo   PASS  Health endpoint answered ok
  set /a PASS+=1
)

echo.
echo 3. The three demo accounts can sign in
call :trylogin "ally@mongalalaw.co.za"  "lawyer123" "Attorney"
call :trylogin "thabo@example.com"      "client123" "Client"
call :trylogin "admin@mongalalaw.co.za" "admin123"  "Administrator"

echo.
echo 4. Upload files are ready
set "DIR=%~dp0docs\demo-upload"
if exist "%DIR%" (
  set /a N=0
  for %%f in ("%DIR%\*.pdf" "%DIR%\*.docx" "%DIR%\*.txt") do set /a N+=1
  if !N! GEQ 8 (
    echo   PASS  !N! upload files found in docs\demo-upload\
    set /a PASS+=1
  ) else (
    echo   FAIL  Only !N! upload files found ^(expected 8^)
    set /a FAIL+=1
  )
  if exist "%DIR%\01 MATCH - Mokoena affidavit.pdf" (echo   PASS  File 01 present) else (echo   FAIL  File 01 missing & set /a FAIL+=1)
  if exist "%DIR%\03 BLOCK - wrong matter Nkosi v ABSA.pdf" (echo   PASS  File 03 present) else (echo   FAIL  File 03 missing & set /a FAIL+=1)
  if exist "%DIR%\06 FAILED - scanned no text.pdf" (echo   PASS  File 06 present) else (echo   FAIL  File 06 missing & set /a FAIL+=1)
  if exist "%DIR%\07 INVALID - plain text file.txt" (echo   PASS  File 07 present) else (echo   FAIL  File 07 missing & set /a FAIL+=1)
) else (
  echo   FAIL  Folder docs\demo-upload\ not found
  set /a FAIL+=1
)

echo.
echo 5. Fallback JAR
if exist "%~dp0backend\target\legal-backend.jar" (
  echo   PASS  Pre-built JAR exists - your fallback is ready
  set /a PASS+=1
) else (
  echo   NOTE  No built JAR - build one before travelling: mvn clean install -DskipTests
)

echo.
echo ==============================================================
echo   RESULT: %PASS% passed, %FAIL% failed
if %FAIL%==0 (
  echo   Ready to present.
) else (
  echo   Fix the FAIL items above before you start.
)
echo ==============================================================
echo.
endlocal
exit /b 0

:trylogin
set "EMAIL=%~1"
set "PW=%~2"
set "LABEL=%~3"
curl -s -m 8 -o "%TEMP%\pf_login.json" -w "%%{http_code}" -X POST "%B%/api/auth/login" -H "Content-Type: application/json" -d "{\"email\":\"%EMAIL%\",\"password\":\"%PW%\"}" > "%TEMP%\pf_code.txt"
set /p CODE=<"%TEMP%\pf_code.txt"
if "%CODE%"=="200" (
  echo   PASS  %LABEL% sign-in ^(%EMAIL%^)
  set /a PASS+=1
) else (
  echo   FAIL  %LABEL% sign-in failed ^(%EMAIL%^) - HTTP %CODE%
  set /a FAIL+=1
)
exit /b 0
