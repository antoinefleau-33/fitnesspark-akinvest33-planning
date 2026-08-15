@echo off
REM Double-clique sur ce fichier pour ouvrir le lanceur.
REM Le "cd /d %~dp0" se place dans le dossier du script : sans lui, un double-clic depuis
REM l'Explorateur demarre dans C:\Windows\System32 et le script est introuvable.
cd /d "%~dp0"
title Lanceur Minecraft

REM On essaie les trois façons d'appeler Python sous Windows, dans l'ordre du plus fiable.
REM "py" est le lanceur officiel installe avec Python ; "python3" est souvent un alias du
REM Microsoft Store qui ouvre la boutique au lieu de lancer quoi que ce soit.
where py >nul 2>&1
if %errorlevel% equ 0 (
    py -3 mclaunch.py %*
    goto fin
)

where python >nul 2>&1
if %errorlevel% equ 0 (
    python mclaunch.py %*
    goto fin
)

where python3 >nul 2>&1
if %errorlevel% equ 0 (
    python3 mclaunch.py %*
    goto fin
)

echo.
echo   Python est introuvable sur cet ordinateur.
echo.
echo   Installe-le depuis https://www.python.org/downloads/
echo   IMPORTANT : coche "Add Python to PATH" pendant l'installation.
echo.

:fin
echo.
pause
