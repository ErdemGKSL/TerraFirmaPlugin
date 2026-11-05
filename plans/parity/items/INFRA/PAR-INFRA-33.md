---
id: PAR-INFRA-33
title: "CE block sound volume and pitch: hit and level-event break sounds at the TFC SoundType's values, devices too"
area: INFRA
status: done
wave: 6
deps: [PAR-INFRA-17]
size: S
aliases: ["follow-up of PAR-INFRA-17 (review fix: sound pitch/volume)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/client/TFCSounds.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/blocksound/**"
  - "paper/src/test/java/dev/tfp/paper/blocksound/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceSound.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceSoundVolumeTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/device/DeviceYaml.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/device/DeviceYamlGoldenTest.java"
  - "paper/src/main/java/dev/tfp/paper/integration/craftengine/CeBlockSoundTable*.java"
  - "docs/verification/PAR-INFRA-33/**"
  - "spikes/s2-destroy-stages/src/main/java/dev/tfp/spike/s2/SoundLog.java"  # listed exception (PAR-INFRA-17's spike): the log line's cancelled= flag only
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11 and Paper 26.3 + client 26.3 with the S2 spike pack (spikes/s2-destroy-stages, docs/spikes/S2-destroy-stages.md §2); its SoundLog prints `[S2] sound <id> volume=<v> pitch=<p> to <player> cancelled=<bool>` for every block sound packet, cancelled ones too (it listens at MONITOR); start both servers with `EXTRA_JVM=-Dtfp.blocksound.log=true` so our `[tfp-sound] SET|DROP` lines are interleaved. A breaker line with `cancelled=true` (right after a `[tfp-sound] DROP ... to <breaker>`) never reaches the client and is not a second break sound"
  - "Use difficulty peaceful. Mine tfps2:metal/block/copper with tfp:metal/pickaxe/bronze (`ce item give <breaker> tfp:metal/pickaxe/bronze`; its base material is iron_pickaxe) while a second client watches; place (`s2 place tfp:bellows`) and break the loaded device tfp:bellows (SoundType.WOOD) the same way. The firepit, grill and pot (NETHER_WART) are not loaded by CraftEngine yet (devices/ folder): their in-game sound check is deferred to PAR-INFRA-23 (PROTOCOL 6.5)"
plan:
  - "Current (PAR-INFRA-17): renderer cubes pass TFC's per-event volume/pitch to CraftEngine (`RenderedSound`: break/place (v+1)/2, p*0.8; step v*0.15, p; hit (v+1)/8, p*0.5; fall v*0.5, p*0.75); measured in game, place, step, fall and the breaker's own break sound are right (copper: 1.0/1.2, 0.15/1.5, 0.5/1.125, 1.0/1.2). Two sounds stay off because CraftEngine 26.9.2 does not read the config for them: its mining loop (`BukkitServerPlayer`, every hit) sends the server block's hit sound at a fixed volume 0.5 pitch 0.5 (TFC metal: 0.25/0.75), and its `LevelEventListener` turns the vanilla break level event 2001 into a `replaced.<break sound>` packet at a fixed 1.0/0.8 for every viewer (TFC metal: 1.0/1.2), so the breaker hears two break sounds and other players only the wrong-pitch one."
  - "Device blocks (PAR-INFRA-08 `DeviceSound.yaml`) still pass only the sound ids, so CraftEngine's own BlockSounds defaults apply to all five events; emit {id, volume, pitch} from `dev.tfp.datagen.rendered.RenderedSound` there too (golden in a new DeviceSoundVolumeTest; DeviceYamlGoldenTest changes go through this item's evidence)."
  - "Packet side (decide async, apply sync is not needed: a pure per-packet rewrite on the Netty thread): rewrite outgoing sound packets at a CE custom block's position whose id is that block's hit or `replaced.` break sound to the block's TFC volume/pitch (a lookup table from the generated content keyed by sound id + CE block id); drop the duplicate break packet for the breaker. The check whether a CE block sits at this position must not read the live world off-region: the lookup goes through an integration/craftengine adapter (CeBlockSoundTable) over a thread-safe snapshot or table kept up to date from place/break events on the region thread. Wire through a FeatureModule (paper/blocksound/**) plus its one hub line in the FeatureModule service file."
  - "Device YAML decision: CraftEngine 26.9.2's BlockSounds defaults for a plain id (break/place 1.0/0.8, step 0.15/1.0, hit 0.5/0.5, fall 0.5/0.75, read from the jar) already are the client values of a (1.0, 1.0) SoundType except hit, which CraftEngine never plays from config (its mining loop hard-codes 0.5/0.5). So `DeviceSound.yaml()` keeps plain ids for (1.0, 1.0) types (every current device: STONE, WOOD, NETHER_WART, TFC types; generated files unchanged, ForgeDevicesTest untouched) and writes {id, volume, pitch} for any other type (METAL); the packet table reads a plain id as (1.0, 1.0). Ledger divergence row."
  - "Done: datagen `DeviceSound.values()/plainIds()/yaml()` + DeviceSoundVolumeTest (4); paper module `block_sound` (`BlockSoundModule`, `BlockSoundTracker`, `TrackedBlocks`, `BlockSoundRewrite`, PacketEvents `BlockSoundPacketHandler`, `-Dtfp.blocksound.log`) and `paper/integration/craftengine/CeBlockSoundTable` (read from CraftEngine's resources folder on the I/O pool); tests CeBlockSoundTableTest (4), BlockSoundRewriteTest (6), BlockSoundTrackerMockBukkitTest (2). In game on Paper 1.21.11 and 26.3 with a breaker and a second viewer: copper hit 0.25/0.75, one break 1.0/1.2 for both players, bellows hit 0.25/0.5 and one break 1.0/0.8; firepit not loadable yet (devices/ folder), bellows used: docs/verification/PAR-INFRA-33/verdict.md. Follow-up PAR-INFRA-35 (terrain blocks whose sounds come only from tfp:settings/sound)."
  - "Review fix (independent in-game run at d97267c, 2026-11-05): the copper half passed on 1.21.11 and 26.3 (hit 0.25/0.75, viewer break 1.0/1.2); the device step named the firepit, which has no item and is not a loaded CE block until PAR-HEAT-06/08/09, so that half could not run. No code change: the step now names the bellows (the loaded WOOD device verdict.md already used), and the firepit/grill/pot NETHER_WART check is a pending step and criterion in PAR-INFRA-23 (deps PAR-HEAT-06, not this item; PROTOCOL 6.5). The done criterion 'devices too' is met by the bellows; NETHER_WART's values stay covered by DeviceSoundVolumeTest and CeBlockSoundTableTest."
  - "Review fix 2 (in-game REJECT of 1669969): the breaker's second `replaced.` break line is the cancelled packet, which the spike's MONITOR SoundLog prints anyway (verdict.md); the `[tfp-sound]` debug log now names the player and marks drops as cancelled; the step spells out the bronze pickaxe give command and a peaceful world. Firepit still not loadable (PAR-INFRA-23). SoundLog `cancelled=` and the 26.3 session.sh match are harness follow-ups outside this item."
  - "Review fix 3 (unblock, 2026-11-05): root cause of the repeated 'breaker hears 1.0/0.8' REJECT is the spike's MONITOR SoundLog printing packets our handler cancelled (PacketEvents calls MONITOR listeners for cancelled packets; the rewrite itself is right, BlockSoundRewriteTest.theBreakerHearsOneBreakSound). Fixed at the source: SoundLog (minimal listed exception in files_owned) now ends each line with `cancelled=<bool>`, and the in-game step requires `-Dtfp.blocksound.log=true`, so a dropped packet reads as dropped without pairing lines by hand. verdict.md: the 1.21.11 bellows-break cell reworded (hit measured on 1.21.11, the break captured on 26.3 only; version-independent path, and the breaker drop is shown on 1.21.11 with copper), the `replaced.block.grass.break` lines explained (no `[tfp-sound]` line: a vanilla grass break near the scene that CraftEngine's level-event listener translates, passed through untouched). Still open as notes: 26.3 session.sh pack-match (spikes/client, PAR-REL-01's harness); a watcher-side `replaced.` rewrite check for a device whose SoundType is not (1.0, 1.0) once one exists; NETHER_WART in game via PAR-INFRA-23 (PROTOCOL 6.5)."
tests:
  - "paper: unit test of the rewrite table (copper hit 0.25/0.75, level-event break 1.0/1.2, a non-CE position untouched)"
  - "tools/datagen: DeviceSound volume/pitch golden for STONE, WOOD, NETHER_WART and a TFCSounds type"
done_criteria:
  - "SoundLog shows every metal-block sound at TFC's volume/pitch for the breaker and a second viewer on 1.21.11 and 26.3, one break sound per break; devices too (the loaded device tfp:bellows; firepit/grill/pot NETHER_WART deferred check tracked in PAR-INFRA-23); evidence in docs/verification/PAR-INFRA-33/"
---

# PAR-INFRA-33: CE block sound volume and pitch: hit and level-event break sounds at the TFC SoundType's values, devices too

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
