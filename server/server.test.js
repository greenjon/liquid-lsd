'use strict';
// Integration test: spawns server.js on a local port. Skipped when `ws` is not installed (npm install in server/).
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
let WebSocket = null;
try { WebSocket = require('ws'); } catch { /* skipped below */ }

const PORT = 19000 + Math.floor(Math.random() * 500);
const open = (q = '') => new Promise((res, rej) => {
  const ws = new WebSocket(`ws://127.0.0.1:${PORT}/${q}`);
  const msgs = [];
  ws.on('message', (d) => msgs.push(d.toString()));
  ws.on('open', () => res({ ws, msgs }));
  ws.on('error', rej);
});
const closed = (ws) => new Promise((res) => ws.on('close', (code) => res(code)));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

test('relay limits', { skip: !WebSocket }, async () => {
  const child = spawn(process.execPath, [__dirname + '/server.js'], {
    env: { ...process.env, LSD_PORT: String(PORT), LSD_HOST: '127.0.0.1', LSD_TOKEN: 't',
           LSD_MAX_PAYLOAD: '2048', LSD_MAX_PER_IP: '2' },
    stdio: 'ignore',
  });
  try {
    for (let i = 0; i < 50; i++) { try { (await open()).ws.close(); break; } catch { await sleep(100); } }
    await sleep(200);
    // per-IP cap: third viewer is refused
    const a = await open(), b = await open(), c = await open();
    assert.equal(await closed(c.ws), 1013);
    // a viewer that sends anything is closed
    a.ws.send('hi');
    assert.equal(await closed(a.ws), 1008);
    // broadcaster still fans out; an oversized frame closes it with 1009
    const bc = await open('?role=broadcast&key=t');
    const full = JSON.stringify({ type: 'state_full', v: 2, preset: { mixer: {} } });
    bc.ws.send(full);
    await sleep(200);
    assert.ok(b.msgs.includes(full));
    const gone = closed(bc.ws);
    bc.ws.send('x'.repeat(5000));
    assert.equal(await gone, 1009);
  } finally { child.kill(); }
});
