# PAR-PROC-24 verification: CE builds the tools with a tfp:mineable/* rule

- Date: 2026-11-06
- Base commit: 6d00d5c plus the PAR-PROC-24 change (datagen regenerated, `:dist:shadowJar`)
- Server: Paper 1.21.11 build 132, CraftEngine 26.9.2, BetterModel and BetterHud as installed by `spikes/client/server.sh`
  (vanilla world); headless client 1.21.11 as `Bot` (`spikes/client/session.sh`, `PACK_WAIT=0`)

## Steps

1. `./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc` and `./gradlew :dist:shadowJar`.
2. `PACK_PORT=8877 spikes/client/server.sh paper 1.21.11 <run> 25877 <jar>` (fresh server folder), read CraftEngine's load lines.
3. RCON: `datapack list enabled`; `setblock` glass / oak log / hay block and `execute if block ... #tfp:mineable/<tool>`.
4. Join as `Bot`; RCON `ce item give Bot tfp:gem_saw`, `tfp:metal/knife/bronze`, `tfp:metal/hammer/steel`,
   `tfp:metal/scythe/copper`; `data get entity Bot Inventory[{Slot:Nb}]...` for each.

## Results

| Criterion | Result |
|---|---|
| CE loads recipes with 0 issues and no `Failed to load recipes` | **Pass**: `Loaded items in 262.74ms (2971)`, `Loaded recipes in 230.64ms (160)` (115 emitted `tfp:crafting/*` incl. `tfp:crafting/gem_saw` + CE's 45 default-pack recipes; PAR-PROC-02 had 159 with 114), no `Found N issue(s)`, no `Failed to load`, no `Missing tag` |
| `ce item give` of `tfp:gem_saw` and a metal knife works | **Pass**: `Gave 1 tfp:gem_saw to Bot` (iron_pickaxe base, tool rule `{blocks: "#tfp:mineable/glass_saw", speed: 7.3f, correct_for_drops: 1b}`), `tfp:metal/knife/bronze` (iron_hoe, `#tfp:mineable/knife`, 7.3), also `tfp:metal/hammer/steel` (`#tfp:mineable/hammer`, 9.5) and `tfp:metal/scythe/copper` (`#tfp:mineable/scythe`, 5.25) |
| Server tags present | **Pass**: datapack `TerraFirmaPlugin/tfp_seasonal_biomes` enabled; glass is in `#tfp:mineable/glass_saw` and not in `#tfp:mineable/knife`; oak log in `#tfp:mineable/hammer`; hay block in `#tfp:mineable/scythe` |
