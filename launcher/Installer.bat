@echo off
REM ============================================================================
REM  Installateur du lanceur Minecraft.
REM
REM  Enregistre CE SEUL fichier ou tu veux (Bureau, Documents...), puis
REM  double-clique dessus. Il telecharge le lanceur dans un sous-dossier et le
REM  demarre. Plus besoin de recuperer une archive depuis GitHub.
REM
REM  Ensuite, les mises a jour se font depuis le lanceur lui-meme :
REM  Parametres -> Mettre a jour le lanceur.
REM ============================================================================
cd /d "%~dp0"
title Installation du lanceur Minecraft

set "DEST=%~dp0LanceurMinecraft"
set "BASE=https://raw.githubusercontent.com/antoinefleau-33/fitnesspark-akinvest33-planning/claude/minecraft-modular-client-poc-78j3i2/launcher"

echo.
echo   Installation dans : %DEST%
echo.

if not exist "%DEST%" mkdir "%DEST%"

REM PowerShell est present sur tout Windows 10/11 : pas besoin de Python pour
REM cette etape, ce qui permet d'installer meme si Python n'est pas encore la.
for %%F in (mclaunch.py gui.py ui.py Lancer.bat README.md) do (
    echo   Telechargement de %%F...
    powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest -Uri '%BASE%/%%F' -OutFile '%DEST%\%%F' -UseBasicParsing -Headers @{'Cache-Control'='no-cache'} } catch { Write-Host '   ECHEC :' $_.Exception.Message; exit 1 }"
    if errorlevel 1 goto erreur
)

echo.
echo   Termine.
echo.

REM Verification de Python avant de lancer, sinon la fenetre se ferme sans rien dire.
where py >nul 2>&1 && goto lancer
where python >nul 2>&1 && goto lancer

echo   ATTENTION : Python n'est pas installe sur cet ordinateur.
echo.
echo   Installe-le depuis https://www.python.org/downloads/
echo   IMPORTANT : coche "Add Python to PATH" pendant l'installation,
echo   puis relance ce fichier.
echo.
pause
exit /b 0

:lancer
echo   Demarrage du lanceur...
cd /d "%DEST%"
call Lancer.bat
exit /b 0

:erreur
echo.
echo   Le telechargement a echoue. Verifie ta connexion Internet.
echo.
pause
exit /b 1
