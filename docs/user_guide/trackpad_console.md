# Trackpad Performance Console (SCS.3m Virtual Console)

The **Trackpad Performance Console** transforms your laptop trackpad into an expressive, multi-touch performance surface inspired by the vintage **Stanton SCS.3m** DJ mixer.

By latching the `CapsLock` key, the trackpad stops acting as a mouse cursor and switches into an absolute capacitive performance console with dedicated crossfader and alpha stem faders.

---

## 4-Zone Surface Layout

```
┌─────────────────┬─────────────────┬─────────────────┐
│ [Y >= 0.94: 1.0]│ [Y >= 0.94: 1.0]│ [Y >= 0.94: 1.0]│
│  Deck A Alpha   │  Deck BG Alpha  │  Deck B Alpha   │  Top 55%
│   (0% - 100%)   │   (0% - 100%)   │   (0% - 100%)   │  (Y: 0.45 to 1.00)
│   mixer.levelA  │  mixer.levelBG  │   mixer.levelB  │
│ [Y <= 0.48: 0.0]│ [Y <= 0.48: 0.0]│ [Y <= 0.48: 0.0]│
├─────────────────┴─────────────────┴─────────────────┤
│                  DEADZONE BUFFER                    │  17% Buffer
│      (retains active drift, rejects new taps)       │  (Y: 0.28 to 0.45)
├─────────────────────────────────────────────────────┤
│ [X <= 0.05: -1]       [Center: 0.0]   [X >= 0.95: +1]│  Bottom 28%
│                     CROSSFADER                      │  (Y: 0.00 to 0.28)
│        Deck A (-1.0) <----------> Deck B (+1.0)     │
│                   mixer.crossfade                   │
└─────────────────────────────────────────────────────┘
```

1. **Bottom 28% (Crossfader)**:
   - Full-width horizontal fader controlling `Mixer.crossfade` from Deck A (`-1.0`) to Deck B (`+1.0`).
   - Center detent: Tapping or holding within $\pm 0.02$ of center snaps directly to `0.0` (50% A / 50% B blend).
2. **Deadzone Buffer (17% Height)**:
   - A physical gap between $Y = 0.28$ and $Y = 0.45$.
   - **Zone Affinity**: New finger taps inside the deadzone are ignored. However, an active touch that starts in the crossfader and drifts upward during fast scratching continues updating the crossfader without interruption.
3. **Top 55% (Three Vertical Alpha Faders)**:
   - **Left Third**: Deck A Level (`mixer.levelA`)
   - **Middle Third**: Deck BG Level (`mixer.levelBG`)
   - **Right Third**: Deck B Level (`mixer.levelB`)
   - Direct jump: Touching anywhere instantly snaps the level to that value.

---

## Multi-Finger Stutters & Video Strobing

Every zone maintains an independent **LIFO (Last-In-First-Out) multi-touch stack**:

### 1. Crossfader Cut Stutters
- **Anchor**: Hold one finger down on the left side (Deck A).
- **Stutter Tap**: Tap repeatedly with a second finger on the right side (Deck B).
- **Release**: Each tap snaps the crossfader to Deck B; lifting the tap snaps immediately back to Deck A.
- Enables machine-gun transform scratching and rhythm-synced visual cuts.

### 2. Alpha Video Strobes & Blackout Gates
- **Anchor**: Hold a finger down at the bottom of the Deck A column ($Y \le 0.48 \implies \text{Level} = 0\%$, blackout).
- **Strobe Tap**: Tap with a second finger at the top of the column ($Y \ge 0.94 \implies \text{Level} = 100\%$, full burst).
- **Release**: Releasing the tap immediately drops Deck A back to complete blackout.
- Creates rhythmic video flashes and stroboscopic accents.

### 3. Sticky Hold Levels
When all fingers are lifted from an alpha strip, the fader level stays frozen at that position. Each deck's active alpha is immediately visible on its respective monitor preview fader slider.

---

## Bezel Clamping

Physical laptop trackpads lose capacitive accuracy near the outer chassis. The console applies comfortable margins:
- **Crossfader**: Left edge clamps to `-1.0` at $X \le 0.05$; right edge clamps to `+1.0` at $X \ge 0.95$.
- **Alpha Stems**: Bottom clamps to `0.0` at $Y \le 0.48$; top hits `1.0` at $Y \ge 0.94$.

---

## Platform Setup & Permissions

### Linux (evdev & uaccess)
On Linux, Liquid LSD reads raw multi-touch data directly via `/dev/input/event*` and uses `ioctl(EVIOCGRAB)` to lock the OS mouse cursor while CapsLock is engaged.

1. **Automated Setup via Polkit in Settings**:
   If permissions are missing, Liquid LSD displays `Touchpad Status: Read/Write Permission Required` with an **[Install Permissions (Polkit)]** button in `Settings > Window Frame & Chrome`. Clicking this button launches `pkexec` to install `/etc/udev/rules.d/70-liquidlsd-touchpad.rules`:
   ```udev
   KERNEL=="event*", SUBSYSTEM=="input", ENV{ID_INPUT_TOUCHPAD}=="1", TAG+="uaccess", TAG+="seat", RUN{builtin}+="uaccess"
   ```
   Systemd's `uaccess` tag immediately grants non-root read/write access to the logged-in desktop seat user via POSIX ACLs without requiring a reboot or group changes. Liquid LSD verifies permissions using `access(2)` via JNA to properly detect POSIX ACL grants. Keeping this in Settings ensures the Mixer crossfader layout remains clean and perfectly framed.
2. **Desktop Installer Script**:
   Running `./scripts/install_desktop.sh` automatically configures this rule and cleans up legacy rules during installation.

### macOS (Cocoa Indirect Touches)
On macOS, trackpads support native indirect touch events through AppKit without requiring root permissions or special drivers.

### Focus Loss Safety
If Liquid LSD loses window focus (e.g. via `Alt+Tab`), the trackpad grab is immediately released, returning normal pointer functionality so you are never trapped.
