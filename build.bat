@echo off
setlocal

cd /d "%~dp0"

:: 1. Clean previous build directory
if exist build rmdir /s /q build
mkdir build

:: 2. Target Starsector core library path
set CORE=%~dp0..\..\starsector-core

:: 3. Collect Java source files
dir /s /b src\*.java > sources.txt

:: 4. Compile classes using Java 17 compatibility
echo Compiling Java source files...
javac --release 17 -cp "%CORE%\starfarer.api.jar;%CORE%\json.jar;%CORE%\log4j-1.2.9.jar;%CORE%\lwjgl.jar;%CORE%\lwjgl_util.jar" -d build @sources.txt

:: Check if javac threw an error
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed! Check errors above.
    del sources.txt
    pause
    exit /b %ERRORLEVEL%
)

:: 5. Clean up temporary file list
del sources.txt

:: 6. Package compiled classes into RoguePlanets.jar
echo Packaging jars\RoguePlanets.jar...
if not exist jars mkdir jars
jar -cvf jars\RoguePlanets.jar -C build .

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCCESS] RoguePlanets.jar compiled and packaged successfully!
) else (
    echo.
    echo [ERROR] Jar packaging failed! (Ensure Starsector is completely closed)
)

endlocal
pause