// sidefeed.js
// A second copy of the live stream, decoded here, for the picture only. Safari plays the Icecast stream out
// loud but gives the analysis graph nothing but zeros through createMediaElementSource (it does pass a
// tape's file through), so the picture heard silence and showed NO SIGNAL. When dsp.js sees the radio's
// playhead moving while the analysis hears exact silence, it starts this: it fetches the same mount, cuts
// the Ogg stream into ~1 s files (the stream's header pages, a little overlap, the new pages), decodes
// each with decodeAudioData, and plays them back to back into the analysis input. Nothing from here is
// heard. The same mount as the <audio> element, so both start from the same point in Icecast's burst and
// the picture keeps time with the sound.
//
// The Ogg part (OggPages, OggChunker) is plain JS so node can test it (tools/sidefeed.test.mjs).

const CHUNK_S = 1.0;      // seconds of new audio per decoded file
const OVERLAP_PAGES = 2;  // earlier pages put in front of each file, so its first new samples decode whole
const MAX_AHEAD_S = 30;   // more than this scheduled ahead (should not happen): drop, to catch up
const RETRY_MS = 5000;
const RETRY_MAX_MS = 60000;

/** Reconnect delay after `attempt` failures in a row (0 = the first): doubles from `base` up to `max`, with +-25% jitter. */
export function backoffMs(attempt, base = RETRY_MS, max = RETRY_MAX_MS, rand = Math.random) {
  const nominal = Math.min(max, base * 2 ** Math.min(attempt, 20));
  return Math.min(max, Math.round(nominal * (0.75 + 0.5 * rand())));
}

// --- Ogg pages ---------------------------------------------------------------------------------------

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let r = i << 24;
    for (let k = 0; k < 8; k++) r = r & 0x80000000 ? (r << 1) ^ 0x04c11db7 : r << 1;
    t[i] = r >>> 0;
  }
  return t;
})();

/** The Ogg page checksum (CRC-32, polynomial 0x04c11db7, not reflected), over the page with its CRC field zeroed. */
export function oggCrc(bytes) {
  let crc = 0;
  for (let i = 0; i < bytes.length; i++) crc = ((crc << 8) ^ CRC_TABLE[((crc >>> 24) ^ bytes[i]) & 0xff]) >>> 0;
  return crc;
}

function readGranule(b, o) {
  const lo = b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24);
  const hi = b[o + 4] | (b[o + 5] << 8) | (b[o + 6] << 16) | (b[o + 7] << 24);
  if (lo === -1 && hi === -1) return -1; // no packet ends on this page
  return (hi >>> 0) * 2 ** 32 + (lo >>> 0);
}

function writeGranule(b, o, g) {
  const hi = Math.floor(g / 2 ** 32), lo = g - hi * 2 ** 32;
  for (let i = 0; i < 4; i++) { b[o + i] = (lo >>> (8 * i)) & 0xff; b[o + 4 + i] = (hi >>> (8 * i)) & 0xff; }
}

/** Splits a byte stream into Ogg pages. push() takes the next bytes and returns the pages they complete. */
export class OggPages {
  constructor() { this.buf = new Uint8Array(0); }

  push(bytes) {
    const joined = new Uint8Array(this.buf.length + bytes.length);
    joined.set(this.buf);
    joined.set(bytes, this.buf.length);
    const b = joined;
    const pages = [];
    let o = 0;
    for (;;) {
      // Find the capture pattern "OggS" (the stream may start mid-page, or carry a broken page).
      while (o + 4 <= b.length && !(b[o] === 0x4f && b[o + 1] === 0x67 && b[o + 2] === 0x67 && b[o + 3] === 0x53)) o++;
      if (o + 27 > b.length) break;
      const nseg = b[o + 26];
      if (o + 27 + nseg > b.length) break;
      let body = 0;
      for (let i = 0; i < nseg; i++) body += b[o + 27 + i];
      const size = 27 + nseg + body;
      if (o + size > b.length) break;
      const bytesOf = b.slice(o, o + size);
      const flags = b[o + 5];
      pages.push({
        bytes: bytesOf,
        continued: !!(flags & 1),
        bos: !!(flags & 2),
        eos: !!(flags & 4),
        granule: readGranule(b, o + 6),
        complete: nseg > 0 && b[o + 27 + nseg - 1] < 255, // the last packet on the page ends on it
        payload: 27 + nseg,                                // offset of the first packet's bytes
      });
      o += size;
    }
    this.buf = b.slice(Math.min(o, b.length));
    if (this.buf.length > 1 << 20) this.buf = new Uint8Array(0); // no page in a megabyte: not Ogg
    return pages;
  }
}

/** The sample rate granule positions count in: the Vorbis identification header's, or 48 kHz for Opus. */
function codecRate(page) {
  const b = page.bytes, o = page.payload;
  if (b[o] === 1 && String.fromCharCode(...b.subarray(o + 1, o + 7)) === 'vorbis') {
    return (b[o + 12] | (b[o + 13] << 8) | (b[o + 14] << 16) | (b[o + 15] << 24)) >>> 0;
  }
  if (String.fromCharCode(...b.subarray(o, o + 8)) === 'OpusHead') return 48000;
  return 0;
}

/**
 * Collects pages into small standalone Ogg files. push(page) returns { file, keep, rate } when a file is
 * ready: `file` decodes on its own (header pages, overlap pages, new pages, renumbered from 0 with the
 * granule positions moved to start at 0), and its last `keep` samples (at `rate`) are the new audio; the
 * part before them only primes the decoder. `keep` is 0 for the first file after joining a stream: all of
 * it is new. A new logical stream (Icecast chains one at a track change or source reconnect) starts over
 * from its header pages.
 */
export class OggChunker {
  constructor(chunkSeconds = CHUNK_S) {
    this.chunkSeconds = chunkSeconds;
    this.reset();
  }

  reset() {
    this.headers = [];
    this.rate = 0;
    this.pending = [];   // new pages since the last file
    this.overlap = [];   // the last file's last pages
    this.base = -1;      // granule position where the last file's new audio ended
    this.prevGranule = -1;
  }

  push(page) {
    if (page.bos) {
      this.reset();
      this.rate = codecRate(page);
      if (this.rate) this.headers.push(page);
      this.prevGranule = 0; // a stream from its start; a joined one's first page is far past this (see below)
      return null;
    }
    if (!this.headers.length) return null; // joined mid-stream: wait for the next stream's headers
    if (page.granule === 0 && !this.pending.length && !this.overlap.length) { this.headers.push(page); return null; }

    page.prev = this.prevGranule; // where the audio before this page ended (-1: not known)
    if (page.granule >= 0) this.prevGranule = page.granule;
    this.pending.push(page);
    if (!page.complete || page.granule < 0) return null;
    const first = this.base >= 0 ? this.base : this.pending.find((p) => p.granule >= 0).granule;
    if (page.granule - first < this.chunkSeconds * this.rate && !page.eos) return null;

    const data = [...this.overlap, ...this.pending];
    // The audio before the file's first page ended here; the file's granule positions count from it. Not
    // known (or more than a page's worth back: Icecast's listeners join mid-stream), the first page only
    // primes the decoder.
    const { prev, granule } = data[0];
    const g0 = prev >= 0 && granule - prev <= 2 * this.rate ? prev : granule;
    const file = buildFile(this.headers, data, g0);
    const keep = this.base >= 0 ? page.granule - this.base : 0;
    this.base = page.granule;
    // The next file's overlap: the last few pages, starting where a packet starts.
    let start = Math.max(0, this.pending.length - OVERLAP_PAGES);
    while (start > 0 && this.pending[start].continued) start--;
    this.overlap = this.pending.slice(start);
    this.pending = [];
    const { rate } = this;
    if (page.eos) this.reset();
    return { file, keep, rate };
  }
}

function buildFile(headers, data, g0) {
  const pages = [...headers, ...data];
  const out = new Uint8Array(pages.reduce((n, p) => n + p.bytes.length, 0));
  let o = 0;
  pages.forEach((p, seq) => {
    const b = p.bytes.slice();
    if (seq === headers.length) b[5] &= ~1; // the first audio page: no packet carried over from before
    if (seq >= headers.length && p.granule >= 0) writeGranule(b, 6, Math.max(0, p.granule - g0));
    if (seq === pages.length - 1) b[5] |= 4; else b[5] &= ~4;
    b[18] = seq & 0xff; b[19] = (seq >>> 8) & 0xff; b[20] = (seq >>> 16) & 0xff; b[21] = (seq >>> 24) & 0xff;
    b[22] = b[23] = b[24] = b[25] = 0;
    const crc = oggCrc(b);
    b[22] = crc & 0xff; b[23] = (crc >>> 8) & 0xff; b[24] = (crc >>> 16) & 0xff; b[25] = (crc >>> 24) & 0xff;
    out.set(b, o);
    o += b.length;
  });
  return out;
}

// --- The feed --------------------------------------------------------------------------------------

/**
 * Fetches `url` and plays its audio into `dest` (an AudioNode of `ctx`) until stop(). Reconnects after
 * a dropped connection. `status` is for window.LSD: 'connecting' | 'playing' | 'retrying' |
 * 'unsupported' (this browser cannot decode the stream's files; it gives up) | 'stopped'.
 */
export function startSideFeed(ctx, dest, url) {
  const feed = { status: 'connecting', files: 0, failures: 0, stop };
  let abort = null;
  let stopped = false;
  let next = 0;          // context time the next decoded piece starts at
  let decoding = Promise.resolve();
  const scheduled = new Set(); // sources started or waiting to start: stop() silences them
  let attempt = 0;             // failed connections in a row, for the backoff

  function stop() {
    stopped = true;
    feed.status = 'stopped';
    abort?.abort();
    for (const src of scheduled) {
      src.onended = null;
      try { src.stop(); } catch { /* not started */ }
      src.disconnect();
    }
    scheduled.clear();
  }

  function play(buffer, keepSeconds) {
    // Only the new audio: the end of the decoded file, `keepSeconds` long (all of it for the first file).
    const startAt = keepSeconds > 0 ? Math.max(0, buffer.duration - keepSeconds) : 0;
    const dur = buffer.duration - startAt;
    if (dur <= 0) return;
    const now = ctx.currentTime;
    if (next < now + 0.02) next = now + 0.1;    // the first piece, or the feed ran dry: start again a little ahead
    if (next - now > MAX_AHEAD_S) return;
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    src.connect(dest);
    scheduled.add(src);
    src.onended = () => { scheduled.delete(src); src.disconnect(); };
    src.start(next, startAt, dur);
    next += dur;
  }

  function decode({ file, keep, rate }) {
    decoding = decoding.then(async () => {
      if (stopped) return;
      try {
        const buffer = await ctx.decodeAudioData(file.buffer);
        if (stopped) return;
        feed.files++;
        feed.status = 'playing';
        play(buffer, keep / rate);
      } catch (err) {
        feed.failures++;
        // Not one file decoded: this browser cannot decode the stream this way.
        if (!feed.files && feed.failures >= 3) {
          console.warn('[sidefeed] cannot decode the stream:', err?.message);
          stop();
          feed.status = 'unsupported';
        }
      }
    });
  }

  async function run() {
    while (!stopped) {
      abort = new AbortController();
      const pages = new OggPages();
      const chunker = new OggChunker();
      try {
        const res = await fetch(`${url}${url.includes('?') ? '&' : '?'}feed=${Date.now()}`, { signal: abort.signal, cache: 'no-store' });
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);
        const reader = res.body.getReader();
        for (;;) {
          const { done, value } = await reader.read();
          if (done || stopped) break;
          attempt = 0;
          for (const page of pages.push(value)) {
            const piece = chunker.push(page);
            if (piece) decode(piece);
          }
        }
      } catch (err) {
        if (stopped) return;
        console.warn('[sidefeed] stream dropped:', err?.message);
      }
      if (stopped) return;
      feed.status = 'retrying';
      await new Promise((r) => setTimeout(r, backoffMs(attempt++)));
    }
  }

  run();
  return feed;
}
