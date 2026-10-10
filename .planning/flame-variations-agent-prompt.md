# Prompt for the fresh implementer agent (clean-room)

Paste everything below the line into a new agent with no prior context.

---

You are adding fractal-flame variations to the Fractal Flame source in this repository (Kotlin + GLSL, branch `spike/flame-source`).

## Clean-room rules (strict)
- Your only source for the mathematics is `.planning/flame-variations-spec.md`. Do not read, search for, download or recall-and-copy any flam3, Apophysis, JWildfire or Electric Sheep source code. If the spec is ambiguous or seems wrong, stop and ask; do not resolve it from outside code.
- Do not look in `/tmp` for reference material.
- No `Co-Authored-By` or Claude attribution lines in commits.

## Task
Implement the variations in the spec for indices **49-98**, **except** 50 super_shape, 65 lazysusan, 67 pre_blur and 98 mobius (skipped for v1; leave their ids out of the catalogue and let the importer report them as unsupported). Indices 0-48 already exist.

Work in batches, committing after each with the full flame tests green:
1. Trig/hyperbolic family, 82-95 (no parameters).
2. Other parameterless: 56 boarders, 57 butterfly, 61 edisc, 62 elliptic, 64 foci, 66 loonie, 70 polar2, 72 scry, 83 log (if not done in batch 1).
3. Parametric ones, in index order, using at most the four slot parameters `A`..`D`.

Read first: `src/main/kotlin/llm/slop/liquidlsd/rendering/FlameVariations.kt` (catalogue; ids must equal flam3 index, the table is index-addressed and `NAMES` compares with a GLSL comment test), `FlameEngine.kt` (the GLSL `vari()` switch, `COMMON`), `FlameImporter.kt` (file names, parameter import and clamping), `FlameSampler.kt` (weighted pool; give new variations modest weights, exotic ones low), `FlameVariationsTest.kt` (the Kotlin mirror pattern), and `docs/developer/flame_source.md`.

Because ids are positions in the table and skipped variations leave holes, check how the table, the GLSL `case` labels and the test `glslCasesMatchTheTable` can accommodate gaps (for example placeholder entries that the sampler never picks and the importer treats as unsupported). Choose the simplest approach that keeps that test meaningful, and explain it in the commit message.

## Requirements per variation
- Equations, defaults, edge cases and the global conventions (epsilon 1e-10, closed-interval random draws, bad-value rule is already handled by the iteration loop - check it is) come from the spec. Follow the spec's weight placement exactly.
- GLSL is single precision: guard divisions and logs so a bad input yields the spec's behaviour (infinite/NaN, which the loop discards) rather than a driver-dependent value.
- Parameters: map each spec parameter to a slot parameter with `min`, `default`, `max`. Use the spec's generator range as min/max, widened if the default lies outside it. The importer maps `.flame` attribute names (given in each spec entry) to these slots; a file value outside the range is clamped with a note, as today.
- Random variations use the shader's `rnd()`; add them to `FlameVariations.RANDOM`.
- Add the Kotlin mirror in `FlameVariationsTest.kt` and a test for **every deterministic spec test pair** (tolerance 1e-4, or tighter where exact).
- Update `docs/developer/flame_source.md` and the source's user-facing docs/release notes only where the variation list is mentioned.

## Done means
`./gradlew test` passes, every spec test pair for implemented variations is asserted, the import notes list the four skipped variations when a file uses them, and each batch is committed with a message describing what and why.

## Report back
List what was implemented, any spec entry you found ambiguous or contradictory (quote the entry number), and any decision you made that the spec did not cover.
