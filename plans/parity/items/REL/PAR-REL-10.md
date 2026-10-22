---
id: PAR-REL-10
title: "Art backlog: replace every placeholder texture"
area: REL
status: open
wave: 53
deps: []
size: M
aliases: ["X-ART-01", "X-ART-02"]
tfc_refs:
  - "src/main/resources/assets/tfc/textures"
files_owned:
  - "docs/art/image-list.md"
  - "art/raw/**"
  - "tools/images/**"
  - "content/art/**"
ui: false
in_game_steps: []
plan:
  - "Per feature item, generate missing art through the imagegen MCP and the image-generation skill (style guide, post-processing), keep raws/prompts in art/raw/<id>/; this item tracks the leftovers at the end."
tests:
  - "no placeholder art referenced (datagen check)"
done_criteria:
  - "docs/art/image-list.md all approved"
---

# PAR-REL-10: Art backlog: replace every placeholder texture

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
