#!/bin/bash

# ═══════════════════════════════════════════════════════════════
# Elysium Code — GitHub Publication Utility
# ═══════════════════════════════════════════════════════════════

echo "🚀 Preparing Elysium Code for public release..."

# 1. Check if Git is initialized (safeguard)
if [ ! -d ".git" ]; then
    echo "⚠️ Git not initialized. Initializing now..."
    git init
    git add .
    git commit -m "Initial commit: Elysium Code Agentic Terminal"
fi

# 2. Ask for GitHub Remote URL
echo "📬 Please enter your GitHub Public Repository URL (e.g., https://github.com/username/elysium-code-android.git):"
read REPO_URL

if [ -z "$REPO_URL" ]; then
    echo "❌ Error: Repository URL cannot be empty."
    exit 1
fi

# 3. Add Remote
echo "🔗 Linking remote..."
git remote add origin "$REPO_URL" 2>/dev/null || git remote set-url origin "$REPO_URL"

# 4. Push to Main
echo "📤 Pushing to GitHub..."
git branch -M main
git push -u origin main

if [ $? -eq 0 ]; then
    echo "✨ SUCCESS! Elysium Code is now public and protected."
    echo "🌐 View your repository at: $REPO_URL"
else
    echo "❌ Push failed. Ensure you have created the repository on GitHub first."
    exit 1
fi
