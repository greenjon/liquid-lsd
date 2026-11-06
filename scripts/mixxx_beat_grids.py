#!/usr/bin/env python3
"""List the fixed beat grids in a Mixxx library, as truth for AudioPhaseReportTest.

Mixxx is an independent beat tracker, and the grids are checked by eye only where someone has looked at them.
Output: bpm, first beat in seconds, path; tab separated. Usage: mixxx_beat_grids.py [--count N] [--seed S] > list.txt
"""
import argparse
import os
import random
import sqlite3
import struct


def varint(b, i):
    v = s = 0
    while True:
        c = b[i]
        i += 1
        v |= (c & 127) << s
        s += 7
        if not c & 128:
            return v, i


def grid(b):
    """Mixxx's BeatGrid protobuf: field 1 {field 1: double bpm}, field 2 {field 1: varint first frame}."""
    bpm, frame, i = None, 0, 0
    while i < len(b):
        tag, i = varint(b, i)
        size, i = varint(b, i)
        part = b[i:i + size]
        i += size
        if tag >> 3 == 1 and part[:1] == b"\x09":
            bpm = struct.unpack("<d", part[1:9])[0]
        elif tag >> 3 == 2 and part[:1] == b"\x08":
            frame = varint(part, 1)[0]
    return bpm, frame


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=os.path.expanduser("~/.mixxx/mixxxdb.sqlite"))
    ap.add_argument("--count", type=int, default=150)
    ap.add_argument("--seed", type=int, default=20261105)
    a = ap.parse_args()
    db = sqlite3.connect(f"file:{a.db}?mode=ro", uri=True)
    rows = db.execute(
        "select tl.location, l.samplerate, l.duration, l.beats from library l join track_locations tl on l.location = tl.id "
        "where l.beats_version = 'BeatGrid-2.0' and l.mixxx_deleted = 0"
    ).fetchall()
    out = []
    for path, rate, secs, beats in rows:
        if not os.path.exists(path) or not rate or not 90 <= secs <= 900:
            continue
        bpm, frame = grid(beats)
        if bpm and 50 <= bpm <= 220:
            out.append((bpm, frame / rate, path))
    random.Random(a.seed).shuffle(out)
    for bpm, first, path in out[: a.count]:
        print(f"{bpm:.4f}\t{first:.5f}\t{path}")


if __name__ == "__main__":
    main()
