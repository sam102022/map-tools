@echo off
cd /d "%~dp0"
set "CAPTURE_TERRITORY=%~1"
echo Lancement des captures d'ecran Google Maps...
node capture_territoires.js
echo.
pause
