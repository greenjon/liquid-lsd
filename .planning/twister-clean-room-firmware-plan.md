# Twister firmware clean-room reimplementation: plan and audit

Status: **SHELVED 2026-10-08.** Not a blocker: native mode is developed against the XT hex, which users can find and flash themselves. Revisit only if we want to ship our own MIT firmware. Written to record the thought process; nothing here has been started.
Not legal advice. If this goes ahead, have an IP lawyer review the protocol before stage 2 begins.

## Goal

The native-mode Twister profile (see `twister-native-mode-plan.md`) depends on a fork of the Midi Fighter Twister firmware. The upstream firmware is source-available with **no license**, which means all rights reserved: we cannot redistribute a fork, and we cannot relicense it. The idea is to produce an independent reimplementation released under MIT.

## Proposed pipeline (original plan)

| Stage | Role | Sees source? |
|---|---|---|
| 1. Plan & methodology | Clean-room protocol, output formats, audit checklists | No |
| 2. Dirty room (spec generator) | Reads upstream source, emits pure hardware/protocol facts: pinouts, bus addresses, timings, bootloader handshake, byte maps. No pseudo-code, C, or original identifiers | Yes |
| 3. Filtration / auditor | Checks stage 2 output against the checklists, strips structural similarity, emits the sanitized Hardware Interface Specification (HIS) | See finding 3 |
| 4. Clean room (implementer) | Blank session with only the HIS, public datasheets (ATxmega), vanilla LUFA docs; writes the firmware | No |
| 5. Assessor | Abstraction-Filtration-Comparison (AFC, the Altai test) between upstream and stage 4 output | Yes |

Operating rules: independent fresh session per stage, no memory or cache carry-over, archive all prompts and completions, and ignore hardware-dictated similarity (register names, constants) under merger / scènes à faire.

## Audit findings

### Accepted constraints

1. **Stage 4 cannot be proven clean.** The upstream firmware is on public GitHub, so the model may have it in its training data. Session isolation does not fix this. Consequence: stage 5 (comparison against the source) is the real evidence, and the process doc must not claim "the implementer never saw the code."
2. **The human must stay out of the code path.** The orchestrator (the user) has not read the firmware, does not intend to, and does not write code, so the main human taint risk is low. Keep it that way as a rule:
   - Do not read stage 2 raw output or stage 5 findings that quote the source.
   - Prompts for stage 4 are written once and locked before stage 2 runs.
   - Stage 5 findings go back to stage 4 only as abstract constraints ("do not use a table-driven approach for X"), never as pointers into the source.
3. **Stage 3 as written cannot detect similarity.** It does not see the source, so it can only check that the spec is code-free, which a lint or regex plus a human does better. Give it read access to the source (acceptable, it sits on the dirty side of the wall), or fold it into a mechanical filter. An LLM auditor is not legal proof; courts weigh documented process and the independence of people. Have a human sign off on the HIS.

### Additional recommendations

- **Stage 1 is a document, not an agent.** The effort belongs in a fixed HIS template: facts only (pin, address, timing, byte layout, handshake step list).
- **Facts versus design.** Specify observable behavior (inputs, outputs, timing limits), not ordered internal procedures. Scan order, state transitions, and buffering strategy leak structure.
- **Tuning data is not hardware-dictated.** Encoder acceleration curves, LED gamma tables, debounce constants, palettes, and detent thresholds are authorial choices and do not get the merger/scènes à faire exception. Re-derive them on a real unit (logic analyzer, scope) or specify tolerance ranges.
- **Prefer black-box sources over the dirty room**: USB descriptors and captured MIDI/SysEx traffic, the step-0 hardware probe, the PCB and ATxmega datasheet, DJTT's published MIDI docs. Each fact obtained this way is one less from the tainted source.
- **The full hardware schematic is available** (`Midi_Fighter_Twister_Schematic.PDF`; a copy sits in the upstream firmware checkout at `/tmp/xt-fork/fw/`). This is the strongest black-box source: it gives pinouts, bus topology, I2C/SPI addresses, and multiplexer and LED-driver wiring unambiguously, so the hardware half of the HIS can be written from the schematic plus the ATxmega and peripheral datasheets, with **no dirty-room involvement at all**. This shrinks the dirty room to behavior the schematic cannot show:
  - the bootloader handshake and USB/MIDI/SysEx message formats (largely obtainable by traffic capture instead),
  - timing and tuning values (obtainable by measuring a real unit),
  - any undocumented device behavior.

  **Coverage check (2026-10-08, from the PDF's text layer, copied alone to `/tmp/schematic-clean/`):** 2 pages, Altium export dated 2013-06 (sheet revision A). Page 1 is the top level: ATXMEGA128 (full port map PA0-PE3, PR0/PR1), USB B socket, USB 2:1 switch, 3V3 regulator, 74HC165 and 74HC595N chains, side switches 1-6, LED control nets (SIN, CLK, CLK_LATCH, OE, RST), PDI/RESET. Page 2 is the encoder block, repeated 16 times: 74HC595N shift registers driving the RGB LEDs and ring through series resistors, plus encoder A/B/switch inputs with pull-ups. Findings:
  - Covered: MCU pin assignments, shift-register topology, LED wiring, encoder and switch inputs, USB wiring. No I2C/SPI peripherals; everything is GPIO plus shift registers.
  - **Not shown, and the main residual gap:** LEDs are plain 74HC595 outputs, so brightness and dimming are software-generated. The PWM scheme, refresh rate, and gamma live entirely in firmware and must be designed fresh (and measured, not copied).
  - No clock source appears (no crystal or oscillator text), so the clock configuration is a datasheet-level choice the HIS must pin down another way (internal oscillator and PLL settings are from the ATxmega manual, or measured).
  - The schematic carries an unresolved note, "CONFIRM UART POLARITY", so a UART header or net exists whose polarity was never settled. Verify on a real board.
  - Dated 2013 and revision A; shipping boards may differ, so spot-check against a physical unit.
  - Also absent: the bootloader and all MIDI/SysEx behavior, as expected.

  Caveats: (a) the schematic is a copyrighted document too, and it lives in a repo with no license. Extracting facts from it (connections, part numbers) is fine, but do not reproduce its drawing, and confirm whether DJTT also publishes it separately (that provenance is better evidence). (b) The PDF sits beside the firmware source, so it must be copied out to a clean directory by itself; whoever handles it should never browse the surrounding checkout. (c) Completeness is now checked (see above): complete for hardware wiring, silent on anything firmware-defined.
- **Scope the dirty room to DJTT-original code.** LUFA is MIT, and Atmel headers/libraries are permissively licensed; reuse them directly.
- **Archiving.** Hash and timestamp artifacts; log every file read and tool call, not just prompts and completions. Run stage 4 with no web tools, in an empty directory outside this project.
- **This repo's notes may be tainted.** Auto-memory and `.planning/` files (including the native-mode plan) may carry firmware-derived knowledge, e.g. the XT firmware SysEx ring-type idea. Audit them before treating anything as clean, and keep stage 4 out of this repo entirely.
- **Stage 5 needs deterministic tools first** (JPlag, MOSS, or token/AST diffing), then LLM triage of the hits. "No similarity found" from an LLM alone is weak evidence.
- **Extend the stage 5 exclusion** beyond register names to USB descriptors, SysEx/MIDI message formats, and LUFA-mandated structure.
- **Hardware safety.** Test on a sacrificial unit. Leave DJTT's bootloader untouched and write only the application section so a bad flash is recoverable.

### Things the original plan skipped

- **Legal posture.** No license means we cannot copy, but reimplementing functionality is generally permissible (copyright does not protect function or interfaces; Google v. Oracle). A clean room is a defense against an access-plus-similarity claim, not a legal requirement. Check the firmware repo README and the updater's EULA for anti-reverse-engineering clauses, especially if anyone dumps or disassembles the shipping binary.
- **Ask DJTT for a license first.** A small company that published its source may grant MIT or permission for a derived work with one email. Costs nothing, avoids the whole exercise, and is friendlier.
- **Trademark and USB IDs.** Do not ship under the "Midi Fighter Twister" name or reuse DJTT's VID/PID. Decide whether the app needs a compatible product ID for detection.
- **Scope.** A full rewrite (USB, matrix scan, LED driver, encoder handling, SysEx) is far larger than the native-mode profile needs. Check whether a narrower route (small upstream patch, or a documented SysEx extension) delivers ring types and RGB.

## Open decisions

1. Ask DJTT for a license or permission before anything else?
2. Narrow route (patch / extension) versus full reimplementation?
3. Who signs off on the HIS (needs a human, ideally with legal input)?
4. Who runs stage 4 and stage 5 given the human-isolation rule above?
5. Audit `.planning/` and memory notes for firmware-derived content.
6. Can the dirty room be dropped entirely (schematic for hardware, capture and measurement for protocol and timing)? If yes, stages 2, 3 and 5's source access reduce to a post-hoc check only.

## Suggested next step if pursued

Draft the one-page protocol and the HIS template (stage 1 deliverable), then get it reviewed before any stage 2 run.
