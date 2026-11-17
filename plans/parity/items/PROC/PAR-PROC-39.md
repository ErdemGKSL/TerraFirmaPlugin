---
id: PAR-PROC-39
title: "Powderkeg CE block: tfc:powderkeg hitbox demands, configuration/powderkeg, in-game explosion check"
area: PROC
status: done
wave: 15
deps: [PAR-PROC-18, PAR-INFRA-13]
size: M
aliases: ["follow-up of PAR-PROC-18 (its CraftEngine block and its in_game_steps, PROTOCOL 3 + 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates/powderkeg.json"
  - "src/main/resources/assets/tfc/models/block/powderkeg.json"
  - "src/main/resources/assets/tfc/models/block/powderkeg_sealed.json"
  - "src/main/resources/assets/tfc/models/block/powderkeg_lit.json"
  - "src/main/resources/data/tfc/loot_table/blocks/powderkeg.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/powderkeg/PowderkegBlock*.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/powderkeg/PowderkegBlock*.java"
  - "content/generated/content/craftengine/tfp/configuration/powderkeg/**"
  - "docs/verification/PAR-PROC-18/**"
ui: true
in_game_steps:
  - "Place tfp:powderkeg (ce item give Bot tfp:powderkeg, right click) on a stone floor; screenshot the unsealed keg in the world (no floor showing through its 1 px inset); open it (right click), shift-click 12 x 64 gunpowder in; screenshot the screen (GUI scale 2 and 3)"
  - "Press the seal button; screenshot the sealed screen (disabled highlight, Unseal tooltip); with the screen sealed, shift-click a gunpowder stack from the inventory, click and shift-click a keg slot and press a number key over one; RCON clear Bot minecraft:gunpowder 0 must report the same count as before (the slots are locked)"
  - "Close the screen; screenshot the sealed keg in the world; setblock bedrock on one side of the keg (tfc:explosion_proof); read time query gametime, setblock a redstone_block on the other side (TFC lights a sealed keg from a redstone signal: neighborChanged/onPlace); screenshot the lit keg; encase the keg in a 31^3 stone cube (fill ... stone replace air) and poll gametime and execute if block <keg> air over RCON until the keg is gone: light to explosion must be 80 ticks (+1 for the poll)"
  - "Measure the crater: execute if block along the axes, then fill the cube's air with glass to count it (and back); compare with vanilla's explosion algorithm at strength 64 in stone (simulated: 3900 to 4000 blocks, axis reach up to 12; strength 48 would be about 2050); the bedrock must remain; cut the cube open and screenshot the crater section"
done_criteria:
  - "The block shows TFC's unsealed, sealed and lit models on the carriers the selector picks (outline: tfc:powderkeg[sealed=*] rows of the carrier report)"
  - "A powderkeg explodes with TFC strength in game (12 x 64 gunpowder: strength 64) 80 ticks after it is lit; explosion_proof blocks survive"
  - "The 12-slot screen lines up at GUI scale 2 and 3; the seal button toggles and locks the slots"
plan:
  - "Deferred from PAR-PROC-18 (PROTOCOL 3 + 6.5): the powderkeg runtime is done and tested (core/powderkeg PowderkegRules, PowderkegState, PowderkegConfig, PowderkegScreenLayout; paper/powderkeg PowderkegDevice kind tfp:powderkeg, PowderkegModule, PowderkegBlasts, PowderkegScreens, PaperPowderkegEffects; datagen powderkeg/PowderkegScreenArt writes configuration/powderkeg/powderkeg_gui.yml). The CraftEngine block is missing because its hitbox carriers must come from the selector (HardcodedCarrierTest) and HitboxDemands.java was outside PAR-PROC-18's files_owned."
  - "Hitbox: tfc-shapes.json already has tfc:powderkeg[sealed=true|false] (PAR-INFRA-13; report: sealed cactus, unsealed composter[level=0] hitbox-only). Add the demands for tfp:powderkeg (unsealed, sealed, lit: TFC's three models; lit is sealed-only in practice but any combination can occur after an explosion lights an unsealed keg) and pin tfp:powderkeg in GeneratedCarrierTest."
  - "Block entry: datagen powderkeg/PowderkegBlock writes configuration/powderkeg/powderkeg.yml through DeviceBlockSpec + DeviceYaml.templated: the tfp:device behaviour, the properties sealed and lit (PowderkegDevice.SEALED / LIT, default false; the device writes both), TFC's strength / sound / tool from TFCBlocks, the loot (the keg itself, survives_explosion), the block item, TFC's models (powderkeg, powderkeg_sealed, powderkeg_lit with the animated powderkeg_outside_lit texture) and textures, block.tfp.powderkeg in every TFC locale. Add a run line to Datagen.java and the section to CeYamlCheck if new."
  - "In-game check (PROTOCOL 6): run the steps with the bot; store docs/verification/PAR-PROC-18/."
  - "Done: HitboxDemands adds tfp:powderkeg unsealed/sealed/lit (weight -4, served last), all three on TFC's sealed outline row (POWDERKEG_ROW) with TFC's models: unsealed cactus[age=13], sealed cactus[age=12], lit cactus[age=11] (repair round: the hollow row's composter[level=1] carrier culled the floor's top face, so the world showed through the open keg's inset); datagen powderkeg/PowderkegBlock writes configuration/powderkeg/powderkeg.yml (sealed, lit; TFC's strength, wood sounds, axe, self loot, block item, three models + animated lit texture, block.tfp.powderkeg in every TFC locale) and its Datagen run line. Tests: datagen PowderkegBlockTest 4, GeneratedCarrierTest.thePowderkegCarriersArePinned (+ tfp:powderkeg in the pinned id set). Ledger feature row and the carrier divergence row. In game 2026-11-17 (Paper 1.21.11, client 1.21.11): pass, fuse 80 ticks, crater 3991 blocks (strength 64), bedrock kept, slots locked while sealed; the seal button's size/weight tooltip line is PAR-INFRA-56 (outside files_owned). Evidence: docs/verification/PAR-PROC-39/verdict.md."
tests:
  - "datagen: PowderkegBlockTest (properties sealed/lit, carriers from the allocation, TFC's models and loot)"
---

# PAR-PROC-39: Powderkeg CE block: tfc:powderkeg hitbox demands, configuration/powderkeg, in-game explosion check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
