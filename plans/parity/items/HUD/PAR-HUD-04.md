---
id: PAR-HUD-04
title: "Two-layer HUD in-game verification at GUI scale 2 and 3 (full, low, submerged)"
area: HUD
status: open
wave: 3
deps: []
size: S
aliases: ["hud-layered-redesign §6", "gaps: M3-SURV-06 two-layer check"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/IngameOverlays.java"
files_owned:
  - "docs/hud/screenshots/**"
  - "docs/hud/catalogue.md"
  - "tools/datagen/src/main/java/dev/tfp/datagen/HudLayouts.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/HudLayouts*"
ui: true
in_game_steps:
  - "gamemode survival Bot"
  - "effect clear Bot; effect give Bot minecraft:saturation 5 10; tfp debug thirst Bot 100 (full)"
  - "damage Bot 16; tfp debug thirst Bot 10; tfp debug hunger Bot 4 (low)"
  - "execute at Bot run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:water; wait 5 s (submerged)"
  - "Repeat at GUI scale 2 and 3; capture hotbar region and top-left corner"
plan:
  - "Run the plan §6 checklist through a cheap-model capture agent: GUI scale 2 and 3, states full / low / submerged (RCON recipes in plans/gaps.md UI-defects entry, now with `tfp debug hunger`)."
  - "At 8x crops check: the fill never covers the frame, the empty channel shows at 0%, layer order 1/2/3, vertical trim compensation, air bar only below full air directly above thirst and red below 60, `offset: left` puts x at the canvas left edge."
  - "If a layer is off, apply the fallback of plan §1 (split = interior width or sequence frames) in HudLayouts.java."
tests:
  - "Screenshots committed under docs/hud/screenshots with a short verdict file"
done_criteria:
  - "Verdict 'pass' for every check at both scales, judged by the main (Opus) session"
---

# PAR-HUD-04: Two-layer HUD in-game verification at GUI scale 2 and 3 (full, low, submerged)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
