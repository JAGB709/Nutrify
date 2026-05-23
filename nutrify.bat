@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  Nutrify — Nutricionista Virtual (Windows)
REM ============================================================
REM  Uso:
REM    nutrify.bat                    -> modo interactivo (GUI)
REM    nutrify.bat tomate cebolla ajo -> consulta directa
REM
REM  Requisito: Java 11 o superior instalado.
REM ============================================================

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%nutrify-all.jar"

REM ── Verificar JAR ────────────────────────────────────────────
if not exist "%JAR%" (
    echo.
    echo  ERROR: No se encontro nutrify-all.jar
    echo  Coloca nutrify-all.jar junto a este script.
    echo.
    pause
    exit /b 1
)

REM ── Verificar Java ───────────────────────────────────────────
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  ERROR: Java no encontrado.
    echo.
    echo  Instala Java 11+:
    echo    Descarga desde: https://adoptium.net/
    echo    O usa winget: winget install EclipseAdoptium.Temurin.11.JDK
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%v in ('java -version 2^>^&1') do (
    echo   Java: %%v
    goto :java_ok
)
:java_ok
echo   JAR : %JAR%
echo.

REM ── Ejecutar Nutrify ─────────────────────────────────────────
java -Djava.awt.headless=false -Xms64m -Xmx512m -jar "%JAR%" %*

endlocal
