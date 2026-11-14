---
id: PAR-PROC-39
title: "Powderkeg CE block: tfc:powderkeg hitbox demands, configuration/powderkeg, in-game explosion check"
area: PROC
status: open
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
  - "tfp debug place tfp:powderkeg on a stone floor; open it (right click), add 12 x 64 gunpowder; screenshot the screen (GUI scale 2 and 3)"
  - "press the seal button; screenshot the sealed screen (disabled highlight, unseal tooltip)"
  - "light it with flint and steel (or a firestarter); screenshot the lit keg; wait 4 s; screenshot the crater"
done_criteria:
  - "The block shows TFC's unsealed, sealed and lit models on the carriers the selector picks (outline: tfc:powderkeg[sealed=*] rows of the carrier report)"
  - "A powderkeg explodes with TFC strength in game (12 x 64 gunpowder: strength 64) 80 ticks after it is lit; explosion_proof blocks survive"
  - "The 12-slot screen lines up at GUI scale 2 and 3; the seal button toggles and locks the slots"
plan:
  - "Deferred from PAR-PROC-18 (PROTOCOL 3 + 6.5): the powderkeg runtime is done and tested (core/powderkeg PowderkegRules, PowderkegState, PowderkegConfig, PowderkegScreenLayout; paper/powderkeg PowderkegDevice kind tfp:powderkeg, PowderkegModule, PowderkegBlasts, PowderkegScreens, PaperPowderkegEffects; datagen powderkeg/PowderkegScreenArt writes configuration/powderkeg/powderkeg_gui.yml). The CraftEngine block is missing because its hitbox carriers must come from the selector (HardcodedCarrierTest) and HitboxDemands.java was outside PAR-PROC-18's files_owned."
  - "Hitbox: tfc-shapes.json already has tfc:powderkeg[sealed=true|false] (PAR-INFRA-13; report: sealed cactus, unsealed composter[level=0] hitbox-only). Add the demands for tfp:powderkeg (unsealed, sealed, lit: TFC's three models; lit is sealed-only in practice but any combination can occur after an explosion lights an unsealed keg) and pin tfp:powderkeg in GeneratedCarrierTest."
  - "Block entry: datagen powderkeg/PowderkegBlock writes configuration/powderkeg/powderkeg.yml through DeviceBlockSpec + DeviceYaml.templated: the tfp:device behaviour, the properties sealed and lit (PowderkegDevice.SEALED / LIT, default false; the device writes both), TFC's strength / sound / tool from TFCBlocks, the loot (the keg itself, survives_explosion), the block item, TFC's models (powderkeg, powderkeg_sealed, powderkeg_lit with the animated powderkeg_outside_lit texture) and textures, block.tfp.powderkeg in every TFC locale. Add a run line to Datagen.java and the section to CeYamlCheck if new."
  - "In-game check (PROTOCOL 6): run the steps with the bot; store docs/verification/PAR-PROC-18/."
tests:
  - "datagen: PowderkegBlockTest (properties sealed/lit, carriers from the allocation, TFC's models and loot)"
---

# PAR-PROC-39: Powderkeg CE block: tfc:powderkeg hitbox demands, configuration/powderkeg, in-game explosion check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
