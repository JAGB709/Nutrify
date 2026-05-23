#!/usr/bin/env bash
# ============================================================
#  Nutrify — Nutricionista Virtual (Linux / macOS)
# ============================================================
#  Uso:
#    ./nutrify.sh                    → modo interactivo (GUI)
#    ./nutrify.sh tomate cebolla ajo → consulta directa
#
#  Requisito: Java 11 o superior instalado.
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/nutrify-all.jar"

# ── Verificar JAR ────────────────────────────────────────────
if [ ! -f "$JAR" ]; then
    echo "╔═══════════════════════════════════════════════════╗"
    echo "  ERROR: No se encontró nutrify-all.jar"
    echo "  Coloca nutrify-all.jar junto a este script."
    echo "╚═══════════════════════════════════════════════════╝"
    exit 1
fi

# ── Verificar Java ───────────────────────────────────────────
if ! command -v java &>/dev/null; then
    echo "╔═══════════════════════════════════════════════════╗"
    echo "  ERROR: Java no encontrado."
    echo ""
    echo "  Instala Java 11+:"
    echo "    Ubuntu/Debian : sudo apt install openjdk-11-jdk"
    echo "    Fedora/RHEL   : sudo dnf install java-11-openjdk"
    echo "    macOS (brew)  : brew install openjdk@11"
    echo "    Descarga      : https://adoptium.net/"
    echo "╚═══════════════════════════════════════════════════╝"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1)
echo "  Java: $JAVA_VER"
echo "  JAR : $JAR"
echo ""

# ── Ejecutar Nutrify ─────────────────────────────────────────
exec java \
    -Djava.awt.headless=false \
    -Xms64m -Xmx512m \
    -jar "$JAR" \
    "$@"
