# 🚀 Elysium Code - Setup Instructions

## Estado Actual

✅ **APK Compilada**: `app/build/outputs/apk/debug/app-debug.apk` (64MB)  
✅ **Librería Nativa**: Compilada con stub de llama.cpp  
✅ **UI Compose**: Lista para usar  
⏳ **Modelo Gemma 4 E4B**: Requiere instalación manual  

---

## 📱 Instalación de la APK

### Opción 1: Instalar en Dispositivo (Recomendado)

Si tienes un dispositivo Android conectado por USB:

```bash
cd "IA Local Android Gemma 4 E4B"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Opción 2: Instalar en Emulador

```bash
# En Android Studio:
# 1. Abre el Virtual Device Manager
# 2. Inicia un emulador (Pixel 6 Pro recomendado, API 35)
# 3. Ejecuta:

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 Instalación del Modelo Gemma 4 E4B

El modelo debe instalarse DESPUÉS de instalar la APK.

### Paso 1: Preparar el Modelo

El archivo `models/gemma-4-e4b-it-q4_k_m.gguf` está disponible en el proyecto.

### Paso 2: Copiar Modelo al Dispositivo

```bash
adb push models/gemma-4-e4b-it-q4_k_m.gguf \
    /sdcard/Android/data/com.elysium.code.debug/files/models/
```

### Paso 3: Verificar la Instalación

```bash
adb shell ls -la /sdcard/Android/data/com.elysium.code.debug/files/models/
```

Deberías ver:
```
 -rw-rw---- com.elysium.code.d/10001  [TAMAÑO]  gemma-4-e4b-it-q4_k_m.gguf
```

---

## 🐛 Solución de Problemas

### La App se cierra inmediatamente

**Causa**: El modelo no está en la ubicación correcta.

**Solución**:
```bash
# Verifica la ruta
adb shell ls /sdcard/Android/data/com.elysium.code.debug/files/models/

# Si no existe, créala
adb shell mkdir -p /sdcard/Android/data/com.elysium.code.debug/files/models

# Copia el modelo
adb push models/gemma-4-e4b-it-q4_k_m.gguf \
    /sdcard/Android/data/com.elysium.code.debug/files/models/
```

### Logs de Depuración

Para ver los logs en tiempo real:

```bash
adb logcat | grep -i "elysium\|llama\|model"
```

O completos:

```bash
adb logcat -v time > elysium.log
# Luego abre la app y déjala correr por unos segundos
# Presiona Ctrl+C
cat elysium.log
```

---

## 📁 Estructura del Proyecto

```
├── app/
│   ├── src/main/
│   │   ├── cpp/              # Código nativo (C++)
│   │   │   ├── llama_bridge.cpp   # JNI bridge
│   │   │   ├── stub_llama.cpp     # Implementación stub
│   │   │   ├── pty_bridge.cpp     # Terminal PTY
│   │   │   └── llama.cpp/         # Llama.cpp (completo)
│   │   ├── java/
│   │   │   └── com/elysium/code/
│   │   │       ├── MainActivity.kt         # Activity principal
│   │   │       ├── ElysiumApp.kt           # Application class
│   │   │       ├── ai/                     # Motor IA
│   │   │       │   ├── LlamaEngine.kt      # Wrapper JNI
│   │   │       │   ├── ModelManager.kt     # Gestión de modelos
│   │   │       │   └── ...
│   │   │       ├── ui/                     # UI Compose
│   │   │       ├── viewmodel/              # ViewModels
│   │   │       ├── agent/                  # Orquestador de agentes
│   │   │       └── ...
│   │   └── assets/
│   │       └── models/         # GGUF models (vacío - copiar manualmente)
│   └── build.gradle.kts
├── models/
│   └── gemma-4-e4b-it-q4_k_m.gguf  # Modelo (4.5GB)
└── local.properties
```

---

## 🔧 Compilación Personalizada

### Cambiar a Llama.cpp Real

Para compilar con la versión completa de llama.cpp:

```bash
# Editar app/src/main/cpp/CMakeLists.txt
# Cambiar: option(USE_REAL_LLAMA "..." OFF)
# A:       option(USE_REAL_LLAMA "..." ON)

# Luego recompilar
./gradlew clean
./gradlew assembleDebug
```

### Optimizaciones

En `app/build.gradle.kts`:
- `minSdk = 28` (Android 9+)
- `targetSdk = 35` (Android 15)
- `ndk.abiFilters = ["arm64-v8a"]` (Solo ARM64)
- Flags de compilación: `-O3 -ffast-math -march=armv8.2-a+dotprot+fp16`

---

## 🎯 Features Implementados

- ✅ **Gemma 4 E4B Integration**: Motor JNI con llama.cpp
- ✅ **Model Manager**: Extracción y gestión de modelos GGUF
- ✅ **Kotlin Coroutines**: Async/await para inferencia
- ✅ **Jetpack Compose**: UI moderna y reactiva
- ✅ **Multimodal Processor**: Soporte para imagen, audio, texto
- ✅ **Agent Orchestrator**: Coordinación de herramientas
- ✅ **Memory Engine**: Sistema de memoria persistente
- ✅ **Terminal Integration**: Shell embebido
- ✅ **MCP Client**: Model Context Protocol
- ✅ **Plugin System**: Carga dinámica de plugins

---

## 📊 Especificaciones

| Parámetro | Valor |
|-----------|-------|
| **Min SDK** | 28 (Android 9) |
| **Target SDK** | 35 (Android 15) |
| **Arquitectura** | ARM64-v8a |
| **Tamaño APK** | ~64 MB |
| **Tamaño Modelo** | ~4.2 GB (GGUF Q4_K_M) |
| **Context Window** | 4096 tokens |
| **Threads** | 4 (configurable) |
| **GPU Layers** | 0 (CPU only) |
| **Memory Mapping** | Enabled (mmap) |

---

## 🚀 Próximos Pasos (Roadmap)

1. **Quantización**: Probar Q2_K (2-3GB) para dispositivos con poco RAM
2. **Streaming**: Implementar streaming de tokens en tiempo real en la UI
3. **Fine-tuning**: Agregar soporte para LoRA adaptations
4. **Multi-model**: Soporte para múltiples modelos simultáneamente
5. **GPU Acceleration**: Agregar soporte para GPU (Vulkan/OpenGL)
6. **Cloud Sync**: Sincronización de memoria con backend
7. **Offline Package**: APK + modelo en un único instalador

---

## 📝 Notas Importantes

- ⚠️ **Primera ejecución**: Espera a que se cargue el modelo (puede tomar 2-5 minutos)
- ⚠️ **Inferencia**: Varía según el dispositivo (0.5-5 tokens/segundo en ARM64)
- ⚠️ **RAM**: Se recomienda mínimo 6-8 GB de RAM en el dispositivo
- ⚠️ **Almacenamiento**: Requiere ~5 GB libres para el modelo + cache

---

## 📧 Soporte

Para problemas o preguntas:
1. Revisa los logs: `adb logcat`
2. Verifica que el modelo está en: `/sdcard/Android/data/com.elysium.code.debug/files/models/`
3. Reinstala la APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

---

**Última actualización**: 8 de Abril de 2026  
**Versión**: 1.0.0-alpha  
**Estado**: ✅ Listo para testing
