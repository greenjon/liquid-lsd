// One animation-frame loop that can never run twice: start() cancels a frame that is still pending before
// asking for a new one, so any order of pause / resume / VISUALS switches leaves exactly one callback queued.
export function createFrameLoop(callback, raf = (f) => requestAnimationFrame(f), caf = (id) => cancelAnimationFrame(id)) {
  let id = 0;
  const run = (now) => { id = 0; callback(now); };
  return {
    start() { if (id) caf(id); id = raf(run); },
    stop() { if (id) { caf(id); id = 0; } },
    pending: () => id !== 0,
  };
}
