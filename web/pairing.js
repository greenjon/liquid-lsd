// Which FX chains suit a preset when the autopilot picks one from the rotation.
// A preset tagged `fullframe` fills the whole frame with colour (no dark background), so feedback
// trails and bloom pile up on it until the picture is one flat colour: it skips chains that hold
// either. Used by autopilot.js and web/checks/render_check.mjs, so the check shows what goes on air.

const FULL_FRAME_AVOIDS = new Set(['feedback', 'bloom']);

/** True when the chain (an FXChainDto) may run on a preset with these tags. */
export function chainFits(presetTags, chainDto) {
  if (!(presetTags ?? []).includes('fullframe')) return true;
  return !(chainDto?.slots ?? []).some((s) => s && FULL_FRAME_AVOIDS.has(s.filterId));
}
