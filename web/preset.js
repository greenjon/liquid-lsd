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

/**
 * Mandala's recipe roll: when Recipe Select is randomized, one recipe is drawn at load, uniformly
 * over every recipe in the Lobes range (or at the fixed Lobes count), and held for the whole time
 * the scene is on air: Lobes and Recipe Select lose their modulators, so nothing switches it later.
 * `recipes` is a MandalaRecipes (mandala.js); without one the parameters settle as usual.
 */
function rollMandalaRecipe(raw, params, recipes, rng) {
  const sel = raw?.['Recipe Select'];
  if (!recipes || !sel?.randomizeBase) return params;
  const lobes = raw.Lobes ?? { baseValue: 4, baseMin: 3, baseMax: 26 };
  const [lo, hi] = lobes.randomizeBase
    ? [lobes.baseMin ?? 3, lobes.baseMax ?? 26]
    : Array(2).fill(recipes.closestPetals(Math.round(lobes.baseValue ?? 4)));
  const roll = recipes.rollRecipe(lo, hi, rng);
  const held = (p, v) => ({ ...p, baseValue: v, randomizeBase: false, modulators: [] });
  return { ...params, Lobes: held(lobes, roll.lobes), 'Recipe Select': held(sel, roll.select) };
}

/**
 * DeckPresetDto -> deck state. The deck's FX chain is separate: presets carry no FX.
 * `mandalaRecipes` (a MandalaRecipes) enables the mandala recipe roll above.
 */
export function parseDeckPreset(dto, rng = Math.random, { mandalaRecipes = null } = {}) {
  if (!dto || typeof dto !== 'object') return { source: 'mandala', params: {}, fx: [] };
  const view = dto.viewParameters || {};
  const source = dto.visualSourceType || 'mandala';
  let params = settleAll(dto.parameters, rng);
  if (source === 'mandala') params = rollMandalaRecipe(dto.parameters, params, mandalaRecipes, rng);
  return {
    name: dto.name,
    source,
    params,
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
