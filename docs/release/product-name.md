# Product name (PAR-REL-09)

Decision 2026-11-19: the product is called **Cairnlands**.

- Display name everywhere players and server owners read: "Cairnlands". Short tagline: "A survival-through-the-ages world for vanilla clients, a from-scratch tribute to TerraFirmaCraft."
- Differs from "TerraFirmaCraft" (no shared word, no TFC logo or branding). Attribution stays in `NOTICE` and in ported file headers ("Ported from TerraFirmaCraft ... @ commit"), licence EUPL-1.2.
- Technical identifiers stay: namespace `tfp`, package `dev.tfp`, plugin descriptor name `TerraFirmaPlugin`. Players never see them except in item ids; a rename would break saved worlds, CraftEngine packs and every config path, so it is deliberately not part of the name decision. Changing the descriptor `name` and the jar file name is a release-time task of PAR-REL-09 (files outside this item's ownership: `paper/src/main/resources/paper-plugin.yml`, `dist/build.gradle.kts`).
- Not yet checked: trademark or Modrinth/Hangar name collisions. Search both before the first listing.
