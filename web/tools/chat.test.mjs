// node --test web/tools/chat.test.mjs : the Spaz Radio chat (chat.js): text cleaning, links, the log merge,
// the online list, and the room's rules (presence, names, sending) against the fake broker. Never the real room.
import test from 'node:test';
import assert from 'node:assert/strict';
import { cleanText, linkify, normalize, mergeHistory, rosterNames, nameHash, newClientId, createChat, CHAN, MAX_TEXT, MAX_MESSAGES } from '../chat.js';
import { createFakeBroker, fakeHistory } from './fakebroker.mjs';

const tick = (ms = 10) => new Promise((r) => setTimeout(r, ms));
const msg = (user, message, time) => ({ user, message, time });

test('text: tags dropped, entities decoded, a stray < kept (the legacy client shows the same)', () => {
  assert.equal(cleanText('Drum &amp; Bass &#x2014; &quot;live&quot; &#39;99'), 'Drum & Bass — "live" \'99');
  assert.equal(cleanText('<b>bold</b> <a href="x">link</a>'), 'bold link');
  assert.equal(cleanText('&lt;script&gt;alert(1)&lt;/script&gt;'), '<script>alert(1)</script>'); // text, shown with textContent
  assert.equal(cleanText('a < b and c > d'), 'a < b and c > d');
  assert.equal(cleanText('&bogus; &#0;'), '&bogus; &#0;');
  assert.equal(cleanText(null), '');
});

test('links: http(s) and www., without the sentence\'s punctuation', () => {
  assert.deepEqual(linkify('see https://spaz.org/tv/, ok'), [{ text: 'see ' }, { url: 'https://spaz.org/tv/', text: 'https://spaz.org/tv/' }, { text: ', ok' }]);
  assert.deepEqual(linkify('www.example.com.'), [{ url: 'https://www.example.com', text: 'www.example.com' }, { text: '.' }]);
  assert.deepEqual(linkify('(https://en.wikipedia.org/wiki/Spaz_(band))'),
    [{ text: '(' }, { url: 'https://en.wikipedia.org/wiki/Spaz_(band)', text: 'https://en.wikipedia.org/wiki/Spaz_(band)' }, { text: ')' }]);
  assert.deepEqual(linkify('javascript:alert(1) no link'), [{ text: 'javascript:alert(1) no link' }]);
  assert.deepEqual(linkify(''), []);
});

test('normalize: live lines get the receive time, the log keeps its ms, junk is dropped', () => {
  assert.deepEqual(normalize({ user: 'gj', message: 'hi' }, 5000), msg('gj', 'hi', 5000));
  assert.deepEqual(normalize({ user: 'gj', message: 'hi', time_received: 1793509809767 }, 5000), msg('gj', 'hi', 1793509809767));
  assert.equal(normalize({ user: 'gj' }, 1), null);
  assert.equal(normalize({ message: 'hi' }, 1), null);
  assert.equal(normalize(null, 1), null);
});

test('merge: first load, failed fetch, gap filled after a reconnect, repeats kept, late lines last', () => {
  const S = 1000;
  const history = [msg('a', '1', 100 * S), msg('b', '2', 200 * S)];
  assert.deepEqual(mergeHistory([], history), history);
  assert.deepEqual(mergeHistory(history, []), history);
  // shown: launch log + a live copy stamped 3 s off; refetched log: the live one + two missed while offline
  const shown = [msg('a', '1', 100 * S), msg('b', '2', 200 * S), msg('c', 'live', 303 * S)];
  const again = [msg('b', '2', 200 * S), msg('c', 'live', 300 * S), msg('d', 'missed', 400 * S), msg('e', 'missed too', 500 * S)];
  assert.deepEqual(mergeHistory(shown, again), [msg('a', '1', 100 * S), ...again]);
  const lols = [msg('a', 'lol', 100 * S), msg('a', 'lol', 110 * S), msg('a', 'lol', 120 * S)];
  assert.deepEqual(mergeHistory(lols.slice(0, 2), lols), lols);
  assert.deepEqual(mergeHistory([msg('a', '1', 100 * S), msg('z', 'now', 900 * S)], [msg('a', '1', 100 * S)]),
    [msg('a', '1', 100 * S), msg('z', 'now', 900 * S)]);
  const many = Array.from({ length: MAX_MESSAGES + 50 }, (_, i) => msg('a', String(i), i * S));
  assert.equal(mergeHistory([], many).length, MAX_MESSAGES);
});

test('online list: one name per person across devices, sorted without case', () => {
  const r = new Map([['mut1', 'spukkin'], ['mut2', 'Abu'], ['mut3', 'spukkin'], ['mut4', 'gj'], ['mut5', 'GJ']]);
  assert.deepEqual(rosterNames(r), ['Abu', 'gj', 'spukkin']);
});

test('client ids and name colours', () => {
  assert.match(newClientId(), /^mut[0-9a-z]{6}$/);
  assert.notEqual(newClientId(), newClientId());
  assert.equal(nameHash('Ray Dio'), nameHash('ray dio'));
});

function room(opts = {}) {
  const broker = createFakeBroker();
  const history = fakeHistory(opts.history ?? []);
  const changes = [], live = [];
  const store = new Map(opts.stored ? [['lsd.chat.nick', opts.stored]] : []);
  const storage = { getItem: (k) => store.get(k) ?? null, setItem: (k, v) => store.set(k, v), removeItem: (k) => store.delete(k) };
  let t = 1_000_000;
  const chat = createChat({ WebSocketImpl: broker.WebSocket, fetchImpl: history, clientId: 'mutme', storage,
    now: () => t, onChange: (w) => changes.push(w), onLive: (m) => live.push(m) });
  return { broker, history, chat, changes, live, store, advance: (ms) => { t += ms; } };
}

test('lurking: the log and the online list show, nothing is published without a name', async () => {
  const r = room({ history: [{ user: 'abu', message: 'hi', time_received: 900_000 }] });
  r.broker.retained.set('presence/mutx', 'abu');
  r.chat.start();
  await tick(30);
  assert.equal(r.chat.state.status, 'online');
  assert.deepEqual(r.chat.state.messages, [msg('abu', 'hi', 900_000)]);
  assert.deepEqual(r.chat.names, ['abu']);
  assert.deepEqual(r.broker.published, []);
  r.chat.leave();
});

test('joining announces the name (retained); a saved name rejoins on the next visit', async () => {
  const r = room();
  r.chat.start();
  await tick(30);
  assert.equal(r.chat.join('  gj  '), true);
  await tick(20);
  assert.equal(r.broker.retained.get('presence/mutme'), 'gj');
  assert.equal(r.store.get('lsd.chat.nick'), 'gj');
  assert.deepEqual(r.chat.names, ['gj']);
  r.chat.leave();

  const again = room({ stored: 'gj' });
  again.chat.start();
  await tick(30);
  assert.equal(again.broker.retained.get('presence/mutme'), 'gj');
  again.chat.leave();
});

test('renaming clears the old name first, then sets the new one', async () => {
  const r = room({ stored: 'gj' });
  r.chat.start();
  await tick(30);
  r.chat.join('greenjon');
  await tick(20);
  const mine = r.broker.published.filter((p) => p.topic === 'presence/mutme').map((p) => p.payload);
  assert.deepEqual(mine, ['gj', '', 'greenjon']);
  assert.equal(r.chat.join('greenjon'), false); // the same name again: nothing to do
  r.chat.leave();
});

test('sending: JSON {user, message} on spazradio, the line shows when it comes back, limits enforced', async () => {
  const r = room({ stored: 'gj' });
  await assert.rejects(r.chat.send('hi'), /offline/);
  r.chat.start();
  await tick(30);
  await r.chat.send('  hello room  ');
  await tick(20);
  const sent = r.broker.published.filter((p) => p.topic === CHAN);
  assert.deepEqual(sent.map((p) => JSON.parse(p.payload)), [{ user: 'gj', message: 'hello room' }]);
  assert.deepEqual(r.live, [msg('gj', 'hello room', 1_000_000)]); // stamped with the receive time
  await assert.rejects(r.chat.send('again'), /too fast/);
  r.advance(1500);
  await r.chat.send('x'.repeat(MAX_TEXT + 100));
  assert.equal(JSON.parse(r.broker.published.at(-1).payload).message.length, MAX_TEXT);
  await assert.rejects(r.chat.send('   '), /empty/);
  r.chat.leave();
  const anon = room();
  anon.chat.start();
  await tick(30);
  await assert.rejects(anon.chat.send('hi'), /no name/);
  anon.chat.leave();
});

test('lines from others: cleaned, stamped, and a malformed one ignored', async () => {
  const r = room();
  r.chat.start();
  await tick(30);
  r.broker.inject(CHAN, JSON.stringify({ user: 'abu', message: 'rock &amp; roll <b>!</b>' }));
  r.broker.inject(CHAN, '{"user":"abu"}');
  r.broker.inject(CHAN, 'not json');
  await tick(20);
  assert.deepEqual(r.live, [msg('abu', 'rock & roll !', 1_000_000)]);
  r.chat.leave();
});

test('presence: empty or "offline" removes a name; the list empties while disconnected', async () => {
  const r = room();
  r.chat.start();
  await tick(30);
  r.broker.inject('presence/mut1', 'abu', { retain: true });
  r.broker.inject('presence/mut2', 'ray', { retain: true });
  await tick(10);
  assert.deepEqual(r.chat.names, ['abu', 'ray']);
  r.broker.inject('presence/mut1', '', { retain: true });
  r.broker.inject('presence/mut2', 'offline');
  await tick(10);
  assert.deepEqual(r.chat.names, []);
  r.broker.inject('presence/mut3', 'zed', { retain: true });
  await tick(10);
  r.broker.dropAll();
  await tick(5);
  assert.equal(r.chat.state.status, 'offline');
  assert.deepEqual(r.chat.names, []);
  r.chat.leave();
});

test('a reconnect fetches the log again and fills the gap; the name is announced again', async () => {
  const r = room({ stored: 'gj', history: [{ user: 'abu', message: 'before', time_received: 900_000 }] });
  r.chat.start();
  await tick(30);
  r.broker.dropAll();
  r.history.lines = [...r.history.lines, { user: 'ray', message: 'said while you were away', time_received: 950_000 }];
  await tick(5);
  r.chat.reconnectNow(); // the network came back: no backoff wait
  await tick(60);
  assert.equal(r.chat.state.status, 'online');
  assert.deepEqual(r.chat.state.messages.map((m) => m.message), ['before', 'said while you were away']);
  assert.equal(r.broker.retained.get('presence/mutme'), 'gj'); // the will cleared it; onReady put it back
  r.chat.leave();
});

test('leaving clears our name and disconnects cleanly', async () => {
  const r = room({ stored: 'gj' });
  r.chat.start();
  await tick(30);
  assert.equal(r.broker.retained.get('presence/mutme'), 'gj');
  r.chat.leave();
  await tick(10);
  assert.equal(r.broker.retained.has('presence/mutme'), false);
  assert.equal(r.broker.clients.size, 0);
});
