@echo off
setlocal
cd /d "%~dp0"

:: Packages the mod for a GitHub release / forum download:
::   release\RoguePlanets-<version>.zip  containing a single RoguePlanets\ folder.
:: Run build.bat first so jars\RoguePlanets.jar is up to date.

for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "(Get-Content -Raw mod_info.json | ConvertFrom-Json).version"`) do set VER=%%v
if "%VER%"=="" (
    echo [ERROR] Could not read the version from mod_info.json.
    pause
    exit /b 1
)
if not exist "jars\RoguePlanets.jar" (
    echo [ERROR] jars\RoguePlanets.jar is missing. Run build.bat first.
    pause
    exit /b 1
)

set STAGE=release\stage\RoguePlanets
if exist release\stage rmdir /s /q release\stage
mkdir "%STAGE%"

:: What players get: game data, art, the jar, source, and the docs. No backups, media or work folders.
for %%d in (data graphics jars src) do robocopy "%%d" "%STAGE%\%%d" /E /NFL /NDL /NJH /NJS /NP >nul
for %%f in (mod_info.json README.md LICENSE CHANGELOG.md RoguePlanets.version build.bat) do copy /y "%%f" "%STAGE%\" >nul

set ZIP=release\RoguePlanets-%VER%.zip
if exist "%ZIP%" del "%ZIP%"
tar -a -c -f "%ZIP%" -C release\stage RoguePlanets
if errorlevel 1 (
    echo tar failed, trying PowerShell instead...
    powershell -NoProfile -Command "Compress-Archive -Path 'release\stage\RoguePlanets' -DestinationPath '%ZIP%'"
)
rmdir /s /q release\stage

if exist "%ZIP%" (
    echo.
    echo [SUCCESS] Created %ZIP%
) else (
    echo.
    echo [ERROR] The zip was not created.
)
endlocal
pause
