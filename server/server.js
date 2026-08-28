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

const PORT            = parseInt(process.env.LSD_PORT  || '9004', 10);
const HOST            = process.env.LSD_HOST           || '0.0.0.0';
const BROADCAST_TOKEN = process.env.LSD_TOKEN          || 'lsd25';

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

const wss = new WebSocketServer({ server: httpServer });

// -------------------------------------------------------
// State
// -------------------------------------------------------
let broadcaster  = null;   // single active broadcaster socket
let currentState = null;   // last full state_full message string (for new viewers)
const viewers    = new Set();

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
    console.log('[relay] Broadcaster connected');

    // Tell existing viewers that a live broadcast has started
    fanOut(JSON.stringify({ type: 'broadcaster_online' }), ws);

    ws.on('message', (data) => {
      const str = data.toString();
      try {
        const msg = JSON.parse(str);
        // Cache full state so viewers joining mid-session get immediate state
        if (msg.type === 'state_full') {
          currentState = str;
        }
        fanOut(str, ws);
      } catch {
        fanOut(str, ws);
      }
    });

    ws.on('close', () => {
      broadcaster  = null;
      currentState = null;
      console.log('[relay] Broadcaster disconnected');
      fanOut(JSON.stringify({ type: 'broadcaster_offline' }), ws);
    });

    ws.on('error', (err) => {
      console.error('[relay] Broadcaster error:', err.message);
    });

    return;
  }

  // --- Viewer ---
  viewers.add(ws);
  console.log(`[relay] Viewer connected (total: ${viewers.size})`);

  // Send current state immediately if broadcaster is active
  ws.send(currentState || JSON.stringify({ type: 'broadcaster_offline' }));

  ws.on('close', () => {
    viewers.delete(ws);
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
      try {
        viewer.send(message);
      } catch (err) {
        console.error('[relay] Send error:', err.message);
      }
    }
  }
}

// -------------------------------------------------------
// Start
// -------------------------------------------------------
httpServer.listen(PORT, HOST, () => {
  console.log(`[relay] Listening on ${HOST}:${PORT}`);
  console.log(`[relay] Health: http://${HOST}:${PORT}/health`);
  console.log(`[relay] Token: ${BROADCAST_TOKEN === 'changeme' ? 'WARNING: using default token!' : 'set'}`);
});
