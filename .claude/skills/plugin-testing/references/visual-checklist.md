# Visual checklist (manual, real client)

Use a vanilla client of the newest supported version plus one of the oldest (1.21.11). Accept the server resource pack. Run `/tfp debug showcase <feature>` and walk through the list. Tick only what you actually looked at.

## Blocks (CraftEngine)
- [ ] Every state has the right texture (no purple/black missing texture, no vanilla look leaking through)
- [ ] Repurposed vanilla appearances: the original vanilla block doesn't appear anywhere it shouldn't
- [ ] Outline/hitbox matches the visual (look at it from several angles)
- [ ] Client-predicted behaviour of the base shape is intended (climbing, slowdown, stepping, waterlogging)
- [ ] Break particles, break progress and sounds look and sound right
- [ ] `entity_renderer` blocks: no z-fighting or flicker, correct rotation, and they disappear when the block is broken
- [ ] Lighting/shading looks right (no fully dark or fully bright faces unless intended)

## Items
- [ ] Icon in inventory, held in hand (1st/3rd person), dropped on the ground, in item frames
- [ ] State-dependent models (heat glow, pan stages, molds filled/empty) switch correctly
- [ ] Tooltip lines (heat, expiry, size/weight) correct, localized, and not saved into the item (check after a creative-mode copy)

## Entities (BetterModel)
- [ ] Model, texture variant, gender and age scale correct
- [ ] idle/walk switch while the animal pathfinds; custom animations (eat, attack, sleep) play
- [ ] After chunk unload/reload and `/bm reload`: variant, toggled parts and tint are re-applied
- [ ] Hitting and right-clicking work on the visible body (carrier hitbox matches the model)
- [ ] No vanilla carrier visible (armor, saddle, fire, name tag) unless intended
- [ ] Mounts: rider sits at a sensible height, walking animates while ridden

## HUD (BetterHud)
- [ ] All elements are in position at GUI scales 1, 2, 3 and auto, and at 16:9 and ultra-wide
- [ ] Health "45/50", thirst and hunger values match `/tfp debug player`
- [ ] Hidden vanilla hearts/food/air really hidden; hotbar, crosshair and XP still visible
- [ ] No interference from other boss bars

## GUIs
- [ ] Background texture aligned with slots at all GUI scales
- [ ] The "click outside inventory" button appears where clicks open the TFC screen
- [ ] Every button/slot does what it shows

## Performance smell test
- [ ] FPS is stable when standing in the showcase. Display-entity-heavy features stay within reason.
- [ ] No visible pop-in or lag when the chunk loads
