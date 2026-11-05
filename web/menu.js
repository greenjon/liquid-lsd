// menu.js
// The TV's service menu: a CRT-green on-screen list over the picture, like an old set's picture menu, in a few
// short pages so the help line always shows. Opened with the MENU button or M, with the TV on or off.
//
// It opens on the page tabs (the page used last), where ← → or Tab change page. ↑ ↓ go from the tabs through the
// rows and round again; on a row, ← → (or its arrows, or the wheel with the cursor on its value) change it and
// Enter runs it. The wheel anywhere else scrolls the rows. Esc, M or ✕ closes. While open it owns the keyboard.
//
// Pages: [{ name, rows }]. Rows: { label, value(): string, step?(dir), run?(), locked?(): bool, needsPower?: bool,
// help?: string }. ui.js builds them. `help` explains the row, on the line at the bottom, for the selected row or
// the one under the cursor. `needsPower` rows act on the picture or the sound, so they lock while the TV is off.

const OFF_HELP = 'TURN THE TV ON TO USE THIS.';
const KEYS_HELP = '←→ PAGE  ↑↓ SELECT  M CLOSE';
const WHEEL_LATCH_MS = 300; // a scroll that carries a value under the cursor stays a scroll until it pauses this long
const TABS = -1;            // `sel` when the page tabs have the keyboard

const div = (cls) => {
  const e = document.createElement('div');
  e.className = cls;
  return e;
};

export function createMenu(screen, pages, { version = '', isOn = () => true, onOpen = () => {}, onClose = () => {} } = {}) {
  const el = document.createElement('div');
  el.id = 'tv-menu';
  el.title = ''; // no inherited cabinet/picture hover help; the hint line explains the rows
  el.setAttribute('role', 'dialog');
  el.setAttribute('aria-label', 'Menu');
  el.hidden = true;
  screen.appendChild(el);
  // A row click re-renders the menu, detaching the clicked element before the click bubbles on, so the
  // cabinet's power handler could no longer see it came from the menu: menu clicks stop here.
  el.addEventListener('click', (e) => e.stopPropagation());

  const head = div('menu-title');
  const name = document.createElement('span');
  name.textContent = 'MENU';
  const tabs = div('menu-tabs');
  tabs.setAttribute('role', 'tablist');
  const ver = document.createElement('span');
  ver.className = 'menu-version';
  ver.textContent = version;
  const closeBtn = document.createElement('button');
  closeBtn.className = 'menu-close';
  closeBtn.textContent = '✕';
  closeBtn.setAttribute('aria-label', 'Close the menu');
  closeBtn.addEventListener('click', () => menu.hide());
  head.append(name, tabs, ver, closeBtn);
  const rowsEl = div('menu-rows');
  rowsEl.setAttribute('role', 'menu');
  const hint = div('menu-hint');
  el.append(head, rowsEl, hint);

  let page = 0;
  let sel = TABS;
  let hover = null;   // the row under the cursor: the hint line explains it
  let listWheelAt = -Infinity;

  const rows = () => pages[page].rows;
  const off = (row) => row.needsPower && !isOn();
  const isLocked = (row) => off(row) || !!row.locked?.();
  const helpFor = (row) => (off(row) ? OFF_HELP : row.help);

  // The wheel changes a value only with the cursor on it (delegated: render() replaces the rows under the cursor).
  rowsEl.addEventListener('wheel', (e) => {
    if (!e.deltaY) return;
    const v = e.target.closest?.('.menu-value[data-row]');
    const row = v && rows()[Number(v.dataset.row)];
    const now = performance.now();
    if (!row || isLocked(row) || now - listWheelAt < WHEEL_LATCH_MS) { listWheelAt = now; return; } // the rows scroll
    e.preventDefault();
    sel = Number(v.dataset.row);
    change(e.deltaY < 0 ? 1 : -1);
  }, { passive: false });
  rowsEl.addEventListener('mouseover', (e) => {
    const r = e.target.closest?.('.menu-row');
    hover = r ? Number(r.dataset.row) : null;
    showHint();
  });
  rowsEl.addEventListener('mouseleave', () => { hover = null; showHint(); });

  function showHint() {
    const row = rows()[hover ?? sel];
    const help = row && helpFor(row);
    hint.textContent = help ?? KEYS_HELP;
    hint.classList.toggle('help', !!help);
  }

  /** `reveal` brings the selected row into view (the keyboard moved it); otherwise the rows stay where they were scrolled. */
  function render(reveal = false) {
    tabs.replaceChildren(...pages.map((p, i) => {
      const tab = document.createElement('button');
      tab.className = 'menu-tab' + (i === page ? ' on' : '') + (i === page && sel === TABS ? ' sel' : '');
      tab.textContent = p.name;
      tab.setAttribute('role', 'tab');
      tab.setAttribute('aria-selected', String(i === page));
      tab.addEventListener('click', () => goPage(i));
      return tab;
    }));
    const scrolled = rowsEl.scrollTop;
    rowsEl.replaceChildren(...rows().map((row, i) => {
      const r = div('menu-row' + (i === sel ? ' sel' : '') + (isLocked(row) ? ' locked' : ''));
      r.dataset.row = i;
      r.setAttribute('role', 'menuitem');
      const label = document.createElement('span');
      label.className = 'menu-label';
      label.textContent = row.label;
      r.appendChild(label);
      if (row.step) {
        const v = document.createElement('span');
        v.className = 'menu-value';
        v.dataset.row = i;
        const left = document.createElement('button'), right = document.createElement('button');
        left.textContent = '◀'; right.textContent = '▶';
        left.setAttribute('aria-label', `${row.label} down`); right.setAttribute('aria-label', `${row.label} up`);
        left.addEventListener('click', (e) => { e.stopPropagation(); sel = i; change(-1); });
        right.addEventListener('click', (e) => { e.stopPropagation(); sel = i; change(1); });
        const text = document.createElement('span');
        text.textContent = row.value();
        v.append(left, text, right);
        r.appendChild(v);
      } else if (row.value) {
        const v = document.createElement('span');
        v.className = 'menu-value';
        v.textContent = row.value();
        r.appendChild(v);
      }
      r.addEventListener('click', () => { sel = i; if (row.run) activate(); else render(); });
      return r;
    }));
    rowsEl.scrollTop = scrolled;
    showHint();
    if (reveal) {
      if (sel === TABS) rowsEl.scrollTop = 0;
      else rowsEl.querySelector('.menu-row.sel')?.scrollIntoView?.({ block: 'nearest' }); // a short window scrolls with the keys
    }
  }

  function goPage(i) {
    page = (i + pages.length) % pages.length;
    sel = TABS;
    hover = null;
    rowsEl.scrollTop = 0;
    render();
  }

  function change(dir, reveal = false) {
    const row = rows()[sel];
    if (row.step && !isLocked(row)) row.step(dir);
    render(reveal);
  }

  function activate() {
    const row = rows()[sel];
    if (row.run && !isLocked(row)) row.run();
    if (!el.hidden) render(true);
  }

  const menu = {
    get open() { return !el.hidden; },
    show() { if (!el.hidden) return; el.hidden = false; sel = TABS; hover = null; rowsEl.scrollTop = 0; render(); onOpen(); },
    hide() { if (el.hidden) return; el.hidden = true; onClose(); },
    toggle() { el.hidden ? menu.show() : menu.hide(); },
    refresh() { if (!el.hidden) render(); },
    /** Keyboard while open; returns true when the key was the menu's. */
    key(e) {
      if (el.hidden) return false;
      const last = rows().length - 1;
      switch (e.key) {
        case 'ArrowUp': sel = sel === TABS ? last : sel - 1; render(true); break; // above the first row: the tabs
        case 'ArrowDown': sel = sel === last ? TABS : sel + 1; render(true); break;
        case 'ArrowLeft': case '-': if (sel === TABS) goPage(page - 1); else change(-1, true); break;
        case 'ArrowRight': case '=': case '+': if (sel === TABS) goPage(page + 1); else change(1, true); break;
        case 'Tab': goPage(page + (e.shiftKey ? -1 : 1)); break;
        case 'Enter': case ' ': if (sel === TABS) { sel = 0; render(true); } else activate(); break;
        case 'Escape': case 'm': case 'M': menu.hide(); break;
        default: return true; // swallow other keys while the menu is open
      }
      return true;
    },
  };
  return menu;
}
