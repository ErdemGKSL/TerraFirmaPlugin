---
id: PAR-HUD-10
title: "Held temperature popup shows TFC's level text, verified in game"
area: HUD
status: open
wave: 6
deps: [PAR-HUD-08, PAR-HUD-09, PAR-HUD-11]
size: S
aliases: ["follow-up of PAR-HUD-08 (after PAR-HUD-09, which also owns HudLayouts.java)", "PAR-HUD-08 in-game reject 2026-10-23 (bare number, no colour, unreadable at GUI scale 2, stale value)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/common/component/heat/Heat.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayoutsTest.java"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClimateValues.java"
  - "integration/betterhud/src/test/java/dev/tfp/integration/betterhud/HudClimateValuesTest.java"
  - "docs/porting/ledger.md"
  - "integration/betterhud/src/main/java/dev/tfp/integration/betterhud/BetterHudIntegration.java"
  - "paper/src/main/java/dev/tfp/paper/TfpPlugin.java"
  - "paper/src/main/java/dev/tfp/paper/integration/HudBridge.java"
  - "paper/src/main/java/dev/tfp/paper/integration/HudSnapshotCache.java"
  - "paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java"
  - "paper/src/test/java/dev/tfp/paper/player/**"
  - "paper/src/test/java/dev/tfp/paper/integration/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 900"
  - "screenshot centre popup at GUI scale 2 and 3 immediately after the command"
  - "tfp debug heat Bot 600, screenshot at once: popup reads Dark Red in DARK_RED, no stale Bright Red"
  - "tfp debug heat Bot 0, screenshot: popup hidden"
plan:
  - "UNBLOCKED (manager, 2026-11-07): the heat-write lag fix files (1)-(3) from the BLOCKED line are now in files_owned (plus paper tests). Publish inline on the player thread, add the per-player HUD push hook, find the blank frame, then redo the screenshot-at-once captures at GUI 2 and 3."
  - "BLOCKED (2026-11-07, review and in-game REJECT): done criterion 2 (no visible lag after `tfp debug heat`) fails in game, and the fix needs files outside files_owned. An independent check found the previous level in 4 of 5 captures 0.1-0.4 s after the RCON reply at GUI 2 and 3 (600 still `Bright Red****`, 0 still `Dark Red`) and one blank frame between values; our own recording put the first changed frame 0.27-0.46 s after the reply. The placeholders are not the cause: HudClimateValuesTest.aPublishedHeatChangeIsTheNextPlaceholderRead shows a published heat write is the very next read (text, colour and condition agree, no tick advance). The delay is upstream: `/tfp debug heat` (TfpPlugin.debugSetHeat) calls PlayerNeedsHudService.refreshHeld, which republishes on the player's NEXT tick, then BetterHud picks it up on its own async update. Needed, outside files_owned: (1) paper/src/main/java/dev/tfp/paper/player/PlayerNeedsHudService.java: publish the held heat inline when already on the player's thread (publishHeld instead of the next-tick hop) for the debug command and every real main-hand heat write, then call a new HUD push hook; (2) paper/src/main/java/dev/tfp/paper/integration/HudBridge.java (or HudSnapshotCache.java) plus integration/betterhud/src/main/java/dev/tfp/integration/betterhud/BetterHudIntegration.java: a per-player `push(uuid)` that calls BetterHud `HudPlayer.update()` at once; (3) paper/src/main/java/dev/tfp/paper/TfpPlugin.java (debugSetHeat) to use it; (4) find the blank frame: BetterHud re-runs the default popup's show every update (HudObjectType.POPUP supplier -> PopupImpl.show), so check whether the popup group is dropped and re-added on a value change (popup `always-check-condition`, `unique`, `key-mapping` in popups/tfp-popups.yml, HudLayouts). Then redo the screenshot-at-once captures at GUI 2 and 3 (first frame after the reply, within about 0.25 s, no blank frame). Widen files_owned to (1)-(3) or split them into a follow-up item."
  - "UNBLOCKED (manager, 2026-11-07): HudClimateValues.java, its test and the ledger are now in files_owned. Map U+066D to ASCII asterisk for the HUD popup only (a glyph BetterHud carries), keep TFC star in item tooltips, add a ledger Divergences row, then re-verify in game that the popup stays centred."
  - "BLOCKED (2026-11-06): the heat stars cannot render in the BetterHud popup without a change outside files_owned. tfp_held_heat (integration/betterhud/src/main/java/dev/tfp/integration/betterhud/HudClimateValues.java, via paper/.../tooltip/HeatDisplayStyle) writes TFC's star U+066D, but no BetterHud text font can carry it: BetterHud 2.2.0-SNAPSHOT-449 builds a ttf/unifont font from Latin plus the `include` subsets only, and none covers U+066D (TextManagerImpl: arab = U+0620-064A and U+0660-0669, hindi adds U+0671-06D3). In game (Paper 1.21.11, GUI 2) the stars drew as missing-glyph boxes at the boss-bar position (top centre) in the level colour and pushed the whole BetterHud line sideways (HUD shifted about 9 GUI px left while stars showed). Needed: widen files_owned to HudClimateValues.java and its test (map U+066D to a glyph BetterHud carries, e.g. ASCII `*`, for the HUD only; ledger divergence), or to a bitmap star glyph made through the image-generation skill (the imagegen MCP was unreachable this session)."
  - "Found while working (keep for the retry): the colour approach works in game. Six text layers in the popup layout (one per TFC Heat colour group: #aaaaaa, #aa0000, #ff5555, #ffaa00, #ffff55, #ffffff), each `color: <hex>` with the element condition `tfp_held_heat_color == '<hex>'` (BetterHud string literal in single quotes), pattern `[tfp_held_heat]`, outline FF000000; `tfp debug heat Bot 600` switched at once to `Dark Red` in DARK_RED, 1200 to `Yellow` in YELLOW, 0 hid the popup. The catalogue needs placeholder rows for tfp_held_heat and tfp_held_heat_color (requireKnown), the popup row names all three placeholders, counts 33 / 14 placeholders. The popup at pixel y -60 covers vanilla's selected-item name (H-59..H-51 in survival, 2 s after a slot change): move it to -70. The BetterHud `arab` include does not help (see above). Note: run :tools:datagen:generateContent and :dist:shadowJar as separate Gradle invocations, or the jar packs the old content; an installed BetterHud change needs `ce reload all` (pack rebuild) before the client sees it."
  - "PAR-HUD-08 publishes tfp_held_temp (number, condition of the popup) and the text placeholders tfp_held_heat (TFC COLOR style: en_us level name + U+066D stars, empty when cold) and tfp_held_heat_color (TFC Heat.getColor as #rrggbb: #aaaaaa GRAY, #aa0000 DARK_RED, #ff5555 RED, #ffaa00 GOLD, #ffff55 YELLOW, #ffffff WHITE; empty when cold). PAR-HUD-11 makes the values lazy at placeholder time. The generated popup layout still draws `[tfp_held_temp]°C` in near-black with a faint outline (unreadable at GUI scale 2)."
  - "Switch the popup layout text in HudLayouts to `[tfp_held_heat]` (keep the `tfp_held_temp > 0` condition) in the level's Heat colour: one text layer per colour group gated on tfp_held_heat_color (or on a level-index number placeholder), or a BetterHud colour override if it can read a placeholder. Use the normal MC text shadow (or a small backing plate) so the text is legible at GUI scale 2. Update the m3-surv-06-held-temp-popup catalogue row."
  - "Rerun datagen, then the in-game screenshot check (PROTOCOL §6) with the steps above; evidence in docs/verification/PAR-HUD-10/."
  - "Done: HudLayouts popup draws [tfp_held_heat] in six text layers (TFC Heat.getColor groups) gated on tfp_held_heat_color, shadow FF000000, pixel y -70; catalogue rows for tfp_held_heat and tfp_held_heat_color (33 elements, 14 placeholders); HudClimateValues draws the star as ASCII * in the HUD only (ledger divergence). Tests: HudLayoutsTest (3 new: colour layers, placement, popup row must name condition/text/colour), HudClimateValuesTest (1 new: ASCII star, same count as the tooltip). In game pass at GUI 2 and 3: docs/verification/PAR-HUD-10/verdict.md. Review fix (2026-11-07): the first capture's GUI 2 900 C crop showed Dark Red**** (a late frame), so the check was recaptured on a fresh server, one scale at a time, strictly sequential, with every RCON reply logged and the screen recorded at 20 fps with wall-clock times; 900 shows Bright Red**** in #ff5555, 600 Dark Red in #aa0000 with no star, 0 no popup, at GUI 2 and 3, the first changed frame 0.27-0.46 s after the command reply. CORRECTED 2026-11-07: that is visible lag, not \"no lag\"; the independent in-game check also saw stale levels and a blank frame, so criterion 2 fails (see the BLOCKED line)."
tests:
  - "HudLayoutsTest: popup text pattern names tfp_held_heat, one colour per TFC Heat colour group, condition unchanged, shadow on"
done_criteria:
  - "Holding a 900 C copper ingot shows `Bright Red٭٭٭٭` in RED (#ff5555) in the popup at GUI scale 2 and 3, legible at GUI scale 2; a cold ingot shows no popup"
  - "The popup changes level text and colour right after `tfp debug heat Bot <t>` with no visible lag"
---

# PAR-HUD-10: Held temperature popup shows TFC's level text, verified in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
