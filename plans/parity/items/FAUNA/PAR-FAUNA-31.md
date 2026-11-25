---
id: PAR-FAUNA-31
title: "Amphibian play-dead pose: penguin and turtle models (follow-up of PAR-FAUNA-12)"
area: FAUNA
status: open
wave: 46
deps: [PAR-FAUNA-12]
size: S
aliases: ["follow-up of PAR-FAUNA-12 (2026-11-25): the models are outside its files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/model/entity/PenguinModel.java"
  - "src/main/java/net/dries007/tfc/client/model/entity/TFCTurtleModel.java"
files_owned:
  - "tools/models/tfpmodels/species/**"
  - "tools/models/imported/tfp_penguin.bbmodel"
  - "docs/verification/PAR-FAUNA-31/**"
ui: true
in_game_steps:
  - "tfp debug animal spawn penguin adult on land; hit it; screenshot (it lies on its back)"
  - "tfp debug animal spawn turtle adult on land; hit it; screenshot (head and legs drawn in)"
plan:
  - "PAR-FAUNA-12 plays the BetterModel animation play_dead while an amphibian plays dead (PaperAmphibianBody.PLAY_DEAD, looped) and stops it after; no model has that animation yet."
  - "Penguin (PenguinModel.setupAnim): core.xRot = -90 degrees while playing dead. Turtle (TFCTurtleModel.setupAnim): head and the four legs hidden; the turtle has no model of ours yet (vanilla turtle carrier), so it needs the TFC turtle model through the model-authoring skill first."
tests:
  - "tools/models validate.py on the changed models; render PNGs reviewed"
done_criteria:
  - "A hit penguin and a hit turtle show TFC's play-dead pose; screenshots reviewed"
---

# PAR-FAUNA-31: Amphibian play-dead pose: penguin and turtle models

Follow `plans/parity/PROTOCOL.md`.
