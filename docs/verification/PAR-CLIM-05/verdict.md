# PAR-CLIM-05 verdict: seasonal grass tint band resolution

Date: 2026-11-18. Server/client: Paper 1.21.11 headless (in-game run pending, see below).

## Decision (owner decision delegated to the manager)

Choice: **(a) more temperature bands**, in its cheapest form.

| Option | Cost | Result |
|---|---|---|
| (a) more temperature bands | +12 variants per biome (16 to 28), 2,000 to 3,500 datapack biomes | June and October at the tint site get different bands; colour still follows TFC's colormap |
| (b) grass-only colour view | A second palette or a per-block tint the vanilla client does not have (grass tint is per biome); would need block-state or resource-pack tricks per grass block | Not cheaper; breaks the "client tints by biome" decision |
| (c) month/phase-based bands | 12 variants per biome if the colour were the month only, but then the colour no longer follows the site's climate (a tropical and a polar chunk would share October's colour); 12 x 16 = 192 per biome if kept per climate | Either loses TFC's climate colour or is 12 times the registry |

Layout (`core/climate/SeasonalColour`): seven temperature sample points every 32 colormap indices (32, 64, ..., 224, about
6.3 C apart) instead of four bands of 64. The four first sample points keep their index and their variant names
`t0..t3`; the new ones are the half steps `t0h`, `t1h`, `t2h`. Band edges lie half-way between sample points except the
edge between `t1h` and `t2`, which is TFC's 0 C index (153), so all bands from `t2` on are at or below 0 C
(`SeasonalColour.isFreezing`, for the client snow look later). Rainfall bands unchanged (edges 128, 179, 217).

## Budget

| | Before | After | Limit |
|---|---|---|---|
| Variants per TFC biome | 16 | 28 | about 32 (2x) |
| Seasonal variant biomes | 2,000 | 3,500 (1.75x) | about 4,000 |
| Server biome registry from our datapack (variants + 125 base) | 2,125 | 3,625 | |
| Client biome registry (with vanilla's biomes) | about 2,190 | about 3,690 | 4,096 keeps the 12-bit global palette |
| Variant JSON on disk (apparent size) | about 0.9 MB | 1.5 MB | |
| Chunk remap | async on the CPU pool | unchanged (band lookup is a loop over 7 edges) | must stay async |

## Expected colours at the tint site (-768, 63, 768)

Readings recorded in `plans/gaps.md` and `docs/porting/tint-investigation.md`: June 14.5 C, October 5.1 C, 186 mm
groundwater in both months (groundwater index 161, rainfall band 1).

| Month | TFC colormap at the reading | Old band (colour) | New band (colour) |
|---|---|---|---|
| June, 14.5 C (index 80) | `#97b355` | `t1r1` `#70a353` | `t1r1` `#70a353` |
| October, 5.1 C (index 127) | `#689f54` | `t1r1` `#70a353` | `t1hr1` `#609c54` |

So June and October now differ, and they differ in TFC's direction: TFC's grass colormap goes from yellow-green to a
darker, less red green as the temperature falls at a fixed groundwater (R 151 to 104), and ours goes from `#70a353`
to `#609c54` (R 112 to 96, G 163 to 156). **Note on the criterion text:** the item's parenthetical "(October
warmer/yellower)" is not what TFC does at this site. TFC's grass colormap has no autumn tone (only the leaves have an
autumn colormap, `foliage_fall.png`); its yellow is the warm and dry corner. October is yellower than June only where
the October groundwater is clearly lower than June's (for example the dry site of `SeasonalBiomeRemapTest`
`dryAutumnStaysYellowThroughLateAutumn`, `#ead159` at index 96 and 197 vs `#c8c359` at 128 and 197 shows that a colder
reading is less yellow at any fixed groundwater). This implementation follows TFC's colormap; the verify stage should
judge "the TFC direction" against the TFC colormap colour at the site's own readings (`/tfp debug chunk -768 768` from
the console, or the climate HUD), which the table above gives for the recorded readings.

## Tests

- `core/src/test/java/dev/tfp/core/climate/SeasonalColourTest.java`: 12 tests (band edges per index, the 0 C edge,
  June/October at the tint site in different bands, sample points inside their bands, stable names).
- `paper/src/test/java/dev/tfp/paper/climate/SeasonalBiomeRemapTest.java`: 14 tests, new
  `juneAndOctoberShowDifferentBandsAtATemperateSite` (same rainfall band, October colder band).
- `tools/datagen` `DatagenTest` (unchanged) still holds: 28 files per biome, `t2r3` centred on index 160 and 236.

## In-game check

Pending: the verify stage runs the item's `in_game_steps` (June then October at (-768, 63, 768), `advance_weather false`,
noon) with a Haiku agent and adds the screenshot crops here. Pass when the October grass differs from June and matches
the TFC colormap direction at the site's readings (see the note above).
