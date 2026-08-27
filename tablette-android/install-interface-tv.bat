@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

rem ==========================================================================
rem  FreePlay TV (version Windows) — installe l'interface facon Android TV
rem  et regle la tablette pour que la manette reste connectee en permanence.
rem
rem  Usage :  install-interface-tv.bat            (installation)
rem           install-interface-tv.bat --annuler  (retour a l'accueil d'origine)
rem ==========================================================================

set PKG=fr.freeplay.tv
set APK=%~dp0FreePlayTV.apk
set BACKUP=%~dp0.launcher-origine

where adb >nul 2>nul
if errorlevel 1 (
    echo adb introuvable. Telecharge les "SDK Platform Tools" de Google :
    echo   https://developer.android.com/tools/releases/platform-tools
    echo Dezippe le dossier, puis relance ce script depuis ce dossier.
    pause
    exit /b 1
)

adb start-server >nul 2>nul
adb get-state >nul 2>nul
if errorlevel 1 (
    echo Aucune tablette detectee. Branche-la en USB, debogage USB active,
    echo et accepte la demande "Autoriser le debogage USB" sur son ecran.
    pause
    exit /b 1
)

for /f "delims=" %%m in ('adb shell getprop ro.product.model') do set MODEL=%%m
echo.
echo [FreePlay TV] Tablette detectee : %MODEL%

if "%~1"=="--annuler" goto :annuler

echo.
echo === Etape 1/4 : Sauvegarde de l'accueil actuel ===
for /f "tokens=1 delims=/" %%h in ('adb shell cmd package resolve-activity -c android.intent.category.HOME --brief ^| findstr /c:"/"') do set CURRENT=%%h
if defined CURRENT (
    if not "%CURRENT%"=="%PKG%" (
        echo %CURRENT%> "%BACKUP%"
        echo   Accueil d'origine memorise : %CURRENT%
    )
)

echo.
echo === Etape 2/4 : Installation de l'interface ===
if not exist "%APK%" (
    echo   Fichier introuvable : %APK%
    echo   Recupere FreePlayTV.apk depuis le depot ^(dossier tablette-android^).
    pause
    exit /b 1
)
adb install -r "%APK%"

echo.
echo === Etape 3/4 : FreePlay TV devient l'ecran d'accueil ===
adb shell cmd package set-home-activity %PKG% > "%TEMP%\fp_home.txt" 2>&1
findstr /c:"Success" "%TEMP%\fp_home.txt" >nul 2>nul
if not errorlevel 1 (
    echo   OK : accueil defini automatiquement.
) else (
    adb shell cmd role add-role-holder --user 0 android.app.role.HOME %PKG% >nul 2>nul
    if not errorlevel 1 (
        echo   OK : accueil defini automatiquement.
    ) else (
        echo   Reglage automatique refuse par la tablette.
        echo   L'ecran de choix va s'ouvrir : selectionne "FreePlay TV" puis "Toujours".
        adb shell am start -a android.settings.HOME_SETTINGS >nul 2>nul
        pause
    )
)
del "%TEMP%\fp_home.txt" >nul 2>nul

echo.
echo === Etape 4/4 : Manette toujours connectee ===
adb shell settings put global bluetooth_on 1 >nul 2>nul
adb shell dumpsys deviceidle whitelist +%PKG% >nul 2>nul
adb shell cmd appops set %PKG% RUN_IN_BACKGROUND allow >nul 2>nul
adb shell cmd appops set %PKG% RUN_ANY_IN_BACKGROUND allow >nul 2>nul
adb shell settings put global stay_on_while_plugged_in 7 >nul 2>nul
adb shell settings put global ble_scan_always_enabled 0 >nul 2>nul
echo   OK : Bluetooth maintenu actif, application exemptee de mise en veille.

adb shell am start -n %PKG%/.MainActivity >nul 2>nul

echo.
echo ============================ TERMINE ! ============================
echo Sur la tablette :
echo   - L'ecran d'accueil FreePlay TV s'affiche (grandes tuiles, heure,
echo     etat de la manette en haut a droite).
echo   - Une tuile "A installer" signale une appli absente : clique dessus,
echo     le Play Store s'ouvre sur la bonne fiche.
echo.
echo Appairer la manette (une seule fois) :
echo   1. Manette eteinte, maintiens son bouton d'appairage jusqu'a ce que
echo      la LED clignote rapidement.
echo   2. Sur la tablette, ouvre la tuile "Manette" et selectionne-la.
echo.
echo Tout annuler : install-interface-tv.bat --annuler
echo ===================================================================
pause
exit /b 0

:annuler
echo.
echo [FreePlay TV] Retour a l'accueil d'origine...
if exist "%BACKUP%" (
    set /p ORIG=<"%BACKUP%"
    adb shell cmd package set-home-activity !ORIG! >nul 2>nul
    if errorlevel 1 adb shell cmd role add-role-holder --user 0 android.app.role.HOME !ORIG! >nul 2>nul
    echo   Accueil d'origine restaure : !ORIG!
) else (
    echo   Accueil d'origine inconnu : choisis-le a l'ecran.
    adb shell am start -a android.settings.HOME_SETTINGS >nul 2>nul
)
adb shell settings put global stay_on_while_plugged_in 0 >nul 2>nul
adb shell dumpsys deviceidle whitelist -%PKG% >nul 2>nul

set /p REP="  Desinstaller aussi l'application FreePlay TV ? [o/N] "
if /i "%REP%"=="o" adb uninstall %PKG% >nul 2>nul

echo Termine : la tablette est revenue a son fonctionnement d'origine.
pause
exit /b 0
