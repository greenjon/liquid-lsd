// mqtt.js
// A small MQTT 3.1.1 client over WebSocket, just enough for the Spaz Radio chat (chat.js): CONNECT with a
// last will, SUBSCRIBE, PUBLISH at QoS 0 or 1 both ways, PINGREQ, DISCONNECT, and reconnecting with backoff.
// Written from the MQTT 3.1.1 specification (OASIS, 2014); no third-party code. QoS 2 is never asked for
// (subscriptions are QoS 1), so its four-way handshake is not implemented.

const enc = new TextEncoder();
const dec = new TextDecoder();

/** The variable-length "remaining length" field (1 to 4 bytes, 7 bits each, low first). */
export function encodeLength(n) {
  const out = [];
  do {
    let d = n % 128;
    n = Math.floor(n / 128);
    if (n > 0) d |= 0x80;
    out.push(d);
  } while (n > 0);
  return out;
}

const u16 = (n) => [(n >> 8) & 0xff, n & 0xff];
const str = (s) => { const b = enc.encode(s); return [...u16(b.length), ...b]; };
const bytes = (s) => (typeof s === 'string' ? enc.encode(s) : s);
const packet = (first, body) => Uint8Array.from([first, ...encodeLength(body.length), ...body]);

/** CONNECT: protocol "MQTT" level 4, clean session, optional will ({topic, payload, qos, retain}). */
export function encodeConnect({ clientId, keepAlive = 30, will = null }) {
  let flags = 0x02;
  if (will) flags |= 0x04 | ((will.qos || 0) << 3) | (will.retain ? 0x20 : 0);
  const body = [...str('MQTT'), 4, flags, ...u16(keepAlive), ...str(clientId)];
  if (will) { const p = bytes(will.payload ?? ''); body.push(...str(will.topic), ...u16(p.length), ...p); }
  return packet(0x10, body);
}

/** SUBSCRIBE (fixed header flags 0010), one or more {topic, qos}. */
export function encodeSubscribe(id, subs) {
  const body = [...u16(id)];
  for (const { topic, qos = 0 } of subs) body.push(...str(topic), qos);
  return packet(0x82, body);
}

/** PUBLISH; `id` is needed (and only sent) for QoS 1. */
export function encodePublish({ topic, payload = '', qos = 0, retain = false, id = 0 }) {
  const body = [...str(topic)];
  if (qos > 0) body.push(...u16(id));
  body.push(...bytes(payload));
  return packet(0x30 | (qos << 1) | (retain ? 1 : 0), body);
}

export const encodePuback = (id) => Uint8Array.from([0x40, 2, ...u16(id)]);
export const encodePing = () => Uint8Array.from([0xc0, 0]);
export const encodeDisconnect = () => Uint8Array.from([0xe0, 0]);

/** Splits a byte stream into packets {type, flags, body}; frames may arrive split or several at once. */
export class Parser {
  constructor() { this.buf = new Uint8Array(0); }
  push(chunk) {
    const add = chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk);
    const b = new Uint8Array(this.buf.length + add.length);
    b.set(this.buf); b.set(add, this.buf.length);
    this.buf = b;
    const out = [];
    for (;;) {
      if (this.buf.length < 2) break;
      let len = 0, mul = 1, i = 1, d;
      do {
        if (i >= this.buf.length) return out; // the length field itself is not complete yet
        d = this.buf[i++];
        len += (d & 0x7f) * mul;
        mul *= 128;
      } while (d & 0x80 && i < 5);
      if (this.buf.length < i + len) break;
      out.push({ type: this.buf[0] >> 4, flags: this.buf[0] & 0x0f, body: this.buf.slice(i, i + len) });
      this.buf = this.buf.slice(i + len);
    }
    return out;
  }
}

/** A PUBLISH packet's parts; the payload decoded as UTF-8. */
export function decodePublish(flags, body) {
  const qos = (flags >> 1) & 3;
  const tl = (body[0] << 8) | body[1];
  const topic = dec.decode(body.subarray(2, 2 + tl));
  let at = 2 + tl, id = 0;
  if (qos > 0) { id = (body[at] << 8) | body[at + 1]; at += 2; }
  return { topic, payload: dec.decode(body.subarray(at)), qos, retain: (flags & 1) === 1, id };
}

const T = { CONNACK: 2, PUBLISH: 3, PUBACK: 4, SUBACK: 9, PINGRESP: 13 };

/**
 * A client that keeps itself connected until close(). Callbacks: onState('connecting'|'online'|'offline'),
 * onReady() after the subscriptions are acknowledged (on every connect, reconnects included), and
 * onMessage({topic, payload, retain}).
 */
export function createClient({
  url, clientId, keepAlive = 30, will = null, subscriptions = [],
  onState = () => {}, onReady = () => {}, onMessage = () => {},
  WebSocketImpl = globalThis.WebSocket, pingTimeoutMs = 10000, maxBackoffMs = 30000, random = Math.random,
}) {
  let ws = null, stopped = true, attempts = 0, nextId = 1;
  let pingTimer = null, pongTimer = null, retryTimer = null;
  let state = 'offline';
  const pending = new Map(); // QoS 1 publishes awaiting PUBACK: id -> {resolve, reject}
  const setState = (s) => { if (s !== state) { state = s; onState(s); } };
  const id = () => { const n = nextId; nextId = nextId >= 0xffff ? 1 : nextId + 1; return n; };
  const send = (bytes) => { if (ws && ws.readyState === 1) ws.send(bytes); };

  function clearTimers() {
    clearInterval(pingTimer); clearTimeout(pongTimer); pingTimer = pongTimer = null;
  }
  function open() {
    clearTimeout(retryTimer); retryTimer = null;
    if (stopped || ws) return;
    setState('connecting');
    const sock = new WebSocketImpl(url, ['mqtt']);
    ws = sock;
    sock.binaryType = 'arraybuffer';
    const parser = new Parser();
    sock.onopen = () => sock.send(encodeConnect({ clientId, keepAlive, will }));
    sock.onmessage = (ev) => { if (ws === sock) for (const p of parser.push(ev.data)) handle(p); };
    sock.onerror = () => {}; // onclose follows
    sock.onclose = () => { if (ws === sock) dropped(); };
  }
  function handle({ type, flags, body }) {
    if (type === T.CONNACK) {
      if (body[1] !== 0) { abandon(); return; } // refused: retry later like any drop
      attempts = 0;
      send(encodeSubscribe(id(), subscriptions));
      clearTimers();
      pingTimer = setInterval(() => {
        send(encodePing());
        clearTimeout(pongTimer);
        pongTimer = setTimeout(abandon, pingTimeoutMs); // half-open socket: start over now
      }, keepAlive * 1000);
    } else if (type === T.SUBACK) {
      setState('online');
      onReady();
    } else if (type === T.PINGRESP) {
      clearTimeout(pongTimer);
    } else if (type === T.PUBACK) {
      const n = (body[0] << 8) | body[1];
      pending.get(n)?.resolve();
      pending.delete(n);
    } else if (type === T.PUBLISH) {
      const m = decodePublish(flags, body);
      if (m.qos === 1) send(encodePuback(m.id));
      onMessage(m);
    }
  }
  /** Gives up on the current socket at once (a browser can take a long time to report a dead one closed). */
  function abandon() {
    const sock = ws;
    if (!sock) return;
    dropped();
    try { sock.close(); } catch { /* already closing */ }
  }
  function dropped() {
    ws = null;
    clearTimers();
    for (const p of pending.values()) p.reject(new Error('connection lost'));
    pending.clear();
    setState('offline');
    if (stopped) return;
    const base = Math.min(maxBackoffMs, 1000 * 2 ** attempts++);
    retryTimer = setTimeout(open, base * (0.75 + random() * 0.5));
  }

  return {
    get state() { return state; },
    connect() { stopped = false; open(); },
    /** Skips the backoff wait (the network just came back). */
    reconnectNow() { if (!stopped && !ws) { attempts = 0; open(); } },
    /** Resolves when sent (QoS 0) or acknowledged (QoS 1); rejects when offline or dropped first. */
    publish(topic, payload, { qos = 0, retain = false } = {}) {
      if (state !== 'online') return Promise.reject(new Error('offline'));
      if (qos === 0) { send(encodePublish({ topic, payload, retain })); return Promise.resolve(); }
      const n = id();
      return new Promise((resolve, reject) => {
        pending.set(n, { resolve, reject });
        send(encodePublish({ topic, payload, qos: 1, retain, id: n }));
      });
    },
    /** Stops for good. Graceful: a DISCONNECT first, so the broker does not publish the will. */
    close({ graceful = true } = {}) {
      stopped = true;
      clearTimeout(retryTimer);
      const sock = ws;
      if (!sock) return;
      if (graceful) send(encodeDisconnect());
      ws = null;
      dropped();
      try { sock.close(); } catch { /* already closing */ }
    },
  };
}
