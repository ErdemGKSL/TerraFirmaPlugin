---
id: PAR-REL-01
title: "Test infrastructure fixes: headless client pack URL, snow-free test site, CE shutdown warning"
area: REL
status: done
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
  - "Done: server.sh seeds CraftEngine config.yml from its jar and sets self-host 127.0.0.1, PACK_PORT (8163) and one_time_token false before the first start, checks ports, TFP_WORLD=1 writes the generator; session.sh waits for the pack (Tab+Return fallback); stop.sh stops through RCON after startup; testroom.sh builds the barrier-roofed snow-free site; run-matrix.sh counts the CraftEngine early-stop lines apart. Run 2026-10-22 (Paper 1.21.11): pack applied 3 s after the first join; room snow-free in January at -17.8 C; the getEntities error reproduced without our jar only when stopped during startup (CraftEngine bug, upstream text in docs/testing/headless.md)."
tests:
  - "A run of spikes/client with the pack applied on first join"
done_criteria:
  - "docs/testing/headless.md describes the reliable recipe"
---

# PAR-REL-01: Test infrastructure fixes: headless client pack URL, snow-free test site, CE shutdown warning

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
