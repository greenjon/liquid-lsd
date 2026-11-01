// chat.js
// The Spaz Radio chat: the same room as the Spaz Radio Android app and spaz.org/radio. No DOM here
// (chatboard.js draws it), so the protocol can be tested in node (tools/chat.test.mjs).
//
// The wire (checked against the live broker 2026-10-31; .planning/web-tv-chat-notes.md):
// - MQTT 3.1.1 over WebSocket at BROKER, no login.
// - Topic `spazradio`: chat lines, JSON {"user", "message"}, no time (only the server's log stamps one).
// - Topic `presence/<clientId>`: the nickname, retained; empty (or "offline") = gone. The last will is an empty
//   retained message there, so a crash clears it too. One person on several devices has several clientIds.
// - HISTORY_URL: the last 100 lines, {status:"OK", history:[{user, message, time_received (ms)}]}.

import { createClient } from './mqtt.js';

export const BROKER = 'wss://radio.spaz.org:1885/mqtt';
export const HISTORY_URL = 'https://radio.spaz.org/djdash/chatlog';
export const CHAN = 'spazradio';
export const MAX_MESSAGES = 300;
export const MAX_TEXT = 500;
export const MAX_NICK = 32;
export const SEND_GAP_MS = 1000;      // at most one line a second
export const MATCH_WINDOW_MS = 120000; // a live copy and the log's copy of one line, stamped by different clocks
export const SELF_HEAL_MS = 10000;     // re-announce presence if ours went missing

/** `mut` + 6 base36 characters, like the other clients' ids. New for every page load, never stored. */
export function newClientId(randomValues = (a) => crypto.getRandomValues(a)) {
  const a = randomValues(new Uint8Array(6));
  return 'mut' + Array.from(a, (b) => (b % 36).toString(36)).join('');
}

const NAMED = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' };
/**
 * A line as plain text: tags dropped and entities decoded, as the legacy web client shows it. The result only
 * ever goes into the page as text (textContent), never as markup.
 */
export function cleanText(s) {
  return String(s ?? '')
    .replace(/<\/?[a-zA-Z][^>]*>/g, '')
    .replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e) => {
      if (e[0] === '#') {
        const n = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
        return n > 0 && n <= 0x10ffff ? String.fromCodePoint(n) : m;
      }
      return NAMED[e.toLowerCase()] ?? m;
    });
}

/** Splits text into [{text}] and [{url, text}] pieces: http(s) and www. links, minus trailing punctuation. */
export function linkify(text) {
  const out = [];
  const re = /\b(?:https?:\/\/|www\.)[^\s<>"]+/gi;
  let last = 0, m;
  while ((m = re.exec(text))) {
    let link = m[0];
    // Trailing punctuation is the sentence's, not the link's; a ")" closing a "(" inside the link stays.
    const count = (c) => link.split(c).length - 1;
    while (/[.,!?;:'")\]]$/.test(link)) {
      if (link.endsWith(')') && count('(') >= count(')')) break;
      link = link.slice(0, -1);
    }
    if (m.index > last) out.push({ text: text.slice(last, m.index) });
    out.push({ url: /^https?:/i.test(link) ? link : `https://${link}`, text: link });
    last = m.index + link.length;
    re.lastIndex = last;
  }
  if (last < text.length) out.push({ text: text.slice(last) });
  return out;
}

/** A line from the wire or the log in our form {user, message, time (ms)}; null when it lacks either part. */
export function normalize(raw, now) {
  if (!raw || typeof raw.user !== 'string' || typeof raw.message !== 'string') return null;
  const t = Number(raw.time_received);
  return { user: cleanText(raw.user), message: cleanText(raw.message), time: t > 0 ? t : now };
}

/**
 * Merges a fetched log into the lines shown, so a reconnect fills the gap without doubles. Each shown line is
 * matched to at most one log line with the same user and text stamped within MATCH_WINDOW_MS (a repeated "lol"
 * keeps every copy). Unmatched shown lines before the first match come first (older than the log, which only
 * holds 100), then the log, then the other unmatched ones (sent after the log was read). With no match at all,
 * shown lines are split by time against the log's oldest. Same rule as the Android app's ChatMessages.merge.
 */
export function mergeHistory(current, history) {
  if (!history.length) return current;
  const used = new Array(history.length).fill(false);
  const matched = current.map((msg) => {
    const i = history.findIndex((h, j) => !used[j] && h.user === msg.user && h.message === msg.message
      && Math.abs(h.time - msg.time) <= MATCH_WINDOW_MS);
    if (i >= 0) used[i] = true;
    return i >= 0;
  });
  const first = matched.indexOf(true);
  const oldest = history[0].time;
  const older = [], newer = [];
  current.forEach((msg, k) => {
    if (matched[k]) return;
    ((first >= 0 ? k < first : msg.time < oldest) ? older : newer).push(msg);
  });
  return [...older, ...history, ...newer].slice(-MAX_MESSAGES);
}

/** The names online: one per person however many devices, sorted without regard to case. */
export function rosterNames(roster) {
  const seen = new Map();
  for (const name of roster.values()) if (!seen.has(name.toLowerCase())) seen.set(name.toLowerCase(), name);
  return [...seen.values()].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
}

/** A stable small number for a name (FNV-1a), so a name gets the same chalk colour on every screen. */
export function nameHash(name) {
  let h = 0x811c9dc5;
  for (const ch of name.toLowerCase()) { h ^= ch.codePointAt(0); h = Math.imul(h, 0x01000193) >>> 0; }
  return h;
}

/**
 * The chat. `onChange(what)` fires with 'messages' (the log was merged in) | 'roster' | 'status' | 'nick';
 * `onLive(msg)` for each line that arrives over the wire, after it is in state.messages. Dependencies are injectable for the tests.
 */
export function createChat({
  onChange = () => {}, onLive = () => {},
  fetchImpl = (...a) => fetch(...a), WebSocketImpl = globalThis.WebSocket,
  now = () => Date.now(), clientId = newClientId(), storage = null,
  setIntervalImpl = (f, ms) => setInterval(f, ms), clearIntervalImpl = (t) => clearInterval(t),
} = {}) {
  const presenceTopic = `presence/${clientId}`;
  const load = () => { try { return storage?.getItem('lsd.chat.nick') || ''; } catch { return ''; } };
  const store = (v) => { try { v ? storage?.setItem('lsd.chat.nick', v) : storage?.removeItem('lsd.chat.nick'); } catch { /* not remembered */ } };
  const state = {
    status: 'idle',   // idle | connecting | online | offline
    messages: [],
    roster: new Map(), // clientId -> name
    nick: cleanText(load()).trim().slice(0, MAX_NICK),
    lastSend: -Infinity,
  };
  let client = null, healTimer = null, started = false;

  const announce = (name) => client?.publish(presenceTopic, name, { qos: 1, retain: true }).catch(() => {});

  async function loadHistory() {
    try {
      const res = await fetchImpl(HISTORY_URL, { cache: 'no-store' });
      if (!res.ok) return;
      const body = await res.json();
      if (body?.status !== 'OK' || !Array.isArray(body.history)) return;
      const t = now();
      const history = body.history.map((m) => normalize(m, t)).filter(Boolean);
      state.messages = mergeHistory(state.messages, history);
      onChange('messages');
    } catch { /* offline or blocked: the live lines still come */ }
  }

  function onMessage({ topic, payload }) {
    if (topic === CHAN) {
      let raw;
      try { raw = JSON.parse(payload); } catch { return; }
      const msg = normalize(raw, now());
      if (!msg) return;
      state.messages = [...state.messages, msg].slice(-MAX_MESSAGES);
      onLive(msg); // instead of onChange('messages'): the board draws a live line as just written
    } else if (topic.startsWith('presence/')) {
      const id = topic.slice('presence/'.length);
      const name = cleanText(payload).trim();
      if (!name || name === 'offline') state.roster.delete(id);
      else state.roster.set(id, name);
      onChange('roster');
    }
  }

  const chat = {
    state,
    clientId,
    get names() { return rosterNames(state.roster); },
    /** Connects (and keeps reconnecting) and reads the log; safe to call twice. */
    start() {
      if (started) return;
      started = true;
      loadHistory(); // lurkers see the room even before the socket is up
      client = createClient({
        url: BROKER, clientId, keepAlive: 30, WebSocketImpl,
        will: { topic: presenceTopic, payload: '', qos: 1, retain: true },
        subscriptions: [{ topic: CHAN, qos: 1 }, { topic: 'presence/#', qos: 1 }],
        onState: (s) => {
          state.status = s;
          if (s !== 'online' && state.roster.size) { state.roster.clear(); onChange('roster'); }
          onChange('status');
        },
        onReady: () => {
          loadHistory(); // fills in whatever was said while the connection was down
          if (state.nick) announce(state.nick);
        },
        onMessage,
      });
      client.connect();
      healTimer = setIntervalImpl(() => {
        if (state.status === 'online' && state.nick && state.roster.get(clientId) !== state.nick) announce(state.nick);
      }, SELF_HEAL_MS);
    },
    reconnectNow() { client?.reconnectNow(); },
    /** The headless check's fake broker and log, swapped in before start(). */
    useTransport(t) { if (!started) ({ WebSocketImpl = WebSocketImpl, fetchImpl = fetchImpl } = t); },
    /** Sets or changes the name; a change clears the old one first (the legacy client's order). */
    join(name) {
      const nick = cleanText(name).replace(/\s+/g, ' ').trim().slice(0, MAX_NICK);
      if (!nick || nick === state.nick) return false;
      const had = state.nick;
      state.nick = nick;
      store(nick);
      onChange('nick');
      if (state.status === 'online') { if (had) announce(''); announce(nick); }
      return true;
    },
    /** Sends a line. Resolves when the broker has it; the line itself shows when it comes back. */
    send(text) {
      const t = String(text ?? '').trim().slice(0, MAX_TEXT);
      if (!t) return Promise.reject(new Error('empty'));
      if (!state.nick) return Promise.reject(new Error('no name'));
      if (state.status !== 'online') return Promise.reject(new Error('offline'));
      if (now() - state.lastSend < SEND_GAP_MS) return Promise.reject(new Error('too fast'));
      state.lastSend = now();
      return client.publish(CHAN, JSON.stringify({ user: state.nick, message: t }), { qos: 1 });
    },
    /** Leaving the page: clear our name, then a clean DISCONNECT (the will covers a crash). */
    leave() {
      if (!client) return;
      if (state.status === 'online' && state.nick) client.publish(presenceTopic, '', { qos: 0, retain: true }).catch(() => {});
      client.close({ graceful: true });
      clearIntervalImpl(healTimer);
      client = null;
      started = false;
    },
  };
  return chat;
}
