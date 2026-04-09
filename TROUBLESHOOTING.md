# 🔧 Troubleshooting Avanzado - Elysium Code

## 📋 Índice de Problemas

1. [App se cierra inmediatamente](#app-se-cierra-inmediatamente)
2. [Modelo no se carga](#modelo-no-se-carga)
3. [Errores de compilación](#errores-de-compilación)
4. [Problemas de rendimiento](#problemas-de-rendimiento)
5. [Errores de JNI](#errores-de-jni)
6. [Espacio insuficiente](#espacio-insuficiente)
7. [Dispositivo no reconocido](#dispositivo-no-reconocido)

---

## 🔴 App se cierra inmediatamente

### Síntoma
App se abre por un segundo y luego se cierra (crash).

### Diagnóstico

```bash
# Ver el crash stacktrace
adb logcat | grep -i "crash\|exception\|fatal"

# Ver logs específicos de la app
adb logcat | grep "com.elysium.code\|MainViewModel\|LlamaEngine"

# Generar reporte completo
adb bugreport > elysium_bug.zip
```

### Solución por Causa

#### Causa 1: Modelo no encontrado
```
E/ModelManager: Model missing! Please push the model via ADB
```

**Solución**:
```bash
# Crear directorio
adb shell mkdir -p /sdcard/Android/data/com.elysium.code.debug/files/models

# Copiar modelo
adb push models/gemma-4-e4b-it-q4_k_m.gguf \
    /sdcard/Android/data/com.elysium.code.debug/files/models/

# Verificar
adb shell ls -la /sdcard/Android/data/com.elysium.code.debug/files/models/
```

#### Causa 2: Librería nativa no se carga
```
E/LlamaEngine: Failed to load native library
E/AndroidRuntime: UnsatisfiedLinkError: dlopen failed
```

**Solución**:
```bash
# Verificar que la librería está en la APK
adb shell find /data/app -name "*.so" | grep elysium_native

# Si no está, recompilar:
./gradlew clean
./gradlew assembleDebug

# Reinstalar
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Causa 3: RAM insuficiente
```
E/LlamaEngine: Failed to load model. Device may not have enough RAM
```

**Solución**:
- Cierra otras aplicaciones
- Reinicia el dispositivo
- Considera quantización Q2_K (reduce modelo de 4.2GB a 2.5GB)

#### Causa 4: Permisos
```
E/ModelManager: Permission denied
```

**Solución**:
```bash
# Dar permisos de almacenamiento
adb shell pm grant com.elysium.code.debug \
    android.permission.READ_EXTERNAL_STORAGE

adb shell pm grant com.elysium.code.debug \
    android.permission.WRITE_EXTERNAL_STORAGE
```

---

## 🔴 Modelo no se carga

### Síntoma
App abre pero muestra "Loading Model..." indefinidamente.

### Diagnóstico

```bash
# Ver logs de carga
adb logcat | grep -i "loading\|extracting"

# Ver uso de RAM
adb shell "dumpsys meminfo com.elysium.code.debug | head -20"

# Ver procesos
adb shell ps | grep elysium
```

### Soluciones

#### Verificar ruta del modelo
```bash
# La ruta debe ser exacta
adb shell ls /sdcard/Android/data/com.elysium.code.debug/files/models/gemma-4-e4b-it-q4_k_m.gguf

# Si el archivo está pero con otro nombre
adb shell ls /sdcard/Android/data/com.elysium.code.debug/files/models/
```

#### Verificar integridad del archivo
```bash
# Local
md5 models/gemma-4-e4b-it-q4_k_m.gguf

# Remoto
adb shell md5sum /sdcard/Android/data/com.elysium.code.debug/files/models/gemma-4-e4b-it-q4_k_m.gguf

# Deben coincidir exactamente
```

#### Aumentar timeout
En `MainViewModel.kt`:
```kotlin
// Cambiar el timeout
val timeout = 60000L // 60 segundos en lugar de 30

withTimeoutOrNull(timeout) {
    // Esperar a que cargue el modelo
}
```

#### Forzar reload
```bash
# Borrar datos de app
adb shell pm clear com.elysium.code.debug

# Reinstalar APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Copiar modelo nuevamente
adb push models/gemma-4-e4b-it-q4_k_m.gguf \
    /sdcard/Android/data/com.elysium.code.debug/files/models/

# Abrir app
adb shell am start -n com.elysium.code.debug/.MainActivity
```

---

## 🔴 Errores de compilación

### Error: "No space left on device"

**Causa**: Disco lleno durante compilación

**Solución**:
```bash
# Limpiar cache de Gradle
./gradlew clean

# Limpiar builds anteriores
rm -rf app/build build

# Limpiar cache de sistema
./gradlew cleanBuildCache

# Recompilar
./gradlew assembleDebug
```

### Error: "Configuration cache is not available"

**Causa**: Configuración de Gradle incompatible

**Solución**:
```bash
./gradlew assembleDebug --no-configuration-cache
```

### Error: "CMake error"

**Causa**: Error en compilación de código nativo

**Solución**:
```bash
# Ver error completo
./gradlew assembleDebug --info 2>&1 | grep -i "cmake\|error"

# Limpiar CMake cache
./gradlew clean

# Recompilar
./gradlew assembleDebug
```

---

## 🔴 Problemas de rendimiento

### Síntoma
Inferencia muy lenta (< 0.1 tokens/segundo)

### Diagnóstico

```bash
# Ver uso de CPU
adb shell top -n 1

# Ver uso de memoria
adb shell "dumpsys meminfo com.elysium.code.debug" | grep TOTAL

# Ver threadcount
adb logcat | grep "thread"
```

### Soluciones

#### Aumentar thread count
En `MainViewModel.kt`:
```kotlin
// Cambiar de 4 a 8 threads
val modelLoaded = llamaEngine.loadModel(ModelConfig(
    modelPath = modelManager.getModelPath(),
    contextSize = 4096,
    threadCount = 8,  // Aumentar esto
    gpuLayers = 0,
    useMmap = true,
    useMlock = false
))
```

#### Usar modelo más pequeño
```bash
# Convertir a Q2_K (reduce de 4.2GB a ~2.5GB)
# y aumenta velocidad en ~20-30%
python3 quantize.py \
    models/gemma-4-e4b-it-q4_k_m.gguf \
    models/gemma-4-e4b-it-q2_k.gguf \
    Q2_K
```

#### Reducir context window
En `MainViewModel.kt`:
```kotlin
val modelLoaded = llamaEngine.loadModel(ModelConfig(
    modelPath = modelManager.getModelPath(),
    contextSize = 2048,  // Reducir de 4096
    ...
))
```

---

## 🔴 Errores de JNI

### Síntoma
```
E/AndroidRuntime: java.lang.UnsatisfiedLinkError: dlopen failed
E/LlamaEngine: Failed to load native library
```

### Diagnóstico

```bash
# Verificar que .so existe en APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep ".so"

# Verificar en dispositivo
adb shell find /data/app -name "*elysium*"

# Ver logs detallados
adb logcat | grep "dlopen\|not found"
```

### Soluciones

#### Recompilar librería nativa
```bash
./gradlew clean
./gradlew assembleDebug --info
```

#### Verificar compilación de stub
```bash
# Comprobar que stub_llama.cpp se incluye
grep "stub_llama" app/src/main/cpp/CMakeLists.txt

# Debe estar en DEVELOPMENT mode
grep "USE_REAL_LLAMA" app/src/main/cpp/CMakeLists.txt
# Debe mostrar: option(USE_REAL_LLAMA ... OFF)
```

#### Limpiar archivos compilados
```bash
rm -rf app/build
rm -rf app/.cxx
./gradlew assembleDebug
```

---

## 🔴 Espacio insuficiente

### Síntoma
```
java.nio.file.FileSystemException: No space left on device
```

### Diagnóstico
```bash
# Ver espacio disponible
df -h /

# Ver tamaño de carpetas
du -sh /Users/jordelmirsdevhome/Downloads/*
```

### Soluciones

#### Limpiar espacio
```bash
# Limpiar cache de Gradle
rm -rf ~/.gradle/caches

# Limpiar builds locales
rm -rf ~/Downloads/celular/IA\ Local\ Android\ Gemma\ 4\ E4B/build
rm -rf ~/Downloads/celular/IA\ Local\ Android\ Gemma\ 4\ E4B/app/build
rm -rf ~/Downloads/celular/IA\ Local\ Android\ Gemma\ 4\ E4B/.gradle

# Ver tamaño liberado
df -h /
```

#### Mover proyecto a disco con más espacio
```bash
# Si hay otro disco disponible
mv /Users/jordelmirsdevhome/Downloads/celular /Volumes/ExternalDrive/
```

---

## 🔴 Dispositivo no reconocido

### Síntoma
```
adb devices
List of attached devices
(no devices)
```

### Diagnóstico
```bash
# Ver si ADB está corriendo
adb status-server

# Ver logs del servidor
adb logcat -v brief
```

### Soluciones

#### ADB no reconoce dispositivo
```bash
# Reiniciar servidor ADB
adb kill-server
adb start-server

# Reconectar USB
# 1. Desconectar cable USB
# 2. Esperar 5 segundos
# 3. Reconectar

# Verificar
adb devices
```

#### Habilitar debugging
En el dispositivo:
1. Ir a Settings > About Phone
2. Tocar Build Number 7 veces
3. Volver a Settings > Developer Options
4. Habilitar "USB Debugging"

#### Aceptar RSA fingerprint
```bash
adb devices

# La primera vez pedirá autorización en el dispositivo
# Aceptar en pantalla del teléfono
```

#### Problema de permisos (macOS)
```bash
# Verificar permisos
ls -la ~/.android/

# Si falta id_rsa:
adb kill-server
adb start-server  # Esto regenerará las claves
```

---

## 📝 Checklist de Depuración

Cuando algo falla, seguir este orden:

- [ ] Ver logs completos: `adb logcat > crash.log`
- [ ] Verificar que modelo existe: `adb shell ls /sdcard/Android/data/com.elysium.code.debug/files/models/`
- [ ] Verificar que librería se cargó: `adb logcat | grep "Native library"`
- [ ] Verificar RAM disponible: `adb shell dumpsys meminfo | head -20`
- [ ] Verificar conexión USB: `adb devices`
- [ ] Verificar permisos: `adb shell pm list permissions | grep STORAGE`
- [ ] Limpiar cache: `adb shell pm clear com.elysium.code.debug`
- [ ] Reinstalar APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] Copiar modelo nuevamente: `adb push models/...`

---

## 🆘 Última Opción: Reset Completo

Si nada funciona:

```bash
# 1. Desinstalar app
adb uninstall com.elysium.code.debug

# 2. Limpiar todo el proyecto
cd "IA Local Android Gemma 4 E4B"
rm -rf .gradle build app/build app/.cxx

# 3. Limpiar Gradle global
rm -rf ~/.gradle/caches

# 4. Recompilar desde cero
./gradlew clean
./gradlew assembleDebug

# 5. Reinstalar completo
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/Android/data/com.elysium.code.debug/files/models
adb push models/gemma-4-e4b-it-q4_k_m.gguf \
    /sdcard/Android/data/com.elysium.code.debug/files/models/

# 6. Abrir app
adb shell am start -n com.elysium.code.debug/.MainActivity
```

---

## 📊 Logs Importantes a Buscar

| Log | Significado | Acción |
|-----|-------------|--------|
| `Model ready at:` | ✅ Modelo encontrado | OK |
| `Model missing!` | ❌ Modelo no existe | Copiar modelo |
| `Native library loaded` | ✅ JNI OK | OK |
| `Failed to load native` | ❌ Error JNI | Recompilar |
| `READY` | ✅ App lista | OK |
| `ERROR` | ❌ Error genérico | Ver stacktrace |
| `OutOfMemoryError` | ❌ RAM insuficiente | Cerrar apps |
| `Permission denied` | ❌ Permisos faltantes | Verificar permisos |

---

## 📞 Contacto/Soporte

Si tienes problemas:

1. **Revisar logs**: `adb logcat`
2. **Verificar modelo**: `adb shell ls /sdcard/Android/data/com.elysium.code.debug/files/models/`
3. **Verificar dispositivo**: `adb devices`
4. **Reporte de error**: Guardar `adb bugreport > error.zip`

---

**Última actualización**: 8 de Abril de 2026  
**Versión**: 1.0.0-alpha
