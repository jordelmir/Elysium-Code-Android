# 🌌 Elysium Code Android (Gemma 4 E4B Edition)

[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://img.shields.io/badge/Build-World--Class-00D4FF.svg)]()
[![Model: Gemma 4 E4B](https://img.shields.io/badge/AI-Gemma--4--E4B-7C3AED.svg)]()

> **The Ultimate Staff-Level Agentic AI Terminal for Android.** 
> Experience true local intelligence, multimodal agency, and a hardened Linux development environment—all in the palm of your hand.

---

## 💎 Features

- **🧠 Local Gemma 4 E4B Internalized**: Full agentic ReAct loop running locally on your device. No cloud. No latency. Zero-trust.
- **📸 Multimodal Mastery**: Chat with images, video, and audio using integrated Android content providers.
- **🐚 Hardened Linux Terminal**: A state-of-the-art PRoot-based environment with `apt` package management and rootfs persistence.
- **⌨️ Developer UI/UX**: Neon-glow aesthetics with a dedicated "Special Keys Bar" (Tab, Ctrl, Alt, etc.) for high-speed terminal productivity.
- **🔧 Automated Maintenance**: Self-healing rootfs installation, automatic permission hardening, and local model sideloading support.

---

## 🚀 Installation & Deployment

### 1. Build the APK
```bash
./gradlew assembleDebug
```

### 2. Provision the Model (Manual Push)
To achieve world-class performance, push the model file directly to the device storage to bypass APK size limits:

```bash
adb shell mkdir -p /sdcard/Documents/Elysium/models
adb push models/gemma-4-e4b-it-q4_k_m.gguf /sdcard/Documents/Elysium/models/
```
*The app will automatically detect and import the model on the next launch.*

---

## 🛠️ Staff-Level Engineering Fixes

This version includes critical stability and performance enhancements:
- **Navigation Purity**: Resolved `NullPointerException` in `SettingsScreen` via proper dependency injection.
- **I/O Resilience**: Fixed the `RootfsInstaller` infinite loop with exit-code verification and clean-state markers.
- **Permission Hardening**: Upgraded PRoot wrapper with `--link2symlink` and dynamic mount points for `/sdcard` and `/home/elysium`, resolving "Permission Denied" errors.
- **Multimodal Pipeline**: Specialized `ActivityResult` launchers and byte-array processing for seamless AI media reasoning.

---

## 📂 Project Structure

- `app/src/main/java/com/elysium/code/main`: High-performance Android core.
- `app/src/main/java/com/elysium/code/ai`: Native Llama.cpp bridge and Gemma orchestrator.
- `app/src/main/java/com/elysium/code/terminal`: PRoot environment and shell manager.
- `app/src/main/java/com/elysium/code/ui`: Premium Jetpack Compose UI (Neon Theme).

---

## 🔒 Security & Privacy

- **100% Offline**: All inference happens on-device. Your code and media never leave your phone.
- **Zero-Trust Tooling**: Explicit authorization required for sensitive terminal operations.
- **.env Protection**: Secure configuration handling for development credentials.

---

## 📜 License

MIT License - Copyright (c) 2024 Elysium AI Team.

---

**Developed with 💜 for the next generation of mobile-first engineers.**
