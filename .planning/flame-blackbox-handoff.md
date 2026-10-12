# Flame variations by black box + curated library: handoff (read this first in a fresh session)

Branch `feature/flame-curator` (8 commits ahead of `main`, local only, not pushed or merged). Rules: clean-room (see
`docs/developer/clean_room_provenance.md`, the ledger and protocol), no "sheep" wording, **no Co-Authored-By lines**.
Memory: `project_flame_curator.md`. Related: `.planning/flame-source-handoff.md`, `.planning/flame-source-plan.md`.

## What exists

- **Curator** (`tools/FlameCurator.kt`, `FlameViability`, `FlameAesthetics`): `./gradlew curateFlames -Pargs="--candidates N --minutes M --out DIR"`;
  `--apply DIR decisions.txt`, `--seed-dir`, `--sheet DIR`. Seeds carry tag `seed` (Pool skips, Breed uses). Output sheet = `sheet.html`
  (click: keeper > seed > reject). **The user has not reviewed the 442-flame run in `build/curated-flames` yet** (re-run after new variations is cheaper
  than reviewing twice; ask).
- **flameStats** (`tools/FlameStats.kt`): variation usage across folders. Data cloned to `~/flame-data/` (electric_sheep_genomes 9.3k distinct,
  FRACTALLOOT 1.4k JWildfire; JWildfire repo only 34). Results in `build/flame-stats/{stats.md,variations.csv}`. The curator writes
  `variation-survival.csv` for the second signal (`--survival`). No third dataset found.
- **Black-box kit** (`tools/blackbox/`, README there): `make_probes.py` (dot-cloud probe flames behind a final xform, binary-coded, groups of 63 because
  JWildfire crashes above 100 xforms, DOT 0.0003), `extract_points.py` (calibrates axes from dots, writes `table.csv`), `run_probes.py flam3|jwildfire VARIATION`
  (headless renderers: `flam3-render`, and `java -cp ~/Apps/j-wildfire-9.00/lib/j-wildfire.jar org.jwildfire.cli.FlameRenderer`), `sweep.py PROGRAM LIST`,
  `selftest.py`. `./gradlew compareVariation -Pargs="name table.csv [--p a,b,c,d]"` or `--batch FILE` / `--list` runs our real shader on a table
  (verdicts MATCH, MATCH-AS-SET, DIFFERENT, NO-DATA; DIFFERENT with a few bad points of 100+ is usually collisions, boundary or far-point noise).
- **Done by observation** (ledger rows exist): `blob`, `ngon`, `perspective`, `fan2` corrected to flam3; `hemisphere` added as id 99 (JWildfire 9.00).
  Ids now 100, 97 built (`pre_blur` added as a pre-stage variation), 3 reserved (`super_shape`, `lazysusan`, `mobius`). `tools/blackbox/cloud_stats.py` observes random variations (cumulative share of hits by radius from many very low quality renders).

## Next steps (my order)

1. ~~Translation-aware probe~~ DONE 2026-10-12 (`--translate tx,ty`, compare reads `probes.json`). Result: `fan` corrected (remainder keeps the dividend's sign); `rings`, `waves` match; `modulus` matches as a set (params 0.8,0.6); `popcorn` 10 of 171 points off by up to 0.04 near the poles of tan (float vs double, not the formula). Only translation is probed, so `waves` b/e and any variation reading the affine part (a, b, d, e) remain unchecked. Not yet run with JWildfire, and uncommitted.
2. ~~Pre stage and `pre_blur`~~ DONE 2026-10-12 (4 slots, `Variation.pre`, `isPre` in the shader, `FlamePreStageTest`; ledger row written). Not built: the post stage (semantics observed: `post_X` at weight w gives `w * X(sum of the regular variations)`), `pre_curl`, `pre_crop`/`post_crop` (their parameters need probing; `cloud_stats.py` handles random variations). The importer now reports unknown `pre_X`/`post_X` names (one word after the prefix) as unsupported; their parameters stay unreported.
3. Remaining deterministic gaps by usage in the JWildfire set: `nblur` (random), `line`, `scry2`, `epispiral`, `glynnia`, `ecollide`, `bswirl`,
   `sphericaln`, `julian2`, `bubble2`, `voron`, `shredlin` (see `stats.md` "Missing from our set"). `linear3d`/`blur3d`/`spherical3d` are 3D twins of ours.
   Random variations need statistics in `extract_points.py` (a dot becomes a cloud; compare centre, spread, shape).
4. Re-run `curateFlames` with the larger set, compare `variation-survival.csv`, then the user's review.

## Gotchas learned

- Background runs are killed at ~10 minutes whatever `timeout` says: run long things in 580 s slices (`timeout 580 python3 ...`); `sweep.py` skips finished tables.
- Never `pkill -f "<text that is in your own command line>"`: it kills your own shell (exit 144). Select by `ps -eo pid,args | awk` on the exact argv.
- Dots clouds must stay tiny (DOT 0.0003): bigger clouds biased strongly bending variations (`swirl`) by 0.1.
- A non-injective variation collides dots (`cosine`, `hyperbolic`...): the decode then assigns a blob to the wrong input; the compare tool's set-level check covers it.
- `run_probes.py` copies each flame to a temp dir for JWildfire (it writes the PNG next to the flame).
- `generateDocs` regenerates tracked html under `src/main/resources/docs`; this repo commits it with the markdown change.
- The assistant that implements may remember other programs' formulas; derive each formula from the observed table alone (ledger rule).

## Open items only the owner can settle (recorded in the ledger)

- Where the first AI's code description is (the committed spec `.planning/flame-variations-spec.md` is its audited form), and which program it read.
- What the `a7cba2b` amendments of `secant2`, `twintrian`, `cross` were based on (unknown). Observation: `cross` and `secant2` match flam3's tables
  except at collisions; `twintrian` is random and still unchecked.
- Name the beat-tracker white paper(s) to cite (BTrack, Stark, Davies and Plumbley 2009; Ellis 2007?).
- Trademark search for Fractal Flame / Garden / Breed; legal review of the ledger before selling.
