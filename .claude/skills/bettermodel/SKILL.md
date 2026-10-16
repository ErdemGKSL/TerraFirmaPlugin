---
name: bettermodel
description: How this plugin renders TFC animals and other animated entities with BetterModel (toxicity188). Packet-only item_display bones riding a real vanilla "carrier" mob, registering .bbmodel files from our jar via ModelAssetsEvent, animation naming/priority/override rules, texture variants, baby scaling, sheared/gendered parts, mounts, persistence and reload pitfalls, Folia threading, performance. Use this whenever a task adds or changes an entity, animal, fish, pet, mount, its model, animation, texture variant, AI-driven animation, or touches .bbmodel files or BetterModel config, even if BetterModel isn't named (e.g. "port the TFC cow", "add a walk animation", "make babies smaller").
---

# BetterModel in this project

How it works (from `references/research-report.md`, BetterModel 3.5.1 @ `fe60076`, researched 2026-10):
- Each Blockbench **group (bone) becomes one packet-only `item_display`** with an `item_model` component. The displays are sent as **passengers of a real server entity** (the carrier), so the client interpolates movement for free.
- The carrier stays a normal server mob: vanilla/Paper pathfinding, Mob Goals, breeding, leashing and riding all keep working. BetterModel only rewrites *outgoing* packets so the carrier looks invisible and equipment-less.
- Animation runs on **BetterModel's own thread pool** (25 ms frames). A model nobody is watching shuts its scheduler down, so unseen animals cost close to nothing.

## Constraints
- **Java 25** for the server (on 1.21.x too) and for our compile toolchain. MC 1.21.4 → 26.3. Paper/Folia supported.
- API: `compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.1")` (verify it's on Maven Central before pinning; report §12). Packages `kr.toxicity.model.api.*`. Declare BetterModel as a dependency in `paper-plugin.yml`.
- Model ids must match `[a-z0-9_.]+`. Ours are `tfp_<animal>[_<variant>]`.

## Getting our models in: `ModelAssetsEvent`, not file copying
Register models straight from our jar on every BetterModel reload:

```java
var bus = BetterModelBukkit.platform().eventBus();          // subscribe in onEnable, NOT onLoad
bus.subscribe(plugin, ModelAssetsEvent.class, e -> {
    try {
        if (e.type() != ModelRenderer.Type.GENERAL) return;
        for (ModelSource m : ourModels) e.addAsset(ModelAsset.of(m.id(), m::bytes)); // suppliers run in parallel
    } catch (Throwable t) { log.error("BetterModel asset registration failed", t); }
});
```

- **Wrap every subscriber body in try/catch.** If a handler throws, BetterModel silently unsubscribes *all* of our listeners for that event class. `addAsset` throws if an admin has a file with the same id, which is another reason to catch.
- Models don't exist until BetterModel's first reload (at `ServerLoadEvent`). Don't spawn or attach models before `PluginEndReloadEvent` succeeds. After it fires, re-ensure trackers on already-loaded entities.
- Extra textures (variant PNGs) go into BetterModel's pack via `PluginStartReloadEvent.zipper()`.
- BetterModel builds `plugins/BetterModel/build.zip`. CraftEngine merges it (default CE config). After a BetterModel reload, a CraftEngine pack rebuild may be needed for clients to see it (open question, see report §12 and `craftengine` skill).

## Preparing TFC `.bbmodel` files (done by `tools/datagen`, never by hand)
TFC's 44 bbmodels all parse: textures are embedded, every cube is in a group, no tag collisions. What has to change at import:
1. **Rename animations to BetterModel names.** Built-ins fire only on exact names: `idle` (loop), `walk` (while `navigation.isInProgress()`), `spawn`, `damage`, `death`, `jump`, `idle_fly`, `walk_fly`. TFC uses names like `animation.deer.walk`. Our own extra names: `run`, `eat`, `sleep`, `attack`, `swim`.
2. **Author missing animations.** Many TFC models have none, because TFC animates procedurally in Java (`client/model/entity/*`). Port the procedural math to keyframes; Molang keyframes such as `math.sin(query.anim_time*…)` are supported.
3. Optionally rename `head` → `h_head` so it follows look direction. **Bone renames must also rename `animations[].animators[*].name`**, because animations bind by bone name.
4. **Texture variants** (male/female, colours): emit one variant model per texture (swap the embedded texture), then swap bones with `perBone(... itemStack(variant.groupByTree(name).getItemStack()))`. For colour-only variants, use a greyscale base + `tint`. There is no texture-swap API (issue #387).
5. **No hitbox bones** (`b_`, `ob_`, `hitbox`, `p_`, `sp_`). Each adds 2 real server entities, and hitbox bones are linked to a client kick on 26.1.2 (issue #445). The carrier's vanilla hitbox handles hits and clicks.

## Carrier strategy
Pick the vanilla type closest in hitbox size, movement physics and pathing:
- Cow/Sheep/Pig/Goat for livestock
- Wolf for predators and pets
- Horse/Donkey/Mule/Llama/Camel for mounts: use the **vanilla mount**, not BetterModel `p_` seats, which mount on any right-click and bypass TFC taming
- Cod/Salmon/Dolphin for fish
- Chicken for small birds

Then:
- `setSilent(true)` and play TFC sounds ourselves.
- Replace AI with our Mob Goals. BetterModel is unaffected.
- `walk` only animates for **pathfinding** movement, not `setVelocity`.

## Driving the model (through `integration/bettermodel` only)

```java
// attach: on the mob's owning thread (Folia: entity scheduler or an event for that entity)
BetterModel.model(id).ifPresent(r -> r.getOrCreate(BukkitAdapter.adapt(mob), TrackerModifier.DEFAULT,
    t -> t.scaler(baby ? ModelScaler.entity().multiply(0.5f) : ModelScaler.entity())));

tracker.animate("eat", AnimationModifier.builder().override(true).priority(10)
        .type(AnimationIterator.Type.PLAY_ONCE).build());                   // layer over walk
tracker.update(TrackerUpdateAction.togglePart(false), BonePredicate.name("wool_body").withChildren()); // sheared
tracker.update(TrackerUpdateAction.tint(0xA0522D), BonePredicate.name("body").notSet());
BetterModel.registry(BukkitAdapter.adapt(mob)).ifPresent(EntityTrackerRegistry::close); // remove + clear PDC
```

Animation rules:
- **No blending.** Per bone, the highest `priority` wins, then the most recently added.
- `override(true)` makes an animation touch only its own keyframed bones, so the rest keep playing the lower animation.
- `AnimationModifier.toBuilder()` drops `priority`; set it again.
- `animate`, `update` and `tint` are safe from any thread. **Creating** trackers must happen on the entity's owning thread.

## Persistence: re-apply our visual state
BetterModel persists only `{model id, scaler, rotator, hide option}` in the entity PDC (`minecraft:bettermodel_tracker`) and auto-recreates trackers when the entity loads. **Tints, toggled parts, item swaps and running animations are lost** on chunk reload, restart and `/bm reload` (issue #402). Therefore:
- Keep the animal's visual state (variant, gender, sheared, age stage) in **our** entity PDC.
- Subscribe to `CreateEntityTrackerEvent` and re-apply everything from our PDC there (idempotently).
- Babies: `ModelScaler.entity()` reads the SCALE attribute, which probably doesn't include vanilla baby scaling. Use `multiply(0.5f)` or a growth-based scale, and test it.
- Animals that grow are a good fit for TFC's calendar-based age: compute the scale from age on (re)create, don't tick it.

## Performance
- Cost per entity ≈ bone count displays per viewer. TFC models have 2–21 bones. Keep hierarchies shallow (child-bone lag, issue #403).
- Visibility follows the carrier's vanilla entity-tracking range. Animation packets are further limited by `max-sight`/`min-sight`/`sight-trace`.
- Turn off `damageTint` per tracker if TFC wants different hit feedback (`TrackerModifier.builder().damageTint(false)`).

## Debugging
`/bm spawn <model>`, `/bm test <model> <animation>`, `/bm reload` (async, full pack rebuild, wipes visual state), config `debug.tracker: true`.

Full API tables, event list, config keys, the TFC bbmodel audit and open questions: `references/research-report.md`.

## Verified in M0-BM-01 (Paper 1.21.11, Cow carrier; see docs/spikes/M0-BM-01.md)
- Verified in M0-BM-01: face UVs of `uv [0,0,0,0]` in a .bbmodel make the model invisible in BetterModel 3.5.1, even with `box_uv`; real per-face rectangles render.
- Verified in M0-BM-01: after a restart, carriers that load before the models are registered get no tracker (`trackerExistsOnLoad=false`); re-attaching them in `PluginEndReloadEvent` restores it.
- Verified in M0-BM-01: a baby flag alone does not shrink a `ModelScaler.entity()` model; SCALE attribute 0.5 and `multiply(0.5f)` both do.
- Verified in M0-BM-01: tint kept in our PDC survives a restart and `/bm reload` only through the `CreateEntityTrackerEvent` re-apply (with re-apply off it is lost).
- Verified in M0-BM-01: the first CraftEngine pack build can omit bettermodel entries; a second `ce reload all` includes them.
