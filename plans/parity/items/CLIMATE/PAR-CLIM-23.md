---
id: PAR-CLIM-23
title: "Verify vanilla /time and the night skip move the calendar in game on Paper 26.3 and 1.21.11 (PAR-CLIM-22 server logs)"
area: CLIMATE
status: done
wave: 16
deps: [PAR-CLIM-22]
size: S
aliases: ["follow-up of PAR-CLIM-22 (2026-11-14): its server-log criterion needs a delegated in-server run"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java"
files_owned:
  - "docs/verification/PAR-CLIM-23/**"
ui: false
plan:
  - "PAR-CLIM-22 replaced the direct TimeSkipEvent.getSkipReason() call (NoSuchMethodError on the 26.x API) with paper/calendar/TimeSkipReasons and added a listener for the world-less ClockTimeSkipEvent (26.x, paper-global.yml time.affects-all-worlds: true). Its link check on the 26.3 API and tests are in docs/verification/PAR-CLIM-22/verdict.md; the in-server run is this item."
  - "Delegated run (test-runner agent, CLAUDE.md): build :dist:shadowJar, start Paper 26.3 (build 244 or later) and Paper 1.21.11 with CraftEngine/BetterModel/BetterHud as in the PAR-INFRA-23 runs. With one player (or the bot) online: `/tfp time set hour 21`, then vanilla `/time set day` and `/time add 1000`, then sleep in a bed (or `/tfp time set hour 21` and let the bot sleep) so the night skip fires. Record `/tfp time` (or the debug calendar readout) before and after each step and scan the log for `NoSuchMethodError`, `Could not pass event TimeSkipEvent` and `Calendar:` warnings. On 26.3 repeat `/time add 1000` once with `time.affects-all-worlds: true` in paper-global.yml."
  - "Done: in-game run 2026-11-17 on Paper 1.21.11-132 and 26.3-253 with a vanilla client as Bot: vanilla /time set day, /time add 1000 and a bed night skip move the calendar on both (set day: 21:01 to 07:01-07:02 next day; add: +1 h; night skip: to 05:00 next day), with time.affects-all-worlds false and true on 26.3, and no NoSuchMethodError, Could not pass event or Calendar: line in any log; calendar.sleep_skip passes on 1.21.11, errors on 26.3 in the test case itself (TimeSkipEvent$SkipReason, follow-up PAR-CLIM-24); 26.x /time set <marker> answers already at time marker although both moved (follow-up PAR-CLIM-25). Evidence: docs/verification/PAR-CLIM-23/ (verdict.md, runs.txt, log-scan.txt)."
tests:
  - "itest calendar.sleep_skip passes on Paper 26.3 as on 1.21.11"
done_criteria:
  - "A vanilla `/time set`, `/time add` and the night skip on Paper 26.3 and 1.21.11 log no error and move the calendar (forward to the same time of day; the night skip to 5 am), with time.affects-all-worlds false and true on 26.3; log excerpts and verdict in docs/verification/PAR-CLIM-23/"
---

# PAR-CLIM-23: Verify vanilla /time and the night skip move the calendar in game on Paper 26.3 and 1.21.11 (PAR-CLIM-22 server logs)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
