// server.js — Liquid LSD WebSocket Relay
//
// Runs on spaz.org VPS. Fans out session state from the broadcaster
// (Liquid LSD desktop app, Phase 5) to all web viewers.
//
// Usage:
//   LSD_PORT=9000 LSD_TOKEN=your-secret node server.js
//
// Deploy:
//   npm install
//   pm2 start server.js --name lsd-relay
//   pm2 save && pm2 startup

'use strict';

const { WebSocketServer, WebSocket } = require('ws');
const { createServer }               = require('http');
const { RelayState }                 = require('./state');

const PORT            = parseInt(process.env.LSD_PORT  || '9004', 10);
const HOST            = process.env.LSD_HOST           || '0.0.0.0';
const BROADCAST_TOKEN = process.env.LSD_TOKEN;

const envInt = (name, def) => {
  const n = parseInt(process.env[name] || '', 10);
  return Number.isFinite(n) && n >= 0 ? n : def;
};
const MAX_PAYLOAD     = envInt('LSD_MAX_PAYLOAD',     1024 * 1024); // bytes per incoming message
const MAX_VIEWERS     = envInt('LSD_MAX_VIEWERS',     500);         // total viewer sockets
const MAX_PER_IP      = envInt('LSD_MAX_PER_IP',      20);          // viewer sockets per client address
const HEARTBEAT_MS    = envInt('LSD_HEARTBEAT_MS',    30000);       // ping interval; 0 disables
const MAX_BUFFERED    = envInt('LSD_MAX_BUFFERED',    1024 * 1024); // viewer send backlog before it is dropped
const TRUST_PROXY     = process.env.LSD_TRUST_PROXY !== '0';        // read client IP from X-Forwarded-For

if (!BROADCAST_TOKEN) {
  console.error('[relay] LSD_TOKEN is not set. Set it to a private secret, e.g. LSD_TOKEN=$(openssl rand -hex 16) node server.js');
  process.exit(1);
}

// -------------------------------------------------------
// HTTP server (health check + WebSocket upgrade target)
// -------------------------------------------------------
const httpServer = createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      viewers: viewers.size,
      broadcasterConnected: broadcaster !== null,
      uptime: Math.round(process.uptime()),
    }));
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ server: httpServer, maxPayload: MAX_PAYLOAD });

// -------------------------------------------------------
// State
// -------------------------------------------------------
let broadcaster  = null;   // single active broadcaster socket
const state      = new RelayState(); // merged broadcast state (for viewers joining mid-session)
const viewers    = new Set();
const perIp      = new Map(); // client address -> viewer count

function clientIp(req) {
  if (TRUST_PROXY) {
    const xff = req.headers['x-forwarded-for'];
    if (typeof xff === 'string' && xff) return xff.split(',').pop().trim(); // the entry the proxy appended
  }
  return req.socket.remoteAddress || 'unknown';
}

function markAlive() { this.isAlive = true; }

// -------------------------------------------------------
// Connection handler
// -------------------------------------------------------
wss.on('connection', (ws, req) => {
  let url;
  try {
    url = new URL(req.url, 'http://localhost');
  } catch {
    ws.close(4000, 'Bad request');
    return;
  }

  const token = url.searchParams.get('key');
  const role  = url.searchParams.get('role') || 'viewer';

  // --- Broadcaster ---
  if (role === 'broadcast') {
    if (token !== BROADCAST_TOKEN) {
      ws.close(4003, 'Unauthorized');
      console.log('[relay] Rejected broadcaster: bad token');
      return;
    }

    if (broadcaster) {
      broadcaster.close(4000, 'Replaced by new broadcaster');
    }
    broadcaster = ws;
    ws.isAlive = true;
    ws.on('pong', markAlive);
    console.log('[relay] Broadcaster connected');

    // Tell existing viewers that a live broadcast has started
    fanOut(JSON.stringify({ type: 'broadcaster_online' }), ws);

    ws.on('message', (data) => {
      // A replaced broadcaster's socket may still deliver a few frames before it closes: drop them
      if (broadcaster !== ws) return;
      const str = data.toString();
      try {
        // Keep the merged state current so viewers joining mid-session get it, not a stale snapshot
        if (!state.ingest(JSON.parse(str))) {
          console.warn('[relay] Ignored broadcaster message (wrong protocol version or no full state yet)');
        }
      } catch {
        // Not JSON: nothing to merge; still relay it as before
      }
      fanOut(str, ws);
    });

    ws.on('close', () => {
      // A replaced broadcaster closes after its successor connected: that is not the end of the broadcast
      if (broadcaster !== ws) return;
      broadcaster  = null;
      state.reset();
      console.log('[relay] Broadcaster disconnected');
      fanOut(JSON.stringify({ type: 'broadcaster_offline' }), ws);
    });

    ws.on('error', (err) => {
      console.error('[relay] Broadcaster error:', err.message);
    });

    return;
  }

  // --- Viewer ---
  const ip = clientIp(req);
  if (viewers.size >= MAX_VIEWERS || (perIp.get(ip) || 0) >= MAX_PER_IP) {
    ws.close(1013, 'Too many connections');
    return;
  }
  perIp.set(ip, (perIp.get(ip) || 0) + 1);
  ws.isAlive = true;
  ws.on('pong', markAlive);
  // Viewers never send anything; a viewer that does is misbehaving
  ws.on('message', () => ws.close(1008, 'Viewers cannot send'));
  viewers.add(ws);
  console.log(`[relay] Viewer connected (total: ${viewers.size})`);

  // Send current state immediately if broadcaster is active
  ws.send(state.snapshotMessage() || JSON.stringify({ type: 'broadcaster_offline' }));

  ws.on('close', () => {
    viewers.delete(ws);
    const left = (perIp.get(ip) || 1) - 1;
    if (left > 0) perIp.set(ip, left); else perIp.delete(ip);
    console.log(`[relay] Viewer disconnected (total: ${viewers.size})`);
  });

  ws.on('error', (err) => {
    console.error('[relay] Viewer error:', err.message);
  });
});

// -------------------------------------------------------
// Fan-out: send message to all viewers except the sender
// -------------------------------------------------------
function fanOut(message, except) {
  for (const viewer of viewers) {
    if (viewer !== except && viewer.readyState === WebSocket.OPEN) {
      if (viewer.bufferedAmount > MAX_BUFFERED) {
        // Not reading: drop it rather than queue every frame in relay memory
        console.warn('[relay] Dropping slow viewer');
        viewer.terminate();
        continue;
      }
      try {
        viewer.send(message);
      } catch (err) {
        console.error('[relay] Send error:', err.message);
      }
    }
  }
}

// -------------------------------------------------------
// Heartbeat: terminate sockets that did not answer the last ping
// -------------------------------------------------------
if (HEARTBEAT_MS > 0) {
  const timer = setInterval(() => {
    for (const ws of [...viewers, ...(broadcaster ? [broadcaster] : [])]) {
      if (ws.isAlive === false) { ws.terminate(); continue; }
      ws.isAlive = false;
      try { ws.ping(); } catch { /* socket closing */ }
    }
  }, HEARTBEAT_MS);
  timer.unref();
}

// -------------------------------------------------------
// Start
// -------------------------------------------------------
httpServer.listen(PORT, HOST, () => {
  console.log(`[relay] Listening on ${HOST}:${PORT}`);
  console.log(`[relay] Health: http://${HOST}:${PORT}/health`);
  console.log(`[relay] Token: set`);
});
