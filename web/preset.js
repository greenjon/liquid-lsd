// Desktop preset formats -> the deck / FX / transition state graph.js renders.
//   .lsd          DeckPresetDto   -> parseDeckPreset
//   .lsdfxchain   FXChainDto      -> parseFxChain
//   .lsdtrans     transition preset -> parseTransition
// ParameterDto objects ({baseValue, baseMin, baseMax, randomizeBase, modulators}) are kept as they
// are; evaluator.js evaluates them every frame. Only randomizeBase is resolved here, once, at load.

import { resolveBase } from './evaluator.js';

/** A ParameterDto with randomizeBase applied: the same dto with a fixed baseValue. */
function settle(param, rng) {
  if (!param || typeof param !== 'object') return param;
  if (!param.randomizeBase) return param;
  return { ...param, baseValue: resolveBase(param, rng), randomizeBase: false };
}

function settleAll(bag, rng) {
  const out = {};
  for (const [k, v] of Object.entries(bag || {})) out[k] = settle(v, rng);
  return out;
}

/** DeckPresetDto -> deck state. The deck's FX chain is separate: presets carry no FX. */
export function parseDeckPreset(dto, rng = Math.random) {
  if (!dto || typeof dto !== 'object') return { source: 'mandala', params: {}, fx: [] };
  const view = dto.viewParameters || {};
  return {
    name: dto.name,
    source: dto.visualSourceType || 'mandala',
    params: settleAll(dto.parameters, rng),
    viewZoom: settle(view.viewZoom, rng),
    viewRotateZ: settle(view.viewRotateZ, rng),
    globalAlpha: settle(dto.globalAlpha, rng),
    fx: [],
  };
}

function parseSlot(slot, rng) {
  if (!slot || !slot.filterId) return null;
  return {
    id: slot.filterId,
    enabled: slot.enabled !== false,
    dryWet: settle(slot.dryWet, rng),
    params: settleAll(slot.parameters, rng),
  };
}

/** FXChainDto -> {fx: [slot|null x3], fxDryWet}. Merge into a deck (or the mixer for the master chain). */
export function parseFxChain(dto, rng = Math.random) {
  const slots = Array.isArray(dto?.slots) ? dto.slots : [];
  return {
    fx: [0, 1, 2].map((i) => parseSlot(slots[i], rng)),
    fxDryWet: settle(dto?.dryWet, rng),
  };
}

/** Transition preset -> {transition, transitionParams} for the mixer. */
export function parseTransition(dto, rng = Math.random) {
  const slot = dto?.slot;
  if (!slot || !slot.filterId || slot.enabled === false) return { transition: 'linear_crossfade', transitionParams: {} };
  return { transition: slot.filterId, transitionParams: settleAll(slot.parameters, rng) };
}
