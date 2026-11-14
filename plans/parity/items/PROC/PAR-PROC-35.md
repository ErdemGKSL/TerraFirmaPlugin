---
id: PAR-PROC-35
title: "Barrel wiring: the block opens the barrel screen, fluid surface visual, lazy rain fill, barrel rack and tap, in-game check"
area: PROC
status: open
wave: 13
deps: [PAR-PROC-09, PAR-PROC-32, PAR-CLIM-02]
size: L
aliases: ["follow-up of PAR-PROC-09 (the device, device-service, core and barrel datagen files it needs are outside its files_owned; PROTOCOL 3a + 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/render/blockentity/BarrelBlockEntityRenderer.java"
  - "src/main/resources/assets/tfc/blockstates/barrel_rack.json"
  - "src/main/resources/assets/tfc/models/block/barrel_rack.json"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelDevice.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelModule.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelScreenLink.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/PaperBarrelEffects.java"
  - "paper/src/main/java/dev/tfp/paper/barrel/BarrelEffects.java"
  - "paper/src/test/java/dev/tfp/paper/barrel/**"
  - "paper/src/main/java/dev/tfp/paper/device/DeviceService.java"
  - "paper/src/test/java/dev/tfp/paper/device/DeviceServiceEditMockBukkitTest.java"
  - "core/src/main/java/dev/tfp/core/barrel/**"
  - "core/src/test/java/dev/tfp/core/barrel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrelrack/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrelrack/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "content/generated/content/craftengine/tfp/configuration/barrel/**"
  - "docs/verification/PAR-PROC-35/**"
ui: true
in_game_steps:
  - "tfp debug barrelgui 5000 (the screen alone): hover the tank; press the seal button; screenshot sealed (the tank shots are PAR-PROC-09's)"
  - "tfp debug place tfp:wood/barrel/oak"
  - "fill half (5 water buckets); right-click; screenshot GUI and block"
  - "check that each water bucket reached the record (PAR-PROC-08 transfer path; the PAR-PROC-09 run saw an empty bucket and nothing drawn): read the tank with the debug fill/inspect path below, not /tfp debug barrelgui (a demo barrel)"
  - "weather rain; leave the barrel unsealed under the sky for 400 ticks; the tank gains 100 mB"
  - "place tfp:barrel_rack, then a barrel on it (sideways); screenshot; the tap drains into a container below"
plan:
  - "Deferred from PAR-PROC-09 (PROTOCOL 3a + 6.5). PAR-PROC-09 built the screen and the numbers: `paper/barrel/gui/` (`BarrelScreens` service from `BarrelGuiModule`, `BarrelLink`, `BarrelSnapshot`, `BarrelTank`, `BarrelScreenLayout`, `BarrelFluidVisuals`, `DemoBarrelLink` behind `/tfp debug barrelgui`) and the art in `datagen/barrelrack/BarrelScreenArt` (`configuration/barrel_gui.yml`, the tank item model `tfp:gui/barrel_tank`). Nothing opens it from the block yet."
  - "1. Open the screen: in `BarrelDevice.useWithItem`, after the sneak seal toggle and the fluid-container transfer, open `BarrelScreens` (TFC opens the menu) instead of PAR-PROC-08's insert/extract placeholder; title = the barrel block's name. `BarrelScreenLink` implements `BarrelLink` over the device record: snapshot = caught-up record; commit = write the three slots + `applyInstant`; toggleSeal = the existing `toggleSeal`; acceptsContainer = `BarrelItemTanks.tank(stack).isPresent()`; acceptsItem = `ItemSizes.fitsBarrel`; sealedLines = the recipe name and TFC's `getExactTimeAndDate(sealedTick)`. The record lives on the block's region, the screen on the viewer's thread: add a `DeviceService` edit call (load, catch up, apply a function, save) and hop with `Scheduler.region` when the viewer's thread does not own the block (Folia), refusing the click otherwise. Call `BarrelScreens.edit/reload` around every other writer of the slots (sneak seal, bucket transfer, removal). Remove the ledger divergence row about the placeholder."
  - "2. Fluid IO slots (TFC `updateFluidIOSlots` every 5 ticks): settle on every access (catch-up and screen commit) with `BarrelFluidTransfer`, so a bucket in the fluid-in slot empties into the tank and the result lands in fluid-out."
  - "3. Rain fill: `BarrelFluidVisuals.rainChecks` + `rainFilled` (TFC: 1 mB water per 4 game ticks, unsealed, upright, raining at the block above). Lazy: the catch-up credits the checks of the interval only while `PrecipitationService.isRainingAt(above)` holds at both ends (record the divergence) or keep a rain-start tick in the record; no ticking."
  - "4. Fluid surface visual: an `entity_renderer` element per barrel (the upright CE block, configuration/barrel) at `BarrelFluidVisuals.surface(...)` height, tinted with the fluid's colour through `tint_source` (CE component-copy trick: `craftengine-gui-furniture` skill), updated by `PaperBarrelEffects` when the tank changes (the in-world equivalent of `BarrelBlockEntityRenderer`)."
  - "5. Barrel rack: `tfp:barrel_rack` CE block (TFC `BarrelRackBlock`: bottom-supported, RACK_SHAPE hitbox demand) from `datagen/barrelrack`, and the barrel's `facing` (up or a side) and `rack` states with the hitbox rows `tfc:wood/barrel[axis=x|z,rack=false|true]`; placing a barrel on a rack lays it sideways facing the player; breaking returns the rack. A sideways unsealed barrel pours 1 mB per tick through its tap into the fluid handler below the tap (TFC `tickPouring`), lazily."
  - "5b. A debug path for a placed barrel: `/tfp debug barrel fill <mB> [fluid]` (and an inspect line) on the barrel the player looks at, so the surface can be shown without buckets."
  - "6. In-game steps above (cheap-model agent, the main session judges): store crops and verdict.md in docs/verification/PAR-PROC-35/."
tests:
  - "paper: BarrelScreenLink commit / seal / instant recipe on the real recipe book (MockBukkit), the screen opened by a right click, busy refusal, edit+reload around the sneak seal"
  - "core or paper: rain fill lazy vs stepped over random rain intervals"
  - "datagen: barrel rack entry, the facing/rack states and their carriers pinned in GeneratedCarrierTest"
done_criteria:
  - "Screenshot of the barrel GUI opened from the block and a half-full barrel showing liquid (moved from PAR-PROC-09); the bucket water reaches the tank"
  - "Rain fills an open barrel; a racked barrel pours from its tap"
---

# PAR-PROC-35: Barrel wiring: the block opens the barrel screen, fluid surface visual, lazy rain fill, barrel rack and tap, in-game check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
