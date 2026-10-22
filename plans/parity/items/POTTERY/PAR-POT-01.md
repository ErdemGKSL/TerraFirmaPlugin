---
id: PAR-POT-01
title: "Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln)"
area: POTTERY
status: open
wave: 21
deps: [PAR-INFRA-02, PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java"
  - "src/main/java/net/dries007/tfc/network/PlaceBlockSpecialPacket.java"
  - "src/main/java/net/dries007/tfc/util/InteractionManager.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/placed/**"
  - "core/src/test/java/dev/tfp/core/placed/**"
  - "core/src/test/resources/dev/tfp/core/placed/**"
  - "paper/src/main/java/dev/tfp/paper/placed/**"
  - "paper/src/test/java/dev/tfp/paper/placed/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/placed/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/placed/**"
  - "content/generated/content/craftengine/tfp/configuration/placed/**"
ui: true
in_game_steps:
  - "hold an ingot; look at the top face of a stone block near its NW corner; press right-click and release after 0.5 s: nothing is placed, the progress bar on the action bar resets"
  - "hold right-click for 1.2 s on the same spot: the ingot is placed in the NW quadrant at 1 s (progress bar fills, place sound), nothing else happens while held; repeat with a vessel (NE), a jug while sneaking (SW) and a mold (SE): four items in one block; screenshot after each (item in the quadrant nearest the cursor, rotated to the player's yaw in 15 deg steps, outline = pressure plate)"
  - "release early: hold 0.6 s, move the cursor to another block or switch hotbar slot, keep holding: nothing placed, the hold restarts on the new target"
  - "tap (< 1 s) a vessel on stone: its GUI opens on release; tap a hoe on dirt: tills at once (action first, no hold)"
  - "place one very large item (large-item slot) on an empty block by holding; screenshot"
  - "pickup: right-click each quadrant with an empty hand (that item returns), sneak + right-click an occupied quadrant with an item in hand (returns it, TFC rule); walk Bot across the block (no collision); break a block with 4 items (all drop, no vanilla pressure plate)"
  - "negatives: a block item held 2 s on stone places the block (no placed item); a side face never places; with `placed-items.gesture: sneak-double-tap` the fallback gesture places and holding does not"
plan:
  - "TFC places items with a key binding (V, PlaceBlockSpecialPacket): only on the top face, the slot from the hit position (x and z fraction above 0.5, PlacedItemBlockEntity.getSlotSelected), one item per slot (stack.split(1)), up to 4 slots, or one large item (size above maxPlacedItemSize up to maxPlacedLargeItemSize) in a centre slot filling the block; rotation = player yaw rounded to 15 deg; empty hand or sneak + right-click takes the item of that slot back."
  - "Gesture (owner decision, NP-CLIENT-01): hold right-click >= 1 s on the top face of a full sturdy block whose space above is empty (TFC's conditions). Mechanism (recommended, path R): while right-click is held on a block, the vanilla client resends the use-on-block packet every 4 ticks (the same repeat that places blocks continuously), and Paper fires PlayerInteractEvent RIGHT_CLICK_BLOCK for each. A per-player HoldTracker (state owned by the player's entity scheduler, Folia-safe; core/placed holds the pure rule with an injected clock) keys a hold by block, face, hand, hotbar slot and item fingerprint; it counts one event per client tick (UseItemOn + UseItem of the same tick count once), accepts a gap of at most 300 ms (about 6 ticks, lag tolerant), and completes when at least 5 events span >= 1000 ms; any other target, face, slot, item or hand resets it; no event for 300 ms = released (an entity-scheduler check every 5 ticks while a hold exists). Each event of a hold is denied for the block and the item (Result.DENY both), so nothing else happens while holding. Progress: action-bar bar updated per event (BetterHud popup later if wanted); completion: the item's place sound, no particles. Anti-spam: one placement per hold, then a release is needed; 250 ms cooldown per player; the hold state is dropped on quit, death, world change."
  - "Which items: action first, hold otherwise. If the held item's right-click on that target does something in the world (block items place a block, hoe/shovel/axe/knife/chisel/firestarter/propick act, rocks place a loose rock, buckets pick up or place fluid, CE/TFC behaviours on the target block, any interactable target: containers, devices, doors, levers), that action runs on the first event and no hold starts (divergence: block items and rocks cannot become placed items, TFC's V could place them). Items whose tap opens a GUI or does nothing off-world (vessels, molds, ingots, bowls, sheets, most materials) start a hold on a solid top face; their tap action is deferred to the release (< 1 s: open the vessel GUI, etc.). Use-duration items (food, jugs with a drinkable fluid, soups, bows, shields, spyglass, javelins): the client starts using and stops repeating, so path R cannot see a hold; they are placed only while sneaking: sneak + hold on a top face, detected by the use start plus a 20-tick check that the player is still using that stack on the same face, then clearActiveItem and place (spike first: confirm the client stops its own use animation on 1.21.11 and 26.x; if not, these items are exempt and noted)."
  - "Compared, not chosen: a CraftEngine `consumable` (use-duration) component on every placeable item gives a native hold animation, client-side release cancel and PlayerItemConsumeEvent, but it changes the item everywhere (food and jugs already consume; vessel GUIs would move to the release), slows the player to 20 % while holding, predicts the consume client-side (stack flicker unless the event is cancelled and the slot resent) and cannot cover 'any item'. Bedrock/touch (Geyser): long-press on touch is break, not use, so touch players get the fallback: config `placed-items.gesture: hold | sneak-double-tap | both` (default `both`; sneak-double-tap = two sneak starts within 400 ms, then a right-click on a top face places at once)."
  - "Hitbox (PAR-INFRA-13 owner rule): every placed item, whatever the item, uses a pressure-plate visual (heavy_weighted_pressure_plate[power=0], hitbox-only empty model, no collision) for all 15 occupancies and the large-item state; no per-item carriers."
  - "CE block `tfp:placed_item` with 4 quadrants shown by 4 item displays (entity_renderer, dynamic); size limits maxPlacedItemSize/maxPlacedLargeItemSize; take back by right-click."
tests:
  - "paper: MockBukkit place/take per quadrant, size refused"
  - "core: HoldTracker with a fake clock: 4 events in 0.8 s place nothing; 6 events over 1.0 s place once; a 350 ms gap resets; target, face, slot or item change resets; two packets in one tick count once; one placement per hold"
  - "paper: MockBukkit: block items and tools with an action on the target never start a hold; vessel tap opens the GUI on release, hold places; the deny result is set on every hold event"
done_criteria:
  - "In-game: four items placed and taken back"
---

# PAR-POT-01: Placed items: put up to four items on the ground (and the straw/log stages of a pit kiln)

> see_not_parity: NP-CLIENT-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
