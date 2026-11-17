---
id: PAR-HEAT-31
title: "Firebox block loaded in game (full-cube pool, copper sounds, in-game screen check), stove and stove pot"
area: HEAT
status: open
wave: 19
deps: [PAR-HEAT-23, PAR-HEAT-25, PAR-INFRA-13]
size: M
aliases: ["follow-up of PAR-HEAT-12 (the firebox CE block needs a FULL_CUBE_POOLS declaration in HitboxDemands, the stove and stove pot need hitbox demands and the firepit/pot device kinds, all outside its files_owned; PROTOCOL 3 + 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/StoveBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/StovePotBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/FireboxScreen.java"
  - "src/main/resources/assets/tfc/blockstates/stove.json"
  - "src/main/resources/assets/tfc/blockstates/stove_pot.json"
files_owned:
  - "core/src/main/java/dev/tfp/core/stove/**"
  - "core/src/test/java/dev/tfp/core/stove/**"
  - "paper/src/main/java/dev/tfp/paper/stove/**"
  - "paper/src/test/java/dev/tfp/paper/stove/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/stove/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/stove/**"
  - "content/generated/content/craftengine/tfp/configuration/stove/**"
  - "content/generated/content/craftengine/tfp/configuration/firebox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSound.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rendered/RenderedSound.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/firebox/FireboxBlock.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/firebox/FireboxBlockTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/coverage/BlockCoverage.java"
  - "docs/verification/PAR-HEAT-12/**"
  - "docs/verification/PAR-HEAT-31/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:firebox[lit=true]; right-click; screenshot the firebox screen (GUI scale 2 and 3): 16 slots on chest columns 3-6, TFC's thermometer on the left; put 4 coal in (click and shift-click), check one item per slot and the cascade to the top-left slots; light it with a firestarter and hover the thermometer column (temperature) and the row under the slots (status line); evidence to docs/verification/PAR-HEAT-12/"
  - "break the firebox in survival: TFC's copper break sound plays and the firebox drops"
  - "tfp debug place tfp:stove[lit=false,facing=north] on stone; put two logs in (right click) and light it with a firestarter; screenshot the lit stove; right-click with a pot item: it becomes tfp:stove_pot; screenshot; sneak right-click with an empty hand: the pot comes back and it is a stove again"
  - "right-click the stove with a wrought iron grill: nothing happens (TFC: no grill on a stove)"
done_criteria:
  - "The firebox block is loaded (TFC's firebox_off / firebox_on models, light 15 when lit, copper sounds) and its screen lines up at GUI scale 2 and 3 (moved from PAR-HEAT-12)"
  - "A stove burns like a firepit with TFC's stove shape (box 1,0,1 to 15,12,15) and facing, takes a pot (stove pot) and gives it back, and refuses a grill"
plan:
  - "Firebox block (deferred from PAR-HEAT-12, PROTOCOL 3 + 6.5): datagen firebox/FireboxBlock already writes configuration/firebox/firebox.yml (lit, firebox_off / firebox_on on the solid auto_state pool, light 15, strength 6, pickaxe, self loot, block item, names; FireboxBlockTest) but Datagen does not run it: GeneratedCarrierTest refuses an auto_state full cube that HitboxDemands.FULL_CUBE_POOLS does not declare. Declare tfp:firebox (appearances unlit, lit -> solid), add the run line `if (FireboxBlock.available(tfc)) FireboxBlock.run(tfc, out);` to Datagen.generate, raise GeneratedCarrierTest's pool count by 2, drop firebox from the BlockCoverage owner line. The runtime (FireboxDevice, screen art firebox_gui.yml) is done."
  - "Copper sounds: DeviceSound has no SoundType.COPPER mapping (RenderedSound throws), so FireboxBlock uses DeviceSound.METAL (ledger divergence). Add COPPER (vanilla SoundType.COPPER: minecraft:block.copper.*, volume 1, pitch 1) to DeviceSound.VANILLA and RenderedSound, switch FireboxBlock.SOUND and its test, and drop the divergence row."
  - "Stove and stove pot: TFC's StoveBlock extends FirepitBlock (BASE_SHAPE box(1, 0, 1, 15, 12, 15), FACING + AXIS, no random-tick fire spread, a pot item converts it to STOVE_POT via AbstractFirepitBlockEntity.convertTo, a wrought iron grill is refused), StovePotBlock extends PotBlock (POT_SHAPE, sneak + empty hand gives the pot back and converts to STOVE, hurting the player and playing ITEM_COOL when lit). Both need hitbox demands and the firepit and pot device kinds (paper/firepit, paper/pot) for tfp:stove and tfp:stove_pot: register kinds reusing FirepitDevice / PotDevice, or add the ids to those modules' kind registration with the owners' agreement."
tests:
  - "datagen: firebox, stove and stove_pot entries (carriers / pools pinned in GeneratedCarrierTest); paper: pot conversion both ways (MockBukkit)"
---

# PAR-HEAT-31: Firebox block in game, stove and stove pot

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
