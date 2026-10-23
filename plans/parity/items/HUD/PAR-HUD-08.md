---
id: PAR-HUD-08
title: "Held item temperature placeholders with real values (popup layout and in-game check in PAR-HUD-10)"
area: HUD
status: done
wave: 4
deps: []
size: S
aliases: ["gaps: tfp_held_temp returns 0"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/Heat.java"
files_owned:
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClimateValues.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
ui: false
in_game_steps: []
plan:
  - "Unblocked 2026-10-23: the only blocking note was the unverified release gate. Root cause: the reviewers' worktree had no content/generated (share-build.sh, PROTOCOL 0.4, was not run, and generateContent was denied), so :content:jar had no id_map.json/pack.yml and :dist:shadowJar failed; nothing in this item's code. With tools/worktree/share-build.sh linking the shared datagen output the gate passes (see the Done line). Visual points (uncoloured numeric popup, legibility, stale value after /tfp debug heat) are out of this item's files and stay deferred per PROTOCOL 6.5: deferred check tracked in PAR-HUD-10 (layout, colour, in-game screenshots) and PAR-HUD-11 (lazy evaluation); both depend on this item, not the other way round, so there is no dep cycle."
  - "Reviewer notes, not blocking: add a HudClimateValuesTest case at an exact level boundary (80.0 C gives Hot with no stars; TFC uses t < max and a strict star test); add a test that fires a cancelled InventoryClickEvent through server.getPluginManager().callEvent and checks nothing is published (ignoreCancelled = true is untested); move HOTBAR_0_RAW to the top of PlayerNeedsHudRefreshMockBukkitTest next to the other constants; restate the Done line's test counts relative to b389f82."
  - "Handover to PAR-HUD-10: the popup still draws an uncoloured [tfp_held_temp]°C; TFC's default COLOR style never shows a number, so switch the layout to tfp_held_heat coloured by tfp_held_heat_color, or record the numeric popup as its own divergence."
  - "Rescoped after the 2026-10-23 review (reject of b389f82): this item is the values side only, checkable by unit tests. The popup layout (level text in the Heat colour, legible at GUI scale 2) lives in HudLayouts, owned by PAR-HUD-09, so it and the in-game screenshot check are PAR-HUD-10. Evaluating the stored heat record at placeholder time (no lag after /tfp debug heat or while cooling), the debug-command refresh and injecting the heat hooks need HudSnapshot, HudValues and the plugin wiring, none of them owned here: PAR-HUD-11."
  - "Publish the held item's current temperature (ItemHeatHooks, lazy) into HudSnapshot on the needs reading and right after every change of the main-hand stack; popup condition tfp_held_temp > 0 as TFC's format (positive temperatures only)."
  - "Done: PlayerNeedsHudService publishes the main-hand stack's temperature (ItemHeatHooks.temperature, lazy; > 0 or 0) on every reading and, through refreshHeld (a heat-only publish on the player's next tick), after a hotbar slot change, swap hands, inventory click or drag, pickup and drop. Paper fires PlayerItemHeldEvent before the slot changes; the next-tick task reads the new slot (MockBukkit test). HudClimateValues: tfp_held_heat is built by HeatDisplayStyle.COLOR.formatColored with the en_us lang (no duplicated level/star code, no title-casing), new tfp_held_heat_color gives TFC Heat.getColor as #rrggbb, both empty exactly where HeatLevel.of is empty. Tests: HudClimateValuesTest (7, +3), PlayerNeedsHudRefreshMockBukkitTest (8, +4: hotbar, swap and drop, inventory click and drag, pickup; a non-player clicker or picker publishes nothing)."
  - "Done (release gate, 2026-10-23, on f3fbbbb with share-build.sh): ./gradlew :core:test :paper:test :integration:betterhud:test :tools:datagen:test checkLicenseHeaders green (core 2425, paper 491, betterhud 17, datagen 239, 0 failures); :dist:shadowJar green (dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar). No code change was needed. Reviewer notes above stay open as non-blocking test nits (boundary 80.0 C: checked by reading, HeatLevel.of and HeatDisplayStyle.stars match TFC Heat.getHeat t < max and the strict star test; ignoreCancelled = true is set on all six republish handlers)."
tests:
  - "integration/betterhud: tfp_held_heat (COLOR text, en_us lang names, empty exactly where HeatLevel.of is empty, 1550 C without stars) and tfp_held_heat_color (TFC Heat.getColor per level group)"
  - "paper: MockBukkit hotbar change publishes the new slot's temperature on the next tick; swap hands, drop, inventory click, inventory drag and pickup republish; a non-player clicker or picker does not"
done_criteria:
  - "tfp_held_heat for a 900 C copper ingot is `Bright Red٭٭٭٭` and tfp_held_heat_color is #ff5555 (TFC RED); both are empty for a cold item"
  - "A hotbar change, hand swap, inventory click or drag, pickup or drop republishes tfp_held_temp on the player's next tick (unit tests)"
  - "Popup layout, colour, GUI-scale-2 legibility and the in-game check: deferred check tracked in PAR-HUD-10; lazy evaluation at placeholder time: deferred check tracked in PAR-HUD-11"
---

# PAR-HUD-08: Held item temperature placeholders with real values (popup layout and in-game check in PAR-HUD-10)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
