@echo off
setlocal

set "territoryName=Territoire CA01"
set "repWork=%~dp0captures_maps\%territoryName%"
set "jarPath=%~dp0bin\photoshop-1.0.jar"

java -jar "%jarPath%" --map "%repWork%\03_style_contraste.png" --mask "%repWork%\01_plan_avec_territoires.png" --mode territory --osm-roads "%repWork%\osm_roads.json" --output "%repWork%\territory_osm_clipped.png" --mask-out "%repWork%\territory_osm_mask.png"
if errorlevel 1 exit /b %errorlevel%

java -jar "%jarPath%" --map "%repWork%\05_style_contraste_sans_rien.png" --mask "%repWork%\02_plan_avec_zones.png" --territory-mask "%repWork%\02_plan_avec_zones.png" --mode zone --zone-color red --osm-roads "%repWork%\osm_roads.json" --output "%repWork%\zone01_osm_clipped.png" --mask-out "%repWork%\zone01_osm_mask.png"
exit /b %errorlevel%
