// chatboard.js
// The blackboard beside the TV that holds the Spaz Radio chat (chat.js): the room's lines in chalk, who is
// here, and a line to write on. It is open on a first visit; its X closes it (remembered), and the CHAT
// button, C or MENU › CHAT bring it back. While it is closed, the CHAT button's LED lights for new lines.

import { linkify, nameHash, MAX_NICK, MAX_TEXT } from './chat.js';

const CHALKS = 5;                    // pastel name colours in tv.css (.cb-c0 … .cb-c4); yellow is yours alone
const GROUP_MS = 5 * 60 * 1000;      // a line within 5 minutes of the same writer's last one joins it
const OPEN_KEY = 'lsd.chat.open';

const el = (tag, cls, text) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
};

export function createChatboard({ chat, board, button, reduced = () => false, storage = null }) {
  const load = () => { try { return storage?.getItem(OPEN_KEY); } catch { return null; } };
  const save = (v) => { try { storage?.setItem(OPEN_KEY, v ? '1' : '0'); } catch { /* not remembered */ } };

  board.replaceChildren();
  const slate = el('div', 'cb-slate');
  const close = el('button', 'cb-close');
  close.type = 'button';
  close.title = 'Close the chat (C brings it back)';
  close.setAttribute('aria-label', 'Close chat');
  close.innerHTML = '<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M7 6c5 5 11 12 19 20"/><path d="M25 6c-6 6-12 12-19 19"/></svg>';
  const title = el('h2', 'cb-title', 'Spaz Radio chat');
  const roster = el('button', 'cb-roster');
  roster.type = 'button';
  roster.title = 'Who is here (click for everyone)';
  const log = el('div', 'cb-log');
  log.setAttribute('role', 'log');
  log.setAttribute('aria-live', 'polite');
  log.setAttribute('aria-label', 'Chat messages');
  const newer = el('button', 'cb-newer', '↓ new');
  newer.type = 'button';
  newer.hidden = true;
  const form = el('form', 'cb-form');
  const me = el('button', 'cb-me');
  me.type = 'button';
  me.title = 'Click to change your name';
  const input = el('input', 'cb-input');
  input.type = 'text';
  input.autocomplete = 'off';
  input.enterKeyHint = 'send';
  const status = el('div', 'cb-status');
  status.setAttribute('aria-live', 'polite');
  form.append(me, input);
  slate.append(close, title, roster, log, newer, form, status);
  const tray = el('div', 'cb-tray');
  tray.append(el('span', 'cb-chalk'), el('span', 'cb-eraser'));
  board.append(slate, tray);

  let naming = !chat.state.nick; // the bottom line asks for a name until there is one
  let rendered = [];              // the message objects on the board, in order
  let unread = false;
  let statusTimer = null;

  // --- Open / close ---
  const isOpen = () => !board.hidden;
  function setOpen(open, { remember = true, focus = false } = {}) {
    board.hidden = !open;
    document.body.classList.toggle('chat-open', open);
    button?.setAttribute('aria-pressed', String(open));
    if (remember) save(open);
    if (open) {
      setUnread(false);
      stickToBottom();
      if (focus) input.focus({ preventScroll: true });
    }
  }
  function setUnread(v) {
    unread = v;
    button?.classList.toggle('unread', v);
  }
  close.addEventListener('click', () => setOpen(false));

  // --- Who is here ---
  function renderRoster() {
    const names = chat.names;
    if (chat.state.status === 'online') {
      roster.textContent = names.length ? `${names.length} here: ${names.join(', ')}` : 'nobody has signed the board yet';
    } else {
      roster.textContent = chat.state.status === 'connecting' || chat.state.status === 'idle'
        ? 'connecting…' : 'offline · reconnecting…';
    }
  }
  roster.addEventListener('click', () => roster.classList.toggle('expanded'));

  // --- The lines ---
  const timeFmt = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });
  const dayFmt = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
  const when = (t) => (new Date(t).toDateString() === new Date().toDateString() ? timeFmt : dayFmt).format(t);
  const isMine = (name) => !!chat.state.nick && name.toLowerCase() === chat.state.nick.toLowerCase();

  function line(msg, prev, fresh) {
    const cont = prev && prev.user === msg.user && msg.time - prev.time < GROUP_MS && msg.time >= prev.time;
    const row = el('div', 'cb-msg' + (cont ? ' cont' : '') + (fresh && !reduced() ? ' new' : ''));
    if (!cont) {
      const who = el('div', 'cb-who');
      const name = el('span', 'cb-name ' + (isMine(msg.user) ? 'cb-mine' : `cb-c${nameHash(msg.user) % CHALKS}`), msg.user);
      const time = el('time', 'cb-time', when(msg.time));
      time.dateTime = new Date(msg.time).toISOString();
      who.append(name, time);
      row.appendChild(who);
    }
    const text = el('div', 'cb-text');
    text.title = when(msg.time);
    for (const part of linkify(msg.message)) {
      if (!part.url) { text.append(part.text); continue; }
      const a = el('a', null, part.text);
      a.href = part.url;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      text.appendChild(a);
    }
    row.appendChild(text);
    return row;
  }
  const atBottom = () => log.scrollHeight - log.scrollTop - log.clientHeight < 40;
  function stickToBottom() { log.scrollTop = log.scrollHeight; newer.hidden = true; }
  function renderMessages(fresh = false) {
    const msgs = chat.state.messages;
    const follow = atBottom() || !rendered.length;
    // The usual case is one new line on the end: add it. Anything else (a merged log, the cap) redraws.
    const appended = msgs.length > rendered.length && rendered.every((m, i) => msgs[i] === m);
    if (appended) {
      for (let i = rendered.length; i < msgs.length; i++) log.appendChild(line(msgs[i], msgs[i - 1], fresh));
    } else {
      log.replaceChildren(...msgs.map((m, i) => line(m, msgs[i - 1], false)));
    }
    rendered = msgs.slice();
    if (!msgs.length) log.replaceChildren(el('div', 'cb-empty', 'the board is clean'));
    if (follow) stickToBottom();
    else if (fresh) newer.hidden = false;
  }
  log.addEventListener('scroll', () => { if (atBottom()) newer.hidden = true; });
  newer.addEventListener('click', stickToBottom);

  // --- The bottom line: your name, then what you write ---
  function renderForm() {
    if (naming) {
      me.hidden = true; // the placeholder says it all; the board is narrow
      input.placeholder = 'your name, in chalk';
      input.maxLength = MAX_NICK;
      input.setAttribute('aria-label', 'Your name in the chat');
    } else {
      me.textContent = `${chat.state.nick}:`;
      me.hidden = false;
      input.placeholder = 'write something…';
      input.maxLength = MAX_TEXT;
      input.setAttribute('aria-label', 'Message');
    }
  }
  function say(text, ms = 0) {
    clearTimeout(statusTimer);
    status.textContent = text;
    if (ms) statusTimer = setTimeout(() => { status.textContent = ''; }, ms);
  }
  me.addEventListener('click', () => {
    naming = true;
    renderForm();
    input.value = chat.state.nick;
    input.focus();
    input.select();
  });
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const value = input.value;
    if (!value.trim()) return;
    if (naming) {
      if (chat.join(value) || chat.state.nick) { naming = false; input.value = ''; renderForm(); renderMessages(); }
      return;
    }
    say('sending…');
    chat.send(value).then(() => {
      say('');
      if (input.value === value) input.value = ''; // unless they already started the next line
    }, (err) => {
      const why = { offline: 'offline · not sent', 'too fast': 'one line a second, please' }[err.message];
      say(why ?? 'not sent', 3000); // the text stays in the box to try again
    });
  });
  input.addEventListener('keydown', (e) => {
    e.stopPropagation(); // the TV's single-key shortcuts stay out of the chat
    if (e.key === 'Escape') {
      if (naming && chat.state.nick) { naming = false; input.value = ''; renderForm(); }
      input.blur();
    }
  });

  // --- Wiring ---
  const onChange = (what) => {
    if (what === 'messages') renderMessages(false);
    else if (what === 'roster' || what === 'status') renderRoster();
    else if (what === 'nick') { renderForm(); renderMessages(); }
  };
  const onLive = () => {
    renderMessages(true);
    if (!isOpen()) setUnread(true);
  };

  renderRoster();
  renderForm();
  renderMessages();
  const saved = load();
  setOpen(saved === null ? true : saved === '1', { remember: false }); // open on a first visit

  return {
    onChange, onLive,
    get open() { return isOpen(); },
    get unread() { return unread; },
    show(opts) { setOpen(true, opts); },
    hide() { setOpen(false); },
    toggle() { setOpen(!isOpen(), { focus: true }); },
  };
}
