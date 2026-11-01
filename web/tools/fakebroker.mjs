// A pretend Spaz Radio chat broker for the tests (tools/chat.test.mjs, tools/mqtt.test.mjs) and the headless
// UI check (checks/ui_check.html), so nothing ever posts to the real room. It speaks enough MQTT 3.1.1 for
// mqtt.js: CONNECT (with a will), SUBSCRIBE (retained messages delivered), PUBLISH QoS 0/1 with retain,
// PINGREQ, DISCONNECT, and the will on a dirty close.
import { Parser, decodePublish, encodePublish, encodePuback } from '../mqtt.js';

const dec = new TextDecoder();
const matches = (filter, topic) => filter === topic || (filter.endsWith('/#') && topic.startsWith(filter.slice(0, -1)));

export function createFakeBroker({ openDelay = 0 } = {}) {
  const retained = new Map();   // topic -> payload
  const clients = new Set();
  const broker = {
    published: [],              // every PUBLISH received: {clientId, topic, payload, qos, retain}
    connects: 0,
    answerPings: true,
    refuse: false,
    clients,
    retained,
    /** Someone else in the room says or sets something. */
    inject(topic, payload, { retain = false } = {}) { route(null, topic, payload, retain); },
    /** Every socket drops at once (a network blip); wills fire. */
    dropAll() { for (const c of [...clients]) c.serverClose(); },
    WebSocket: null,
  };

  function route(from, topic, payload, retain) {
    if (retain) { if (payload === '') retained.delete(topic); else retained.set(topic, payload); }
    for (const c of clients) {
      const sub = c.subs.find((s) => matches(s.topic, topic));
      if (sub && c.open) c.deliver(encodePublish({ topic, payload, qos: 0, retain: false }));
    }
  }

  class FakeWebSocket {
    constructor(url, protocols) {
      this.url = url; this.protocol = protocols?.[0] ?? ''; this.readyState = 0;
      this.subs = []; this.open = false; this.graceful = false; this.will = null;
      this.parser = new Parser();
      clients.add(this);
      setTimeout(() => { if (this.readyState !== 0) return; this.readyState = 1; this.open = true; this.onopen?.(); }, openDelay);
    }
    deliver(bytes) { setTimeout(() => { if (this.open) this.onmessage?.({ data: bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.length) }); }, 0); }
    send(bytes) {
      for (const p of this.parser.push(bytes)) this.handle(p);
    }
    handle({ type, flags, body }) {
      if (type === 1) { // CONNECT
        broker.connects++;
        const cf = body[7];
        let at = 10;
        const read = () => { const n = (body[at] << 8) | body[at + 1]; const s = body.subarray(at + 2, at + 2 + n); at += 2 + n; return s; };
        this.clientId = dec.decode(read());
        if (cf & 0x04) this.will = { topic: dec.decode(read()), payload: dec.decode(read()), retain: !!(cf & 0x20) };
        this.deliver(Uint8Array.from([0x20, 2, 0, broker.refuse ? 5 : 0]));
      } else if (type === 8) { // SUBSCRIBE
        let at = 2;
        const granted = [];
        while (at < body.length) {
          const n = (body[at] << 8) | body[at + 1];
          const topic = dec.decode(body.subarray(at + 2, at + 2 + n));
          const qos = body[at + 2 + n];
          at += 3 + n;
          this.subs.push({ topic, qos });
          granted.push(qos);
        }
        this.deliver(Uint8Array.from([0x90, 2 + granted.length, body[0], body[1], ...granted]));
        for (const [topic, payload] of retained) {
          if (this.subs.some((s) => matches(s.topic, topic))) this.deliver(encodePublish({ topic, payload, retain: true }));
        }
      } else if (type === 3) { // PUBLISH
        const m = decodePublish(flags, body);
        broker.published.push({ clientId: this.clientId, topic: m.topic, payload: m.payload, qos: m.qos, retain: m.retain });
        if (m.qos === 1) this.deliver(encodePuback(m.id));
        route(this, m.topic, m.payload, m.retain);
      } else if (type === 12) { // PINGREQ
        if (broker.answerPings) this.deliver(Uint8Array.from([0xd0, 0]));
      } else if (type === 14) { // DISCONNECT
        this.graceful = true;
      }
    }
    gone() {
      if (!this.open) return;
      this.open = false; this.readyState = 3;
      clients.delete(this);
      if (!this.graceful && this.will) route(this, this.will.topic, this.will.payload, this.will.retain);
    }
    close() { this.gone(); setTimeout(() => this.onclose?.({}), 0); }
    serverClose() { this.gone(); setTimeout(() => this.onclose?.({}), 0); }
  }
  broker.WebSocket = FakeWebSocket;
  return broker;
}

/** A stand-in for the chat log endpoint: `lines` are {user, message, time_received}. */
export function fakeHistory(lines = []) {
  const f = async () => ({ ok: true, json: async () => ({ status: 'OK', history: f.lines }) });
  f.lines = lines;
  return f;
}
