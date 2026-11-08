#!/bin/bash
# pwlab.sh <dir> <command...>: runs the command with a private PipeWire + WirePlumber, inside its own user, network and
# mount namespaces, with /dev/snd hidden, so nothing reaches the real sound hardware or the desktop's PipeWire.
# Call through: unshare -Urnm pwlab.sh ...
D=$1; shift
mount -t tmpfs none /dev/snd
mkdir -p $D/run $D/cfg/wireplumber/wireplumber.conf.d $D/state
chmod 700 $D/run
cat > $D/cfg/wireplumber/wireplumber.conf.d/90-lab.conf <<'C'
wireplumber.profiles = {
  main = {
    monitor.alsa = disabled
    monitor.alsa-midi = disabled
    monitor.bluez = disabled
    monitor.bluez-midi = disabled
    monitor.v4l2 = disabled
    monitor.libcamera = disabled
  }
}
C
export XDG_RUNTIME_DIR=$D/run PIPEWIRE_RUNTIME_DIR=$D/run XDG_CONFIG_HOME=$D/cfg XDG_STATE_HOME=$D/state DBUS_SESSION_BUS_ADDRESS=unix:path=/nonexistent
unset PULSE_SERVER
pipewire > $D/pipewire.log 2>&1 &
echo $! > $D/pw.pid
sleep 1
wireplumber > $D/wireplumber.log 2>&1 &
echo $! > $D/wp.pid
export PWLAB_DIR=$D
sleep 2
# Stop this lab's PipeWire and WirePlumber afterwards (by the pid files, which labrun2 rewrites on a restart):
# left running they hold the socket in $D/run and the next lab can't start.
stop_lab() { for f in wp.pid pw.pid; do [ -f "$D/$f" ] && kill "$(cat "$D/$f")" 2>/dev/null; done; }
trap 'stop_lab; exit 143' TERM INT
"$@"; rc=$?
stop_lab
exit $rc
