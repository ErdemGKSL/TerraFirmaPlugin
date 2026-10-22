---
id: PAR-INFRA-05
title: "Item tooltip pipeline: several decorations per item, TFC styles and colours"
area: INFRA
status: blocked
wave: 2
deps: [PAR-INFRA-01]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java"
  - "src/main/java/net/dries007/tfc/client/ClientInventoryTooltip.java"
  - "src/main/java/net/dries007/tfc/config/FoodExpiryTooltipStyle.java"
  - "src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java"
  - "src/main/java/net/dries007/tfc/config/TimeDeltaTooltipStyle.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/tooltip/**"
  - "paper/src/test/java/dev/tfp/paper/tooltip/**"
  - "paper/src/main/java/dev/tfp/paper/packet/ItemDecorationHandler.java"
  - "paper/src/main/java/dev/tfp/paper/packet/ClientItemDecoration.java"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/ingot/copper"
  - "tfp debug heat Bot 900"
  - "Hover the ingot: 'Bright Red٭٭٭٭ - Can Weld' in TFC colour, not italic. The marks are U+066D (TFC's ItemTemperatureDisplayStyle star), drawn small by the vanilla font so they look like '****'; this is TFC's glyph, not a defect. Lazy cooling is real: if the hover is checked more than a few seconds later, run 'tfp debug heat Bot 900' again first"
  - "Wait ~35 s (copper cools 0.28 C/tick, 900 -> ~704 C), keep hovering: 'Dark Red٭٭٭٭ - Can Work' in dark red without re-opening the inventory"
  - "ce item give Bot tfp:food/mutton; tfp debug heat Bot 200; hover: the expiry line ('Expires on: ... (in ...)', dark green) and the heat line together"
  - "gamemode creative Bot; pick the mutton up and put it back; hover again: exactly the same 2 lines, no duplicates"
  - "Carry the heated ingot on the cursor (screenshot) and open a chest holding it (screenshot)"
  - "Open the player inventory, a chest and a crafting table; the server log has no 'Tooltip refresh failed' and no IndexOutOfBoundsException"
plan:
  - "BLOCKED 2026-10-22: rejected after 2 repairs. Code and TFC fidelity were accepted (HeatDisplayStyle colours match Heat.java, U+066D star and 1..4 threshold loop match ItemTemperatureDisplayStyle, weld-before-work suffix matches IHeatView.addTooltipInfo, magenta italic line gone so done criterion 2 is closed, scope/threading/tests/ledger/ARCHITECTURE §10.1 fine). Blocking: done criterion 1 ('two contributors render together in-game without duplicating on creative pick-up') has no screenshot evidence, and docs/verification/PAR-INFRA-05/ (captures + verdict.md, PROTOCOL §6.3) does not exist."
  - "Reviewer next steps 1/3: have the test-runner agent run every in_game_steps entry and commit the PNG crops plus verdict.md (date, HEAD commit, versions, pass/fail per criterion) under docs/verification/PAR-INFRA-05/: heated mutton at 200 C ('ce item give Bot tfp:food/mutton; tfp debug heat Bot 200') showing the dark-green 'Expires on: ... (in ...)' line and the heat line together; 'gamemode creative Bot', pick the mutton up and put it back, before/after hovers with exactly 2 lines and no duplicates; the heated ingot carried on the cursor; a chest window holding the ingot; open the player inventory, a chest and a crafting table, then a server-log excerpt with no 'Tooltip refresh failed' and no IndexOutOfBoundsException."
  - "Reviewer next steps 2/3: pin the 900 C case. Run 'tfp debug heat Bot 900' right before the hover (or read the temperature back at capture time); the hover must show 'Bright Red' + four U+066D marks + ' - Can Weld' (the earlier capture showed two marks and 'Can Work' because the ingot had cooled to ~810-850 C, below the ~864 C weld point). After ~35 s, without reopening the inventory, the same hover must show 'Dark Red' + four marks + ' - Can Work' in dark red (proves TooltipRefresher in game). Retake r-07 (HUD hotbar at gui3); it was captured on the 'Loading terrain...' screen."
  - "Reviewer next steps 3/3: only then set status done, add the final Done plan line, update STATUS.md and the ledger row (drop 'Not yet: the in-game screenshot check', status glue -> playable). Non-blocking: in TooltipRefresher.run() clock.get() is read after capture() but outside its try, so a throwing clock leaves the busy flag set and stops the watch for good; fold the clock read into the captured supplier or release the flag in a finally. The view token System.identityHashCode(view) can collide between objects; acceptable, but note it if view changes are ever missed."
  - "Reopened 2026-10-22 (in-game check rejected): the done criterion 'two contributors in-game without duplicating on creative pick-up' has no screenshot evidence yet, and the cool-down case showed stale text (lore built at send time, never resent). The stale text is fixed by TooltipRefresher (below); status stays open until docs/verification/PAR-INFRA-05/ holds the captures of in_game_steps and verdict.md (PROTOCOL §6)."
  - "Current: `ItemDecorationHandler` applies one heat line to SET_SLOT/WINDOW_ITEMS with a strip on creative return."
  - "Generalise to an ordered list of `TooltipContributor`s (heat, food, size/weight, fluid contents, forging, metal info) contributed by modules; one combined `tfp:lore` marker so the strip removes exactly what we added."
  - "Port TFC's colours: heat level colours per `Heat` enum (not italic, no magenta default), food expiry styles, time-delta formatting."
  - "Cover the remaining packets: container content in other windows, cursor item, merchant, entity equipment if needed."
  - "Done: paper/tooltip (TooltipPipeline with one `tfp:lore` marker, TooltipContributor in TFC's onItemTooltip order, TooltipItem/TooltipData/TooltipStack/TooltipContext, HeatDisplayStyle with TFC's Heat colours and star marks, FoodExpiryStyle, TimeDeltaStyle + timeAndDate, TooltipStyles.TFC_DEFAULTS, HeatTooltipContributor with the work/weld suffix, FoodExpiryContributor, TooltipModule providing TooltipPipeline and taking over the start-up item-decoration handler); paper/packet ItemDecorationHandler (SET_SLOT, WINDOW_ITEMS + carried, SET_CURSOR_ITEM, SET_PLAYER_INVENTORY, MERCHANT_OFFERS results; strip on CREATIVE_INVENTORY_ACTION; PacketStack/NbtData adapters; takeOver). Lang keys tooltip.* (en_us, tr_tr). Tests: TooltipPipelineTest 11, TfcTooltipLinesTest 12, TimeDeltaStyleTest 3, TooltipPacketWiringTest 3. ARCHITECTURE.md §10.1; ledger feature row + 6 divergences. The old magenta italic HeatTooltipDecoration line is no longer sent (UI defect 2); deleting that class and its TfpPlugin lines is left to PAR-HEAT-02 (outside files_owned). Pending: the in-game screenshot check of PROTOCOL §6 (docs/verification/PAR-INFRA-05/: copper ingot at 900 C shows red 'Bright Red٭٭٭٭ - Can Weld', not italic; a heated food shows the expiry and heat lines; creative pick-up and put-back leaves no lore) was not run by this worker (no test-runner agent available)."
  - "Review fix: the 704 C capture ('Bright Red - Can Work', no stars, while /tfp debug heat said DARK_RED) is one internally consistent line for 730-770 C: the stack was decorated when sent and never sent again. The debug command, ItemHeatHooks and the tooltip read the same GlobalClockHolder player ticks and the same HeatState rule (TooltipRefresherMockBukkitTest checks the debug level against the tooltip label and colour at every 10th tick of a 900 C cool-down, and 704 C = 'Dark Red٭٭٭٭ - Can Work' in DARK_RED). New TooltipRefresher (module tooltip): per player, every 20 ticks on the entity scheduler, snapshot the open view's and cursor's heat/food stacks; on the CPU pool compare each stack's rendered lines with the previous run (or, first seen, with its lines one period earlier); on the entity thread re-validate the view and updateInventory() once. Tests: TooltipRefresherTest 6 (730 C boundary, star steps, first sight, view change, cooling out of sight), TooltipRefresherMockBukkitTest 3. Pending: the in-game captures listed in in_game_steps."
  - "Review fix 2 (in-game check rejected 2026-10-22): TooltipRefresher.snapshot threw 'Index 46 out of bounds for length 46' on every player-inventory open. The loop was already i < countSlots(), but on Paper 1.21.11 countSlots() of the player's own crafting view is 5 + PlayerInventory.getSize(), larger than the 46 raw slots of InventoryMenu. The snapshot now reads view.getTopInventory().getContents() and view.getBottomInventory().getContents() (keys: top i, bottom 1000 + i, so off-hand and armour are included) and view.getCursor(); no raw slot is read. The exception also escaped after the in-flight CAS, so the watch stopped for good: the snapshot now runs in capture(), which releases the flag on failure and logs the first failure per player. Test: TooltipRefresherSnapshotTest 2 (stub crafting view with countSlots() > 46: no throw, grid/main/off-hand/cursor captured; failing snapshot releases the flag three times in a row, one log record). Star glyph checked against /opt/tfc ItemTemperatureDisplayStyle: U+066D, same as ours, no divergence. Still pending (item stays open): docs/verification/PAR-INFRA-05/ captures of heated mutton with the expiry and heat lines, creative pick-up and put-back before/after, cursor carry, chest window, and verdict.md."
tests:
  - "paper: NBT-level tests: ordering, idempotent strip, client locale applied"
  - "paper: creative-return strip test with two contributors"
done_criteria:
  - "Two contributors render together in-game without duplicating on creative pick-up (screenshot)"
  - "UI defect 2 of plans/gaps.md (magenta italic heat line) closed"
---

# PAR-INFRA-05: Item tooltip pipeline: several decorations per item, TFC styles and colours

> see_not_parity: NP-CLIENT-04 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
