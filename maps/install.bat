@echo off
cd /d "%~dp0"
echo Installation des dependances Playwright...
call npm install
call npx playwright install chromium
echo.
echo Installation terminee avec succes !
pause
