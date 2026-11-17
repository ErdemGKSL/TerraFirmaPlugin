# PAR-HEAT-23 verification

- Date: 2026-11-17
- Build: `./gradlew check` passes with the four firepit model carriers (`daylight_detector[inverted=false,power=0|10|12|14]`, hidden as power 1/11/13/15 for real detectors), appearances `lit_x`, `lit_z`, `unlit_x`, `unlit_z` with TFC's `firepit_lit` / `firepit_unlit` models (z turned y 90).
- In-game steps: pending, run by the verify stage (headless Paper 1.21.11 + client, PROTOCOL 6). Screenshots of the lit and unlit firepit on both axes at GUI scale 2 and 3 and the cooking check go here and to `docs/verification/PAR-HEAT-19/`.
