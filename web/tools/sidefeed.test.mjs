// node --test web/tools/sidefeed.test.mjs : the side feed's Ogg page splitter and file cutter (sidefeed.js).
// checks/stream_fixture.ogg is 6 s of Vorbis with ~0.3 s pages, like the Icecast stream's.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { OggPages, OggChunker, oggCrc } from '../sidefeed.js';

const fixture = new Uint8Array(fs.readFileSync(new URL('../checks/stream_fixture.ogg', import.meta.url)));
const allPages = () => new OggPages().push(fixture);

const crcOk = (b) => {
  const z = b.slice();
  z[22] = z[23] = z[24] = z[25] = 0;
  const want = (b[22] | (b[23] << 8) | (b[24] << 16) | (b[25] << 24)) >>> 0;
  return oggCrc(z) === want;
};

/** The pages of a cut file, with their flags, sequence numbers and granule positions. */
function inspect(file) {
  const pages = new OggPages().push(file);
  return pages.map((p) => ({ ...p, seq: p.bytes[18] | (p.bytes[19] << 8), crc: crcOk(p.bytes) }));
}

test('pages: the same pages whatever the read sizes, and their checksums hold', () => {
  const whole = allPages();
  assert.equal(whole.length, 22);
  assert.ok(whole.every((p) => crcOk(p.bytes)));
  assert.ok(whole[0].bos && whole.at(-1).eos);
  const split = new OggPages();
  const pieces = [];
  for (let o = 0; o < fixture.length; o += 777) pieces.push(...split.push(fixture.subarray(o, o + 777)));
  assert.deepEqual(pieces.map((p) => p.granule), whole.map((p) => p.granule));
});

test('pages: junk before a page is skipped', () => {
  const junk = new Uint8Array(100).fill(7);
  const joined = new Uint8Array(junk.length + fixture.length);
  joined.set(junk);
  joined.set(fixture, junk.length);
  assert.equal(new OggPages().push(joined).length, 22);
});

test('files: standalone, renumbered, counted from 0, and their new audio adds up', () => {
  const pages = allPages();
  const chunker = new OggChunker(1);
  const files = pages.map((p) => chunker.push(p)).filter(Boolean);
  assert.ok(files.length >= 5, `${files.length} files`);
  for (const { file, rate } of files) {
    assert.equal(rate, 44100);
    const ps = inspect(file);
    assert.ok(ps.every((p) => p.crc), 'checksums');
    assert.deepEqual(ps.map((p) => p.seq), ps.map((_, i) => i), 'sequence numbers');
    assert.ok(ps[0].bos && ps.at(-1).eos && ps.slice(1, -1).every((p) => !p.bos && !p.eos));
    assert.equal(ps[1].granule, 0, 'the header pages come first');
    const audio = ps.slice(2);
    assert.ok(!audio[0].continued);
    assert.ok(audio[0].granule > 0 && audio[0].granule < 44100, 'the granule positions start near 0');
    for (let i = 1; i < audio.length; i++) assert.ok(audio[i].granule >= audio[i - 1].granule);
  }
  // The first file is all new; the rest keep exactly the audio after the one before.
  assert.equal(files[0].keep, 0);
  const firstEnd = pages.find((p, i) => i > 1 && p.granule - pages[2].granule >= 44100).granule;
  const kept = files.slice(1).reduce((n, f) => n + f.keep, 0);
  assert.equal(firstEnd + kept, pages.at(-1).granule);
  // Every later file starts with overlap: its audio is longer than what it keeps.
  for (const f of files.slice(1)) assert.ok(inspect(f.file).at(-1).granule > f.keep);
});

test('files: joining mid-stream (as from Icecast) starts with what arrives', () => {
  const pages = allPages();
  const chunker = new OggChunker(1);
  const files = [...pages.slice(0, 2), ...pages.slice(8)].map((p) => chunker.push(p)).filter(Boolean);
  assert.ok(files.length >= 3);
  assert.equal(files[0].keep, 0);
  const audio = inspect(files[0].file).slice(2);
  assert.equal(audio[0].granule, 0, 'the first page only primes the decoder');
});

test('files: nothing before a stream\'s headers; a new stream starts over', () => {
  const pages = allPages();
  const chunker = new OggChunker(1);
  assert.equal(pages.slice(5).map((p) => chunker.push(p)).filter(Boolean).length, 0, 'no headers, no files');
  const files = pages.map((p) => chunker.push(p)).filter(Boolean);
  assert.equal(files[0].keep, 0);
});
