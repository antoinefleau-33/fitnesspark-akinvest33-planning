@echo off
REM ============================================================================
REM  Fabrique un .exe autonome du lanceur.
REM
REM  Le resultat pese ~15 a 20 Mo. Ce poids n'est pas du remplissage : il
REM  contient l'interpreteur Python et la bibliotheque graphique Tk. C'est la
REM  raison pour laquelle l'exe fonctionne sur un PC ou Python n'est PAS
REM  installe -- contrairement aux fichiers .py, qui en ont besoin.
REM
REM  A titre de comparaison, Lunar Client pese 55 Mo parce qu'il embarque un
REM  navigateur Chromium complet (Electron). Le poids mesure ce qui est
REM  embarque, jamais le nombre de fonctionnalites.
REM ============================================================================
cd /d "%~dp0"
title Compilation du lanceur

echo.
echo   Etape 1/2 : installation de l'outil de compilation
echo.
py -m pip install --upgrade pyinstaller
if errorlevel 1 (
    python -m pip install --upgrade pyinstaller
    if errorlevel 1 goto erreur
)

echo.
echo   Etape 2/2 : compilation ^(peut prendre 2 a 3 minutes^)
echo.

REM --windowed : pas de fenetre noire de console derriere l'interface.
REM --onefile  : un seul .exe au lieu d'un dossier de centaines de fichiers.
REM Les trois modules sont explicitement inclus : PyInstaller suit les imports,
REM mais les nommer evite toute surprise si le code evolue.
py -m PyInstaller --onefile --windowed --name "Lanceur Minecraft" ^
    --hidden-import mclaunch --hidden-import ui ^
    --collect-submodules tkinter ^
    gui.py
if errorlevel 1 goto erreur

echo.
echo   ============================================================
echo     Termine. Ton executable est dans le dossier "dist".
echo.
echo     Il fonctionne sur n'importe quel PC Windows, meme sans
echo     Python installe.
echo.
echo     Note : Windows Defender signale souvent a tort les .exe
echo     produits par PyInstaller. C'est un faux positif connu,
echo     pas un probleme de ce programme.
echo   ============================================================
echo.
pause
exit /b 0

:erreur
echo.
echo   La compilation a echoue.
echo   Verifie que Python est installe et accessible ^(commande "py"^).
echo.
pause
exit /b 1
