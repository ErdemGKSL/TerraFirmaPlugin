# PAR-METAL-05 verification: anvil forging GUI

- Date: 2026-11-25. Code under test: the PAR-METAL-05 commit on a clean worktree of origin HEAD (jar from `:dist:shadowJar`
  after `generateContent`, with two local, uncommitted workarounds in datagen for unrelated upstream breakages: the
  groundcover generator (PAR-GATHER-12, blocked) and a missing `RenderedSound` mapping).
- Verdict: **Pending in-game check (container cannot run client).** The headless Paper 1.21.11 server (CraftEngine 26.9.2,
  BetterModel, BetterHud, PacketEvents) started and primed twice, but the container's memory cgroup OOM-killed the server
  (4 GB resident) each time the client joined (`dmesg`: `Memory cgroup out of memory: Killed process ... (java)`), while other
  agents' servers and clients were running. No in-game screenshot exists.

## What was checked instead

- `01-generated-background.png`: the generated `tfp:gui/anvil` (176 x 204) over magenta, looked at: TFC's panel with the second
  input, flux and main input frames and the arrows on the left column (chest columns 1), the dark display with its three step
  slots and the eight red and green step buttons with TFC's icons at chest columns 3 to 6 rows 2 and 3, the hammer frame on the
  right (column 8), the progress bar at TFC's position and the player inventory on the 5-row chest's rows. PASS.
- Unit and MockBukkit tests (see the ledger row): slot positions on the chest grid, frames of every state, the step rules,
  hammer damage, overwork, finishing with the perfect and a lower bonus, the commit and overflow handling, plan button and
  plain click.

## Steps still to run in game (`in_game_steps`)

1. `ce item give Bot tfp:metal/anvil/copper`, place it, sneak-place a hot copper double ingot, hold a hammer.
2. Plain click: the forging screen opens (GUI scale 2 and 3), buttons aligned on the slots, no art offset.
3. Plan button: choose "pickaxe head"; the forging screen returns with the target pointer at `11 + 74`.
4. Click steps: the progress marker moves, the last-step icons and the rule overlays (red, green) change, the hammer loses
   durability; finish the plan with the rules met: the item becomes the pickaxe head with a "Perfectly/Expertly Forged" style bonus.
