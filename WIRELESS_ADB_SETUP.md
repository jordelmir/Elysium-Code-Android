# 📱 Conexión ADB Wireless con Shizuku

## ⚠️ Problema Detectado

El puerto 37761 no está respondiendo en la IP 192.168.1.16. Esto puede deberse a:

1. **Shizuku no está activo** - Necesita estar ejecutándose
2. **ADB Wireless no está habilitado** - Se debe activar en Shizuku
3. **Firewall del dispositivo** - Podría estar bloqueando el puerto
4. **Sesión expirada** - Los datos pueden haber vencido

---

## ✅ Pasos para Conectar Correctamente

### Paso 1: Verificar Shizuku en el Dispositivo

En tu Android:
1. Abre **Shizuku**
2. Verifica que dice **"✅ Shizuku is active"** en verde
3. Si no está activo, toca para activarlo

### Paso 2: Habilitar ADB Wireless en Shizuku

En Shizuku:
1. Ve a **Settings** ⚙️
2. Busca **"Wireless ADB Debugging"** o **"ADB over WiFi"**
3. Asegúrate que esté **HABILITADO (ON)**
4. Debería generar un código de 6 dígitos nuevo

### Paso 3: Obtener Nuevas Credenciales

Cuando habilites ADB Wireless, Shizuku mostrará:
- **IP**: Como `192.168.1.X`
- **Puerto**: Como `5555` o `37761`
- **Código**: 6 dígitos (válido por ~5 minutos)

**IMPORTANTE**: El código cambia cada vez y caduca. Necesitas obtener uno NUEVO.

### Paso 4: Conectar desde la Mac

Una vez tengas los nuevos datos, usa:

```bash
# Opción A: Si el código es necesario (ADB 11.0.0+)
adb pair 192.168.1.X:PUERTO
# Luego escribe el código cuando pida

# Opción B: Conexión directa (sin código)
adb connect 192.168.1.X:PUERTO

# Verificar conexión
adb devices
```

---

## 🔧 Solución de Problemas

### Si sigue sin funcionar:

**Problema**: "Connection refused"
```bash
# Reiniciar adb
adb kill-server
adb start-server

# Intentar de nuevo
adb connect 192.168.1.X:PUERTO
```

**Problema**: "Unauthorized"
```bash
# En el dispositivo:
# 1. Aceptar la ventana de autorización ADB
# 2. Marcar "Always trust from this computer"
# 3. Toca ALLOW
```

**Problema**: "Offline"
```bash
# Verificar ping al dispositivo
ping 192.168.1.16

# Si no responde, WiFi está desconectado o IP cambió
```

---

## 📋 Comandos Útiles

```bash
# Ver dispositivos conectados
adb devices -l

# Ver dispositivos online
adb devices | grep device

# Verificar conexión
adb shell echo "OK"

# Ver info del dispositivo
adb shell getprop ro.build.version.release

# Instalar APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⏰ Proceso Completo (Resumen)

1. **En el Android**:
   - Abre Shizuku
   - Ve a Settings > Wireless ADB Debugging
   - Habilita
   - Copia la IP, Puerto y Código (anótalos ahora)

2. **En la Mac**:
   ```bash
   adb pair 192.168.1.X:PUERTO
   # Ingresa el código cuando lo pida
   
   adb connect 192.168.1.X:PUERTO
   adb devices
   ```

3. **En el Android**:
   - Si aparece una ventana de autorización, toca **ALLOW**

4. **De vuelta en Mac**:
   ```bash
   adb devices
   # Debe mostrar: 192.168.1.X:PUERTO     device
   ```

---

## ✨ Una vez Conectado

Ya podrás instalar la APK:

```bash
cd "IA Local Android Gemma 4 E4B"

# Instalar APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Crear directorio para modelo
adb shell mkdir -p /sdcard/Android/data/com.elysium.code.debug/files/models

# Copiar modelo (~4.2 GB)
adb push models/gemma-4-e4b-it-q4_k_m.gguf \
   /sdcard/Android/data/com.elysium.code.debug/files/models/

# Verificar
adb shell ls /sdcard/Android/data/com.elysium.code.debug/files/models/
```

---

## 🆘 Si Nada Funciona

1. **Reinicia el dispositivo**
2. **Abre Shizuku nuevamente**
3. **Habilita ADB Wireless nuevamente**
4. **Copia los NUEVOS datos**
5. **Intenta conectar**

---

**Nota**: Los datos de conexión (**IP:Puerto:Código**) cambien cada vez que se reinicia Shizuku o se deshabilita ADB Wireless. Son de **corta duración** (5-30 minutos típicamente).

¿Necesitas ayuda con alguno de estos pasos?
