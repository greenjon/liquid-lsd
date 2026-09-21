# Shaders License Audit Log

This document tracks the licensing, authorship, and commercial distribution rights for all Interactive Shader Format (ISF) visual shaders, filters, and generators bundled with **Liquid LSD**.

---

## Licensing Policy
To preserve the right to commercially distribute or monetize Liquid LSD:
1. **Permissive Licenses Only**: All bundled shaders must use permissive licenses (MIT, BSD-2/3-Clause, Apache 2.0, CC0 / Public Domain) or be original cleanroom implementations.
2. **Strictly Prohibited**: No shaders under copyleft (GPL/LGPL/AGPL), non-commercial licenses (CC-BY-NC), or all-rights-reserved copyright may be bundled.
3. **Attribution Preservation**: Copyright notices and original author attributions are preserved in the shader header metadata (`CREDIT`).

---

## Bundled Image Filters (FX)

| Filter Name | File Path | Origin / Credit | License | Commercial Viability | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Kaleidoscope** | `default_filters/kaleidoscope.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Multi-axis polyhedral folding with aspect-ratio preservation and mirror boundary reflection. |
| **Radial Blur** | `default_filters/radial_blur.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Stochastic dithered multi-tap radial zoom blur eliminating concentric stepping rings. |
| **RGB Split** | `default_filters/rgb_split.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Dual-mode chromatic aberration: 3-tap discrete RGB glitch and 12-tap spectral lens dispersion. |
| **Polar Tunnel** | `default_filters/polar_tunnel.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Aspect-preserving logarithmic polar coordinate tunnel with spiral twist and singularity depth fog. |
| **Color Levels** | `default_filters/color_levels.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Perceptual Oklab color grading (Oklab by Björn Ottosson, Public Domain) with ACES filmic rolloff and dither. |
| **Gradient Map** | `default_filters/gradient_map.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Multi-stop perceptual gradient duotone colorizer with cosine palettes (Inigo Quilez, MIT/Public Domain) and phase cycling. |
| **Directional Blur** | `default_filters/directional_blur.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Stochastic dithered motion blur with angle, exponential decay, and streak diffusion. |
| **Wave Displace** | `default_filters/wave_displace.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Dual-mode fluid displacement: Cartesian cross-waves and concentric circular droplet ripples with mirror boundary wrap. |
| **Pixelate** | `default_filters/pixelate.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Multi-lattice aspect-preserving pixelation (Square, Diamond, Hexagonal Honeycomb) with retro color depth quantization. |
| **Retro CRT** | `default_filters/retro_crt.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Analog CRT simulation with barrel tube curvature, RGB phosphor triad sub-pixel mask, and scanline raster. |
| **Pinch Bulge** | `default_filters/pinch_bulge.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Aspect-preserving spherical lens deformation with cubic Hermite smoothstep falloff (Bulge vs Pinch). |
| **Neon Edge** | `default_filters/neon_edge.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Directional Sobel edge detection with angle-to-hue glowing neon palettes (Cyber Rainbow, Cyan/Pink, Acid Green, Flame). |
| **Luma Displace** | `default_filters/luma_displace.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | 2D surface normal gradient refraction simulating liquid marbling and melting glass with chromatic dispersion. |
| **Video Strobe** | `default_filters/video_strobe.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Multi-mode rhythmic strobe and persistent freeze gate with beat flash modes and duty cycle modulation. |
| **Fluid Smear** | `default_filters/fluid_smear.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Persistent 2-pass fluid curl-noise advection simulating organic liquid melting and viscous paint smearing. |
| **Thermal Scanner** | `default_filters/thermal_scanner.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Tactical FLIR thermal camera and military night vision optics with heat bloom, sensor grain, and optic vignette. |
| **Mirror Sphere** | `default_filters/mirror_sphere.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Raytraced 3D chrome mirror sphere reflecting the visual environment with surface normals, Fresnel rim glow, and specular glints. |
| **Halftone** | `default_filters/halftone.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Authentic 4-plate CMYK lithographic screen angles with anti-aliased dot rosettes, monochrome halftone, and paper texture. |
| **Anamorphic Streak** | `default_filters/anamorphic_streak.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Cinematic anamorphic horizontal exponential glare streak with chromatic dispersion and starburst cross-flare. |
| **VHS Glitch** | `default_filters/vhs_glitch.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Authentic magnetic VHS videotape degradation with tracking jitter, head-switching bar, Y/C chrominance delay, and RF static. |
| **Vortex Swirl** | `default_filters/vortex_swirl.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Logarithmic gravitational accretion vortex swirl with aspect-ratio preservation, smooth Hermite falloff, and angular dispersion. |
| **Faceted Glass** | `default_filters/faceted_glass.fs` | Liquid LSD Engine (Cleanroom) | MIT | Approved | Dynamic cellular Voronoi crystal gem facets with 3D prism refraction, surface normal glints, and bevel highlights. |
| **3D Elevation** | `default_filters/3d_elevation.fs` | Liquid LSD Engine | MIT | Approved | Displaces image vertices into a 3D isometric plane with pitch/yaw/roll camera rotation. |
| **Bloom** | `default_filters/bloom.fs` | Liquid LSD Engine | MIT | Approved | Multi-pass bright-threshold isolation with separable horizontal/vertical blur passes. |
| **Feedback** | `default_filters/feedback.fs` | Liquid LSD Engine | MIT | Approved | Multi-pass persistent feedback loop with transform, zoom, and rotation. |
| **Invert** | `default_filters/invert.fs` | Liquid LSD Engine | MIT | Approved | Fast color inversion filter. |
| **Luma Key** | `default_filters/luma_key.fs` | Liquid LSD Engine | MIT | Approved | Luminance-based alpha masking filter. |

---

## Bundled Visual Sources (Generators)

| Source Name | File Path | Origin / Credit | License | Commercial Viability | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Dynamic Spiral** | `library/sources/dynamic_spiral/dynamic_spiral.fs` | Liquid LSD Engine | MIT | Approved | Procedural phyllotaxis and logarithmic spiral generator with audio modulation. |
| **Icosa H3** | `library/sources/icosa_h3/icosa_h3.fs` | Liquid LSD Engine | MIT | Approved | Raymarched hyperbolic icosahedral geometry. |
| **Mandala** | `library/sources/mandala/mandala.fs` | Liquid LSD Engine | MIT | Approved | Procedural rotating kaleidoscopic mandala generator. |
