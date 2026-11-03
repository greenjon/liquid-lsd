// tapedeck.js
// The TV's VCR: the TAPES list over the picture, the transport strip along the bottom of it, and the LIVE
// button that goes back to the radio. A tape is an audio input (dsp.js), so the picture reacts to it as it
// does to the radio. While one is in, a desktop broadcast does not take the picture over (autopilot.js
// setTapeHold): its picture follows the live sound, not the tape. The LIVE button pulses instead.
import { cvState, insertTape, ejectTape, setTapePaused, seekTape, tapeStatus } from './dsp.js';
import { setTapeHold, stationAir } from './autopilot.js';
import { scheduleState, upcoming } from './schedule.js';
import { tapesState, setTapes, refreshTapes, tapesStale, searchShows, randomShow, onThisDayQuery, formatClock, createResumeStore, fetchPeaks } from './tapes.js';

const SAVE_EVERY_S = 10;
const BUFFER_ROWS = 6;     // rows drawn beyond the visible ones
const GLITCH_MS = 500;
const COUNT = new Intl.NumberFormat();

const el = (tag, cls, text) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
};
const iconBtn = (cls, text, title) => {
  const b = el('button', `ts-btn ${cls}`, text);
  b.type = 'button';
  b.title = title;
  b.setAttribute('aria-label', title);
  return b;
};

export function createTapeDeck({ page, strip, button, liveBtn, storage = null, isOn = () => true, reduced = () => false, stickyStrip = () => false,
  message = () => {}, onOpen = () => {}, onChange = () => {} }) {
  const resume = createResumeStore(storage);
  let current = null;       // the show on the deck
  let airName = '';         // the live show on Spaz Radio, while a tape is in
  let peaks = null;         // Float32Array of levels 0..1, or null for a plain bar
  let dragFrac = null;      // the strip's position while it is being dragged
  let saveIn = SAVE_EVERY_S;

  // --- The list ---
  page.replaceChildren();
  const head = el('div', 'tp-head');
  head.append(el('span', 'tp-name', 'SPAZ RADIO · TAPES'));
  const closeBtn = el('button', 'tp-close', '✕');
  closeBtn.type = 'button';
  closeBtn.title = 'Close (Esc)';
  closeBtn.setAttribute('aria-label', 'Close tapes');
  head.append(closeBtn);
  const input = el('input', 'tp-search');
  input.type = 'search';
  input.autocomplete = 'off';
  input.enterKeyHint = 'go';
  input.setAttribute('aria-label', 'Search the archive');
  const list = el('div', 'tp-list');
  list.setAttribute('role', 'listbox');
  list.setAttribute('aria-label', 'Archive shows');
  const sizer = el('div', 'tp-sizer');
  const rowsEl = el('div', 'tp-rows');
  sizer.append(rowsEl);
  list.append(sizer);
  const hint = el('div', 'tp-hint', 'TYPE TO SEARCH · ↑↓ PICK · ENTER PLAYS · ESC CLOSES');
  page.append(head, input, list, hint);

  let items = [];     // what the list holds: action rows, then shows
  let sel = 0;
  let rowH = 0;

  const isOpen = () => !page.hidden;

  function rebuild() {
    const query = input.value.trim();
    const matches = searchShows(tapesState.shows, query);
    items = [];
    if (!query && tapesState.shows.length) {
      const last = resume.latest();
      const show = last && tapesState.shows.find((s) => s.id === last.id);
      if (show) items.push({ action: 'RESUME', label: show.title, value: formatClock(last.t), run: () => insert(show) });
      items.push({ action: 'RANDOM TAPE', label: 'ANY SHOW FROM THE ARCHIVE', value: '', run: () => insert(randomShow(tapesState.shows), { fromStart: true }) });
      items.push({ action: 'ON THIS DAY', label: 'THIS DATE, EVERY YEAR', value: '', run: () => { input.value = onThisDayQuery(Date.now()); sel = 0; rebuild(); } });
    }
    for (const show of matches) items.push({ show });
    input.placeholder = tapesState.shows.length ? `SEARCH ${COUNT.format(tapesState.shows.length)} SHOWS` : 'SEARCH';
    sel = Math.max(0, Math.min(sel, items.length - 1));
    list.scrollTop = Math.min(list.scrollTop, Math.max(0, items.length * rowH - list.clientHeight));
    renderRows();
  }

  function makeRow(item, i) {
    const row = el('button', 'tp-row' + (i === sel ? ' sel' : ''));
    row.type = 'button';
    row.setAttribute('role', 'option');
    row.setAttribute('aria-selected', String(i === sel));
    if (item.show) {
      const saved = resume.get(item.show.id);
      if (current?.id === item.show.id) row.classList.add('current');
      row.append(el('span', 'tp-date', item.show.date),
        el('span', 'tp-title', (current?.id === item.show.id ? '▶ ' : '') + item.show.title),
        el('span', 'tp-len', saved ? `${formatClock(saved)} / ${formatClock(item.show.seconds)}` : formatClock(item.show.seconds)));
    } else {
      row.classList.add('tp-action');
      row.append(el('span', 'tp-date', item.action), el('span', 'tp-title', item.label), el('span', 'tp-len', item.value));
    }
    row.addEventListener('click', () => { sel = i; pick(); });
    return row;
  }

  function renderRows() {
    if (!isOpen()) return;
    if (!items.length) {
      sizer.style.height = '';
      rowsEl.style.transform = '';
      const text = tapesState.loading && !tapesState.shows.length ? 'LOADING THE ARCHIVE…'
        : tapesState.failed ? 'THE ARCHIVE IS UNAVAILABLE · TRY AGAIN LATER' : 'NO SHOWS MATCH';
      rowsEl.replaceChildren(el('div', 'tp-empty', text));
      return;
    }
    if (!rowH) {
      const probe = makeRow(items[0], 0);
      rowsEl.replaceChildren(probe);
      rowH = probe.getBoundingClientRect().height || 20;
    }
    // Only the rows near the view exist: 3,813 buttons would make typing and scrolling crawl.
    sizer.style.height = `${items.length * rowH}px`;
    const first = Math.max(0, Math.floor(list.scrollTop / rowH) - BUFFER_ROWS);
    const last = Math.min(items.length, Math.ceil((list.scrollTop + list.clientHeight) / rowH) + BUFFER_ROWS);
    rowsEl.style.transform = `translateY(${first * rowH}px)`;
    const rows = [];
    for (let i = first; i < last; i++) rows.push(makeRow(items[i], i));
    rowsEl.replaceChildren(...rows);
  }
  list.addEventListener('scroll', renderRows, { passive: true });
  window.addEventListener('resize', () => { rowH = 0; renderRows(); });
  document.addEventListener('fullscreenchange', () => { rowH = 0; renderRows(); });

  function moveSel(delta) {
    if (!items.length) return;
    sel = Math.max(0, Math.min(items.length - 1, sel + delta));
    const top = sel * rowH, bottom = top + rowH;
    if (top < list.scrollTop) list.scrollTop = top;
    else if (bottom > list.scrollTop + list.clientHeight) list.scrollTop = bottom - list.clientHeight;
    renderRows();
  }
  function pick() {
    const item = items[sel];
    if (!item) return;
    if (item.show) insert(item.show);
    else item.run();
  }
  /** The list's own keys; true when the key was one of them. */
  function nav(e) {
    const page_ = Math.max(1, Math.floor(list.clientHeight / (rowH || 20)) - 1);
    switch (e.key) {
      case 'ArrowDown': moveSel(1); break;
      case 'ArrowUp': moveSel(-1); break;
      case 'PageDown': moveSel(page_); break;
      case 'PageUp': moveSel(-page_); break;
      case 'Enter': pick(); break;
      case 'Escape': close(); break;
      default: return false;
    }
    return true;
  }
  input.addEventListener('keydown', (e) => {
    e.stopPropagation(); // typing a search is not the TV's single-key shortcuts
    if (nav(e)) e.preventDefault();
  });
  input.addEventListener('input', () => { sel = 0; list.scrollTop = 0; rebuild(); });

  function open() {
    if (!isOn() || isOpen()) return;
    onOpen();
    page.hidden = false;
    button.setAttribute('aria-pressed', 'true');
    rowH = 0;
    sel = 0;
    list.scrollTop = 0;
    if (tapesStale()) refreshTapes().then(() => { if (isOpen()) rebuild(); });
    rebuild();
    if (typeof matchMedia === 'function' && matchMedia('(pointer: fine)').matches) {
      input.focus({ preventScroll: true }); // not on a phone: its keyboard would cover the list
      input.select();
    }
  }
  function close() {
    if (!isOpen()) return;
    input.blur();
    page.hidden = true;
    button.setAttribute('aria-pressed', 'false');
  }
  closeBtn.addEventListener('click', close);
  page.addEventListener('click', (e) => e.stopPropagation()); // the cabinet's near-miss handler stays out of it
  button.addEventListener('click', () => (isOpen() ? close() : open()));

  // --- The transport strip ---
  strip.replaceChildren();
  const ejectBtn = iconBtn('ts-eject', 'EJECT', 'Eject the tape and go back to the radio (L)');
  const backBtn = iconBtn('ts-back', '«30', 'Back 30 seconds (,)');
  const pauseBtn = iconBtn('ts-pause', '❚❚', 'Pause the tape (K)');
  const fwdBtn = iconBtn('ts-fwd', '30»', 'Forward 30 seconds (.)');
  const wave = el('canvas', 'ts-wave');
  wave.title = 'Click or drag to move through the show';
  const clock = el('span', 'ts-clock');
  strip.append(ejectBtn, backBtn, pauseBtn, fwdBtn, wave, clock);
  let stripTimer = null;

  const length = () => current?.seconds || tapeStatus()?.duration || 0;
  function drawStrip() {
    if (!current || !strip.classList.contains('show')) return;
    const status = tapeStatus();
    const dur = length();
    const pos = dragFrac !== null ? dragFrac * dur : status?.pos ?? 0;
    clock.textContent = `${formatClock(pos)} / ${formatClock(dur)}`;
    pauseBtn.textContent = status?.paused ? '▶' : '❚❚';
    const box = wave.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const w = Math.round(box.width * dpr), h = Math.round(box.height * dpr);
    if (!w || !h) return;
    if (wave.width !== w || wave.height !== h) { wave.width = w; wave.height = h; }
    const g = wave.getContext('2d');
    g.clearRect(0, 0, w, h);
    const played = dur > 0 ? (pos / dur) * w : 0;
    const barW = Math.max(1, Math.round(2 * dpr)), mid = h / 2;
    for (let x = 0; x < w; x += barW) {
      const level = peaks ? peaks[Math.min(peaks.length - 1, Math.floor((x / w) * peaks.length))] : 0.25;
      const half = Math.max(dpr, (level * h) / 2);
      g.fillStyle = x < played ? '#8cff8c' : 'rgba(140,255,140,0.3)';
      g.fillRect(x, mid - half, Math.max(1, barW - dpr), half * 2);
    }
    g.fillStyle = '#d9ffd9';
    g.fillRect(Math.min(w - dpr, played), 0, dpr, h);
  }
  function showStrip(ms = 4000) {
    if (!current) return;
    strip.classList.add('show');
    clearTimeout(stripTimer);
    if (!stickyStrip()) stripTimer = setTimeout(() => { if (dragFrac === null && !strip.matches(':hover')) strip.classList.remove('show'); else showStrip(ms); }, ms);
    drawStrip();
  }
  function hideStrip() {
    clearTimeout(stripTimer);
    strip.classList.remove('show');
  }
  const fracAt = (e) => {
    const box = wave.getBoundingClientRect();
    return Math.max(0, Math.min(1, (e.clientX - box.left) / box.width));
  };
  wave.addEventListener('pointerdown', (e) => {
    if (!length()) return;
    try { wave.setPointerCapture?.(e.pointerId); } catch { /* the pointer is already gone: a plain click still seeks */ }
    dragFrac = fracAt(e);
    drawStrip();
    e.preventDefault();
  });
  wave.addEventListener('pointermove', (e) => { if (dragFrac !== null) { dragFrac = fracAt(e); drawStrip(); } });
  wave.addEventListener('pointerup', (e) => {
    if (dragFrac === null) return;
    const frac = fracAt(e);
    dragFrac = null;
    seekTo(frac * length());
  });
  wave.addEventListener('pointercancel', () => { dragFrac = null; drawStrip(); });
  strip.addEventListener('click', (e) => e.stopPropagation());
  ejectBtn.addEventListener('click', () => eject());
  backBtn.addEventListener('click', () => seekBy(-30));
  fwdBtn.addEventListener('click', () => seekBy(30));
  pauseBtn.addEventListener('click', () => togglePause());

  // --- The deck ---
  function save() {
    const status = tapeStatus();
    if (current && status) resume.set(current.id, status.pos, current.seconds);
  }
  function insert(show, { fromStart = false } = {}) {
    if (!show) return;
    if (current?.id === show.id) { close(); showStrip(); return; }
    save();
    const startAt = fromStart ? 0 : resume.get(show.id);
    current = show;
    peaks = null;
    fetchPeaks(show).then((levels) => { if (current === show) { peaks = levels; drawStrip(); } });
    setTapeHold(true);
    if (!reduced()) cvState.glitchUntil = performance.now() + GLITCH_MS; // the picture rolls as the tape goes in
    insertTape(show.url, startAt);
    close();
    liveBtn.setAttribute('aria-pressed', 'true');
    liveBtn.setAttribute('aria-disabled', 'false');
    airName = currentAir(); // already on when the tape went in: the viewer chose this, so no announcement
    liveBtn.classList.toggle('pulsing', !!airName);
    saveIn = SAVE_EVERY_S;
    onChange(current);
    showStrip(6000);
    message(startAt > 0 ? `RESUMING AT ${formatClock(startAt)}` : 'TAPE IN', 2000);
  }
  function eject(text = 'LIVE RADIO') {
    if (!current) return;
    save();
    current = null;
    peaks = null;
    airName = '';
    hideStrip();
    liveBtn.classList.remove('pulsing');
    liveBtn.setAttribute('aria-pressed', 'false');
    liveBtn.setAttribute('aria-disabled', 'true');
    if (!reduced()) cvState.glitchUntil = performance.now() + GLITCH_MS;
    ejectTape();
    setTapeHold(false);
    onChange(null);
    message(text, 2500);
  }
  function seekTo(seconds) {
    if (!current) return;
    seekTape(seconds);
    save();
    showStrip();
  }
  function seekBy(delta) {
    const status = tapeStatus();
    if (!current || !status) return;
    const dur = length();
    seekTo(Math.max(0, dur ? Math.min(dur - 1, status.pos + delta) : status.pos + delta));
  }
  function togglePause() {
    const status = tapeStatus();
    if (!current || !status) return;
    setTapePaused(!status.paused);
    message(status.paused ? 'PLAY' : 'TAPE PAUSED', 1500);
    if (!status.paused) save();
    onChange(current);
    showStrip();
  }

  /** The live show on Spaz Radio now: a desktop broadcast, or the schedule's show on air. */
  function currentAir() {
    const scheduled = upcoming(scheduleState.shows, Date.now()).find((s) => s.onAir)?.name;
    if (stationAir.onAir) return stationAir.broadcaster || scheduled || 'SPAZ RADIO';
    return scheduled || '';
  }
  function everySecond() {
    if (!current) return;
    const air = currentAir();
    if (air !== airName) {
      if (air && !airName) message(`LIVE NOW: ${air} · PRESS L`, 6000); // the tape plays on; the button pulses
      airName = air;
      liveBtn.classList.toggle('pulsing', !!air);
      onChange(current);
    }
    drawStrip();
    if (--saveIn <= 0) { saveIn = SAVE_EVERY_S; if (isOn() && !tapeStatus()?.paused) save(); }
  }
  setInterval(everySecond, 1000);

  liveBtn.setAttribute('aria-disabled', 'true');
  liveBtn.addEventListener('click', () => {
    if (!isOn()) return;
    if (current) eject();
    else message('ALREADY ON THE RADIO', 1500);
  });
  window.addEventListener('lsd-tape-ended', () => {
    if (!current) return;
    const { id } = current;
    eject('END OF TAPE');
    resume.clear(id); // eject remembered the last position; a finished show starts from the top next time
  });
  window.addEventListener('pagehide', save);

  return {
    get current() { return current; },
    get airName() { return airName; },
    get pageOpen() { return isOpen(); },
    open, close, insert, eject, seekBy, seekTo, togglePause, save, showStrip, hideStrip,
    toggle() { isOpen() ? close() : open(); },
    /** Keys while the list is open and its search box does not have them. A character goes to the search box. */
    key(e) {
      if (nav(e)) { e.preventDefault(); return; }
      if (e.key.length === 1) { input.focus({ preventScroll: true }); return; }
      if (e.key === 'Backspace') { input.focus({ preventScroll: true }); return; }
      e.preventDefault();
    },
    /** The headless check's fixture instead of the network. */
    setShows: setTapes,
  };
}
