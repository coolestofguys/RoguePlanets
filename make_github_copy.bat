@echo off
setlocal
cd /d "%~dp0"

:: Makes a clean copy of the mod for uploading to GitHub by hand:
::   Desktop\RoguePlanets-github\
:: Only the real mod and repo files are copied. Backups, build output, release zips,
:: the forum post and the unrevealed promo GIFs are left out.

set OUT=%USERPROFILE%\Desktop\RoguePlanets-github
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

for %%d in (data graphics jars src media) do robocopy "%%d" "%OUT%\%%d" /E /NFL /NDL /NJH /NJS /NP >nul
for %%f in (mod_info.json README.md LICENSE CHANGELOG.md RoguePlanets.version build.bat make_release.bat make_github_copy.bat ADDING_CONTENT.md .gitignore) do if exist "%%f" copy /y "%%f" "%OUT%\" >nul

echo.
echo [SUCCESS] Clean copy made at:
echo   %OUT%
echo.
echo Note: the GitHub website takes at most 100 files per upload, so drag it in two batches
echo (for example src first, then everything else).
endlocal
pause
