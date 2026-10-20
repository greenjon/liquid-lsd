// Guided tour player: one clip per chapter (the whole chapter), captions synced to the clip.
(function () {
  'use strict';
  const steps = JSON.parse(document.getElementById('tour-data').textContent);
  const $ = (id) => document.getElementById(id);
  const video = $('tour-video');
  const counter = $('tour-counter');
  const chapter = $('tour-chapter');
  const caption = $('tour-caption');
  const fill = $('tour-progress-fill');
  const player = $('tour-player');
  const overlay = $('tour-overlay');
  const scrub = $('tour-scrub');
  const pauseBtn = $('tour-pause');
  const fullBtn = $('tour-full-btn');
  const auto = $('tour-autoplay');
  const speed = $('tour-speed');
  const links = $('tour-links');
  const navItems = Array.from(document.querySelectorAll('.tour-nav [data-step]'));
  const lines = Array.from(document.querySelectorAll('.tour-transcript [data-line]'));
  let cur = -1;

  function pad(n) { return String(n).padStart(2, '0'); }

  function select(i, opts) {
    i = Math.max(0, Math.min(steps.length - 1, i));
    const s = steps[i];
    const changed = i !== cur;
    cur = i;
    if (changed) {
      video.poster = s.poster;
      video.src = s.clip;
      video.defaultPlaybackRate = video.playbackRate = Number(speed.value);
      video.loop = !auto.checked;
      video.play().catch(() => {});
      counter.textContent = pad(i + 1) + ' / ' + pad(steps.length);
      chapter.textContent = s.vtitle + ' · ' + s.title;
      caption.textContent = '';
      links.innerHTML = '';
      if (s.docs) {
        const a = document.createElement('a');
        a.href = s.docs;
        a.textContent = 'Read the ' + s.vtitle + ' docs →';
        links.appendChild(a);
      }
      navItems.forEach((el) => {
        const on = Number(el.dataset.step) === i;
        el.classList.toggle('active', on);
        if (on) el.setAttribute('aria-current', 'step'); else el.removeAttribute('aria-current');
        if (on && opts && opts.scroll) el.scrollIntoView({ block: 'nearest' });
      });
      if (!opts || opts.hash !== false) history.replaceState(null, '', '#' + s.slug);
    }
  }

  function tick() {
    if (cur < 0) return;
    const s = steps[cur];
    const t = video.currentTime;
    const pct = video.duration ? Math.min(100, (t / video.duration) * 100) : 0;
    fill.style.width = pct + '%';
    scrub.setAttribute('aria-valuenow', String(Math.round(pct)));
    let active = -1;
    s.captions.forEach((c, k) => { if (t >= c.start && t < c.end) active = k; });
    caption.textContent = active >= 0 ? s.captions[active].text : '';
    lines.forEach((el) => {
      el.classList.toggle('now', Number(el.dataset.step) === cur && Number(el.dataset.line) === active);
    });
  }

  function fromHash() {
    const h = decodeURIComponent(location.hash.slice(1));
    const i = steps.findIndex((s) => s.slug === h);
    return i >= 0 ? i : 0;
  }

  navItems.forEach((el) => el.addEventListener('click', (e) => {
    e.preventDefault();
    select(Number(el.dataset.step));
  }));
  lines.forEach((el) => el.addEventListener('click', () => {
    const i = Number(el.dataset.step);
    select(i);
    const c = steps[i].captions[Number(el.dataset.line)];
    if (c) video.currentTime = c.start;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }));

  $('tour-prev').addEventListener('click', () => select(cur - 1));
  $('tour-next').addEventListener('click', () => select(cur + 1));
  function togglePause() {
    if (video.paused) video.play().catch(() => {}); else video.pause();
  }
  function syncPlayIcon() {
    pauseBtn.classList.toggle('is-playing', !video.paused);
    pauseBtn.setAttribute('aria-label', video.paused ? 'Play' : 'Pause');
  }
  video.addEventListener('play', syncPlayIcon);
  video.addEventListener('pause', syncPlayIcon);
  pauseBtn.addEventListener('click', togglePause);

  // Controls overlay (YouTube-style): shown on cursor movement over the video, hidden 5 s after the
  // cursor stops, and at once when it leaves. Also shown for 5 s on load. A tap shows them on touch screens.
  const HIDE_MS = 5000;
  let hideTimer = null;
  let busy = false; // speed menu open
  function hideControls() {
    clearTimeout(hideTimer);
    player.classList.add('controls-hidden');
  }
  function showControls() {
    player.classList.remove('controls-hidden');
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      if (busy || overlay.matches(':hover')) {
        showControls();
      } else {
        hideControls();
      }
    }, HIDE_MS);
  }
  player.addEventListener('pointermove', (e) => { if (e.pointerType !== 'touch') showControls(); });
  player.addEventListener('pointerenter', (e) => { if (e.pointerType !== 'touch') showControls(); });
  player.addEventListener('pointerleave', (e) => { if (e.pointerType !== 'touch' && !busy && !document.fullscreenElement) hideControls(); });
  speed.addEventListener('focus', () => { busy = true; });
  speed.addEventListener('blur', () => { busy = false; });
  speed.addEventListener('change', () => speed.blur());
  let touchWasHidden = false;
  video.addEventListener('pointerdown', (e) => { touchWasHidden = e.pointerType === 'touch' && player.classList.contains('controls-hidden'); });
  video.addEventListener('click', () => {
    if (touchWasHidden) { touchWasHidden = false; showControls(); return; }
    togglePause();
  });

  // Seek bar: click or drag within the current clip
  function seekTo(e) {
    const r = scrub.getBoundingClientRect();
    const f = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
    if (video.duration) video.currentTime = f * video.duration;
    tick();
  }
  scrub.addEventListener('pointerdown', (e) => { scrub.setPointerCapture(e.pointerId); scrub.dataset.drag = '1'; seekTo(e); });
  scrub.addEventListener('pointermove', (e) => { if (scrub.dataset.drag) seekTo(e); });
  scrub.addEventListener('pointerup', () => { delete scrub.dataset.drag; });

  function toggleFullscreen() {
    if (document.fullscreenElement) document.exitFullscreen();
    else if (player.requestFullscreen) player.requestFullscreen();
  }
  fullBtn.addEventListener('click', toggleFullscreen);
  document.addEventListener('fullscreenchange', showControls);
  video.addEventListener('timeupdate', tick);
  video.addEventListener('ended', () => { if (auto.checked && cur < steps.length - 1) select(cur + 1); });
  speed.addEventListener('change', () => { video.defaultPlaybackRate = video.playbackRate = Number(speed.value); });
  auto.addEventListener('change', () => { video.loop = !auto.checked; });
  window.addEventListener('hashchange', () => select(fromHash(), { hash: false }));
  document.addEventListener('keydown', (e) => {
    if (e.target.closest('input, textarea, select') || e.altKey || e.ctrlKey || e.metaKey) return;
    if (e.key === 'ArrowRight') { select(cur + 1); e.preventDefault(); }
    else if (e.key === 'ArrowLeft') { select(cur - 1); e.preventDefault(); }
    else if (e.key === ' ' && !e.target.closest('button, a')) { togglePause(); e.preventDefault(); }
    else if (e.key === 'k') { togglePause(); }
    else if (e.key === 'f') { toggleFullscreen(); }
  });

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { video.removeAttribute('loop'); video.autoplay = false; }
  select(fromHash(), { scroll: true, hash: false });
  syncPlayIcon();
  showControls();
})();
