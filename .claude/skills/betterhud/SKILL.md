---
name: betterhud
description: How this plugin drives BetterHud (toxicity188) for every HUD element. TFC health "45/50", thirst, hunger, nutrition, item temperature, date/climate readouts, popups, hiding vanilla bars. Covers BetterHud's boss-bar + core-shader rendering model, the YAML object types (images/layouts/huds/popups/texts), the packs/ install folder, the Java API for placeholders/listeners/triggers, async evaluation rules on Folia, CraftEngine pack merging, Java 25 requirement and known gotchas. Use this whenever a task adds or changes anything shown on the player's screen outside an inventory GUI, registers a placeholder, touches BetterHud files, or debugs HUD alignment/visibility, even if BetterHud isn't named (e.g. "show thirst bar", "display temperature", "hide hearts").
---

# BetterHud in this project

BetterHud draws the **whole HUD as the text of one boss bar per player**. It uses generated bitmap fonts, and a generated **core text shader** moves each glyph to its screen position. So:
- Values (numbers, strings, bar fill) update live, about 20×/s per player, evaluated **asynchronously**.
- Positions, scales, layers and animations are **baked into the resource pack**. Changing them means a reload *and* the client re-applying the pack.
- Only one pack can own the `rendertype_text` (≤26.1) / `text` (26.2+) core shader. Anything else shipping that shader conflicts.

Facts below come from `references/research-report.md` (BetterHud `master` @ `8f4b11d`, tag 2.0.0, researched 2026-10). Re-check the report's "UNVERIFIED" list before relying on those items.

## Version and platform constraints
- Release 2.0.0: MC 1.21–26.1.2. Dev 2.2.0: MC 1.21.4–26.3 (drops ≤1.21.3). Folia is supported.
- **Requires Java 25** (class file v69) for both the server and our compile against the API. This sets the project's toolchain. See `CLAUDE.md`.
- One NMS module per MC version, so a new MC version needs a BetterHud update before we can support it.
- API: `compileOnly("io.github.toxicity188:BetterHud-bukkit-api:2.0.0")` from Maven Central. Keep it `compileOnly`, and exclude `net.kyori` so Paper's Adventure wins. Entry point: `BetterHudAPI.inst()`. `softdepend: [BetterHud]`.

## Where our content goes
Install into **`plugins/BetterHud/packs/tfp/`**. That is an external pack folder with the same structure as BetterHud's data folder (`assets/ images/ layouts/ huds/ popups/ texts/ fonts/`). Never write into the user's main folders. Rules that follow from BetterHud's load order:
- Prefix **every id** with `tfp_` and every asset path with `tfp/`. Duplicate ids are first-wins with a warning. Duplicate texture *file names* are first-wins **silently**.
- Packs load before the main folder. Our layouts may only reference our own images/texts, never ones from the main folder.
- A pack folder renamed to start with `-` is skipped. If the user did that (`packs/-tfp`), respect it and don't recreate `packs/tfp`.
- There is **no Java API to create HUDs/layouts/images**. They are generated YAML, written by the `dependency-bootstrap` installer (hash manifest, user-edit protection).

## Our HUD, element by element

| TFC element | BetterHud building block |
|---|---|
| Health bar + "45/50" text | `listener` image (`class: placeholder`, `value: tfp_health`, `max: tfp_max_health`) + text `"[tfp_health]//[tfp_max_health]"` with `placeholder-string-format: {number: "#"}` |
| Hunger bar | `listener` image, `class: food` (built-in) or our own listener |
| Thirst bar | `listener` image, `class: tfp_thirst` (registered via `ListenerManager`) |
| Date / ambient temperature / climate | text element with `tick: 20` (slow-changing) |
| Held item temperature | `default: true` popup with condition `tfp_held_temp > 0` |
| Nutrition | Shown in our inventory GUI, not the HUD (TFC also uses a screen) |

A complete starter skeleton (images, font, layouts, huds, popup) is in `references/research-report.md` §11. Pixel offsets there are placeholders, so tune them in-game. Anchor HUD `gui` coordinates at 0/50/100 only, and do fine placement with layout pixel offsets, otherwise positions drift between GUI scales.

## Registering placeholders (Java)

```java
var api = BetterHudAPI.inst();
var pm  = api.getPlaceholderManager();

pm.getNumberContainer().addPlaceholder("tfp_thirst",
    HudPlaceholder.of((args, reason) -> p -> hudCache.snapshot(p.uuid()).thirst()));

HudPlaceholder.<Number>builder().requiredArgsLength(1)
    .function((args, reason) -> {                      // runs at YAML parse time: parse args here, once
        Nutrient n = Nutrient.byName(args.get(0));
        return p -> hudCache.snapshot(p.uuid()).nutrient(n);
    })
    .add("tfp_nutrient", pm.getNumberContainer());

api.getListenerManager().addListener("tfp_thirst",
    yaml -> event -> (HudListener) p -> hudCache.snapshot(p.uuid()).thirst() / 100.0);  // 0..1
```

Rules:
- **Register in `onEnable`.** BetterHud resolves placeholders while parsing YAML, and an unknown placeholder makes that element/layout/hud fail to load. Its first parse happens after all `onEnable`s: at `ServerLoadEvent`, or inside CraftEngine's pack build when CraftEngine is present. Registrations survive `/hud reload`.
- **Placeholder functions run async, 20×/s/player.** On Folia, touching world/entity state there throws. They must be O(1) reads from `hudCache`: an immutable per-player snapshot (`ConcurrentHashMap<UUID, HudSnapshot>`) that our code publishes from the player's region thread when values change. Never compute TFC logic inside a placeholder.
- Use UUIDs, never `HudPlayer.handle()` identity (on Folia it's a wrapper).
- Don't route TFC data through PlaceholderAPI. BetterHud re-parses PAPI every tick.
- Custom triggers: prefer firing `CustomPopupEvent`. Registering our own Bukkit listener per reload leaks listeners.

Per-player show/hide: `getHudManager().getHud("tfp_main").add(hudPlayer)`, `popup.show(UpdateEvent.EMPTY, hudPlayer)`, `hudPlayer.setHudEnabled(false)`. `getHudPlayer(uuid)` can be null (before join registration, Bedrock players).

## Hiding vanilla bars
BetterHud's `remove-default-hotbar` is all-or-nothing (hotbar and crosshair disappear too), so don't use it. Instead, ship transparent sprites for exactly what TFC replaces, through **our CraftEngine pack**:
`assets/minecraft/textures/gui/sprites/hud/heart/*.png` (all 48 including `container*`, `vehicle_*`, `*_blinking`, `hardcore_*`), `food_*.png` (6), `air.png`, `air_bursting.png`, `air_empty.png`. The exact list is in report §6.2.

## Resource pack and CraftEngine
- When CraftEngine is installed (our case), BetterHud **skips its own startup reload**. On every CraftEngine pack build, BetterHud reloads and adds its output to CraftEngine's external zips/folders. CraftEngine hosts the merged pack. So:
  - keep BetterHud `pack-type` as `zip`/`folder` (not `none`), and keep `enable-self-host: false`;
  - after changing our HUD files at runtime: `BetterHudAPI.inst().reload()` (async) **and** trigger a CraftEngine pack rebuild. `/hud reload` alone doesn't reach clients.
- Check that nothing else in the merged pack ships a `rendertype_text`/`text` core shader (other HUD/nameplate plugins, ItemsAdder text effects, Nexo gif shaders).

## Performance
- Put `tick: N` on slow elements (date, climate: `tick: 20`). Default evaluation is every update.
- Avoid layout `animations` for static bars (each frame multiplies pack glyphs and shader cases). Keep `split` at 25–50.
- Disable the HUD for AFK players. Raise `tick-speed` server-wide only if needed (popup durations scale with it, because they count update cycles, not ticks).

## Gotchas checklist
- `/` is an escape character in text patterns: write `//` for a slash.
- `disable-number-format` defaults to `true`. Set `placeholder-string-format: {number: "#"}` for integers.
- Condition operands must have the same type. Quote string literals: `"'abc'"`.
- BetterHud ships a demo `test_hud` in `config.yml default-hud`. The installer removes it (see `dependency-bootstrap`).
- Ender Dragon/Wither boss bars shift the HUD with `merge-boss-bar: true`.
- After a BetterHud upgrade, delete `plugins/BetterHud/shaders/` (it's a cache that's never auto-updated).
- The wiki is partly outdated (e.g. `shader.yml hotbar:` options no longer exist). Trust the report/source.
- Debug a pattern in chat with `/hud parse <text>`.
