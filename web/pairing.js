// Which FX chains suit a preset when the autopilot picks one from the rotation.
// A preset tagged `fullframe` fills the whole frame with colour (no dark background), so feedback
// trails and bloom pile up on it until the picture is one flat colour: it skips chains that hold
// either. And a chain should answer a different part of the music than the look does: a look that
// mostly follows the kick does not get a chain that only follows the kick too (`dominantBand`,
// `bands`, `chainAnswers`; the autopilot falls back to any fitting chain when none answers).
// Used by autopilot.js and web/checks/render_check.mjs, so the check shows what goes on air.

const FULL_FRAME_AVOIDS = new Set(['feedback', 'bloom']);

/** True when the chain (an FXChainDto) may run on a preset with these tags. */
export function chainFits(presetTags, chainDto) {
  if (!(presetTags ?? []).includes('fullframe')) return true;
  return !(chainDto?.slots ?? []).some((s) => s && FULL_FRAME_AVOIDS.has(s.filterId));
}

const BAND = {
  audio_bass: 'low', audio_flux_bass: 'low',
  audio_mid: 'mid', audio_flux_mid: 'mid',
  audio_high: 'high', audio_flux_high: 'high',
};

/** Audio modulators per band ('low' / 'mid' / 'high') in a bag of ParameterDtos. */
function countBands(params, counts) {
  for (const p of Object.values(params ?? {})) {
    for (const m of p?.modulators ?? []) {
      const band = BAND[m.sourceId];
      if (band && !m.bypassed) counts[band] = (counts[band] ?? 0) + 1;
    }
  }
  return counts;
}

/** The band most of a preset's (DeckPresetDto) audio modulators follow, or null if it follows none. */
export function dominantBand(presetDto) {
  const counts = countBands(presetDto?.parameters, {});
  let best = null;
  for (const [band, n] of Object.entries(counts)) if (!best || n > counts[best]) best = band;
  return best;
}

/** The bands any modulator in a chain (FXChainDto) follows. */
export function bands(chainDto) {
  const counts = {};
  for (const s of chainDto?.slots ?? []) if (s) countBands(s.parameters, counts);
  countBands({ dryWet: chainDto?.dryWet }, counts);
  return new Set(Object.keys(counts));
}

/**
 * chainFits, and the chain does more than echo the look: a chain whose audio modulation follows only
 * the band the look mostly follows doubles it (a kick look under a kick-only chain). Chains that also
 * follow another band, or none, answer. Most chains punch on the kick, so "only" keeps ~half of them
 * open to kick looks instead of a handful.
 */
export function chainAnswers(presetTags, dominant, chainDto) {
  if (!chainFits(presetTags, chainDto)) return false;
  const b = bands(chainDto);
  return !(dominant && b.size === 1 && b.has(dominant));
}
