# Midi Fighter Twister: LED / ring MIDI notes

Source: DJ TechTools "Midi Fighter Twister - User Guide 2026" (PDF in the project root, git-ignored; "Setting RGB /
Indicator Segment Animation State", p. 4-6, and the encoder "Indicator" setting, p. 14). An earlier pasted summary of
this was wrong in several places and has been replaced by this page. MIDI channels below are 1-16.

## Messages (CC or Note On, same number as the encoder, default bank 1 = 0-15)
| Target | Channel | Value | Effect |
|---|---|---|---|
| Ring position | 1 | 0-127 | the encoder's value, shown on the 11-LED ring |
| RGB segment colour | 2 | 0 = inactive colour, 127 = active colour, 1-126 = colour scale | colour of the 6 o'clock RGB segment |
| RGB strobe / pulse / brightness / rainbow | **3** | 1-8 strobe, 9-16 pulse, 17-47 brightness, 127 rainbow | animates or dims the RGB segment |
| Indicator (ring) strobe / pulse / brightness | **6** | 49-56 strobe, 57-64 pulse, 65-95 brightness | animates or dims the ring |

- Colour and animation combine: send the colour on ch2 first, then the animation.
- Strobe/pulse rate follows incoming MIDI clock; with none, it assumes 120 BPM.
- Channels follow the encoder's configured channel/number, so changing those in the Utility moves the feedback too.
- Our measured side buttons are on ch4, which does not conflict with any of the above.

## Ring style
Set per encoder in the Utility (not by MIDI): **Dot** (one LED), **Bar** (bar graph), **Blended Bar** (leading LED varies in
brightness), **Spread** (from the middle outwards). Our `.mfs` currently only sets the encoder mode (relative) and
switch/LED defaults; the indicator type is whatever the Utility default is.

## What this means for the send knobs (Library / Pair view, knobs 9-13)
Wanted: receptive = whole ring lit at 50% brightness; not receptive = off.
- Make the ring full: ring position 127 on a **Bar** ring (a Dot ring would show a single LED).
- Dim it to 50%: indicator brightness on ch6; 65-95 is the range, so about 80 for half.
- Off: ring position 0 (and/or brightness 65).
- Brightness must be reset (to 95, full) for every other knob and when leaving browse, otherwise it sticks.
- Today (`PerformSurface.sendLight`) a receptive send knob is ring position 0.5 and a non-receptive one is dark.
- Pulse (ch6 57-64) on the receptive send knobs is also available if "special" should be animated.

## Open questions
1. Is indicator type per knob in the `.mfs`? Bar for knobs 9-13 would also change how those knobs look when used as performance knobs.
2. Is the ch6 brightness value sticky across bank changes? (Test on hardware.)
3. Colour value 127 is documented as "active colour", while the app has been treating it as white; check that against the `.mfs` active colour.

## Implemented 2026-10-08 (works on hardware)
A first test went wrong (dot rings, knobs not matching the screen, RGB flicker), but the cause was the new code running against an old custom Twister profile saved in Preferences, not the ch6 writes. With the built-in profile it is fine. After changing the profile schema, delete the saved Twister profile in Preferences so the default reloads.

`KnobFeedbackDef.indicatorChannel` (Twister: 5), `KnobLight.ringBrightness`; `PerformSurface.sendLight` = ring 127 at brightness 0.5 (-> 80). Ring style stays the Utility's.

## To do (remaining)
1. Add a ring-brightness output to `ControllerFeedback` (profile field for the indicator channel, default 6).
2. Light the send knobs as above and restore full brightness elsewhere.
3. Set the indicator type in `midi-fighter-twister.mfs` if Bar is needed.
