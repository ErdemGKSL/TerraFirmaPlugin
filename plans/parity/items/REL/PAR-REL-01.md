---
id: PAR-REL-01
title: "Test infrastructure fixes: headless client pack URL, snow-free test site, CE shutdown warning"
area: REL
status: open
wave: 1
deps: []
size: S
aliases: ["gaps: test client pack 403/127.0.0.1", "gaps: snow cover test surface", "gaps: CE getEntities on shutdown", "gaps: headless client CE mapping check"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/SelfTests.java"
files_owned:
  - "spikes/client/**"
  - "spikes/run-matrix.sh"
  - "docs/testing/headless.md"
ui: false
in_game_steps: []
plan:
  - "spikes/client/server.sh sets CraftEngine self-host ip 127.0.0.1 and port 8163 before the first join; accept the pack prompt within seconds (xdotool Continue/Proceed)."
  - "A reusable snow-free indoor test room command sequence for colour measurements."
  - "Confirm whether CE's 'Asynchronous Chunk getEntities' at shutdown also happens without our jar; file upstream or fix."
tests:
  - "A run of spikes/client with the pack applied on first join"
done_criteria:
  - "docs/testing/headless.md describes the reliable recipe"
---

# PAR-REL-01: Test infrastructure fixes: headless client pack URL, snow-free test site, CE shutdown warning

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
