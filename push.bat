@echo off
setlocal
cd /d "%~dp0"

rem Usage: push.bat "commit message"   (no arg = prompt)
set "MSG=%~1"
if not defined MSG set /p "MSG=Commit message: "
if not defined MSG (echo empty message & pause & exit /b 1)

git rev-parse --is-inside-work-tree >nul 2>&1 || (echo not a git repo & pause & exit /b 1)

findstr /x /c:"*.log" .gitignore >nul 2>&1 || echo *.log>>.gitignore
findstr /x /c:"*.lck" .gitignore >nul 2>&1 || echo *.lck>>.gitignore
git rm -r -q --cached --ignore-unmatch "*.log" "*.lck" >nul 2>&1

git add -A
git diff --cached --name-only | findstr /i "\.log \.lck" >nul && (echo STOP: log/lck staged & git reset -q & pause & exit /b 1)

git diff --cached --quiet && (echo nothing to commit & git status --short & pause & exit /b 0)

git commit -q -m "%MSG%" || (echo commit failed & pause & exit /b 1)

git pull --rebase origin main
if errorlevel 1 (
  echo.
  echo PULL/REBASE CONFLICT. Resolve files, then: git rebase --continue ^&^& git push origin main
  echo Abort: git rebase --abort
  pause & exit /b 1
)

git push origin main
git log --format="%%h %%an %%s" -3
pause
