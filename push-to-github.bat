@echo off
setlocal enabledelayedexpansion
REM ============================================================================
REM  push-to-github.bat  -  publish LegalCase ERP to a GitHub repository
REM
REM  Usage:
REM     push-to-github.bat https://github.com/<your-username>/legal-case-erp.git
REM
REM  Safe to run more than once: it will not overwrite an existing remote, and it
REM  never force-pushes. Run it from the folder that contains pom.xml.
REM ============================================================================

if "%~1"=="" (
  echo.
  echo  Usage:  push-to-github.bat https://github.com/^<your-username^>/legal-case-erp.git
  echo.
  echo  Create the empty repository first at https://github.com/new
  echo  Do NOT tick "Add a README" / ".gitignore" / "licence" - this project has them already.
  echo.
  exit /b 1
)
set "URL=%~1"

echo.
echo == 1/6  Checking this is the project root ==
if not exist "pom.xml" (
  echo    ERROR: pom.xml not found here.
  echo    Run this from the project folder, for example:
  echo      cd /d "C:\Users\Ally Mongala\Downloads\Project\legal-case-erp"
  exit /b 1
)
echo    OK - pom.xml found in %CD%

echo.
echo == 2/6  Checking Git ==
where git >nul 2>&1
if errorlevel 1 (
  echo    ERROR: Git is not installed or not on PATH.
  echo    Install from https://git-scm.com/download/win and reopen this window.
  exit /b 1
)
for /f "tokens=*" %%v in ('git --version') do echo    %%v

echo.
echo == 3/6  Initialising the repository ==
git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
  git init -q
  git config init.defaultBranch main >nul 2>&1
  echo    New repository created.
) else (
  echo    Existing repository found - reusing it.
)

REM make sure a commit identity exists, otherwise the commit fails
git config user.name  >nul 2>&1 || git config user.name "Ally Mongala"
git config user.email >nul 2>&1 || git config user.email "219361629@tut4life.ac.za"

echo.
echo == 4/6  Staging and committing ==
git add .
git diff --cached --quiet >nul 2>&1
if errorlevel 1 (
  git commit -q -m "LegalCase ERP: AI-assisted case management prototype (ISJ107V)"
  echo    Committed.
) else (
  echo    Nothing new to commit.
)

echo.
echo == 5/6  Setting the remote ==
git remote get-url origin >nul 2>&1
if errorlevel 1 (
  git remote add origin "%URL%"
  echo    origin -^> %URL%
) else (
  for /f "tokens=*" %%r in ('git remote get-url origin') do echo    origin already points at %%r
  echo    If that is wrong:  git remote set-url origin %URL%
)

git branch -M main

echo.
echo == 6/6  Pushing to GitHub ==
echo    If a browser window opens, sign in and approve.
echo    If it asks for a password, paste a Personal Access Token (not your password):
echo      GitHub -^> Settings -^> Developer settings -^> Personal access tokens -^> Tokens (classic)
echo.
git push -u origin main
if errorlevel 1 (
  echo.
  echo    Push failed. Read the message above. Common causes:
  echo      - wrong URL or repository does not exist yet
  echo      - repository was created WITH a README: run
  echo          git pull --allow-unrelated-histories origin main
  echo        then run this script again
  echo      - password used instead of a personal access token
  exit /b 1
)

echo.
echo ============================================================
echo   Done. Your project is on GitHub.
echo ============================================================
echo   Next:
echo     1. Open the repository page and confirm the files are there.
echo     2. Check the "Actions" tab - the build workflow runs automatically.
echo     3. Replace the placeholder URL in the Assignment 3 and 4 documents
echo        with:  %URL%
echo     4. Make sure the repository visibility is Public (Settings -^> General)
echo        so the marker can open it without an account.
echo.
endlocal
