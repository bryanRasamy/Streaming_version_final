@echo off
setlocal EnableDelayedExpansion

REM ========================================
REM Script de compilation et exécution
REM Projet : Streaming UDP
REM Version Windows (batch)
REM ========================================

echo.
echo ========================================
echo   Streaming UDP - Lancement (Windows)
echo ========================================
echo.

REM Couleurs (simplifiées pour Windows CMD)
set "RED=[31m"
set "GREEN=[32m"
set "YELLOW=[33m"
set "NC=[0m"

REM Dossiers
set "CLASSES_DIR=.\classe"

REM Classe principale à lancer
set "MAIN_CLASS=princip.Main"

REM ========================================
REM 1. NETTOYAGE (optionnel)
REM ========================================
echo %YELLOW%🧹 Nettoyage des anciennes classes...%NC%
if exist "%CLASSES_DIR%" (
    rd /s /q "%CLASSES_DIR%"
)
mkdir "%CLASSES_DIR%" 2>nul

REM ========================================
REM 2. COMPILATION
REM ========================================
echo %YELLOW%🔨 Compilation du code...%NC%

REM Trouver tous les .java et compiler
dir /s /b *.java > sources.txt 2>nul
if not exist sources.txt (
    echo %RED%❌ Aucun fichier .java trouvé !%NC%
    pause
    exit /b 1
)

javac -d "%CLASSES_DIR%" -encoding UTF-8 @sources.txt

if %ERRORLEVEL% neq 0 (
    echo.
    echo %RED%❌ Compilation échouée !%NC%
    del sources.txt 2>nul
    pause
    exit /b 1
)

del sources.txt 2>nul
echo %GREEN%✅ Compilation réussie !%NC%

REM ========================================
REM 3. EXÉCUTION
REM ========================================
echo.
echo %YELLOW%🚀 Lancement du programme...%NC%
echo %YELLOW%━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━%NC%
echo.

java -Djava.awt.headless=false -cp "%CLASSES_DIR%" %MAIN_CLASS%

if %ERRORLEVEL% neq 0 (
    echo.
    echo %RED%❌ Exécution échouée (code erreur : %ERRORLEVEL%)%NC%
    pause
    exit /b 1
)

echo.
echo %GREEN%✅ Programme terminé avec succès !%NC%
echo.
pause
