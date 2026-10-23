// menu.js
// The TV's service menu: a CRT-green on-screen list over the picture, like an old set's picture menu.
// Opened with the MENU button or M; ↑ ↓ pick a row, ← → (or the wheel, or the row's arrows) change it,
// Enter runs an action row, Esc or M closes. While it is open it owns the keyboard.
//
// Rows: { label, value(): string, step?(dir), run?(), locked?(): bool, help?: string }. ui.js builds them;
// `help` explains the row: a hover tooltip, and the line at the bottom while the row is selected.

export function createMenu(screen, rows, { onOpen = () => {}, onClose = () => {} } = {}) {
  const el = document.createElement('div');
  el.id = 'tv-menu';
  el.setAttribute('role', 'menu');
  el.hidden = true;
  screen.appendChild(el);
  let sel = 0;

  function render() {
    el.innerHTML = '<div class="menu-title">MENU</div>';
    rows.forEach((row, i) => {
      const r = document.createElement('div');
      r.className = 'menu-row' + (i === sel ? ' sel' : '') + (row.locked?.() ? ' locked' : '');
      r.setAttribute('role', 'menuitem');
      if (row.help) r.title = row.help; // the mouse gets it on hover; the keyboard in the hint line
      const label = document.createElement('span');
      label.className = 'menu-label';
      label.textContent = row.label;
      r.appendChild(label);
      if (row.step) {
        const v = document.createElement('span');
        v.className = 'menu-value';
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
      r.addEventListener('wheel', (e) => { e.preventDefault(); sel = i; change(e.deltaY < 0 ? 1 : -1); }, { passive: false });
      el.appendChild(r);
    });
    const hint = document.createElement('div');
    hint.className = 'menu-hint';
    // The selected row's explanation, or the keys.
    hint.textContent = rows[sel].help ?? '↑↓ SELECT  ←→ CHANGE  ENTER OK  M CLOSE';
    if (rows[sel].help) hint.classList.add('help');
    el.appendChild(hint);
  }

  function change(dir) {
    const row = rows[sel];
    if (row.step && !row.locked?.()) row.step(dir);
    render();
  }

  function activate() {
    const row = rows[sel];
    if (row.run && !row.locked?.()) row.run();
    if (!el.hidden) render();
  }

  const menu = {
    get open() { return !el.hidden; },
    show() { if (!el.hidden) return; el.hidden = false; sel = 0; render(); onOpen(); },
    hide() { if (el.hidden) return; el.hidden = true; onClose(); },
    toggle() { el.hidden ? menu.show() : menu.hide(); },
    refresh() { if (!el.hidden) render(); },
    /** Keyboard while open; returns true when the key was the menu's. */
    key(e) {
      if (el.hidden) return false;
      switch (e.key) {
        case 'ArrowUp': sel = (sel + rows.length - 1) % rows.length; render(); break;
        case 'ArrowDown': sel = (sel + 1) % rows.length; render(); break;
        case 'ArrowLeft': case '-': change(-1); break;
        case 'ArrowRight': case '=': case '+': change(1); break;
        case 'Enter': case ' ': activate(); break;
        case 'Escape': case 'm': case 'M': menu.hide(); break;
        default: return true; // swallow other keys while the menu is open
      }
      return true;
    },
  };
  return menu;
}
