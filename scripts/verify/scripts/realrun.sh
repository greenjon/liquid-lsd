#!/bin/bash
cd /tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/../rt
export DISPLAY=:77 XDG_DATA_HOME=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/xdg/data XDG_CONFIG_HOME=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/xdg/config XDG_CACHE_HOME=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/xdg/cache LIBGL_ALWAYS_SOFTWARE=1
unset WAYLAND_DISPLAY; export XDG_SESSION_TYPE=x11 XDG_RUNTIME_DIR=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/xdg/run
export JAVA_OPTS="-Duser.home=/tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/xdg/home"
mkdir -p /tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/xdg/home
exec build/install/fx/bin/fx --no-audio --window 1280x720 > /tmp/claude-1000/-home-gj-projects-liquid-lsd/393fa06e-6df6-4655-91bc-3d0b1e86c9c4/scratchpad/verify/out/real_$1.log 2>&1
