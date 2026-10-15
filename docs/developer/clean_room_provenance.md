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
| Beat tracker (`BeatTrackerEngine`) | BTrack (Adam Stark) and the Dan Ellis causal dynamic-programming beat tracker; the code header says "inspired by" | Implemented from the published algorithms, not ported | `DECISIONS.md` (beat clock), `docs/developer/beat_sync.md`, `audio_dsp.md` | Owner's account: built solely from the white paper. Name the paper(s) to cite (BTrack: Stark, Davies and Plumbley, DAFx 2009; Ellis 2007) |
| Flame variations 0-48 (49) | Draves and Reckase, "The Fractal Flame Algorithm" (2003, rev. 2008), Appendix A | Implemented from the paper's formulas, quirks followed as written | `DECISIONS.md` "Fractal Flame Variations Come From the Published Paper Only", commit `87c1980`, `FlameVariationsTest` | See the three amended entries below |
| Flame variations 46, 47, 48 (`secant2`, `twintrian`, `cross`) | The paper, then amended | Commit `a7cba2b` changed them to "follow flam3" (`secant2` +/-1 shift, `twintrian` log of sin^2 alone with a -30 fallback, `cross` sqrt(s^2 + 1e-10)) | `a7cba2b` | **Unknown** (owner does not know). Treat as unclean until re-derived: revert to the paper as written and re-derive by black box, or document the basis |
| Flame variations 49-97 (46 built; 50, 65, 67 and 98 reserved) | A written mathematical specification, `.planning/flame-variations-spec.md` (equations, defaults, edge cases, test pairs) | Owner's account (2026-10-11): one AI read the original program's code and wrote a description; a second AI read only that description and wrote our maths; then several rounds of importing real `.flame` files and `.png` renders checked that our output matched. `.planning/flame-variations-agent-prompt.md` forbids the implementer any other source | Commits `a7cba2b` (spec), `18ad6d4`, `7c7582a`, `de8e555` (implementation) | **The first AI's original description file** (the committed spec is its audited form per commit `a7cba2b`; the original was not found in the repo, `/tmp` or the home folder), and which program's code it read. Keep it, or record that it is gone. The spec itself says "the original code" and "the original random-flame generator", so its author did look at code: the spec side is not clean, the implementation side is. `SHADERS_LICENSE.md` already carries this caveat |
| Variations still to add (`nblur`, `pre_curl`, ...) | Black-box observation only | The protocol below, with the kit in `tools/blackbox/` | see the black-box log | n/a |
| `.flame` importer | Attribute names from the paper and from sample files the user supplies | No third-party parser was read | `FlameImporter`, `docs/developer/flame_source.md` | n/a |
| Mandelbox source (`mandelbox`) | Defining mathematics: box fold, sphere fold (min and max radius), scale, running-derivative distance estimate, orbit traps | Own derivation from the definition; no third-party shader or Fragmentarium code read or recalled; the removed earlier `mandelbox` (`fc1ad3f`) not used. The author assistant may have seen community shaders in training | `library/sources/mandelbox/mandelbox.fs`, `.planning/heavy-sources-notes/mandelbox.md` | n/a (owner to accept the clean-room statement) |
| KIFS source (`kifs`) | Defining mathematics of iterated function systems: Menger sponge, Sierpinski tetrahedron and octahedron constructions, reflection-group fundamental-domain folding, similarity-scaled distance estimate, orbit traps | Written from the mathematics (folds, scale and offset about the IFS fixed vertex, base-shape distance divided by s^n); no Shadertoy, Fragmentarium or other shader source and no earlier removed `kifs` code read or recalled. The author assistant may have seen similar shaders in training | `library/sources/kifs/kifs.fs`, `.planning/heavy-sources-notes/kifs.md` | n/a (owner to accept the clean-room statement) |
| Mandelbulb source (`mandelbulb`) | Triplex power map z^n + c in spherical coordinates (White and Nylander) and the running-derivative distance estimate 0.5 log(r) r / dr, from the published mathematics; orbit-trap and smooth-escape colouring are textbook | Own derivation and implementation; no third-party shader source read or copied; the removed earlier `mandelbulb` (`fc1ad3f`) not used. The author assistant may have seen community Mandelbulb shaders in training | `library/sources/mandelbulb/mandelbulb.fs`, `.planning/heavy-sources-notes/mandelbulb.md` | n/a (owner to accept the clean-room statement) |
| Pseudo Kleinian source (`pseudo_kleinian`) | Textbook mathematics: reflection groups and sphere inversion (Kleinian groups), the Lipschitz distance estimate, orbit traps (Hart) | Derived by the assistant from the mathematics and tuned by rendering (headless GL, 2026-10-14); no Shadertoy, Fragmentarium or other shader source read; the removed earlier `pseudo_kleinian` (`fc1ad3f`) not used. The author assistant may have seen similar shaders in training; the form (periodic triangle fold, scale-capped inversion, core-sphere distance estimate) was chosen by experiment | `library/sources/pseudo_kleinian/pseudo_kleinian.fs`, `.planning/heavy-sources-notes/pseudo_kleinian.md` | n/a (owner to accept the clean-room statement) |
| Apollonian source (`apollonian`) | Textbook mathematics: Apollonian sphere packings and inversion groups, lattice translation, Lipschitz distance estimate, orbit traps (Hart) | Derived by the assistant from the mathematics and tuned by rendering (headless GL, 2026-10-14); no Shadertoy, Fragmentarium or other shader source read. The author assistant may have seen similar shaders in training; the form was chosen by experiment | `library/sources/apollonian/apollonian.fs`, `.planning/heavy-sources-notes/apollonian.md` | n/a (owner to accept the clean-room statement) |
| Escape Time source (`escape_time`) | Textbook mathematics: the quadratic and power iteration z^p + c (Julia, Mandelbrot), the Burning Ship variant (absolute value before squaring, Michelitsch and Rossler), Newton's method on z^3 - 1 with a complex relaxation factor, the smooth escape-count formula and orbit traps (Hart) | Derived by the assistant from the definitions and tuned by rendering (headless GL, 2026-10-14); no Shadertoy, Fragmentarium or other shader source read or recalled | `library/sources/escape_time/escape_time.fs`, `.planning/heavy-sources-notes/escape_time.md` | n/a |
| Nebula Volume source (`nebula_volume`) | Standard emission-absorption volume rendering (Beer-Lambert transmittance, front-to-back accumulation), fractional Brownian motion of value noise, domain warping, ridged noise | Own implementation with our own integer hash; no Shadertoy, Fragmentarium or other shader source read or recalled. The author assistant may have seen community nebula shaders in training | `library/sources/nebula_volume/nebula_volume.fs`, `.planning/heavy-sources-notes/nebula_volume.md` | n/a (owner to accept the clean-room statement) |
| Reaction Diffusion source (`reaction_diffusion`) | Gray-Scott equations (Pearson 1993) with the standard 9-point Laplacian; commonly published (feed, kill) regimes | Written from the equations; seed noise and relief lighting are our own; no third-party shader source read | `library/sources/reaction_diffusion/reaction_diffusion.fs`, `.planning/heavy-sources-notes/reaction_diffusion.md` | n/a |
| Fluid Ink source (`fluid_ink`) | Stable fluids (Stam 1999): semi-Lagrangian advection, Jacobi pressure projection; vorticity confinement (Fedkiw, Stam and Jensen 2001) | Written from the papers; emitters, curl noise and ink rendering are our own; no third-party shader source read | `library/sources/fluid_ink/fluid_ink.fs`, `.planning/heavy-sources-notes/fluid_ink.md` | n/a |
| Lenia source (`lenia`) | Lenia (Chan 2019): kernel K(r) = exp(4 - 1/(r(1-r))), Gaussian growth mapping, Euler update | Written from the paper's formulas; seeding and rendering are our own; no third-party code read | `library/sources/lenia/lenia.fs`, `.planning/heavy-sources-notes/lenia.md` | n/a |
| Quasicrystal source (`quasicrystal`) | Sum of N rotated cosine plane waves (de Bruijn's multigrid / cut-and-project picture), cosine palettes (textbook) | Written by the assistant from the mathematics; no third-party shader code read | `library/sources/quasicrystal/quasicrystal.fs`, `.planning/light-sources-notes/quasicrystal.md` |
| Thin Film source (`thin_film`) | Thin-film interference: optical path 2 n d cos(theta_t), per-wavelength phase for R/G/B, Snell refraction; value-noise fbm and domain warping (textbook) | Own implementation with our own integer hash; no third-party shader code read | `library/sources/thin_film/thin_film.fs`, `.planning/light-sources-notes/thin_film.md` |
| Tunnel Scenes source (`tunnel_scenes`) | Analytic ray-plane and ray-cylinder intersection, Truchet tiling, polar mapping (textbook) | Written from the mathematics; no third-party shader code read | `library/sources/tunnel_scenes/tunnel_scenes.fs`, `.planning/light-sources-notes/tunnel_scenes.md` |
| Video Feedback source (`video_feedback`) | Inverse-warp resampling of the previous frame, Rodrigues rotation of colour about the grey axis, exponential decay, screen blend (textbook) | Written from the definitions; no third-party shader code read | `library/sources/video_feedback/video_feedback.fs`, `.planning/light-sources-notes/video_feedback.md` |
| Hyperbolic Tiling source (`hyperbolic_tiling`) | Coxeter (2,P,Q) triangle group, reflection folds and circle inversion in the Poincare disk, hyperbolic distance, Mobius translation | Derived from the mathematics; no third-party shader code read | `library/sources/hyperbolic_tiling/hyperbolic_tiling.fs`, `.planning/light-sources-notes/hyperbolic_tiling.md` |
| Variation statistics and curated library | Counts from public flame collections; our own search generates the shipped flames | Tallies only; no genome is shipped (the Electric Sheep archive has non-commercial terms for some genomes) | `FlameStats`, `FlameCurator` | Datasets used are listed in `.planning` notes, not in the build |

Built variations: 98 of the 101 ids (the paper's 49, 46 from the spec, and `hemisphere`, `pre_blur` and `plusrecip` by black box). The three reserved ids are `super_shape`,
`lazysusan` and `mobius`.

## Black-box log

| Variation | Observer | Implementer | Observed with | Evidence | Status |
|---|---|---|---|---|---|
| `hemisphere` (id 99, the first black-box variation) | automated: `run_probes.py jwildfire` driving `org.jwildfire.cli.FlameRenderer` | the assistant, from `table.csv` only | JWildfire 9.00 | table in `build/blackbox/jwildfire/hemisphere/`, pairs in the two variation tests | done 2026-10-12: the output is the input scaled by 1/sqrt(r^2 + 1), 88 of 91 clean points within 0.02 |
| `plusrecip` (id 100) | automated: `run_probes.py jwildfire plusrecip` | the assistant, fitting hypotheses to `table.csv` only | JWildfire 9.00 | table in `tools/blackbox/build/blackbox/jwildfire/plusrecip/`, pairs in the two variation tests | done 2026-10-13: with (u, t) the real and imaginary parts of sqrt(4 - z^2), z = x + iy, the output is (sign(x) (x + t), y +/- u), the sign of the second term being that of x*y; 141 of 147 clean points fit within 0.02 (the other six lie on y = 0, where the sign depends on which side of the axis a dot's pixels fall). A parameter may exist that the probe did not vary: only the default was observed |
| `inversion` (importer alias) | `run_probes.py jwildfire inversion` | the assistant, from `table.csv` only | JWildfire 9.00 | `tools/blackbox/build/blackbox/jwildfire/inversion/`; `FlameImporterTest` | done 2026-10-13: every clean point (157) is the input divided by x^2 + y^2 within 0.007, which is our `spherical`, so the importer reads `inversion` as `spherical`. No new variation. `linear3D` and `spherical3D` are read as `linear` and `spherical` (3D twins, z not modelled; not observed, the import notes say so) |
| `pre_blur` (id 67) and the pre stage | `run_probes.py jwildfire` on `pre_spherical` (+ a regular variation) for the structure; `cloud_stats.py jwildfire` (100 low-quality renders of one dot) for the cloud | the assistant, from the observed numbers only | JWildfire 9.00 | `build/blackbox/jw_pre/`, `build/blackbox/cloud_w*`; `FlamePreStageTest` | done 2026-10-12: a pre variation replaces the point before the others (`sinusoidal` + `pre_spherical` = sin(spherical(p)); weight 0.5 gives 0.5 * spherical(p); 0 of 93 off); `post_spherical` at weight 0.5 gave 0.5 * spherical(sum of the others) (1 of 64 off; no post stage built yet); `pre_blur` keeps the point and adds a random direction at a distance with a bell-shaped magnitude, scale linear in w (w 0.5 and 1 curves coincide), isotropic (8 sectors within 6%), the cloud centred on the point; the shader uses 1.25 * (sum of four uniforms - 2), fitting the cumulative share within 0.02 |
| `fan` (id 22, correction) | `run_probes.py flam3 fan --translate 0.5,0.3` | the assistant, fitting hypotheses to the observed table | flam3 3.1.1 | table in `build/blackbox/flam3t/fan/`, pair in `FlameVariationsTest` | done 2026-10-12: the wrap `(angle + f) mod (pi c^2)` is a remainder that keeps the sign of its dividend (ours floored); 189 of 189 points fit, 159 before |
| `blob`, `ngon`, `perspective`, `fan2` (corrections to ids 23, 38, 30, 25) | `run_probes.py flam3` with flam3 3.1.1 (`flam3-render`); the sweep compared every deterministic variation | the assistant, fitting hypotheses to the observed tables | flam3 3.1.1 (Fedora package), probe kit at 100 px per unit | tables in `build/blackbox/flam3/`, pairs in `FlameVariationsTest` and `FlameShaderValuesTest` | done 2026-10-11 |

What the observations changed (each fits its table at 98 to 100% of points):
- **`blob`:** the output points along the input; ours was rotated 90 degrees.
- **`ngon`:** the angle wrap is `t > half ? t - p2 : t`; ours had the branches the other way round.
- **`perspective`:** the angle parameter is a fraction of pi/2 (the importer already passed the file value through).
- **`fan2`:** the wrapped quantity is the angle plus `p2` modulo `p1`; observed at five parameter settings.

These four came from the paper as written (ids 0-48). The sweep also showed 50 variations matching outright, 13 more matching as a set (dots decoded to the wrong input where outputs collide), and 11 flagged with only a handful of mismatches each; those are boundary points (`rectangles` at y = 0), far points where a dot's tiny size is amplified (`swirl`), and branch cuts (`log` at the negative x axis), not formula differences. `waves` and `modulus` returned no data (they depend on the transform's own coefficients); the probe now takes a known translation (`--translate`), and with it `rings`, `waves`, `modulus` (as a set) and `popcorn` match, `fan` was corrected.

The assistant that implements may have seen flame-program formulas in its general training. The rule is therefore that it
derives each formula from the observed table alone and lets the table decide; a formula that does not fit the table is
wrong whatever it remembers.

## Black-box protocol for new variations

1. **Pick the variation from the statistics, not from a source.** Name, rank and parameter names are facts about files. `tools/blackbox/make_probes.py` builds the probe flames; see its README.
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

## Replaced third-party contribution (2026-07-06 commits)

A contributor (Lawrence Norton, 15 commits on `codex/*` branches, 2026-07-06; no license grant on file) added path
confinement, a scan cache, JACK failure reporting, process timeouts, MIDI profile filename checks, a mixer layout
calculator, a shared playlist parser, a GL debug callback, tests and planning docs. About 500 source/test lines and 450
doc lines survived to HEAD. The history still contains the commits; this section covers the working tree.

| Step | Who | What they saw | Output |
|---|---|---|---|
| Spec | the assistant (spec writer) | the surviving attributed hunks and their callers | `.planning/lawrence-rewrite-spec.md`: behavior, interfaces, test obligations |
| Source rewrite, pass 1 | a second assistant (implementer) | the spec, callers, and unmasked `PlaylistParser`, `GLDebug`, `MixerLayout` outside the attributed hunks | new bodies in those three files |
| Source rewrite, pass 2 | the implementer | masked copies (attributed hunks replaced by markers); also saw the first 60 lines and function index of `FileSystemManager.kt`, lines 446, 458 and 548-551 of `Main.kt`, and the `SessionSerializer` call site (line 203) | new hunks spliced by line range |
| README | the spec writer | the masked README, then the diff after splicing | headings and prose rewritten; commands and code fences are facts |
| Docs | n/a | n/a | `.planning/codebase/*`, `.planning/STATE.md`, the debug note and the quick-task folder deleted |
| Tests | the implementer | masked copies of the five test files with attributed hunks (`MidiMappingManagerTest`, `SessionStateTest`, `FileSystemManagerTest`, `PlayQueueManagerTest`, `MixerLayoutTest`) | replacement hunks written from the spec's test obligations; `./gradlew test` green. `MidiEngineQueueTest` (3 lines) and `BgQueueManagerTest` (1 line) left as imports/braces only |

Residue that still blames to the contributor (accepted): imports, braces, blank lines, code fences and commands in the
README, and lines whose text is fixed by an interface other code calls (`clearScanCache()`, `tryReconnect(force)`,
`midiProfileFile`, the `lastJackFailure` fields, the `MixerPanel` argument list built from ImGui getters). About 280 lines remain by `git blame -w -M -C`, none of them logic beyond one-line calls. Candidates to
rename if a stricter line is wanted: `ScanCacheEntry` and the `GLDebug` callback text decode.

**To confirm:** a written grant or a statement from the contributor about their commits; they were AI-assisted
(`codex/*` branch names), so the copyright position of that output is unclear either way.

## Credits and notices

- Credit Draves and Reckase and the beat-tracking authors in the shipped credits (`docs/licenses/SHADERS_LICENSE.md` has
  the Fractal Flame paragraph; verify authors, year and revision against the paper before a commercial release).
- A trademark search for the user-facing names (Fractal Flame, Garden, Breed) is still open.
- None of this is legal advice. Have the ledger reviewed before selling.
