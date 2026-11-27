'use strict';
// Integration test: spawns server.js on a local port. Skipped when `ws` is not installed (npm install in server/).
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const net = require('node:net');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
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

test('control socket: mode 660, unknown commands refused, exit closes viewers and exits 0', { skip: !WebSocket }, async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-relay-'));
  const sock = path.join(dir, 'control.sock');
  const port = PORT + 600;
  const child = spawn(process.execPath, [__dirname + '/server.js'], {
    env: { ...process.env, LSD_PORT: String(port), LSD_HOST: '127.0.0.1', LSD_TOKEN: 't', LSD_CONTROL_SOCKET: sock },
    stdio: 'ignore',
  });
  const exited = new Promise((res) => child.on('exit', (code) => res(code)));
  const send = (line) => new Promise((res, rej) => {
    const c = net.connect(sock, () => c.end(line));
    let out = '';
    c.on('data', (d) => { out += d; });
    c.on('close', () => res(out));
    c.on('error', rej);
  });
  try {
    for (let i = 0; i < 50 && !fs.existsSync(sock); i++) await sleep(100);
    assert.equal(fs.statSync(sock).mode & 0o777, 0o660);
    assert.match(await send('reboot\n'), /unknown command/);
    const viewer = await new Promise((res, rej) => {
      const ws = new WebSocket(`ws://127.0.0.1:${port}/`);
      ws.on('open', () => res(ws));
      ws.on('error', rej);
    });
    const viewerClosed = new Promise((res) => viewer.on('close', (code) => res(code)));
    assert.match(await send('exit'), /ok/); // no trailing newline, as from `nc -N`
    assert.equal(await viewerClosed, 1012);
    assert.equal(await exited, 0);
    assert.equal(fs.existsSync(sock), false);
  } finally {
    child.kill();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('control socket: a stale socket file is replaced, the relay runs without one it cannot create', { skip: !WebSocket }, async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lsd-relay-'));
  const sock = path.join(dir, 'control.sock');
  // leave a dead socket file behind, as a killed relay would
  await new Promise((res) => { const s = net.createServer().listen(sock, () => { s.unref(); res(); }); });
  const port = PORT + 700;
  const run = (control) => spawn(process.execPath, [__dirname + '/server.js'], {
    env: { ...process.env, LSD_PORT: String(port), LSD_HOST: '127.0.0.1', LSD_TOKEN: 't', LSD_CONTROL_SOCKET: control },
    stdio: 'ignore',
  });
  const health = async () => {
    for (let i = 0; i < 50; i++) {
      try { return await (await fetch(`http://127.0.0.1:${port}/health`)).json(); } catch { await sleep(100); }
    }
    return null;
  };
  let child = run(sock);
  try {
    assert.equal((await health())?.status, 'ok');
    for (let i = 0; i < 50; i++) { // the new socket answers
      const ok = await new Promise((res) => { const c = net.connect(sock, () => { c.end('nope\n'); }); c.on('data', () => res(true)); c.on('error', () => res(false)); });
      if (ok) break;
      await sleep(100);
    }
    child.kill(); await new Promise((r) => child.on('exit', r));
    child = run(path.join(dir, 'missing', 'control.sock'));
    assert.equal((await health())?.status, 'ok');
  } finally {
    child.kill();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
