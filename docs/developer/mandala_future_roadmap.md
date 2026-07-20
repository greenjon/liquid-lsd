# Mandala Visual Source - Future Feature Roadmap & UX Notes

This document captures design ideas and proposed features for future iterations (v2+) of the Mandala visual source selector and recipe management system in Liquid LSD Desktop.

---

## 1. Visual Recipe Vault / Gallery Popover (Idea #2)

- **Concept**: A visual modal window or popover grid displaying pre-rendered thumbnail snapshots or live GPU micro-previews of all ~300 built-in recipes.
- **Filtering**: Category tabs across the top (`All`, `3 Lobes`, `4 Lobes`, `5 Lobes`, `6 Lobes`, `8+ Lobes`).
- **Interaction**: Clicking any thumbnail instantly updates `Lobes.baseValue` and `Recipe Select.baseValue` to match the target recipe.
- **Challenge / Note**: Each recipe's appearance depends on current arm parameters (`L1`, `L2`, `L3`, `L4`) and rotation/color settings. Micro-previews will need default reference parameters for snapshot rendering.

---

## 2. Geometric Tagging & Humanized Naming (Idea #3)

- **Concept**: Assign human-readable names and geometric style tags/badges to the ~300 recipes instead of displaying raw math vectors like `[26, 23, 14, 14]`.
- **Taxonomy / Tags**:
  - *Starburst*, *Floral / Petal*, *Lattice / Mesh*, *Harmonic Ring*, *Crystalline*, *Spike Ring*.
- **UI Presentation**:
  - Recipe display: `#7: Floral Weave (4 Lobes · [26, 23, 14, 14])`.
  - Enables tag filtering in the visual browser or preset search.

---

## 3. Dual Indexing Modes: Local vs. Global Recipe Index (Idea #4)

- **Concept**: Re-evaluate parameter mapping between local lobe-relative indexing vs. global recipe indexing.
- **Local Indexing (Current)**:
  - `Recipe Select` (0.0..1.0) maps relative to the array of recipes for the *currently active lobe count*.
  - *Pro*: Modulating `Lobes` with an LFO keeps the recipe relative position steady within each lobe count group.
- **Global Indexing**:
  - A separate or toggled parameter (`Global Recipe ID` from `0..300`) that sweeps sequentially through all recipes across all lobe counts.
  - *Pro*: Sweeping an LFO across the global index moves continuously through all 300 recipes in the library.

---

## 4. Favorites & Performance Quick-Slots (Idea #5)

- **Concept**: Performers often rely on 10–20 go-to Mandala recipes during live VJ sets.
- **Quick-Slots**:
  - Add "Star" / Favorite bookmarking to save recipes into quick-recall slots (e.g. `[ Fav 1 ] [ Fav 2 ] [ Fav 3 ] [ Fav 4 ]`).
  - Render quick-recall buttons on the Deck / PatchGrid UI for zero-latency switching during live performances.

---
*Created on 2026-07-20 to track future UI/UX enhancements for Mandala Visual Generator.*
