# Black-box variation kit

Tools for observing one variation of another flame program without its code. The method and rules are in
`docs/developer/clean_room_provenance.md`; read that first.

**Roles.** The *observer* runs the other program and renders the probe flames. The *implementer* reads only the table
this kit produces (and the paper), never the other program's code, docs on the formula, or its UI help text for it.
These scripts are neutral: they read no flame program and contain no variation formulas.

## Steps

1. `python3 make_probes.py <variation_attribute_name> [--param name=value ...] --out DIR`
   Writes 19 flames: dot clouds at known input points through a final xform holding only the variation, binary-coded so
   each dot can be identified, plus a calibration flame and a filled-square overview.
2. **Observer:** open each `probe_*.flame` in the reference program and render it to a PNG named like the flame
   (`probe_grid_bit0.png`, ...). Do not change the size, camera, quality or filter. Settings that matter: image 1000 x
   1000, centre 0 0, 100 pixels per unit, white palette, no density-estimation blur. If dots look faint, raise the
   quality, not the brightness curve. If the program has a batch renderer, render the whole folder at once.
3. `python3 extract_points.py DIR IMAGES --out table.csv` measures the image axes from the calibration render and writes
   input point, output point, dot size and a flag per probe point. Dots that merge (the variation folds points together)
   or leave the picture are flagged, and the answer is a finer or shifted probe, not a guess.
4. **Implementer:** derive a formula that reproduces `table.csv` (to about 0.01 units), then add table rows as unit tests
   in `FlameVariationsTest` and a ledger row.
5. `python3 selftest.py` checks this tooling with a made-up map (no flame program involved).

For a variation with parameters, repeat step 1 with `--param` values (the default, and a few others) into separate folders.
For a random variation, a dot becomes a cloud: compare the cloud's centre, spread and shape across the grid instead of a
point table (add statistics to `extract_points.py` when the first one comes up).
