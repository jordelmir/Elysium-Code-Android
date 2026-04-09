#!/bin/bash

# ═══════════════════════════════════════════════════════════════
# Elysium Code — Model Setup Utility
# ═══════════════════════════════════════════════════════════════

MODEL_DIR="models"
MODEL_FILE="gemma-4-e4b-it-q4_k_m.gguf"
MODEL_URL="https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"

echo "🌌 Initializing Elysium Model Setup..."

if [ ! -d "$MODEL_DIR" ]; then
    echo "📁 Creating models directory..."
    mkdir -p "$MODEL_DIR"
fi

if [ -f "$MODEL_DIR/$MODEL_FILE" ]; then
    echo "✅ Model already exists. Ready to deploy."
    exit 0
fi

echo "📥 Downloading Gemma 4 weights (this may take a while)..."
# Using curl with progress bar
curl -L -o "$MODEL_DIR/$MODEL_FILE" "$MODEL_URL"

if [ $? -eq 0 ]; then
    echo "✨ Model successfully integrated."
else
    echo "❌ Download failed. Please check your internet connection."
    exit 1
fi
