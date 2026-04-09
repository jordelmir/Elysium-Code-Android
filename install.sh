#!/bin/bash

# ═══════════════════════════════════════════════════════════════
# ELYSIUM CODE - Installation Script
# ═══════════════════════════════════════════════════════════════
# 
# Este script automatiza la instalación de:
# 1. APK en el dispositivo
# 2. Modelo Gemma 4 E2B

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
MODEL_PATH="$PROJECT_DIR/models/gemma-4-e4b-it-q4_k_m.gguf"
DEVICE_MODEL_DIR="/sdcard/Android/data/com.elysium.code.debug/files/models"

echo "═══════════════════════════════════════════════════════════════"
echo "🚀 ELYSIUM CODE - Instalador"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Verificar que adb está disponible
if ! command -v adb &> /dev/null; then
    echo "❌ Error: adb no encontrado. Por favor instala Android SDK Platform Tools."
    exit 1
fi

# Verificar conexión de dispositivo
echo "📱 Verificando conexión con dispositivo..."
if ! adb devices | grep -q "device$"; then
    echo "❌ Error: No hay dispositivo conectado."
    echo "   Conecta un dispositivo Android por USB o inicia un emulador."
    exit 1
fi

DEVICE_COUNT=$(adb devices | grep "device$" | wc -l)
echo "✅ Dispositivo(s) encontrado(s): $DEVICE_COUNT"
echo ""

# Verificar que la APK existe
if [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: APK no encontrada en $APK_PATH"
    echo "   Primero compila el proyecto: ./gradlew assembleDebug"
    exit 1
fi

# Verificar que el modelo existe
if [ ! -f "$MODEL_PATH" ]; then
    echo "❌ Error: Modelo no encontrado en $MODEL_PATH"
    exit 1
fi

# Paso 1: Instalar APK
echo "📦 Paso 1/3: Instalando APK..."
adb install -r "$APK_PATH"
if [ $? -eq 0 ]; then
    echo "✅ APK instalada correctamente"
else
    echo "❌ Error al instalar APK"
    exit 1
fi
echo ""

# Paso 2: Crear directorio en el dispositivo
echo "📁 Paso 2/3: Preparando almacenamiento en el dispositivo..."
adb shell mkdir -p "$DEVICE_MODEL_DIR"
echo "✅ Directorio creado: $DEVICE_MODEL_DIR"
echo ""

# Paso 3: Copiar modelo
echo "🤖 Paso 3/3: Copiando modelo Gemma 4 E4B (~4.5 GB)..."
echo "   (Esto puede tomar 5-15 minutos según la velocidad de conexión)"
echo ""

FILESIZE=$(stat -f "%z" "$MODEL_PATH" 2>/dev/null || stat -c "%s" "$MODEL_PATH")
FILESIZE_GB=$(echo "scale=2; $FILESIZE / 1024 / 1024 / 1024" | bc)

echo "   Tamaño del archivo: ${FILESIZE_GB}GB"
echo ""

adb push "$MODEL_PATH" "$DEVICE_MODEL_DIR/"
if [ $? -eq 0 ]; then
    echo "✅ Modelo copiado correctamente"
else
    echo "❌ Error al copiar modelo"
    exit 1
fi
echo ""

# Verificación final
echo "🔍 Verificación final..."
REMOTE_FILE_SIZE=$(adb shell stat -c "%s" "$DEVICE_MODEL_DIR/gemma-4-e4b-it-q4_k_m.gguf" 2>/dev/null || echo "0")

if [ "$REMOTE_FILE_SIZE" == "$FILESIZE" ]; then
    echo "✅ Verificación OK: Archivo íntegro"
else
    echo "⚠️  Verificación: Tamaño local: $FILESIZE, remoto: $REMOTE_FILE_SIZE"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "✅ ¡INSTALACIÓN COMPLETADA!"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "Próximos pasos:"
echo "1. Abre la app 'Elysium Code' en tu dispositivo"
echo "2. Espera a que el modelo se cargue en memoria (2-5 minutos)"
echo "3. Disfruta del poder de Gemma 4 E4B en tu dispositivo"
echo ""
echo "Para ver los logs:"
echo "   adb logcat | grep -i 'elysium\\|llama\\|model'"
echo ""
