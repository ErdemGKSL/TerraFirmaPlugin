---
id: PAR-DATA-16
title: "Pistons cannot move water sources: 1-tick piston pulses drop the water of waterlogged blocks (TFC's MC-130183 fix)"
area: DATA
status: open
wave: 12
deps: [PAR-INFRA-10]
size: S
aliases: ["parity audit 2026-11-08: mixin/PistonBlockEntityMixin named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/mixin/PistonBlockEntityMixin.java"
  - "src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/piston/**"
  - "core/src/test/java/dev/tfp/core/piston/**"
  - "paper/src/main/java/dev/tfp/paper/piston/**"
  - "paper/src/test/java/dev/tfp/paper/piston/**"
ui: false
in_game_steps: []
plan:
  - "Current (audit 2026-11-08): nothing handles it. TFC restricts moving water sources (infinite water is limited), and patches PistonMovingBlockEntity so a waterlogged block moved by a piston, also by a 1-tick pulse (finalTick, vanilla bug MC-130183), arrives without its water; the same applies to every TFC fluid-logged block."
  - "First check whether Paper 1.21.11 and 26.3 already fix MC-130183 (Paper has a config for piston behaviour); record the result in the ledger."
  - "If not fixed: on BlockPistonExtendEvent/BlockPistonRetractEvent snapshot the moved waterlogged blocks (vanilla waterlogged and our CE fluid-logged states), decide in core/piston which arrive dry, and after the move apply the dry state through the region commit queue on the owning region (decide async, apply sync); never create a water source at the old or new position."
  - "Ledger: feature row; Divergences row only if a Paper-side limit prevents an exact match."
tests:
  - "core: decision table (waterlogged slab/stairs/fence and a CE fluid-logged block, extend and retract, 1-tick and normal pulse) gives dry arrival"
  - "paper: MockBukkit piston events produce the dry state; itest `piston.no_water_dupe` with a 1-tick pulse"
done_criteria:
  - "No piston pulse moves a water source with a waterlogged block, on Paper and Folia; tests green"
---

# PAR-DATA-16: Pistons cannot move water sources

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
