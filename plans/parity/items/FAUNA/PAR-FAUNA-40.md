---
id: PAR-FAUNA-40
title: "Publish the penguin and turtle play_dead models into the BetterModel jar (follow-up of PAR-FAUNA-31)"
area: FAUNA
status: open
wave: 47
deps: [PAR-FAUNA-31]
size: S
aliases: ["follow-up of PAR-FAUNA-31 (2026-11-25): publish_models.py writes outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/model/entity/PenguinModel.java"
  - "src/main/java/net/dries007/tfc/client/model/entity/TFCTurtleModel.java"
files_owned:
  - "integration/bettermodel/src/main/resources/bettermodel/models/**"
  - "core/src/main/resources/dev/tfp/core/animal/model_variants.tsv"
  - "tools/models/publish_models.py"
  - "docs/verification/PAR-FAUNA-40/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn penguin adult on land; hit it; screenshot (it lies on its back)"
  - "tfp debug animal spawn turtle adult on land; hit it; screenshot (head and legs drawn in)"
plan:
  - "PAR-FAUNA-31 added the play_dead animation to tools/models/imported/tfp_penguin.bbmodel and tools/models/tfpmodels/species/out/tfp_turtle.bbmodel, but the BetterModel jar holds the published copies (tfp_penguin, tfp_penguin_baby, tfp_turtle). Run python3 -I tools/models/publish_models.py /opt/tfc, check the diff only adds play_dead to those three models, and review the in-game screenshots. Re-run tools/models/tfpmodels/species/play_dead.py after any re-import of the penguin (import_tfc.py rewrites it)."
tests:
  - "tools/models validate.py on the published models; the published turtle and penguin models carry play_dead"
done_criteria:
  - "A hit penguin and a hit turtle show TFC's play-dead pose in game; screenshots reviewed"
---

# PAR-FAUNA-40: Publish the penguin and turtle play_dead models

Follow `plans/parity/PROTOCOL.md`.
