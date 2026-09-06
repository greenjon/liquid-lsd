#!/bin/bash
set -e
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICONS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/512x512/apps"

mkdir -p "$APPS_DIR" "$ICONS_DIR"

# Install icon
if [ -f "$PROJECT_ROOT/src/main/resources/icons/icon-512.png" ]; then
    cp "$PROJECT_ROOT/src/main/resources/icons/icon-512.png" "$ICONS_DIR/liquid-lsd.png"
elif [ -f "$PROJECT_ROOT/icon.png" ]; then
    cp "$PROJECT_ROOT/icon.png" "$ICONS_DIR/liquid-lsd.png"
fi

# Determine executable target
if [ -f "$PROJECT_ROOT/run-linux.sh" ]; then
    EXEC_CMD="$PROJECT_ROOT/run-linux.sh"
else
    EXEC_CMD="$PROJECT_ROOT/gradlew run"
fi

cat << DESKTOP_EOF > "$APPS_DIR/liquid-lsd.desktop"
[Desktop Entry]
Version=1.0
Type=Application
Name=Liquid LSD
GenericName=Audio-Reactive Visual Synthesizer
Comment=Libre Shader Decks - Real-time audio-reactive graphics workstation
Exec=$EXEC_CMD
Icon=liquid-lsd
Terminal=false
Categories=AudioVideo;Graphics;Audio;
StartupNotify=true
StartupWMClass=liquid-lsd
DESKTOP_EOF

chmod +x "$APPS_DIR/liquid-lsd.desktop"

command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" 2>/dev/null || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache "${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor" 2>/dev/null || true

echo "✓ Liquid LSD desktop entry and icon successfully installed to $APPS_DIR/liquid-lsd.desktop"
