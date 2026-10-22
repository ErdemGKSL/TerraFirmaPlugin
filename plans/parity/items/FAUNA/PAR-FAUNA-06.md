---
id: PAR-FAUNA-06
title: "Animal products: milk, wool, eggs; sheared look"
area: FAUNA
status: open
wave: 42
deps: [PAR-FAUNA-05, PAR-INFRA-04]
size: M
aliases: ["M5-LIV-02"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/entities/livestock/DairyAnimal.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/WoolyAnimal.java"
  - "src/main/java/net/dries007/tfc/common/entities/livestock/ProducingAnimal.java"
  - "src/main/java/net/dries007/tfc/common/component/EggComponent.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/products/**"
  - "core/src/test/java/dev/tfp/core/products/**"
  - "core/src/test/resources/dev/tfp/core/products/**"
  - "paper/src/main/java/dev/tfp/paper/products/**"
  - "paper/src/test/java/dev/tfp/paper/products/**"
  - "core/src/main/java/dev/tfp/core/livestock/products/**"
  - "core/src/test/java/dev/tfp/core/livestock/products/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn sheep adult female"
  - "use shears; screenshot sheared model"
plan:
  - "Bucket on a dairy animal fills milk (fluid on the bucket), shears give wool (sheared part toggled in the model), poultry lay eggs with an egg component (fertilized, hatch day); markElderlyIfDue after uses."
tests:
  - "core: product goldens exist"
  - "paper: milk/shear interactions"
done_criteria:
  - "Milking and shearing in-game with cooldowns"
---

# PAR-FAUNA-06: Animal products: milk, wool, eggs; sheared look

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
