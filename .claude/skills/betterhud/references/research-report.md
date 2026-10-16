# BetterHud — primary-source research report (for the TerraFirmaPlugin HUD integration)

> Note for this repo: the report uses `tfc_` id prefixes and a `packs/terrafirma/` folder in its examples. Our convention is the provisional namespace from `CLAUDE.md`: prefix `tfp_`, folder `packs/tfp/`.

Researched 2026-10-16. Everything here was checked against:

- **Repo:** `https://github.com/toxicity188/BetterHud`. I cloned `master` at commit `8f4b11d` ("feat: drop 1.21.3 or lower version support", 2026-10-07). `gradle.properties` says `version=2.2.0`, `supported_version=26.3`. I also fetched tag `2.0.0` to diff the API against it.
  Local clone: (scratch, not kept)
- **Wiki:** `https://github.com/toxicity188/BetterHud.wiki.git`. The last wiki commit is 2025-07-10, so parts of it are **outdated**. Local clone: (scratch, not kept).
- **Official addon example:** `https://github.com/toxicity188/BetterHud-MMOCore`. Local clone: (scratch, not kept).
- **Modrinth API** (`api.modrinth.com/v2/project/betterhud2/version`) and the **Maven Central** metadata and jars.
- **GitHub issue search** (`toxicity188/BetterHud`). I could get titles and bodies only. Reading issue comments was blocked in this session.

Below, a path like `dist/.../X.kt` is relative to the repo root. `kr/toxicity/hud` is shortened to `k/t/h` where the path would be long.

---

## Table of contents

1. [TL;DR for the TFC integration](#1-tldr)
2. [Versions, platforms and the rendering model](#2-versions-platforms-rendering)
3. [Data folder layout and every config type (with repo YAML)](#3-data-folder--config-types)
4. [Placeholder system](#4-placeholder-system)
5. [Java API](#5-java-api)
6. [Hiding vanilla HUD parts; GUI scale and resolution](#6-hiding-vanilla-hud--gui-scale)
7. [Resource-pack generation and merging (CraftEngine, Nexo, ItemsAdder, self-host)](#7-resource-pack-generation--merging)
8. [How a third-party plugin should auto-install content](#8-auto-install-strategy)
9. [Performance](#9-performance)
10. [Gotchas](#10-gotchas)
11. [Proposed TFC skeleton (our design, not from the repo)](#11-proposed-tfc-skeleton)
12. [UNVERIFIED and open questions](#12-unverified--open-questions)

---

## 1. TL;DR

- **How it renders.** BetterHud draws the HUD as the text of **one boss bar per player**, using custom bitmap fonts. A **core text shader** that it generates (`rendertype_text` on MC 1.21.4–26.1, `text` on 26.2+) then moves each glyph to its screen position. The y-ascent of each glyph encodes an element id, and the shader `switch` decodes it. BetterHud does not use the action bar, title, scoreboard or display entities. The design requires the BetterHud resource pack to be loaded on the client.
- **Supported versions.** The latest release is **2.0.0** (2026-05-16, Modrinth), for Bukkit/Paper/Folia 1.21–26.1.2. `master` is 2.2.0-SNAPSHOT: it supports **1.21.4–26.3** and drops 1.21.0–1.21.3.
  - **Java 25 is required.** Both the plugin and the API jars are compiled to class-file version 69 (I checked `BetterHud-bukkit-api-2.0.0.jar`). Our plugin must compile with JDK ≥ 25 when it uses the API, and the server must run Java 25.
  - Folia is officially supported (`foliaSupported = true`).
  - There is also a Velocity plugin and a Fabric server mod.
- **API dependency.** `compileOnly("io.github.toxicity188:BetterHud-bukkit-api:2.0.0")` from `mavenCentral()`.
  - Entry point: `BetterHudAPI.inst()`.
  - Register placeholders with `getPlaceholderManager().getNumberContainer()`, `getStringContainer()` or `getBooleanContainer()`, then `.addPlaceholder(name, HudPlaceholder)`.
  - For bars, either register a 0..1 **listener** with `getListenerManager().addListener(...)`, or use the built-in `class: placeholder` listener with `value`/`max` placeholders.
- **Threading.** Placeholder functions are evaluated **asynchronously** on BetterHud's per-player async timer: every `tick-speed` ticks (default 1), i.e. 20 times per second per player. On Folia, reading world state there throws (issue #5). **TFC placeholders must read from a thread-safe per-player snapshot cache.**
- **Register placeholders before the YAML is parsed.** Placeholders, listeners and images are resolved **when BetterHud parses the YAML**. An unknown placeholder or listener makes that image, layout or hud fail to load.
  - BetterHud's first load runs on `ServerLoadEvent` (`BukkitBootstrapImpl.onEnable`). When CraftEngine or Nexo is present and `merge-with-external-resources: true` (the default), it runs later instead, inside CraftEngine's `AsyncResourcePackCacheEvent` or Nexo's `NexoPrePackGenerateEvent`.
  - So register from our `onEnable` (we soft-depend on BetterHud). Registrations survive `/hud reload`, because the containers are never cleared.
- **Auto-install location.** BetterHud loads **external packs** from `plugins/BetterHud/packs/<name>/`. Each pack has the same subfolders as the main data folder: `images/`, `layouts/`, `huds/`, `popups/`, `texts/`, `heads/`, `compasses/`, `backgrounds/`, `assets/`, `fonts/`. Folders whose name starts with `-` are skipped.
  - This is the clean place for our generated content: `plugins/BetterHud/packs/terrafirma/`. We never touch the user's own folders.
  - Files we write before BetterHud's first reload are picked up with no extra work. Afterwards, call `BetterHudAPI.inst().reload()`.
- **Hiding vanilla bars.**
  - BetterHud's only option is `config.yml remove-default-hotbar: true`. It is **all or nothing**: it ships transparent copies of every `gui/sprites/hud/*` sprite, so the hotbar, crosshair, XP bar, armor, air, food, hearts, effects and locator bar all disappear.
  - `shader.yml disable-level-text: true` hides only the XP level number.
  - For a TFC-style "hide hearts, food and air but keep the hotbar" result, ship our own transparent `assets/minecraft/textures/gui/sprites/hud/{heart/*,food_*,air*}.png`. Put them through our CraftEngine pack, or through BetterHud's `merge-other-folders`.
- **CraftEngine.** CraftEngine integration is built in (`CraftEngineCompatibility.kt`). When CraftEngine regenerates its pack, BetterHud reloads and adds its own build output (zip or folder) to CraftEngine's `externalZips()` or `externalFolders()`. CraftEngine then hosts the merged pack. Nexo is handled the same way. ItemsAdder needs manual config (wiki).

---

## 2. Versions, platforms, rendering

### 2.1 Versions and platforms

| Item | Value | Source |
|---|---|---|
| Latest *release* | 2.0.0 (2026-05-16). Bukkit/Folia game versions 1.21 … 1.21.11, 26.1, 26.1.1, 26.1.2 | Modrinth API |
| Latest dev build | 2.2.0-SNAPSHOT-449 (2026-10-06). Bukkit/Folia/Paper/Purpur 1.21.4 … 26.3; Fabric 26.3; Velocity | Modrinth API |
| Server versions the code accepts | 1.21.4 (V1_21_R3), 1.21.5 (R4), 1.21.6–8 (R5), 1.21.9–10 (R6), 1.21.11 (R7), 26.1–26.1.2 (V26_R1), 26.2–26.3 (V26_R2). Any other version → "Unsupported minecraft version" and the plugin disables itself | `bootstrap/bukkit/.../BukkitBootstrapImpl.kt` `onLoad`; `api/bukkit-api/.../nms/NMSVersion.java` |
| 2.0.0 also supported | 1.21–1.21.1 (V1_21_R1) and 1.21.2–3 (V1_21_R2) | `git show 2.0.0:api/bukkit-api/.../NMSVersion.java` |
| Folia | Yes. `foliaSupported = true`; uses a `PaperScheduler` when `io.papermc.paper.threadedregions.RegionizedServer` exists | `bootstrap/bukkit/build.gradle.kts`; `BukkitBootstrapImpl.kt` |
| Java | 25. `targetJavaVersion = 25` (Adoptium toolchain); README says "Requires Java 25"; changelog 2.0.0 says "Java 25 Usage"; API jar major version 69 | `buildSrc/src/main/kotlin/standard-conventions.gradle.kts`; `changelog/2.0.0.md` |
| Other platforms | Velocity 3.3–3.5 (proxy-side HUD, only `name`/`ping` placeholders); Fabric server 26.1.x+ (needs Fabric API) | `README.md`; wiki `placeholders.md` |
| Uses NMS | Yes, one module per version (`nms/v1_21_R3` … `nms/v26_R2`). A new MC version needs a BetterHud update | `settings.gradle.kts` |
| Client pack formats | Pack overlays `betterhud_1_21_4` (formats 46–55), `betterhud_1_21_6` (56–83), `betterhud_26_1` (84–87), `betterhud_26_2` (88–96), `betterhud_26_3` (97–99) | `dist/.../pack/PackOverlay.kt` |
| Old clients | Changelog 1.14.1: "drop <=1.20.2 client support" | `changelog/1.14.1.md` |
| Bedrock | Disabled for Bedrock players by default (`disable-to-bedrock-player: true`, detected through Geyser or Floodgate) | `common-resources/config.yml`; `BukkitBootstrapImpl.register` |

### 2.2 Rendering pipeline

**1. Boss bar.** On join, BetterHud injects a Netty `ChannelDuplexHandler` into the player's pipeline and sends its own boss bar.
- Every update it sends `ClientboundBossEventPacket.createUpdateNamePacket` with the composed component (`nms/v26_R2/.../NMSImpl.kt` `PlayerBossBar.update`).
- The bar texture for the configured color (`shader.yml bar-color`, default `yellow` in the shipped file, `RED` if missing) is replaced with a transparent `background.png` (`ShaderManagerImpl.reload`, `copy("background")`/`copy("progress")`, `bars.png`).

**2. Other plugins' boss bars.** With `merge-boss-bar: true` (the default), BetterHud intercepts their boss-bar packets.
- The first foreign bar's title becomes an "additional component" rendered in the default font inside the HUD bar.
- `bossbar-line` (1–7) reserves dummy bars for further foreign bars (`NMSImpl.kt` `writeBossBar`).
- FAQ: an Ender Dragon or Wither bar breaks the alignment. The fix is `merge-boss-bar: false` (wiki `General-FAQ.md`).

**3. Fonts.** Each HUD or popup gets a generated font, `assets/<encoded ns>/font/hud_<id>_image.json` or `popup_<id>_image.json`.
- Every image frame and text glyph is a bitmap provider with a huge negative ascent that encodes an id (`util/Huds.kt` `createAscent`, `HUD_DEFAULT_BIT=13`, `HUD_MAX_BIT=10`).
- Horizontal placement uses a space font with advances from -8192 to 8192 (`resource/GlobalResource.kt`).

**4. Core shader.** `common-resources/shaders/<overlay>/text.vsh` checks `pos.y >= ui.y && ProjMat[3].x == -1`, which means GUI-projected text pushed off-screen.
- It decodes the id from `pos.y`, then applies the generated `switch(id)` case: `xGui = ui.x * <gui.x>/100`, `yGui = ui.y * <gui.y>/100`, layer, opacity, scale, and the properties `wave`, `rainbow` and `tiny_rainbow`.
- The cases are compiled at reload time (`ShaderManagerImpl.compileShader`).
- The output goes to `<overlay>/assets/minecraft/shaders/core/rendertype_text.{vsh,fsh}`; on 26.2+ the file is `text.{vsh,fsh}` (`PackOverlay.shaderCoreName`).
- The 1.21.4 overlay also ships `rendertype_text.json`, and the 1.21.6 overlay ships `shaders/include/sample_lightmap.glsl` (`common-resources/betterhud_1_21_*`).

**5. Consequence.** Positions, scales and layers are **baked into the resource pack**. Changing a y coordinate, layout geometry, scale or animation requires a reload **and the client must re-download or re-apply the pack**. Wiki `General-FAQ.md`: "Using '/hud reload' does not move the y coordinate. Don't forget you need to reapply the resourcepack." Text content and placeholder values are live.

### 2.3 Compatibility with other packs and shaders

- **Two packs cannot both override `rendertype_text` (or `text` on 26.2+).** Merging is "first file wins" or "last pack wins", depending on the merger.
  - Any other plugin or pack that ships its own `rendertype_text` core shader breaks BetterHud, or BetterHud breaks it.
  - Examples: other HUD plugins, ItemsAdder "text-effects", Nexo "generate_gif_shaders", CustomNameplates, TAB-logo tricks.
  - Wiki `Caution.md` and `How-to-install.md` tell users to disable ItemsAdder `text-effects`, and Nexo `generate_gif_shaders: false`.
  - Issue #227 is a CustomNameplates `bars.png` conflict plus an ItemsAdder shader conflict.
  - Issue #479 (open): the BetterHud text shader changes the colors of other bitmap fonts (for example a TAB logo) because of a global lightmap multiplication. In current `master`, `text.vsh` for 26.2/26.3 still does `vertexColor *= sample_lightmap(Sampler2, UV2)` outside the GUI guard.
- **CraftEngine.** If CraftEngine ships its own `rendertype_text` / `text` shaders, they collide with BetterHud's. **UNVERIFIED** whether current CraftEngine does this.
- **OptiFine, Iris, Sodium.** I found no statement in the repo or wiki. Vanilla core shaders in resource packs are generally ignored by OptiFine; Iris with a shaderpack active normally still uses vanilla core shaders for GUI text. **UNVERIFIED.**
- **Shader compile errors.** Issue #344 (open, 1.21.10): "Couldn't compile fragment shader (minecraft:core/rendertype_text): 'FogColor' redefinition". Issue #321 (open): HUD invisible on 1.21.9/1.21.10 clients with BetterHud 1.14.0. The overlay system in `master` addresses per-version shaders.

---

## 3. Data folder and config types

### 3.1 Layout of `plugins/BetterHud/`

```
config.yml  database.yml  font.yml  shader.yml  version.txt
shaders/<overlay>/text.vsh|fsh   # copied ONCE from jar; user-editable, NOT auto-updated
assets/        # PNGs referenced by images/, compasses/, texts images
fonts/         # .ttf/.otf for texts/
images/ layouts/ huds/ popups/ texts/ heads/ compasses/ backgrounds/
placeholders/  # PAPI pre-fetch tasks (Bukkit only)
packs/<name>/  # external packs: same structure, loaded BEFORE main folder
.users/<uuid>.yml   # per-player enabled non-default huds/popups/compasses + pointers (yml db)
.cache/zip-hash.txt
```

- The `default/` folder of the jar is copied **only if the data folder does not exist yet** (`BetterHudImpl.init`).
- Subfolders are created on demand (`util/Files.kt` `subFolder`).
- Any file or folder whose name starts with `-` is skipped. The shipped empty file `huds/-All file prefixed with a hyphen will be skipped.yml` demonstrates this (`util/Files.kt` `forEachAllFolder`). Packs whose name starts with `-` are skipped too (`BetterHudImpl.reload`).
- Every YAML file can hold **multiple top-level ids**. Each top-level key whose value is a map is one object (`util/Yamls.kt` `forEachAllYaml`).
- Non-`.yml` files inside config folders trigger a warning.

**Load order** (`BetterHudImpl.reload`, `managers` list):

1. Config, Minecraft, Command, Database, Listener, Placeholder, Trigger.
2. Then, **for each `packs/*` directory in alphabetical order**, then the main folder: Background → Image → Text → PlayerHead → Layout → Hud → Popup → Compass.
3. Then Shader, Player, Encode.
4. `PackGenerator.generate`.

How duplicates and cross-references resolve:

- **Layouts, huds, popups, texts and heads.** The first definition of an id wins; later duplicates log `Collision has been occurred in <type>: <id>` (`util/Maps.kt` `putSync`). Because packs load before the main folder, **a pack's id beats the same id in the user's main folder**.
- **Images.** Each folder builds a local map, then `imageMap += map`. **Later folders override earlier ones**, so the main folder overrides packs.
- **Cross-references are resolved immediately.** For example, `ImageLayout.Impl` calls `ImageManager.getImage(n)`. A layout in pack A can therefore reference images from pack A or from packs loaded before it, **but not from the main folder** (loaded later). A main-folder layout can reference pack images.

### 3.2 `config.yml` (shipped defaults, `common-resources/config.yml`; parsed in `dist/.../manager/ConfigManagerImpl.kt`)

```yaml
debug: false
auto-save-time: 300
location-provide-time: 1
debug-level: assets
metrics: true
version-check: true
number-format: "#,###.#"
tick-speed: 1                 # player update period in ticks (async). <=0 disables updating
namespace: "betterhud"
default-font-name: "font.ttf"
bossbar-line: 1               # 1..7, which bossbar line the HUD occupies
bossbar-resource-pack-line: 0
merge-boss-bar: true
disable-to-bedrock-player: true
build-folder-location: "BetterHud/build"   # relative to plugins/
pack-type: zip #folder, zip, none
enable-protection: false
enable-self-host: false
force-update: false
self-host-ip: "*"
self-host-port: 8163
merge-other-folders: []       # folders or .zip under plugins/ merged INTO BetterHud's pack
loading-head: random #steve, alex, etc.
default-hud:
  - test_hud
default-popup: []
default-compass: []
load-minecraft-default-textures: false
included-minecraft-list:
  - block
  - item
resourcepack-obfuscation: false
clear-build-folder: true #If you use Oraxen, set this to false.
use-legacy-format: false #Please don't enable it unless it is necessary.
legacy-serializer: ampersand #section, ampersand, both
minecraft-jar-version: bukkit #1.21, 1.20.4, etc.
remove-default-hotbar: false
disable-legacy-offset: true
merge-with-external-resources: true
```

- `config.yml`, `shader.yml`, `font.yml` and `database.yml` are **merged with the jar defaults and re-saved** on every reload (`configuration/PluginConfiguration.kt`). Missing keys are added; user values are kept.
- `disable-legacy-offset: false` adds a -54 px offset to every layout (`location/PixelLocation.kt` `hotBarHeight`). New installs ship `true`.
- `default-hud` in the shipped config is `[test_hud]`, which is the demo health/hunger HUD. It is shown to everyone until removed.

`shader.yml`:

```yaml
bar-color: yellow
disable-level-text: false
```

`font.yml`:

```yaml
scale: 16
height: 8
ascent: 7
merge-default-bitmap: true
use-unifont: true
```

The wiki's `shader.yml` options `hotbar: disable:` etc. **no longer exist**. Only the two keys above are read (`ShaderManagerImpl.reload`). `shader/HotBarShader.kt` exists but nothing references it.

### 3.3 `images/` (`dist/.../image/enums/ImageType.kt`, `element/ImageElement.kt`)

There are three types: `single`, `listener` and `sequence`. `file`/`files` are relative to `<working dir>/assets/`.

**Repo default** (`common-resources/default/images/default-image.yml`):

```yaml
health_empty:
  type: single
  file: health_empty.png

health_bar:
  type: listener
  file: health_bar.png
  split: 25
  split-type: left
  setting:
    listener:
      class: health

hunger_bar:
  type: listener
  file: hunger_bar.png
  split: 25
  split-type: right
  setting:
    listener:
      class: food
```

Keys:

- **`listener`:**
  - `split` (default 25): the number of pre-cut frames. Each frame is a separate PNG in the pack.
  - `split-type`: `left|right|up|down|circle|reverse_circle` (default `left`). See `SplitType.kt`.
  - `setting.listener` is **required**.
- **`sequence`:** `files: [a.png, b.png:4]`, where `:N` holds that frame for N ticks. `frame` is a global multiplier.
- **All types:**
  - `flip: [x, y]`
  - `setting.scale`
  - `setting.animation-type: loop|play_once`
  - `setting.follow`
  - `setting.children` (list, map or `"*"`) and `setting.children-mapper`
  - `conditions` and `color-overrides` inside `setting`
- **Lazy listener** (smooth damage trail), from `default/images/entity-image.yml`:

```yaml
entity_health_white:
  type: listener
  split-type: left
  split: 25
  file: "entity/white.png"
  setting:
    listener:
      class: entity_health
      lazy: true
      delay: 10
      initial-value: entity_last_health_percentage
```

  Lazy options (`ListenerManagerImpl.LazyListenerSetting`): `delay` (0), `multiplier` (0.5), `expiring-second` (5), `initial-value` (a number placeholder).
- The generated PNG name is the file path with `/` replaced by `_`, plus `_<split-type>_<split>_<n>.png` for listener frames. It is written to `assets/<ns>/textures/`.
  - PackGenerator uses `computeIfAbsent`, so **the first file with a given path wins silently**.
  - **Always namespace asset paths**, for example `assets/tfc/thirst_bar.png`.

### 3.4 `layouts/` (`dist/.../layout/*.kt`, wiki `layouts.md`)

**Repo default** (`default/layouts/default-layout.yml`):

```yaml
health:
  images:
    1:
      name: health_empty
      x: 0
      y: 0
    2:
      name: health_bar
      x: 15
      y: 4
  animations:
    duration: 60
    x-equation: 0
    y-equation: 3cos(t/30 * pi)
```

Text layout (`default/layouts/entity-layout.yml`):

```yaml
entity_health_text:
  texts:
    1:
      name: entity_font
      pattern: "Health: <#FFD800>[entity_health] // [entity_max_health]"
      placeholder-string-format:
        number: "#,###"
      y: -16
      x: -32
      scale: 0.5
      align: left
```

**Layout-level keys:**

- `x`, `y`, `opacity`
- `align`, `offset`: `left|center|right`
- `images` / `texts` / `heads`: maps of numbered entries
- `animations`: `type: loop|play_once`, `duration` (default 20), `x-equation`, `y-equation`, `opacity-equation`
- `conditions`, `color-overrides`

**Keys common to every element** (`HudLayout.Impl`):

- `name`, `x`, `y`, `opacity`
- `layer` (int)
- `outline` (`true` or `false`, or a shadow value)
- `properties: [wave, rainbow, tiny_rainbow]`
- `follow`, `cancel-if-follower-not-exists` (default true)
- `render-scale: {x, y, static-scale}`
- **`tick`** (default 1): recompute this element every N updates; `tick <= 0` means compute once (`util/Providers.kt`)
- `conditions`, `color-overrides`
- `placeholder-option`, `placeholder-string-format`

**Image entry** (`ImageLayout.Impl`):

- `color`, `scale` (default 1), `space` (default 1)
- `stack`, `max-stack`: number placeholders that repeat the image like vanilla hearts
- `reversed`, `clear-listener`

**Text entry** (`TextLayout.Impl`):

- `pattern` (**required**), `scale`, `space` (default 0)
- `align`, `line-align`, `color`
- `number-equation`, `number-format`, `disable-number-format` (**default true**)
- `background`, `background-scale`
- `emoji-pixel`, `emoji-scale`
- `use-legacy-format` (default from config), `legacy-serializer`
- `line` (default 1), `split-width` (default 200), `line-width` (default 10), `force-split`

**Head entry:** `type: standard|fancy`, `align`.

**Pattern syntax** (`PlaceholderManagerImpl.parse`):

- `[placeholder]` inserts a value.
- MiniMessage-style colors are supported, plus `<image:id>` and `<space:n>` (wiki).
- **`/` is an escape character: write `//` for a literal slash.** So "45/50" is written `"[tfc_health]//[tfc_max_health]"`.

### 3.5 `huds/` (`dist/.../hud/HudImpl.kt`)

Repo default (`default/huds/default-hud.yml`):

```yaml
test_hud:
  layouts:
    1:
      name: health
      gui:
        x: 35
        y: 100
      pixel:
        x: 0
        y: -80
    2:
      name: hunger
      gui:
        x: 65
        y: 100
      pixel:
        x: 0
        y: -80
```

Keys:

- `layouts.N.name`
- `gui: {x, y}`: percentages of the screen, 0–100, decimals allowed. `x` and `y` can also sit directly on the entry and are added to `gui`.
- `pixel: {x, y}`: GUI-pixel offset
- `tick`
- `conditions`: hud-wide, evaluated with `UpdateEvent.EMPTY`
- `default: true`: shown to every player, same as listing it in `config.yml default-hud`

### 3.6 `popups/` (`dist/.../popup/PopupImpl.kt`)

Repo default (`default/popups/entity-popup.yml`):

```yaml
entity_health_popup:
  move:
    duration: 3
    pixel:
      x-equation: 0
      y-equation: 40t
  push: true
  key-mapping: true
  triggers:
    1:
      class: entity_attack
  layouts:
    1:
      name: entity_health_image
      gui:
        x: 0
        y: 0
      pixel:
        x: 128
        y: 32
  duration: 20
```

Keys:

- `x`, `y` (gui percent)
- `move`: `duration` = max simultaneous stacked instances; `pixel`/`gui` `x-equation`/`y-equation` over `t` = slot index
- `duration`: **in update cycles**, default -1 = infinite. Implemented as `++i < duration` on each player update, so it equals ticks only when `tick-speed: 1`.
- `group` (default: the id), `unique`, `queue` (only when duration > 0), `push`, `always-check-condition`
- `sort: last|first`
- `key-mapping`: reuse the instance keyed by the event key
- `index`: a number placeholder
- `tick`
- `frame-type: local|global`
- `triggers` and `hide-triggers`: maps of `{class: <trigger>, ...}`
- `conditions`
- `default: true`

Popups can be shown via:

- a trigger;
- `/hud popup show <player> <popup>`;
- the API: `popup.show(UpdateEvent.EMPTY, hudPlayer)`;
- `CustomPopupEvent` with the `custom` trigger;
- adding the popup as a persistent object (`popup.add(player)` or `default-popup`), in which case it shows while its conditions hold.

### 3.7 `texts/` (fonts) (`dist/.../manager/TextManagerImpl.kt`, wiki `fonts.md`)

```yaml
entity_font:              # repo default texts/entity-font.yml
  merge-default-bitmap: true
  use-unifont: true
  include: []
```

Keys:

- `type: ttf` (default) or `bitmap`
- `file`: a ttf/otf in `fonts/`. Without it, the default font or unifont is used.
- `scale` (default 16), `use-unifont`, `merge-default-bitmap`
- `include`: language subsets, e.g. `korean`, `japan`, … (Latin is always included)
- `images: {key: {name: <assets file>, x, y, scale}}`: used as `<image:key>` in patterns
- For `bitmap`: `chars: {1: {file, codepoints: ["abc", ...], ascent}}`

### 3.8 Other config types

**`heads/`:**

```yaml
test_head:
  pixel: 4
```

Renders a player skin head; the layout uses `type: fancy` for a 3D head. Offline-mode servers use MineTools and HTTP skin providers; SkinsRestorer is supported.

**`backgrounds/`:** `<name>.yml` (`x`, `y`) plus a folder `<name>/left.png`, `body.png`, `right.png`. The wiki marks it "Deprecated - In BetterHud 1.9, background will recreated". The code still loads it (`BackgroundManager.kt`).

**`compasses/`** (repo `default/compasses/default_compass.yml`):

```yaml
default_compass:
  type: circle
  default: true
  file:
    n:
      name: "compass/n.png"
    point:
      name: "compass/point.png"
      y: -8
  scale: 0.75
  scale-equation: 1 - t/20
  length: 20
  space: 2
  gui:
    x: 50
    y: 0
  pixel:
    x: 0
    y: 20
  apply-opacity: true
```

Excerpt only; the real file also has `s`, `w`, `e`, `nw`, `ne`, `sw`, `se` and `chain`. `custom-icon` and `color-equation` are also available. Pointers are set with `/hud pointer set`, the Skript `point add`, or a `PointedLocationProvider`.

**`placeholders/`** (Bukkit, PAPI pre-fetch; `BukkitBootstrapImpl` `addReloadEndTask`):

```yaml
test:
  variable: online
  placeholder: "%server_online%"
  update: 20
  #async: true
```

The value is stored into `HudPlayer.getVariableMap()` and read with `[string:online]`, `[number:online]` or `[boolean:online]`. Tasks are synchronous unless `async: true`.

**`conditions`** (wiki `conditions.md`; `placeholder/Conditions.kt`), usable on images, layouts, elements, huds and popups:

```yaml
conditions:
  1:
    first: (number)papi:player_x
    second: 1
    operation: '>='
    gate: or      # and (default) | or
```

- Operations: `== != > >= < <=` for numbers; only `== !=` for strings and booleans.
- `first` and `second` must have the same type. A string literal is quoted: `"'1'"`.
- Do not use `[ ]` inside conditions.

**`color-overrides`:**

```yaml
color-overrides:
  1:
    color: "#FF8080"
    conditions: {...}
  default-color: "#FFFFFF"
```

Possible bug: `ColorOverride.builder` iterates every key, including `default-color`, as an object. See §12.

---

## 4. Placeholder system

### 4.1 Syntax

Parsed by `PlaceholderManagerImpl.find`:

```
[(type)name:arg1,arg2@equation]
```

- `(number)`, `(string)` or `(boolean)` cast the result: `toString()` and then the type's parser.
- `:a,b` passes comma-separated args. `HudPlaceholder.getRequiredArgsLength()` is enforced at parse time.
- `@<exp4j>` applies to numeric results; `t` is the value. Example: `[health@t*2]`, `[(number)papi:player_x@t*2]`. Inside text patterns, division is written `//`.
- Literals are also placeholders: `'text'` is a string, `123` a number, `true`/`false` a boolean.
- Built-in generic placeholders:
  - `number:<var>`, `string:<var>`, `boolean:<var>` read `HudPlayer.getVariableMap()`
  - `tick`, `popup_count`, `popup_index`
- Placeholder options in YAML:
  - `placeholder-option`: `evaluate` for numbers; `trim`, `join`, `replace: {from, to}` for strings; `reversed` for booleans
  - `placeholder-string-format: {number: "#,###", string: ..., boolean: ...}`
- A raw number with no format prints with `DecimalFormat("#.###")`.

### 4.2 Built-in Bukkit placeholders

Source: `bootstrap/bukkit/.../module/bukkit/BukkitStandardModule.kt`.

- **Listeners (0..1):** `health`, `vehicle_health`, `food`, `armor`, `air`, `exp`, `absorption`, plus the generic `placeholder` listener with `value` and `max` (`ListenerManagerImpl`).
- **Numbers:**
  - `health`, `max_health`, `health_percentage` (0..1), `last_health`, `last_health_percentage`, `last_damage`
  - `food`, `armor`, `air`, `max_air`, `level`, `hotbar_slot`, `absorption`, `max_health_with_absorption`, `empty_space`
  - `potion_effect_duration:<effect>`, `total_amount:<material>`, `storage:<material>`
  - `vehicle_*` variants
- **Strings:** `name`, `world`, `gamemode`, `custom_variable:<key>` (only inside a `custom`-trigger popup)
- **Booleans:** `dead`, `frozen`, `burning`, `has_main_hand`, `has_off_hand`, `has_permission:<perm>`
- **Triggers:**
  - `custom` (needs `name`; fired by `CustomPopupEvent`) and `death`
  - from the entity module (prefixed `entity_`): `entity_attack`, `entity_damage`, `entity_dead`, `entity_kill`
  - from the item module: `item_update` (`type: drop|pickup`)
  - Entity-trigger placeholders: `entity_health`, `entity_max_health`, `entity_name`, `entity_type`, `entity_custom_name`, … (`BukkitEntityModule.kt`)
- **Prefixing.** Module keys other than `standard` are prefixed `<module>_` (`manager/ModuleManager.kt`). Compatibility placeholders are prefixed with the lower-cased plugin name, e.g. `mmocore_mana`, `worldguard_in_region:<id>`, `vault_money`, `skript_variable:...`, `parties_*`, `mythicmobs_*` (`manager/CompatibilityManager.kt`, wiki `placeholders.md`).

### 4.3 PlaceholderAPI

- Two ways to use it:
  - `[papi:<identifier>]`, i.e. `%<identifier>%`. It returns a **string**, so cast with `(number)papi:...` for bars and conditions. It is evaluated **inline on BetterHud's async thread** (`BukkitBootstrapImpl.onEnable`, `stringContainer.addPlaceholder("papi", ...)`).
  - The sync pre-fetch through `placeholders/*.yml`, described above.
- Wiki `Optimization-Tip.md`: "Do not use PAPI unless it is absolutely necessary… PAPI have to search what this placeholder is in every tick… Condition comparison with PAPI is very, very inefficient."
- **Do not route TFC data through PAPI. Register native placeholders.**

### 4.4 Update frequency

- Each `HudPlayer` runs `asyncTaskTimer(1, tick-speed)` that calls `update()` (`player/HudPlayerImpl.kt`). With the default `tick-speed: 1`, that is **20 times per second per player, asynchronously**.
- Every element's placeholder functions run on each update, unless the element, hud or popup sets `tick: N`. In that case the value is cached and recomputed when `player.tick % N == 0` (`util/Providers.kt` `TickProvider`).
- `HudPlayer.update()` can be called manually to force an immediate refresh.

---

## 5. Java API

### 5.1 Coordinates

From README and Maven Central:

```kotlin
repositories { mavenCentral() }
dependencies {
    compileOnly("io.github.toxicity188:BetterHud-bukkit-api:2.0.0") // latest on Central; transitively BetterHud-api:2.0.0
}
java { toolchain.languageVersion = JavaLanguageVersion.of(25) } // API classes are Java 25 (class v69)
```

- The 2.0.0 POM declares these **compile-scope** transitive dependencies: `BetterCommand:1.4.3`, `adventure-api` / `minimessage` / `serializer-legacy` / `serializer-gson` 4.26.1, `kotlin-stdlib` 2.3.21 and `BetterHud-api` 2.0.0. Keep the dependency `compileOnly` and never shade it. Excluding `net.kyori` from it is advisable so Paper's Adventure wins on the compile classpath.
- `HudPlayer` extends `kr.toxicity.command.BetterCommandSource`, so BetterCommand must be on the compile classpath. The transitive dependency already provides it.
- Maven Central has `BetterHud-bukkit-api` versions 1.9.2 … 1.14.1 and 2.0.0. SNAPSHOT builds (2.1/2.2) are not on Central. For those, see `jitpack.yml`; **UNVERIFIED** whether JitPack works.
- `plugin.yml` of a consumer (pattern from BetterHud-MMOCore): `depend: [BetterHud]` or `softdepend: [BetterHud]`.

### 5.2 Main types (`api/src/main/java/kr/toxicity/hud/api/...`)

| Type | Methods |
|---|---|
| `BetterHudAPI` | `static BetterHud inst()`. Set when BetterHud's plugin class is constructed, so it is available from our `onEnable` |
| `BetterHud` | `reload(ReloadFlagType...)`, `reload(ReloadInfo)` → `ReloadState`; `isOnReload()`; `addReloadStartTask(Runnable)`, `addReloadEndTask(Consumer<ReloadState>)`; `getPlaceholderManager()`, `getListenerManager()`, `getTriggerManager()`, `getHudManager()`, `getPopupManager()`, `getCompassManager()`, `getPlayerManager()`, `getShaderManager()`, `getConfigManager()`, `getDatabaseManager()`, `getTextManager()`; `bootstrap()`; `loadAssets(prefix, File)`; `getWidth(codepoint)`; `isMergeBossBar()`; `getDefaultKey()` |
| `BetterHudBootstrap` | `dataFolder()`, `scheduler()` (`HudScheduler`: `task`, `task(LocationWrapper, …)`, `taskLater`, `asyncTask`, `asyncTaskLater`, `asyncTaskTimer`), `isFolia()`, `isPaper()`, `sendResourcePack(player)`, `minecraftVersion()`, `mcmetaVersion()`, `logger()` |
| `PlaceholderManager` | `getNumberContainer()`, `getStringContainer()`, `getBooleanContainer()` → `PlaceholderContainer<T>.addPlaceholder(String, HudPlaceholder<T>)`, `getAllPlaceholders()` |
| `HudPlaceholder<T>` | `Function<HudPlayer,T> invoke(List<String> args, UpdateEvent reason)`; `getRequiredArgsLength()`; factories `HudPlaceholder.of((args, reason) -> player -> value)` and `HudPlaceholder.<T>builder().requiredArgsLength(n).function(...).build()` / `.add(name, container)`; `PlaceholderFunction.of(Function<HudPlayer,T>)` |
| `ListenerManager` | `addListener(String name, Function<YamlObject, Function<UpdateEvent, HudListener>>)` |
| `HudListener` | `double getValue(HudPlayer)` in the range 0..1; a value below 0 means "ignore / animate" (`EMPTY`); `ZERO`; `clear(player)` |
| `TriggerManager` | `addTrigger(String, Function<YamlObject, HudTrigger<?>>)`. Bukkit: implement `HudBukkitEventTrigger<E>` (`getEventClass`, `getKey(E)`, `registerEvent(BiConsumer<UUID, UpdateEvent>)`) |
| `PlayerManager` | `getHudPlayer(UUID)` (nullable before join or after quit), `getAllHudPlayer()`, `addLocationProvider(...)` |
| `HudPlayer` | `uuid()`, `handle()` (Bukkit `Player`; on Folia **a wrapper CraftPlayer**, so compare by UUID), `update()`, `isHudEnabled()` / `setHudEnabled(bool)`, `getVariableMap()` (mutable, concurrent), `getHuds()` / `getPopups()` / `getCompasses()`, `getHudObjects()`, `setBarColor`, `pointers()`, `reload()`, `save()`, `getTick()` |
| `HudObject` (`Hud`, `Popup`, `Compass`) | `add(player)` / `remove(player)` (persisted in `.users/` for non-default objects), `isDefault()`, `getName()` |
| `HudManager` / `PopupManager` | `getHud(name)`, `getAllNames()`, `getAllHuds()`; `getPopup(name)` |
| `Popup` | `show(UpdateEvent, HudPlayer)` → `PopupUpdater` (`update()`, `remove()`, `getIndex()`, `setIndex()`) or null; `hide(player)` removes the whole group |
| `ShaderManager` | `addConstant(k, v)`; `addTagSupplier(ShaderType.TEXT_VERTEX / TEXT_FRAGMENT, supplier)` to inject GLSL at `#GenerateOtherMainMethod` / `#GenerateOtherDefinedMethod` |
| `ReloadState` | sealed: `Success(Map<String, byte[]> resourcePack, File directory, long time)`, `Failure(Throwable)`, `ON_RELOAD` |
| `ReloadFlagType` | `FORCE_GENERATE_RESOURCE_PACK` |

**Bukkit events** (`api/bukkit-api/.../event`):

- `HudPlayerJoinEvent`: fired on the main thread after the player's DB data loads.
- `HudPlayerQuitEvent`
- `HudUpdateEvent`
- `PluginReloadStartEvent`, `PluginReloadedEvent(ReloadState)`: both **async** (`super(true)`).
- `CustomPopupEvent(Player, name)` with mutable `getVariables()`: fire it to show popups that have `triggers: {1: {class: custom, name: <name>}}`. Read the variables with `[custom_variable:key]`.
- `UpdateItemEvent`

All share one `HandlerList` (`BetterHudEvent.HANDLER_LIST`).

### 5.3 Example code

Written by me against the signatures above; not copied from the repo.

```java
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import kr.toxicity.hud.api.listener.HudListener;
import java.util.function.Function;

void registerBetterHud(TfcPlayerCache cache) {          // call in onEnable (softdepend BetterHud)
    var api = BetterHudAPI.inst();
    var pm  = api.getPlaceholderManager();

    // number placeholders — MUST be thread-safe (called async, 20x/s/player)
    pm.getNumberContainer().addPlaceholder("tfc_thirst",
        HudPlaceholder.of((args, reason) -> p -> cache.snapshot(p.uuid()).thirst()));
    pm.getNumberContainer().addPlaceholder("tfc_thirst_max",
        HudPlaceholder.of((args, reason) -> p -> 100));
    pm.getStringContainer().addPlaceholder("tfc_date",
        HudPlaceholder.of((args, reason) -> p -> cache.calendarString()));
    pm.getBooleanContainer().addPlaceholder("tfc_hud_visible",
        HudPlaceholder.of((args, reason) -> p -> cache.snapshot(p.uuid()).hudVisible()));

    // placeholder with an argument: [tfc_nutrient:grain]
    HudPlaceholder.<Number>builder()
        .requiredArgsLength(1)
        .function((args, reason) -> {
            var nutrient = Nutrient.valueOf(args.get(0).toUpperCase()); // parse ONCE at config load
            return p -> cache.snapshot(p.uuid()).nutrient(nutrient);
        })
        .add("tfc_nutrient", pm.getNumberContainer());

    // 0..1 listener for bars: class: tfc_thirst
    api.getListenerManager().addListener("tfc_thirst",
        yaml -> event -> (HudListener) p -> cache.snapshot(p.uuid()).thirst() / 100.0);
}
```

Showing and hiding per player:

```java
var hp = BetterHudAPI.inst().getPlayerManager().getHudPlayer(player.getUniqueId());
var hud = BetterHudAPI.inst().getHudManager().getHud("tfc_main");
if (hp != null && hud != null) hud.add(hp);            // persisted in BetterHud/.users/<uuid>.yml
var popup = BetterHudAPI.inst().getPopupManager().getPopup("tfc_item_temp");
if (hp != null && popup != null) popup.show(kr.toxicity.hud.api.update.UpdateEvent.EMPTY, hp);
hp.setHudEnabled(false);                               // turns all BetterHud output off for this player
```

### 5.4 Threading

- **Placeholders and listeners** are invoked from `HudPlayerImpl.update()`, which runs on `asyncTaskTimer`. On Folia that is the Folia async scheduler.
  - The built-in placeholders read `bukkitPlayer.health` etc. through a "Folia adapted" `CraftPlayer` that bypasses thread checks (`NMSImpl.getFoliaAdaptedPlayer`).
  - World access from a placeholder (biome, blocks) **throws on Folia** (issue #5 stack trace: `Cannot read world asynchronously` from a PAPI expansion).
  - **Rule:** compute TFC values on the owning region thread, publish immutable snapshots (`ConcurrentHashMap<UUID, Snapshot>`), and have placeholders only read them.
- **`HudPlaceholder.invoke(args, reason)`** is called when an element is built (at reload, or when the object is added to a player). Do the argument parsing there, not per tick.
- **`reload()`** runs synchronously on the calling thread. The `/hud reload` command wraps it in `asyncTask`. There is an `AtomicBoolean` guard: a concurrent call returns `ReloadState.ON_RELOAD`. During a reload, `update()` sends nothing (`PLUGIN.isOnReload`).
- **Registration storage.** Placeholder, listener and trigger registries are plain `HashMap`s. Register them during `onEnable` on the main thread.
- **Custom Bukkit triggers.** `registerEvent` is called again on every reload, for every popup that uses the trigger.
  - BetterHud's own triggers register on `((BukkitBootstrap) bootstrap).triggerListener()`, which it unregisters at reload start (`BukkitBootstrapImpl` `addReloadStartTask { HandlerList.unregisterAll(listener) }`; `util/Bukkits.kt createBukkitTrigger`).
  - A third-party trigger that registers its own listener object would leak a duplicate listener on every reload. Register against `triggerListener()` with the BetterHud `Plugin` as owner, or just fire `CustomPopupEvent`.

---

## 6. Hiding vanilla HUD and GUI scale

### 6.1 What BetterHud offers

| Option | Effect | Source |
|---|---|---|
| `config.yml remove-default-hotbar: true` | Adds the jar's `empty/` asset tree into the pack: transparent `textures/gui/icons.png` and **all** `textures/gui/sprites/hud/*` sprites (48 heart sprites, food, air and bursting air, armor, hotbar plus selection and offhand, crosshair and attack indicators, XP bar, jump bar, effect backgrounds, locator bar) | `ConfigManagerImpl.reload`; `common-resources/empty/...` |
| `shader.yml disable-level-text: true` | Uncomments the `//HideExp` lines in `text.vsh`, hiding the green XP level number | `ShaderManagerImpl.reload`; `shaders/*/text.vsh` |
| per-player `setHudEnabled(false)` or `/hud turn off` | Hides BetterHud output only | `HudPlayer` |

Hotbar relocation (`shader.yml hotbar:`, wiki) has been **removed** from current code.

### 6.2 Recommended approach for TFC

Hide hearts, food, air and armor but keep the hotbar, crosshair and XP:

- Ship transparent PNGs at `assets/minecraft/textures/gui/sprites/hud/heart/*.png` (all 48, including `container*`, `vehicle_*`, `*_blinking`, `hardcore_*`), `food_*.png` (6), `air.png`, `air_bursting.png`, `air_empty.png`, and `armor_*.png` if wanted.
  - These names come from `common-resources/empty/...`, which BetterHud keeps current for 1.21.x and 26.x.
  - `icons.png` is only relevant to pre-1.20.2 clients, which BetterHud no longer supports.
- Delivery options:
  - (a) inside our CraftEngine resource pack (best if we already ship CraftEngine content);
  - (b) a folder `plugins/<ourplugin>/hud-pack/` listed in BetterHud `merge-other-folders` (this requires editing BetterHud's `config.yml`).
- Mount health (`heart/vehicle_*`) is drawn instead of the food bar when riding. Decide whether to hide it.
- Server-side alternatives are limited. Bukkit's `Player#setHealthScale` changes the heart count, not visibility, and food cannot be hidden server-side. A resource pack is the standard approach. **UNVERIFIED** for 26.x whether Mojang added a GUI-element toggle.

### 6.3 GUI scale and resolution

- The shader computes `ui = ceil(2/ProjMat)` in GUI pixels. `gui.x`/`gui.y` are a percentage of the GUI-scaled screen; `pixel` offsets are GUI pixels and scale with the GUI scale like vanilla elements (`text.vsh`, `ShaderManagerImpl.compileShader`).
- Wiki `huds.md`: positions drift between GUI scales unless HUD `gui` coordinates are only 0, 50 or 100. Anchor at an edge or the center and do the fine placement with layout pixel offsets.
  - Vanilla's bar rows sit around `gui: {x: 50, y: 100}` with pixel offsets about -91 to +91 horizontally and -39 vertically, relative to a 182 px hotbar. **UNVERIFIED exact offsets; tune in-game.**
- `render-scale.static-scale: true` keeps an element's size constant regardless of the GUI scale. The wiki recommends positioning such elements through HUD coordinates.
- The shader checks `pos.y >= ui.y`, so elements are placed relative to the real screen size: any resolution works.

---

## 7. Resource-pack generation and merging

### 7.1 Output

`PackGenerator.generate` plus `PackType`:

- The pack contains `pack.mcmeta` (with overlays), `pack.png`, `assets/<namespace (encoded)>/font|textures/...`, `assets/minecraft/textures/gui/(sprites/boss_bar|bars.png)`, and the version overlays with core shaders.
- **`pack-type`:**
  - `zip`: writes `plugins/BetterHud/build.zip` (the `build-folder-location` value plus `.zip`). It skips rewriting when the SHA-256 of the content is unchanged (`.cache/zip-hash.txt`; changelog 1.14.0 "pack hash to avoid generating same resource pack").
  - `folder`: writes into `build-folder-location`. With `clear-build-folder`, it deletes stale files outside `assets/<namespace>`.
  - `none`: in-memory only. Use this for Polymer, and for Nexo per the How-to-install page.
- **`enable-self-host: true`:** BetterHud serves the zip from a built-in HTTP server on `self-host-port` (8163). `self-host-ip: "*"` resolves the public IP through `checkip.amazonaws.com`. It sends the pack on join and after reload (`PackUploader.kt`, `BukkitBootstrapImpl.sendResourcePack`, `setResourcePack(uuid, url, sha1, null, false)`, i.e. not forced).
- **`merge-other-folders: [..]`** merges folders or `.zip` files under `plugins/` *into* BetterHud's pack. `pack.mcmeta` overlays are merged.
- **`ReloadState.Success.resourcePack()`** exposes the whole pack as `Map<path, bytes>`. A plugin that builds its own pack can take this from `addReloadEndTask` and set `pack-type: none`.

### 7.2 CraftEngine (built in since 1.14.0, "auto-merge with Nexo and CraftEngine")

Source: `bootstrap/bukkit/.../compatibility/craftengine/CraftEngineCompatibility.kt`.

```kotlin
override fun start() {
    ConfigManagerImpl.preReload()
    if (ConfigManagerImpl.mergeWithExternalResources) (BOOTSTRAP as BukkitBootstrapImpl).skipInitialReload = true
    registerListener(object : Listener {
        @EventHandler
        fun AsyncResourcePackCacheEvent.generate() {
            if (!ConfigManagerImpl.mergeWithExternalResources) return
            when (val result = PLUGIN.reload()) {
                is ReloadState.Success -> {
                    result.directory()?.let {
                        when {
                            it.isFile -> cacheData().externalZips().add(it.toPath())
                            it.isDirectory -> cacheData().externalFolders().add(it.toPath())
                        }
                        info("Successfully merged with CraftEngine.")
                    }
                }
                ...
```

- Active when CraftEngine is enabled (`CompatibilityManager`) and `merge-with-external-resources: true` (the default).
- BetterHud then **skips its own ServerLoadEvent reload**. Every CraftEngine pack build triggers a full BetterHud reload and merge, and CraftEngine handles distribution.
- **Do not also enable BetterHud self-host.**
- `pack-type` must not be `none`, or `directory()` is null and nothing merges.
- **Consequence:** after changing BetterHud content, the user must trigger a **CraftEngine** pack rebuild for clients to receive it. A plain `/hud reload` regenerates `BetterHud/build.zip`, but CraftEngine does not re-merge it. **UNVERIFIED** which CraftEngine command does this (probably `/ce reload pack` or `/ce reload all`).
- Compiled against `net.momirealms:craft-engine-bukkit:26.9.1` (`bootstrap/bukkit/build.gradle.kts`). Issue #464 (open, 2026-07) "Will there be plans to support craftengine?" and #254 (CraftEngine MiniMessage image tags) ask for more. Reading comments was blocked.
- **Shader collision risk:** if CraftEngine or another of its packs provides `rendertype_text` / `text` core shaders, the merged result is undefined. **UNVERIFIED.**

### 7.3 Nexo and ItemsAdder

- **Nexo:** same mechanism through `NexoPrePackGenerateEvent` → `addResourcePack(dir)` (`NexoCompatibility.kt`). The wiki (`Merge-with-external-resource.md`) recommends either `build-folder-location: Nexo/pack/external_packs/BetterHud` + `pack-type: zip`, or the auto-merge, plus Nexo `generate_gif_shaders: false`.
- **ItemsAdder (manual):** either `merge_other_plugins_resourcepacks_folders: ["BetterHud/build"]` or `build-folder-location: ItemsAdder/contents/BetterHud/resourcepack` + `pack-type: folder`. Also `effects.text-effects.enabled: false`, `hide-scoreboard-numbers: false` and `custom-font.enabled: false` (wiki `How-to-install.md`).

---

## 8. Auto-install strategy

What the code supports:

1. **Put our content in `plugins/BetterHud/packs/<ours>/`.** It is a first-class, isolated location (`BetterHudImpl.reload`; `supportExternalPacks = true` for the Image, Text, Layout, Hud, Popup, Compass, PlayerHead and Background managers).
   - Structure: `packs/terrafirma/{assets,fonts,images,layouts,huds,popups,texts}/`.
   - Prefix every id with `tfc_` and every asset path with `tfc/`. Collisions are first-wins with a warning (ids) or silently first-wins (texture file names).
   - Our layouts must reference only our own images and texts, or ones from packs that sort before ours alphabetically. Main-folder images load later and cannot be referenced from our layouts.
2. **No Java API registers HUDs, layouts or images programmatically.** All HUD objects come from YAML. The API covers placeholders, listeners, triggers, shader tags and per-player add/remove/show. So generating YAML files is required.
3. **Timing.**
   - Declare `softdepend: [BetterHud]` (or `depend`) and `loadbefore` nothing.
   - In `onEnable`: write and refresh the files, then register placeholders, listeners and triggers. BetterHud's first full reload happens at `ServerLoadEvent`, or later inside CraftEngine's pack build. Both come after all `onEnable`s, so no explicit reload is needed at startup.
   - For runtime changes (our `/tfc reload`), call `BetterHudAPI.inst().reload()` from an async task and handle `ReloadState`. With CraftEngine, also trigger a CraftEngine pack rebuild.
4. **Preserving user edits** (our design):
   - Keep `packs/terrafirma/.tfc-manifest.yml` with the SHA-256 of each file we generated, plus the generator version.
   - On startup, overwrite a file only if its current hash equals the recorded hash, meaning the user has not edited it. Otherwise leave it and log once, or write `<file>.new` next to it.
   - Offer a config flag `betterhud.auto-install: true|false` in our plugin. If a user renames the folder to `-terrafirma`, BetterHud skips it, so we must not recreate it: check for `packs/-terrafirma` too.
   - Users cannot override our hud or layout ids by redefining them in the main folder, because packs load first and win. They must edit our files, disable them, or copy them under new ids.
5. **The BetterHud demo HUD.** `default-hud: [test_hud]` in `config.yml` shows a demo health/hunger HUD to everyone. Options:
   - document that admins should remove it;
   - have our plugin rewrite `default-hud` (invasive, but `config.yml` is re-saved and merged by BetterHud, so the edit persists);
   - rely on our hud being `default: true` and tell users to clean up.
   Default objects cannot be removed per player persistently: `HudPlayerImpl.reload` re-adds them.
6. **Showing our HUD.**
   - Option 1: put `default: true` in our hud YAML so every player gets it.
   - Option 2: leave it non-default and call `hud.add(hudPlayer)` on `HudPlayerJoinEvent`. Non-default objects are persisted per player in `.users/<uuid>.yml`, or MySQL via `database.yml`.
   - Use `conditions` (for example `[tfc_hud_visible]`) for context-based hiding. Use `hp.setHudEnabled(false)` to hide everything.
7. **The shader cache folder.** `BetterHud/shaders/<overlay>/` is a cache copied from the jar **only when missing** (`ShaderManager.ShaderType.lines`). Changelog 2.0.0: "Need to change BetterHud/shaders". After upgrading BetterHud, stale shaders persist until that folder is deleted. Our installer could warn when `BetterHud/version.txt` changes.

---

## 9. Performance

- **Packets.** One boss-bar `UpdateName` packet per player every `tick-speed` ticks: 20 per second by default, sent unconditionally even when unchanged (`HudPlayerImpl.update` → `NMSImpl.PlayerBossBar.update`). Wiki: "Network usage is very low", and since 1.11 it uses a third of 1.0's bandwidth.
- **CPU.** Every element's placeholder and condition is evaluated per player per update, asynchronously. The wiki reports a 500-player server: "TPS has no issue… CPU usage can be high but it can be optimized."
- **Recommendations** (wiki `Optimization-Tip.md` and the code):
  - Avoid PAPI. Native placeholders are pre-parsed, while PAPI re-parses every tick.
  - Use `tick: N` on slowly-changing elements: calendar/date, climate, nutrition (e.g. `tick: 20`).
  - Turn the HUD off for AFK players (`setHudEnabled(false)`).
  - Raise `tick-speed` globally (2–4) if needed. Popup `duration` and animation timing scale with it.
  - Make placeholder functions O(1) reads from a snapshot.
- **Resource-pack size.**
  - Every `split` frame is a PNG and every animation frame duplicates glyphs and shader cases. The default layout animation `duration: 60` creates 60 variants.
  - Avoid layout `animations` for static TFC bars.
  - Keep `split` moderate: 25–50 for a 50–80 px bar.
  - Use `include:` to limit font languages.
  - `load-minecraft-default-textures: false` (the shipped default) keeps the pack small.

---

## 10. Gotchas

1. **Java 25 for both the server and our compile**, if we compile against the API. A Paper 1.21.x server on Java 21 cannot run BetterHud 2.x. Use BetterHud 1.14.x for Java 21 (**UNVERIFIED**: 1.14.x target JVM).
2. **Placeholders must exist before the YAML is parsed**, otherwise "this placeholder not found" (`PlaceholderManagerImpl.find`) and that element, layout and hud fail. If our plugin enables after BetterHud's first reload (for example a hot-loaded plugin), call `reload()`.
3. **`/` is an escape character in text patterns.** Write `[a]//[b]` to get `a/b`. Division inside `@equation` in a pattern also uses `//` (wiki `operations.md`).
4. **`disable-number-format` defaults to `true`.** Numbers then print with `#.###`, unless `placeholder-string-format.number` is set. For "45/50", set `placeholder-string-format: {number: "#"}` on the text element.
5. **Placeholder types are strict in conditions.** Number vs `'string'`, cast with `(number)` (wiki `conditions.md`).
6. **Position, scale or layer changes need a pack re-apply on the client.**
7. **Two core shaders can't coexist** (BetterHud vs ItemsAdder text effects, Nexo gif shaders, CustomNameplates, other HUD plugins). Issue #479: the BetterHud shader changes the colors of other custom bitmap fonts.
8. **Boss bars of the Ender Dragon or Wither** shift the HUD. Use `merge-boss-bar: false` (wiki FAQ). Other plugins' boss bars are captured into the HUD bar by default.
9. **Popup `duration` counts update cycles, not real ticks** (`PopupImpl.show0`).
10. **`HudPlayer` is null for Bedrock players by default, and before join registration.** It is registered at `PlayerJoinEvent` priority HIGHEST, and DB loading is async.
11. **On Folia, `HudPlayer.handle()` is a wrapper `CraftPlayer`.** Use UUIDs.
12. **With CraftEngine or Nexo, BetterHud does not reload at server start.** It reloads when they build their pack. Our `PluginReloadedEvent` listeners fire then.
13. **Collisions of texture file names across packs are silent** (`PackGenerator.addTask` uses `computeIfAbsent`).
14. **The wiki is partly outdated:** hotbar shader options, the shader-core file list in the FAQ, `/hud` vs `/betterhud`.
    - Commands: root `hud`, aliases `betterhud` and `bh`.
    - Permissions: `betterhud.reload`, `betterhud.hud.add|remove`, `betterhud.popup.show|hide|add|remove`, `betterhud.turn.on|off(.admin)`, `betterhud.parse`, …
    - `/hud parse <text>` tests a pattern in chat, which is useful when debugging our placeholders.
15. **`legacy-serializer` mismatches** make PAPI strings with `&`/`§` codes break the HUD (wiki FAQ).
16. **Players lose foreign boss bars briefly on join or reload.** `HudPlayerBukkit.initBossBar` removes them and re-adds them 20 ticks later.

---

## 11. Proposed TFC skeleton

**This section is our own design**, using only keys verified above. It is not copied from the repo.

`plugins/BetterHud/packs/terrafirma/images/tfc-images.yml`:

```yaml
tfc_health_bg:
  type: single
  file: tfc/health_bg.png
tfc_health_fill:
  type: listener
  file: tfc/health_fill.png
  split: 50
  split-type: left
  setting:
    listener:
      class: placeholder
      value: tfc_health        # registered number placeholder
      max: tfc_max_health
tfc_thirst_fill:
  type: listener
  file: tfc/thirst_fill.png
  split: 40
  split-type: left
  setting:
    listener:
      class: tfc_thirst        # registered via ListenerManager
tfc_hunger_fill:
  type: listener
  file: tfc/hunger_fill.png
  split: 40
  split-type: left
  setting:
    listener:
      class: food              # built-in vanilla food 0..20
```

`.../texts/tfc-font.yml`:

```yaml
tfc_font:
  merge-default-bitmap: true
  use-unifont: true
```

`.../layouts/tfc-layouts.yml`:

```yaml
tfc_vitals:
  images:
    1: {name: tfc_health_bg, x: -91, y: -40}
    2: {name: tfc_health_fill, x: -90, y: -39}
    3: {name: tfc_hunger_fill, x: 10, y: -39}
    4: {name: tfc_thirst_fill, x: 10, y: -33}
  texts:
    1:
      name: tfc_font
      pattern: "[tfc_health]//[tfc_max_health]"
      placeholder-string-format:
        number: "#"
      align: center
      x: -50
      y: -40
      scale: 0.5
      layer: 1
tfc_info:
  texts:
    1:
      name: tfc_font
      pattern: "[tfc_date]  [tfc_ambient_temp]°C"
      tick: 20
      align: right
      x: -4
      y: 4
      scale: 0.5
```

`.../huds/tfc-huds.yml`:

```yaml
tfc_main:
  default: true
  conditions:
    1: {first: tfc_hud_visible, second: true, operation: "=="}
  layouts:
    1: {name: tfc_vitals, gui: {x: 50, y: 100}}
    2: {name: tfc_info,   gui: {x: 100, y: 0}}
```

`.../popups/tfc-popups.yml` (item temperature when holding hot items):

```yaml
tfc_item_temp:
  default: true          # shown whenever its condition holds; no duration
  conditions:
    1: {first: tfc_held_temp, second: 0, operation: ">"}
  layouts:
    1: {name: tfc_item_temp_layout, gui: {x: 50, y: 100}, pixel: {x: 0, y: -60}}
```

Pixel offsets are placeholders; tune them in-game.

---

## 12. UNVERIFIED / open questions

1. **OptiFine, Iris and Sodium.** Is the HUD visible with Iris plus a shaderpack, or with OptiFine? No primary statement found.
2. **CraftEngine.**
   - Does CraftEngine ship its own `rendertype_text`/`text` core shaders that would collide?
   - Which CraftEngine command re-triggers `AsyncResourcePackCacheEvent`, so BetterHud content changes reach clients?
   - When does CraftEngine fire the event at startup relative to other plugins' `onEnable`?
   - Issue #464 comments were unreadable.
3. **The `rendertype_text`/`text` lightmap color bug** (#479) on current builds. 26.2/26.3 `text.vsh` still multiply by the lightmap outside the GUI-only guard.
4. **Whether 2.2.0 will be released to Maven Central.** Only 2.0.0 is there. The API diff 2.0.0 → master touches only `NMSVersion`, `MinecraftVersion`, `BetterHudDependency`, `BetterHud` constants and `ShaderManager.ShaderType.lines(String overlay)`. Placeholder, listener, trigger and HUD APIs are unchanged.
5. **The Java target of BetterHud 1.14.x**, in case a Java-21 fallback is needed.
6. **Exact vanilla-aligned pixel offsets** for bars at `gui {50,100}`.
7. **Limit on distinct shader ids.** HUD_MAX_BIT=10 suggests at most about 1023 unique (gui, scale, layer, opacity, property) combinations, inferred from `util/Huds.kt`. Not confirmed.
8. **`color-overrides.default-color` parsing.** `ColorOverride.builder` iterates all entries as objects, so a `default-color` string may throw. Inferred from code only; untested.
9. **A 26.x gamerule or option to hide vanilla HUD elements server-side.** Not researched.
10. **The `BetterHud-api-2.0.0.pom`/`.jar` direct URL returned 404** while `maven-metadata.xml` lists 2.0.0. It is probably a CDN or path quirk; Gradle resolution should be verified in our build.
