#!/bin/bash
# Rebuilds the throwaway test build: tests/web/.cache/app/rt (a worktree of HEAD with test-only hooks) and w/ (work area, library copy with fixtures).
set -e
ROOT=$(git rev-parse --show-toplevel); C=$ROOT/tests/web/.cache/app; R=$ROOT/.planning/auto-verify-2026-11-06
mkdir -p "$C/w/cwd" "$C/w/out" "$C/w/scripts" "$C/w/xdg"
git -C "$ROOT" worktree prune
[ -d "$C/rt" ] || git -C "$ROOT" worktree add --detach "$C/rt" HEAD -q
(cd "$C/rt" && python3 "$R/round2/apply_probe.py" && python3 "$R/round3/apply_probe3.py")
rm -rf "${C:?}/lib_src"; cp -r "$ROOT/library" "$C/lib_src"
python3 "$R/round3/fixtures.py" "$C/lib_src" "$C/w/libbase"
cd "$C/rt" && ./gradlew installDist -q --no-daemon -x test 2>&1 | grep -v '^w:\|warning' | tail -5
