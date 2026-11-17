---
id: PAR-INFRA-55
title: "Installer: rebuild the CraftEngine pack on the restart that applies a raised serverside-blocks (the first-install pack misses the blocks that could not allocate)"
area: INFRA
status: done
wave: 15
deps: [PAR-INFRA-54]
size: S
aliases: ["found by PAR-PROC-37's in-game re-run (docs/verification/PAR-PROC-10/verdict.md, 'Stale pack')"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/install/Installer.java"
  - "paper/src/main/java/dev/tfp/paper/install/PackRebuildTrigger.java"
  - "paper/src/test/java/dev/tfp/paper/install/InstallerTest.java"
ui: true
in_game_steps:
  - "Fresh run dir, TFP_WORLD=1, spikes/client/server.sh paper 1.21.11: the priming start raises block.serverside-blocks and asks for a restart; restart without any manual CraftEngine reload"
  - "Check the hosted pack (plugins/CraftEngine/cache/hosted/default/resource_pack.zip) holds assets/minecraft/blockstates/iron_trapdoor.json and campfire.json (the log pile carriers) and the server log of the restart shows a pack rebuild"
  - "Join with the headless client, sneak-place one oak log on stone: only TFC's one-log model shows, no vanilla iron trapdoor"
plan:
  - "Seen 2026-11-14 (PAR-PROC-37 re-run): the priming start builds the CraftEngine pack while serverside-blocks is still 2000, so CraftEngine refuses tfp:log_pile, tfp:burning_log_pile and tfp:charcoal_pile ('Cannot allocate enough real block states') and their blockstates and models are missing from the pack. The restart allocates them, but our content fingerprint did not change, so the installer asks for no rebuild and CraftEngine serves the cached first-start pack: a one-log pile shows a vanilla iron trapdoor until `ce reload all`. Any first install on a server whose serverside-blocks must grow hits this."
  - "Fix: when the installer raises serverside-blocks (or any other setting that only takes effect after a restart and changes which blocks CraftEngine loads), record that the next start must rebuild the pack (e.g. clear the last-build fingerprint PackBuild keeps, or fold the applied serverside-blocks value into the fingerprint), so the restart rebuilds once."
  - "Done: Installer writes install/pack-rebuild-after-restart.txt when its edit raises CraftEngine block.serverside-blocks; PackRebuildTrigger does not record that start's build; the next start plans one rebuild from the marker (content unchanged) and recordPackBuilt removes it. Tests: InstallerTest +3 (raise -> one rebuild on restart -> none after; no raise -> none; failed rebuild retried), ./gradlew check green. In-game steps left to the verify stage (evidence: docs/verification/PAR-INFRA-55/)."
  - "Repair 2026-11-17: the review's red check was OreOverlayServiceTest.blockChangeFastPathRidesWithThePacketAndThePatchAddsNothing, outside files_owned and unrelated to the installer; it passes alone (3 reruns) and in a second full ./gradlew check, so it is flaky; follow-up PAR-INFRA-59. In-game steps passed: docs/verification/PAR-INFRA-55/verdict.md."
tests:
  - "paper: InstallerTest (a start that raises serverside-blocks makes the next start ask for one rebuild; a start that changes nothing asks for none)"
done_criteria:
  - "After the priming start and one restart, with no manual reload, the served pack draws the log pile, burning pile and charcoal pile (in-game steps above)"
---

# PAR-INFRA-55: Installer: rebuild the CraftEngine pack on the restart that applies a raised serverside-blocks (the first-install pack misses the blocks that could not allocate)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
