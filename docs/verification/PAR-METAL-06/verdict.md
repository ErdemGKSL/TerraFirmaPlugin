# PAR-METAL-06 verification: welding on the anvil

- Date: 2026-11-25. Verdict: **Pending in-game check (container cannot run client).** The container's memory cgroup OOM-killed the
  headless server (4 GB resident) when the client joined in PAR-METAL-05 (same recipe, same machine) while other agents' servers
  were running; no second server and client were started for this item.
- Checked instead: `AnvilWeldMockBukkitTest` (11 tests, all pass) on a copper anvil with fakes of CraftEngine and the heat.
- Steps still to run (`in_game_steps`): place a copper (tier 1) or better anvil, put two hot wrought iron ingots in the input
  slots and flux (`tfc:powder/flux`) in the flux slot, open the screen: the weld button is lit; press it: the main slot holds
  a wrought iron double ingot, the second slot is empty, one flux is gone, sparks and the anvil sound play. Repeat with a
  sneaking click while holding a hammer. A cold ingot gives "Not hot enough to weld!", no flux "There is no flux in the anvil!".
