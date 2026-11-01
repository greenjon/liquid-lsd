// node --test web/tools/mqtt.test.mjs : the chat's MQTT 3.1.1 client (mqtt.js): packet bytes, the stream
// parser, and the client against the fake broker (tools/fakebroker.mjs).
import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeLength, encodeConnect, encodeSubscribe, encodePublish, encodePuback, encodePing, encodeDisconnect, Parser, decodePublish, createClient } from '../mqtt.js';
import { createFakeBroker } from './fakebroker.mjs';

const hex = (u8) => Buffer.from(u8).toString('hex');
const tick = (ms = 5) => new Promise((r) => setTimeout(r, ms));

test('remaining length: 1 to 4 bytes, 7 bits each, low first (spec 2.2.3)', () => {
  assert.deepEqual(encodeLength(0), [0x00]);
  assert.deepEqual(encodeLength(127), [0x7f]);
  assert.deepEqual(encodeLength(128), [0x80, 0x01]);
  assert.deepEqual(encodeLength(16383), [0xff, 0x7f]);
  assert.deepEqual(encodeLength(16384), [0x80, 0x80, 0x01]);
  assert.deepEqual(encodeLength(268435455), [0xff, 0xff, 0xff, 0x7f]);
});

test('CONNECT: MQTT level 4, clean session, keepalive, client id, retained QoS 1 will', () => {
  assert.equal(hex(encodeConnect({ clientId: 'ab', keepAlive: 30 })), '100e00044d5154540402001e00026162');
  // flags 0x2e = will retain 0x20 | will QoS 1 0x08 | will 0x04 | clean 0x02; empty will payload
  assert.equal(hex(encodeConnect({ clientId: 'ab', keepAlive: 60, will: { topic: 'p/ab', payload: '', qos: 1, retain: true } })),
    '101600044d515454042e003c00026162' + '0004702f6162' + '0000');
});

test('SUBSCRIBE, PUBLISH, PUBACK, PINGREQ, DISCONNECT bytes', () => {
  assert.equal(hex(encodeSubscribe(1, [{ topic: 'a', qos: 1 }, { topic: 'b/#', qos: 0 }])), '820c0001000161010003622f2300');
  assert.equal(hex(encodePublish({ topic: 't', payload: 'hi' })), '30050001746869');
  assert.equal(hex(encodePublish({ topic: 't', payload: 'x', qos: 1, retain: true, id: 258 })), '3306000174010278');
  assert.equal(hex(encodePuback(258)), '40020102');
  assert.equal(hex(encodePing()), 'c000');
  assert.equal(hex(encodeDisconnect()), 'e000');
});

test('parser: split frames, several in one chunk, and a long payload', () => {
  const big = 'x'.repeat(300); // a 2-byte remaining length
  const a = encodePublish({ topic: 'spazradio', payload: '{"user":"gj","message":"hi"}' });
  const b = encodePublish({ topic: 'presence/mut1', payload: big, qos: 1, retain: true, id: 7 });
  const all = Uint8Array.from([...a, ...b, 0xd0, 0x00]);
  const p = new Parser();
  const got = [];
  for (let i = 0; i < all.length; i += 3) got.push(...p.push(all.subarray(i, i + 3)));
  assert.equal(got.length, 3);
  assert.deepEqual(decodePublish(got[0].flags, got[0].body), { topic: 'spazradio', payload: '{"user":"gj","message":"hi"}', qos: 0, retain: false, id: 0 });
  assert.deepEqual(decodePublish(got[1].flags, got[1].body), { topic: 'presence/mut1', payload: big, qos: 1, retain: true, id: 7 });
  assert.equal(got[2].type, 13);
  assert.deepEqual(new Parser().push(Uint8Array.from([0x30, 0x80])), []); // length field not complete yet
});

test('UTF-8 both ways', () => {
  const pkt = encodePublish({ topic: 'spazradio', payload: 'ça va ✨' });
  const [p] = new Parser().push(pkt);
  assert.equal(decodePublish(p.flags, p.body).payload, 'ça va ✨');
});

test('client: connects, subscribes, gets retained and live messages, QoS 1 publish resolves on PUBACK', async () => {
  const broker = createFakeBroker();
  broker.retained.set('presence/other', 'abu');
  const states = [], msgs = [];
  let ready = 0;
  const c = createClient({ url: 'wss://x', clientId: 'me', WebSocketImpl: broker.WebSocket,
    subscriptions: [{ topic: 'spazradio', qos: 1 }, { topic: 'presence/#', qos: 1 }],
    onState: (s) => states.push(s), onReady: () => ready++, onMessage: (m) => msgs.push(m) });
  c.connect();
  await tick(20);
  assert.deepEqual(states, ['connecting', 'online']);
  assert.equal(ready, 1);
  assert.deepEqual(msgs.map((m) => [m.topic, m.payload, m.retain]), [['presence/other', 'abu', true]]);
  broker.inject('spazradio', '{"user":"abu","message":"yo"}');
  await c.publish('spazradio', '{"user":"me","message":"hi"}', { qos: 1 });
  await tick(20);
  assert.deepEqual(msgs.slice(1).map((m) => m.payload), ['{"user":"abu","message":"yo"}', '{"user":"me","message":"hi"}']);
  c.close();
  await tick(10);
  assert.equal(c.state, 'offline');
});

test('client: publish while offline rejects; graceful close sends DISCONNECT so the will stays quiet', async () => {
  const broker = createFakeBroker();
  const c = createClient({ url: 'wss://x', clientId: 'me', WebSocketImpl: broker.WebSocket,
    will: { topic: 'presence/me', payload: '', qos: 1, retain: true }, subscriptions: [{ topic: 'spazradio', qos: 1 }] });
  await assert.rejects(c.publish('spazradio', 'x'), /offline/);
  c.connect();
  await tick(20);
  await c.publish('presence/me', 'gj', { qos: 1, retain: true });
  c.close({ graceful: true });
  await tick(10);
  assert.equal(broker.retained.get('presence/me'), 'gj'); // no will: the caller clears its own name first
});

test('client: a dropped socket reconnects with backoff, fires the will, and onReady runs again', async () => {
  const broker = createFakeBroker();
  let ready = 0;
  const c = createClient({ url: 'wss://x', clientId: 'me', WebSocketImpl: broker.WebSocket, random: () => 0.5,
    maxBackoffMs: 40, will: { topic: 'presence/me', payload: '', qos: 1, retain: true },
    subscriptions: [{ topic: 'spazradio', qos: 1 }], onReady: () => ready++ });
  c.connect();
  await tick(20);
  await c.publish('presence/me', 'gj', { qos: 1, retain: true });
  broker.dropAll();
  await tick(5);
  assert.equal(c.state, 'offline');
  assert.equal(broker.retained.has('presence/me'), false); // the will cleared it
  await tick(80);
  assert.equal(c.state, 'online');
  assert.equal(ready, 2);
  assert.equal(broker.connects, 2);
  c.close();
});

test('client: no PINGRESP in time drops the socket and starts over', async () => {
  const broker = createFakeBroker();
  broker.answerPings = false;
  const states = [];
  // keepAlive is in seconds on the wire; 0.02 s keeps the test fast (the encoder truncates it to 0).
  const c = createClient({ url: 'wss://x', clientId: 'me', WebSocketImpl: broker.WebSocket, keepAlive: 0.02,
    pingTimeoutMs: 15, maxBackoffMs: 10, random: () => 0.5, subscriptions: [{ topic: 'spazradio', qos: 1 }], onState: (s) => states.push(s) });
  c.connect();
  await tick(70);
  assert.ok(states.includes('offline'), states.join(','));
  assert.ok(broker.connects >= 2, `connects ${broker.connects}`);
  c.close();
});

test('client: a refused CONNACK is treated as a drop and retried', async () => {
  const broker = createFakeBroker();
  broker.refuse = true;
  const c = createClient({ url: 'wss://x', clientId: 'me', WebSocketImpl: broker.WebSocket, maxBackoffMs: 10, random: () => 0.5 });
  c.connect();
  await tick(30);
  assert.notEqual(c.state, 'online');
  broker.refuse = false;
  await tick(40);
  assert.equal(c.state, 'online');
  c.close();
});
