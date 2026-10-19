// Guided tour player: one looping clip per chapter, captions synced to the clip.
(function () {
  'use strict';
  const steps = JSON.parse(document.getElementById('tour-data').textContent);
  const $ = (id) => document.getElementById(id);
  const video = $('tour-video');
  const counter = $('tour-counter');
  const chapter = $('tour-chapter');
  const caption = $('tour-caption');
  const fill = $('tour-progress-fill');
  const pauseBtn = $('tour-pause');
  const auto = $('tour-autoplay');
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
      video.src = s.clip;
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
      pauseBtn.innerHTML = '&#10074;&#10074;';
    }
  }

  function tick() {
    if (cur < 0) return;
    const s = steps[cur];
    const t = video.currentTime;
    fill.style.width = (s.dur ? Math.min(100, (t / s.dur) * 100) : 0) + '%';
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
    if (c && c.inClip) video.currentTime = c.start;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }));

  $('tour-prev').addEventListener('click', () => select(cur - 1));
  $('tour-next').addEventListener('click', () => select(cur + 1));
  function togglePause() {
    if (video.paused) { video.play(); pauseBtn.innerHTML = '&#10074;&#10074;'; }
    else { video.pause(); pauseBtn.innerHTML = '&#9654;'; }
  }
  pauseBtn.addEventListener('click', togglePause);
  video.addEventListener('click', togglePause);
  video.addEventListener('timeupdate', tick);
  video.addEventListener('ended', () => { if (auto.checked && cur < steps.length - 1) select(cur + 1); });
  auto.addEventListener('change', () => { video.loop = !auto.checked; });
  window.addEventListener('hashchange', () => select(fromHash(), { hash: false }));
  document.addEventListener('keydown', (e) => {
    if (e.target.closest('input, textarea') || e.altKey || e.ctrlKey || e.metaKey) return;
    if (e.key === 'ArrowRight') { select(cur + 1); e.preventDefault(); }
    else if (e.key === 'ArrowLeft') { select(cur - 1); e.preventDefault(); }
    else if (e.key === ' ' && !e.target.closest('button, a')) { togglePause(); e.preventDefault(); }
  });

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { video.removeAttribute('loop'); video.autoplay = false; }
  select(fromHash(), { scroll: true, hash: false });
})();
