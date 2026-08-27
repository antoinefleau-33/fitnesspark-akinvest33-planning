@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

rem ==========================================================================
rem  FreePlay Tablette (version Windows) — transforme une tablette Android
rem  en "box" Freebox : TV OQEE, Netflix / Disney+ / Prime Video, Steam Link,
rem  optimisations. Aucun flash, 100 %% réversible.
rem
rem  Prérequis : adb installé (voir README.md), tablette branchée en USB
rem  avec le débogage USB activé.
rem
rem  Usage :  install-tablette.bat            (configuration)
rem           install-tablette.bat --annuler  (annule les réglages)
rem ==========================================================================

where adb >nul 2>nul
if errorlevel 1 (
    echo adb introuvable. Telecharge les "SDK Platform Tools" de Google :
    echo   https://developer.android.com/tools/releases/platform-tools
    echo Dezippe le dossier puis relance ce script DEPUIS ce dossier,
    echo ou ajoute-le au PATH.
    pause
    exit /b 1
)

adb start-server >nul 2>nul
adb get-state >nul 2>nul
if errorlevel 1 (
    echo Aucune tablette detectee. Verifie que :
    echo   1. La tablette est branchee en USB au PC ;
    echo   2. Le debogage USB est active ^(voir README.md^) ;
    echo   3. Tu as accepte la demande "Autoriser le debogage USB" sur l'ecran.
    pause
    exit /b 1
)

for /f "delims=" %%m in ('adb shell getprop ro.product.model') do set MODEL=%%m
echo.
echo [FreePlay Tablette] Tablette detectee : %MODEL%

if "%~1"=="--annuler" goto :annuler

echo.
echo === Etape 1/3 : Installation des applis ===
echo Le Play Store s'ouvre sur la TABLETTE pour chaque appli :
echo appuie sur "Installer" sur la tablette, puis une touche ici.
echo.
call :installer net.oqee.androidmobile "OQEE by Free (chaines TV Freebox)"
call :installer com.netflix.mediaclient "Netflix"
call :installer com.disney.disneyplus "Disney+"
call :installer com.amazon.avod.thirdpartyclient "Prime Video"
call :installer com.valvesoftware.steamlink "Steam Link (jeux Steam en streaming)"
call :installer org.videolan.vlc "VLC"
call :installer fr.freebox.network "Freebox Connect (Wi-Fi Freebox)"

echo.
echo === Etape 2/3 : Optimisations ===
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
adb shell settings put system screen_off_timeout 1800000
adb shell settings put global wifi_sleep_policy 2 >nul 2>nul
echo   OK : animations accelerees, veille ecran 30 min, Wi-Fi toujours actif.

echo.
echo === Etape 3/3 : Nettoyage optionnel ===
set /p REP="Desactiver les applis preinstallees inutiles (Facebook, OneDrive, LinkedIn...) ? Reversible. [o/N] "
if /i "%REP%"=="o" (
    for %%p in (com.facebook.katana com.facebook.appmanager com.facebook.services com.facebook.system com.microsoft.skydrive com.linkedin.android com.samsung.android.app.spage) do (
        adb shell pm disable-user --user 0 %%p >nul 2>nul
    )
    echo   OK : desactivees. Reactivation : install-tablette.bat --annuler
) else (
    echo   OK, on ne touche a rien.
)

echo.
echo ============================ TERMINE ! ============================
echo Sur la tablette :
echo   1. TV    : ouvre OQEE by Free et connecte-toi avec ton compte Free.
echo   2. Video : connecte-toi dans Netflix, Disney+ et Prime Video.
echo   3. Jeux  : ouvre Steam Link (Steam doit tourner sur ton PC, meme
echo              reseau Wi-Fi). Manette Bluetooth recommandee.
echo   4. Wi-Fi : Freebox Connect pour verifier le signal (reseau 5 GHz).
echo Tout annuler : install-tablette.bat --annuler
echo ===================================================================
pause
exit /b 0

:installer
adb shell pm list packages | findstr /c:"package:%~1" >nul 2>nul
if not errorlevel 1 (
    echo   [deja installee] %~2
    goto :eof
)
echo   --^> %~2
adb shell am start -a android.intent.action.VIEW -d "market://details?id=%~1" >nul 2>nul
echo       Appuie sur "Installer" sur la tablette...
pause
goto :eof

:annuler
echo.
echo [FreePlay Tablette] Annulation des reglages...
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell settings put system screen_off_timeout 120000
for %%p in (com.facebook.katana com.facebook.appmanager com.facebook.services com.facebook.system com.microsoft.skydrive com.linkedin.android com.samsung.android.app.spage) do (
    adb shell pm enable --user 0 %%p >nul 2>nul
)
echo Termine : reglages d'origine restaures, applis reactivees.
echo (Les applis installees ne sont pas supprimees.)
pause
exit /b 0
