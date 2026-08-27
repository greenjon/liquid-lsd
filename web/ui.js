// ui.js
import { startAudio, setVolume } from './dsp.js';

// Power state — imported by renderer.js each frame
export const powerState = {
  on: false,          // is the TV powered on?
  warmupProgress: 0,  // 0..1, advanced by renderer.js each frame
};

// -------------------------------------------------------
// Power Switch
// -------------------------------------------------------
export function initUI() {
  const powerSwitch = document.getElementById('powerSwitch');
  const tvBody = document.getElementById('tv-body');
  const canvas = document.getElementById('glCanvas');

  let audioStarted = false;

  powerSwitch.addEventListener('click', async () => {
    if (powerState.on) return; // Phase 3: one-way switch (no power-off)

    powerState.on = true;
    powerState.warmupProgress = 0;  // renderer will advance this
    tvBody.classList.add('powered-on');
    powerSwitch.classList.add('active');

    if (!audioStarted) {
      audioStarted = true;
      try {
        await startAudio();
      } catch (err) {
        console.error('Audio start failed:', err);
      }
    }
  });

  // -------------------------------------------------------
  // Volume Dial — drag to rotate
  // -------------------------------------------------------
  const dial = document.getElementById('volumeDial');
  let dragging = false;
  let dragStartY = 0;
  let currentVolume = 0.8;  // start at 80%
  let currentAngle  = -36;  // degrees: -150=min, +150=max → 0.8 maps to ~90deg

  const MIN_ANGLE = -150;
  const MAX_ANGLE =  150;

  function setDialAngle(angle) {
    currentAngle = Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, angle));
    dial.style.transform = `rotate(${currentAngle}deg)`;
    const vol = (currentAngle - MIN_ANGLE) / (MAX_ANGLE - MIN_ANGLE);
    currentVolume = vol;
    setVolume(vol);
  }

  // Initialize dial at 80% volume
  setDialAngle(Math.round(MIN_ANGLE + 0.8 * (MAX_ANGLE - MIN_ANGLE)));

  dial.addEventListener('mousedown', (e) => {
    dragging = true;
    dragStartY = e.clientY;
    e.preventDefault();
  });
  window.addEventListener('mousemove', (e) => {
    if (!dragging) return;
    const delta = dragStartY - e.clientY;  // drag up = increase volume
    dragStartY = e.clientY;
    setDialAngle(currentAngle + delta * 1.5);
  });
  window.addEventListener('mouseup', () => { dragging = false; });

  // Touch support for mobile
  dial.addEventListener('touchstart', (e) => {
    dragging = true;
    dragStartY = e.touches[0].clientY;
    e.preventDefault();
  }, { passive: false });
  window.addEventListener('touchmove', (e) => {
    if (!dragging) return;
    const delta = dragStartY - e.touches[0].clientY;
    dragStartY = e.touches[0].clientY;
    setDialAngle(currentAngle + delta * 1.5);
  });
  window.addEventListener('touchend', () => { dragging = false; });

  // -------------------------------------------------------
  // Fullscreen — double-click canvas
  // -------------------------------------------------------
  canvas.addEventListener('dblclick', () => {
    if (!document.fullscreenElement) {
      canvas.requestFullscreen().then(() => {
        document.body.classList.add('fullscreen-mode');
      }).catch(console.error);
    } else {
      document.exitFullscreen().then(() => {
        document.body.classList.remove('fullscreen-mode');
      }).catch(console.error);
    }
  });

  document.addEventListener('fullscreenchange', () => {
    if (!document.fullscreenElement) {
      document.body.classList.remove('fullscreen-mode');
    }
  });

  // -------------------------------------------------------
  // Broadcast status — fired by autopilot.js when relay state changes
  // -------------------------------------------------------
  window.addEventListener('lsd-broadcast-status', (e) => {
    const badge = document.getElementById('station-badge');
    const text  = badge ? badge.querySelector('.station-text') : null;
    const led   = document.getElementById('stationLed');
    if (e.detail.live) {
      if (text) text.textContent = 'SPAZ RADIO \u2022 LIVE';
      if (led) {
        led.style.background = '#00ff44';
        led.style.boxShadow  = '0 0 6px rgba(0,255,68,0.9), 0 0 12px rgba(0,255,68,0.4)';
      }
    } else {
      if (text) text.textContent = 'SPAZ RADIO \u2022 CH.1';
      if (led) {
        led.style.background = '';
        led.style.boxShadow  = '';
      }
    }
  });
}

// Auto-init when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initUI);
} else {
  initUI();
}
