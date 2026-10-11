# Clean-room provenance

Liquid LSD may be sold, so every algorithm that comes from someone else's work needs a recorded source and method. This
file is the ledger. Add a row **before** a new algorithm ships, not after. It records what the repository itself shows;
items only the owner can confirm are marked **To confirm** and should be settled (and ideally attested in writing) before a
commercial release.

## The rules

- **Allowed sources:** a published paper, our own derivation, textbook mathematics, and black-box observation of a
  running program (below).
- **Not allowed:** reading, copying, decompiling, disassembling or recalling-and-copying another program's source code,
  including GPL/LGPL code. Shader, OpenCL or script files that an app ships as plain text count as source. A forum post or
  wiki page that quotes an app's code counts too.
- **Counts are fine.** Statistics about which variations flames use (`flameStats`) copy no genome and no code. Only the
  tallies are kept or published.
- **Separate the roles** where a formula is not in a paper: whoever observes the other program never writes our code, and
  whoever writes our code sees only the written observations.

## Ledger

| Component | Source of the maths | Method | Evidence in the repo | To confirm |
|---|---|---|---|---|
| Beat tracker (`BeatTrackerEngine`) | BTrack (Adam Stark) and the Dan Ellis causal dynamic-programming beat tracker; the code header says "inspired by" | Implemented from the published algorithms, not ported | `DECISIONS.md` (beat clock), `docs/developer/beat_sync.md`, `audio_dsp.md` | Exact papers to cite (Stark, Davies and Plumbley, DAFx 2009; Ellis 2007), and that BTrack's reference source was not consulted |
| Flame variations 0-48 (49) | Draves and Reckase, "The Fractal Flame Algorithm" (2003, rev. 2008), Appendix A | Implemented from the paper's formulas, quirks followed as written | `DECISIONS.md` "Fractal Flame Variations Come From the Published Paper Only", commit `87c1980`, `FlameVariationsTest` | See the three amended entries below |
| Flame variations 46, 47, 48 (`secant2`, `twintrian`, `cross`) | The paper, then amended | Commit `a7cba2b` changed them to "follow flam3" (`secant2` +/-1 shift, `twintrian` log of sin^2 alone with a -30 fallback, `cross` sqrt(s^2 + 1e-10)) | `a7cba2b` | **What the amendments were derived from.** If anything but the paper or our own reasoning, revert to the paper as written and re-derive by black box |
| Flame variations 49-97 (46 built; 50, 65, 67 and 98 reserved) | A written mathematical specification, `.planning/flame-variations-spec.md` (equations, defaults, edge cases, test pairs) | Owner's account: a two-party clean room. One party wrote the spec from the original programs; a second implementer saw only the spec (`.planning/flame-variations-agent-prompt.md` forbids any other source) | Commits `a7cba2b` (spec), `18ad6d4`, `7c7582a`, `de8e555` (implementation) | **Who wrote the spec, from what material, when, and a statement that the implementer saw nothing else.** The spec itself says "the original code" and "the original random-flame generator", so its author did look at code: the spec side is not clean, the implementation side is. `SHADERS_LICENSE.md` already carries this caveat |
| Variations still to add (`pre_blur`, `nblur`, `hemisphere`, `pre_curl`, ...) | Black-box observation only | The protocol below | none yet | n/a |
| `.flame` importer | Attribute names from the paper and from sample files the user supplies | No third-party parser was read | `FlameImporter`, `docs/developer/flame_source.md` | n/a |
| Variation statistics and curated library | Counts from public flame collections; our own search generates the shipped flames | Tallies only; no genome is shipped (the Electric Sheep archive has non-commercial terms for some genomes) | `FlameStats`, `FlameCurator` | Datasets used are listed in `.planning` notes, not in the build |

Built variations: 95 of the 99 ids (the paper's 49 and 46 from the spec). The four reserved ids are `super_shape`,
`lazysusan`, `pre_blur` and `mobius`.

## Black-box protocol for new variations

1. **Pick the variation from the statistics, not from a source.** Name, rank and parameter names are facts about files.
2. **Observer** (a person, or an agent that writes no implementation code): run the other program as a user would. Build a
   flame with one xform, identity pre-affine, weight 1, only the variation under study, and (if the program allows) a
   final xform of identity. Record **only** what the program shows or exports by design:
   - rendered images at fixed settings, and any point or parameter values it shows or saves;
   - for deterministic variations, where chosen input points land (a grid, the axes, a ring, near-origin points); a
     handful of exact pairs from a fine sweep is the goal;
   - for random ones, the distribution of the output (moments, a histogram) over many samples;
   - the parameters it exposes, with their defaults and the range its UI allows.
   Write these into a spec file shaped like `.planning/flame-variations-spec.md` (inputs, outputs, tolerances). No
   formulas, no code, no screenshots of source.
3. **Implementer** reads only that file and the paper, derives a formula that reproduces the table, implements it, and adds
   the pairs as unit tests. If two formulas fit, the observer adds probe points to separate them.
4. **Check by image:** render the same flame in our engine and in the program and compare. Differences go back to the
   observer as new probes, never as "the original does X".
5. **Log it:** add a ledger row (variation, date, observer, implementer, spec file, tests). Anything the observer or
   implementer was unsure the rules allowed is recorded and the item is dropped or redone.

The reverse-engineering tools (decompilers, memory inspection) are not part of this protocol.

## Credits and notices

- Credit Draves and Reckase and the beat-tracking authors in the shipped credits (`docs/licenses/SHADERS_LICENSE.md` has
  the Fractal Flame paragraph; verify authors, year and revision against the paper before a commercial release).
- A trademark search for the user-facing names (Fractal Flame, Garden, Breed) is still open.
- None of this is legal advice. Have the ledger reviewed before selling.
