# ⚡ Comandos Terminal Útiles para Elysium Code

## 📱 Gestión de la Aplicación

### Instalar APK
```bash
# Instalar aplicación
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Instalar con upgrade
adb install -d app/build/outputs/apk/debug/app-debug.apk
```

### Lanzar Aplicación
```bash
# Lanzar la app
adb shell am start -n com.elysium.code.debug/com.elysium.code.MainActivity

# Lanzar y esperar
adb shell am start -n com.elysium.code.debug/com.elysium.code.MainActivity && sleep 2
```

### Ver Logs
```bash
# Ver todos los logs
adb logcat

# Ver logs específicos de la app
adb logcat | grep "Elysium\|llama\|model\|EditorViewModel\|EnhancedShellManager"

# Guardar logs en archivo
adb logcat > logs.txt

# Ver logs con timestamps
adb logcat -v time
```

### Manejar Paquetes
```bash
# Listar paquetes instalados
adb shell pm list packages | grep elysium

# Información del paquete
adb shell pm dump com.elysium.code.debug

# Desinstalar aplicación
adb uninstall com.elysium.code.debug
```

---

## 📁 Exploración del Sistema de Archivos

### Archivos de la Aplicación
```bash
# Ver directorio de archivos de la app
adb shell ls -la /sdcard/Android/data/com.elysium.code.debug/

# Ver archivos internos
adb shell ls -la /data/data/com.elysium.code.debug/

# Ver modelos descargados
adb shell find /sdcard/Android/data/com.elysium.code.debug/ -type f

# Ver espacio usado
adb shell du -sh /sdcard/Android/data/com.elysium.code.debug/
```

### Arrastrar Archivos (Pull/Push)
```bash
# Bajar archivo del dispositivo
adb pull /sdcard/Android/data/com.elysium.code.debug/files/models/gemma-4-e4b-it-q4_k_m.gguf ./

# Subir archivo al dispositivo
adb push ./my_file.txt /sdcard/Android/data/com.elysium.code.debug/files/
```

---

## 🔨 Compilación y Build

### Compilar (Development)
```bash
# Clean build
./gradlew clean assembleDebug

# Build rápido
./gradlew assembleDebug

# Build con output detallado
./gradlew assembleDebug -info
```

### Build Release
```bash
# Compilar release
./gradlew assembleRelease

# Build con firma
./gradlew assembleRelease -Pandroid.injected.signing.store.file=keystore.jks
```

### Limpiar Cache
```bash
# Limpiar gradle cache
./gradlew clean

# Limpiar build completo
./gradlew clean && ./gradlew assembleDebug
```

---

## 🧪 Testing y Debugging

### Iniciar Debugger
```bash
# Conectar debugger (Android Studio)
./gradlew assembleDebug
# Luego en Android Studio: Run → Debug 'app'

# ADB debugging manual
adb logcat -v threadtime | grep Elysium
```

### Profiling
```bash
# Ver procesos
adb shell ps -A | grep com.elysium.code.debug

# Ver memoria usada
adb shell dumpsys meminfo com.elysium.code.debug

# Ver CPU usage
adb shell top | grep com.elysium.code.debug
```

### Shell Remoto
```bash
# Abrir shell en el dispositivo
adb shell

# Una vez en el shell:
cd /sdcard/Android/data/com.elysium.code.debug/
ls -la
cat files/test.txt
```

---

## 🌐 Red y Conectividad

### Ver Conexión ADB
```bash
# Listar dispositivos conectados
adb devices

# Ver conexión específica
adb devices -l

# Conectar por WiFi (si está en la misma red)
adb connect 192.168.1.16:5555
```

### Port Forwarding
```bash
# Forward puerto local a dispositivo
adb forward tcp:8080 tcp:8080

# Forward inverso
adb reverse tcp:8080 tcp:8080

# Listar forwards
adb forward --list
```

---

## 📊 Información del Dispositivo

### Especificaciones
```bash
# Info general
adb shell getprop ro.build.version.release
adb shell getprop ro.product.model
adb shell getprop ro.product.brand
adb shell getprop ro.boot.hardware

# Arquitectura
adb shell uname -m
adb shell getprop ro.product.cpu.abi

# RAM disponible
adb shell cat /proc/meminfo | head -5

# Espacio de almacenamiento
adb shell df
```

---

## 🎯 Comandos Útiles para Desarrollo

### Build Gradual
```bash
# Compilar solo Kotlin
./gradlew compileDebugKotlin

# Compilar solo resources
./gradlew mergeDebugResources

# Compilar solo native code (CMake)
./gradlew configureCMakeDebug buildCMakeDebug
```

### Instalar y Ejecutar (One-liner)
```bash
# Build, install, y launch
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.elysium.code.debug/com.elysium.code.MainActivity

# Alternativa más corta
./gradlew installDebug && adb shell am start -n com.elysium.code.debug/com.elysium.code.MainActivity
```

### Ver Build Info
```bash
# Ver tamaño del APK
ls -lh app/build/outputs/apk/debug/app-debug.apk

# Analizar APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | head -20

# Ver contenido de assets
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep assets
```

---

## 🔍 Debug del Editor y Terminal

### Ver Archivos Creados
```bash
# Listar archivos en directorio de la app
adb shell ls -R /sdcard/Android/data/com.elysium.code.debug/files/

# Ver contenido de archivo
adb shell cat /sdcard/Android/data/com.elysium.code.debug/files/test.txt

# Ver tamaño de archivos
adb shell du -h /sdcard/Android/data/com.elysium.code.debug/files/*
```

### Testear Comandos Terminal (desde device)
```bash
# Acceder al shell del dispositivo
adb shell

# Una vez dentro, ejecutar comandos:
ls /system/bin
pwd
whoami
date
df -h
```

---

## 📝 Scripts Útiles

### Script de Deploy Automático
```bash
#!/bin/bash
echo "🔨 Compilando..."
./gradlew assembleDebug
if [ $? -eq 0 ]; then
    echo "✅ Build exitoso"
    echo "📱 Instalando..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    echo "🚀 Lanzando app..."
    adb shell am start -n com.elysium.code.debug/com.elysium.code.MainActivity
    echo "✅ Aplicación iniciada"
else
    echo "❌ Compilación fallida"
fi
```

### Script para Ver Logs
```bash
#!/bin/bash
echo "🔍 Siguiendo logs de Elysium..."
adb logcat -v time | grep -E "Elysium|EditorViewModel|Terminal|Chat|ERROR|Exception"
```

### Script para Backup
```bash
#!/bin/bash
echo "💾 Haciendo backup de archivos..."
mkdir -p backup
adb pull /sdcard/Android/data/com.elysium.code.debug/files/ ./backup/
echo "✅ Backup completado en ./backup/"
```

---

## ⚙️ Troubleshooting

### APK no instala
```bash
# Ver error de instalación
adb install app/build/outputs/apk/debug/app-debug.apk -v

# Desinstalar y reinstalar
adb uninstall com.elysium.code.debug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### App se bloquea
```bash
# Ver crashes en logcat
adb logcat | grep "FATAL\|Exception\|AndroidRuntime"

# Ver proceso completo
adb logcat -v threadtime > full_logs.txt
```

### ADB no conecta
```bash
# Reiniciar daemon
adb kill-server
adb start-server

# Ver si el dispositivo está en lista
adb devices

# Conectar específicamente
adb connect 192.168.1.16:5555
```

---

## 🎓 Referencias Rápidas

### Estructura de Directorios
```
/sdcard/Android/data/com.elysium.code.debug/
├── files/                    ← Archivos editados, modelos
├── cache/                    ← Cache de app
└── no_backup/               ← Datos sin backup
```

### Package Name
```
com.elysium.code.debug         ← Debug build
com.elysium.code               ← Release build
```

### MainActivity
```
com.elysium.code.MainActivity
```

---

**¡Comandos listos para usar! 🚀**
