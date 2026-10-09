// Checks desktop-format content (presets, FX chains, transition presets) against what the web
// can actually render: known source/filter ids, parameter names that exist in the ISF header,
// complete ParameterDtos and modulators the evaluator accepts. Used by content.test.mjs and by
// preset authoring. validateAll() returns a list of problem strings (empty = fine).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseHeader } from '../isf.js';
import { validateModulator } from '../evaluator.js';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const MANDALA_PARAMS = ['Lobes', 'Recipe Select', 'L1', 'L2', 'L3', 'L4', 'Thickness', 'Hue Offset', 'Hue Sweep', 'Depth'];
const KNOWN_SOURCES = /^(lfo|seq|beatSine|bpm|audio_(amp|bass|mid|high)|audio_flux_(amp|bass|mid|high)|midi_(cc|note)_\d+_\d+)$/;
const PARAM_FIELDS = ['baseValue', 'baseMin', 'baseMax', 'randomizeBase', 'modulators'];

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const catalog = () => readJson(path.join(web, 'catalog.json'));

function inputNames(entry) {
  return parseHeader(fs.readFileSync(path.join(web, entry.file), 'utf8')).INPUTS
    .filter((i) => i.TYPE.toLowerCase() !== 'image').map((i) => i.NAME);
}

function checkParam(where, name, dto, problems) {
  if (!dto || typeof dto !== 'object') { problems.push(`${where}: ${name} is not a ParameterDto`); return; }
  for (const f of PARAM_FIELDS) if (!(f in dto)) problems.push(`${where}: ${name} lacks ${f}`);
  if (!Array.isArray(dto.modulators)) return;
  dto.modulators.forEach((mod, i) => {
    for (const p of validateModulator(mod)) problems.push(`${where}: ${name} modulator ${i}: ${p}`);
    if (typeof mod.sourceId === 'string' && !KNOWN_SOURCES.test(mod.sourceId)) {
      problems.push(`${where}: ${name} modulator ${i}: unknown sourceId ${mod.sourceId}`);
    }
  });
}

function checkParams(where, bag, allowed, problems) {
  for (const [name, dto] of Object.entries(bag || {})) {
    if (!allowed.includes(name)) problems.push(`${where}: no parameter named ${name} (have ${allowed.join(', ')})`);
    checkParam(where, name, dto, problems);
  }
}

export function validatePreset(file, dto, cat = catalog()) {
  const where = path.basename(file), problems = [];
  const src = dto.visualSourceType;
  if (src === 'mandala') {
    checkParams(where, dto.parameters, MANDALA_PARAMS, problems);
  } else {
    const entry = cat.sources.find((s) => s.id === src);
    if (!entry) problems.push(`${where}: unknown source ${src}`);
    else checkParams(where, dto.parameters, inputNames(entry), problems);
  }
  for (const key of ['viewZoom', 'viewRotateZ']) {
    if (dto.viewParameters?.[key]) checkParam(where, key, dto.viewParameters[key], problems);
  }
  if (dto.globalAlpha) checkParam(where, 'globalAlpha', dto.globalAlpha, problems);
  return problems;
}

function validateSlot(where, slot, cat, problems) {
  const entry = cat.filters.find((f) => f.id === slot.filterId);
  if (!entry) { problems.push(`${where}: unknown filter ${slot.filterId}`); return; }
  checkParam(where, 'dryWet', slot.dryWet, problems);
  checkParams(`${where} (${slot.filterId})`, slot.parameters, inputNames(entry), problems);
}

export function validateFxChain(file, dto, cat = catalog()) {
  const where = path.basename(file), problems = [];
  if (!Array.isArray(dto.slots) || dto.slots.length > 3) problems.push(`${where}: slots must be an array of up to 3`);
  for (const slot of dto.slots || []) if (slot) validateSlot(where, slot, cat, problems);
  return problems;
}

export function validateTransition(file, dto, cat = catalog()) {
  const where = path.basename(file), problems = [];
  const slot = dto.slot;
  if (!slot) return [`${where}: no slot`];
  const entry = cat.transitions.find((t) => t.id === slot.filterId);
  if (!entry) return [`${where}: unknown transition ${slot.filterId}`];
  checkParams(`${where} (${slot.filterId})`, slot.parameters, inputNames(entry).filter((n) => n !== 'progress'), problems);
  return problems;
}

const STALE_PARAM = /no parameter named/;

/**
 * Every preset in web/presets, every FX chain and transition preset in the catalog. Parameter
 * names the desktop's own shipped chains/transitions reference but the shader no longer has are
 * not errors here (the desktop ignores them too); `warnings`, if given, collects them.
 */
export function validateAll(warnings = []) {
  const cat = catalog(), problems = [];
  const desktop = (file) => file.startsWith('content/');
  const route = (file, list) => { for (const p of list) (desktop(file) && STALE_PARAM.test(p) ? warnings : problems).push(p); };
  const dir = path.join(web, 'presets');
  for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.lsd')).sort()) {
    problems.push(...validatePreset(f, readJson(path.join(dir, f)), cat));
  }
  for (const c of cat.fxChains) route(c.file, validateFxChain(c.file, readJson(path.join(web, c.file)), cat));
  for (const t of cat.transitionPresets) route(t.file, validateTransition(t.file, readJson(path.join(web, t.file)), cat));
  return problems;
}
