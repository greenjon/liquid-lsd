#!/usr/bin/env python3
"""Checks the app's Link code against another Link client in its own process.

Needs `pip install aalink`, the library from scripts/build_link_native.sh, and a network interface
that allows multicast. Runs LinkExternalPeerReportTest, then compares the two logs by wall clock.
"""
import asyncio
import bisect
import os
import statistics
import subprocess
import sys
import tempfile
import time

from aalink import Link

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BAR = 4.0


async def peer(poke, rows, gradle):
    link = Link(137.0)
    link.quantum = BAR
    link.enabled = True
    while gradle.poll() is None:
        if os.path.exists(poke):
            with open(poke) as f:
                link.tempo = float(f.read())
            os.remove(poke)
        rows.append((time.time(), link.beat, link.tempo, link.num_peers))
        await asyncio.sleep(0.005)


def wrap(value):
    return value - BAR * round(value / BAR)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        app_log = os.path.join(tmp, "app.log")
        env = dict(os.environ, LSD_LINK_APP_LOG=app_log, LSD_LINK_POKE=os.path.join(tmp, "poke"))
        gradle = subprocess.Popen(
            ["./gradlew", "test", "--rerun", "--tests", "*LinkExternalPeerReportTest*"],
            cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        rows = []
        asyncio.run(peer(env["LSD_LINK_POKE"], rows, gradle))
        output = gradle.stdout.read()
        if gradle.returncode != 0 or not os.path.exists(app_log):
            print(output[-3000:])
            return 1
        with open(app_log) as f:
            app = [line.split() for line in f]

    times = [r[0] for r in rows]
    stages = {}
    for stage, t, beats, bpm in app:
        t, beats, bpm = float(t), float(beats), float(bpm)
        i = bisect.bisect_left(times, t)
        if not 0 < i < len(rows):
            continue
        (t0, b0, tempo0, _), (t1, b1, _, _) = rows[i - 1], rows[i]
        peer_beat = b0 + (b1 - b0) * (t - t0) / (t1 - t0) if t1 > t0 else b0
        stages.setdefault(stage, []).append((wrap(beats - peer_beat) * 60000.0 / tempo0, bpm, tempo0, t))

    print(f"peers seen by the other client: {max(r[3] for r in rows)}")
    print(f"{'stage':22} {'median ms':>10} {'p95 |ms|':>9} {'app BPM':>9} {'peer BPM':>9}")
    for stage, values in stages.items():
        errors = [v[0] for v in values]
        p95 = sorted(abs(e) for e in errors)[int(len(errors) * 0.95)]
        print(f"{stage:22} {statistics.median(errors):10.1f} {p95:9.1f} {values[-1][1]:9.2f} {values[-1][2]:9.2f}")
    putback = stages.get("lead-putback")
    if putback:
        moved = [r[0] for r in rows if putback[0][3] <= r[0] <= putback[-1][3] and abs(r[2] - 126.0) > 2.6]
        print(f"the other client held its own tempo for {moved[-1] - moved[0] if moved else 0:.2f} s before it was put back")
    return 0


if __name__ == "__main__":
    sys.exit(main())
