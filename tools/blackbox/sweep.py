#!/usr/bin/env python3
"""Observes every deterministic variation we have with a command-line renderer (at our default parameters) and compares
our shader with each table. Writes build/blackbox/sweep_PROGRAM.md. LIST is the output of `gradlew compareVariation -Pargs=--list`.

Usage: sweep.py PROGRAM LIST [--only name ...]
"""
import os, subprocess, sys

here = os.path.dirname(os.path.abspath(__file__))
program, lst = sys.argv[1], sys.argv[2]
only = set(sys.argv[sys.argv.index("--only") + 1:]) if "--only" in sys.argv else None
jobs = []
for line in open(lst):
    parts = line.split()
    name, kind = parts[0], parts[1]
    params = parts[2].split(",") if len(parts) > 2 else []
    if kind != "fixed" or (only and name not in only):
        continue
    out = f"build/blackbox/{program}/{name}"
    if not os.path.isfile(f"{out}/table.csv"):
        cmd = [sys.executable, os.path.join(here, "run_probes.py"), program, name, "--out", out]
        for p in params:
            cmd += ["--param", p]
        r = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if r.returncode != 0:
            print(f"{name}: render or extraction failed"); continue
    # our parameter values in our order = the order the list gives them
    jobs.append(f"{name}|{out}/table.csv|{','.join(p.split('=')[1] for p in params)}")
    print(f"observed {name}", flush=True)
open(f"build/blackbox/batch_{program}.txt", "w").write("\n".join(jobs) + "\n")
r = subprocess.run(["./gradlew", "compareVariation", f"-Pargs=--batch build/blackbox/batch_{program}.txt"], capture_output=True, text=True)
res = [l for l in r.stdout.splitlines() if l.startswith("RESULT") or l.startswith("  ")]
open(f"build/blackbox/sweep_{program}.md", "w").write(f"# {program} sweep\n\n```\n" + "\n".join(res) + "\n```\n")
print("\n".join(l for l in res if l.startswith("RESULT")))
