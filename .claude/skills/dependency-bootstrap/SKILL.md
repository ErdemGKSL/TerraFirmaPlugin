---
name: dependency-bootstrap
description: How this plugin automatically installs, verifies and configures its three client-side dependencies (CraftEngine, BetterModel, BetterHud): dependency declaration and load order in paper-plugin.yml, version compatibility checks, optional hash-pinned auto-download of missing jars, writing our generated content into each plugin's folder at the right lifecycle moment, safe edits to their config files, user-edit protection via a hash manifest, first-run/restart handling, and the runtime reload sequence. Use this whenever a task touches plugin startup, onLoad/onEnable ordering, installing or updating content in CraftEngine/BetterModel/BetterHud folders, editing their config.yml, version support, "/tfp reload", or when something works only after a manual restart or reload, even if the user just says "it should set itself up".
---

# Auto-installing and configuring CraftEngine, BetterModel, BetterHud

Goal: a server owner drops in our jar (plus the three plugins, or lets us fetch them), restarts once, and gets a working TFC server. That means no manual YAML copying and no "forgot to enable X". At the same time we never trample an owner's deliberate edits.

Per-plugin facts come from the `craftengine`, `bettermodel` and `betterhud` skills and their research reports. This skill is the orchestration.

## 1. Declaration and load order (`paper-plugin.yml`)

```yaml
name: TerraFirmaPlugin            # provisional
main: dev.tfp.paper.TfpPlugin
bootstrapper: dev.tfp.paper.TfpBootstrap   # datapack discovery (biomes), Brigadier commands
loader: dev.tfp.paper.TfpLoader            # Caffeine/JCTools via MavenLibraryResolver
api-version: '1.21.4'             # lowest supported; verify the string format (tooling report §2.4)
load: STARTUP                     # needed so our generator applies to the default worlds
folia-supported: true
dependencies:
  server:
    CraftEngine: { load: BEFORE, required: true,  join-classpath: true }
    BetterModel: { load: BEFORE, required: true,  join-classpath: true }
    BetterHud:   { load: BEFORE, required: true,  join-classpath: true }
```

Read each dependency's exact `name:` from its jar's plugin descriptor before finalizing (open question in the tooling report). `load: BEFORE` means *they* load before *us*.

## 2. Lifecycle: what happens when

| Moment | Our action | Why then |
|---|---|---|
| bootstrap | Register datapack (custom biomes, damage types, tags) and commands | Only possible in a Paper bootstrapper |
| `onLoad()` | Version check. Write/refresh the **CraftEngine pack** (`plugins/CraftEngine/resources/tfp/`). Write the **BetterHud pack** (`plugins/BetterHud/packs/tfp/`). Apply config edits. Register **CraftEngine behaviours/functions/conditions** | CE parses configs in its own onEnable (with `delay-configuration-load: false`, which we need for startup worldgen). BetterHud parses at ServerLoad or during CE's pack build. Both come later. |
| `onEnable()` | Subscribe to **BetterModel** events (`ModelAssetsEvent`, `CreateEntityTrackerEvent`, `PluginEndReloadEvent`). Register **BetterHud** placeholders/listeners. Our listeners, schedulers, packet layer | BetterModel's event bus ignores subscriptions before we are enabled. BetterHud resolves placeholders at its first parse, which happens after all onEnables. |
| `CraftEngineReloadEvent(first)` | Cache CE block/item definitions (and refresh them on every later reload) | Definitions don't exist earlier |
| BetterModel `PluginEndReloadEvent(Success)` | Mark models ready. Re-ensure trackers on loaded entities | Models don't exist earlier |
| Ready gate | Our "fully ready" flag = CE loaded ∧ BM models ready ∧ our registries loaded. The self-test harness waits on it | One gate instead of scattered checks |

## 3. Version compatibility
At `onLoad`, read the installed versions (`getPluginMeta().getVersion()`) and the MC version (`ServerBuildInfo`). Compare them against `content/compat.yml`, generated at build time from tested combinations, e.g.:
```yaml
minecraft: { min: "1.21.4", tested: ["1.21.11", "26.2", "26.3"] }
CraftEngine: { min: "26.9.1" }
BetterModel: { min: "3.5.1" }
BetterHud:   { min: "2.0.0" }
java: 25
```
- Below minimum → refuse to enable, with a clear message naming the plugin and the required version.
- Untested newer version → warn once and continue.
- Java < 25 → refuse, because BetterModel and BetterHud can't run anyway.

## 4. Optional auto-download of missing dependencies
Only if `auto-install.download-missing: true` (opt-in, default **false**: downloading executable code needs the owner's explicit consent).
- Sources: Modrinth version ids pinned in `compat.yml` with **SHA-512** hashes. Verify the hash before saving the jar into `plugins/`. Never fetch "latest".
- A dependency can't be loaded mid-startup. After downloading, log exactly what was installed, disable ourselves, and ask for a restart.
- Never replace or upgrade a jar the owner already installed.

## 5. Writing content into other plugins' folders
One installer handles all three targets:
- **Manifest:** `<target>/.tfp-manifest.json` maps each file path to the sha256 we wrote and our content version.
- **Overwrite rule:** current hash == manifest hash (untouched by the owner) → overwrite with the new content. Different (owner edited it) → leave it, write `<file>.tfp-new` beside it, and log one summary line. Missing file that we wrote before → the owner deleted it deliberately, so don't recreate it unless `auto-install.restore-deleted: true`.
- **Opt-out:** the owner can disable a target (`auto-install.craftengine: false`, etc.). BetterHud skips folders that start with `-`: if `packs/-tfp` exists, don't recreate `packs/tfp`.
- **Minimal churn:** write only changed files, atomically (temp + move). CraftEngine caches configs by mtime+size, and BetterHud/BetterModel hash their outputs, so needless rewrites trigger needless rebuilds.
- Targets:
  - CraftEngine → `plugins/CraftEngine/resources/tfp/` (`pack.yml`, `configuration/**`, `resourcepack/**`)
  - BetterHud → `plugins/BetterHud/packs/tfp/` (`images/ layouts/ huds/ popups/ texts/ assets/tfp/`)
  - BetterModel → **no files**. Models are registered from our jar via `ModelAssetsEvent`.

## 6. Config edits we make (and only these)
Edit with a comment-preserving YAML editor that changes the single key and leaves the rest of the file alone. Record each change in the manifest, so we don't fight an owner who changed it back on purpose (if the value differs from both the original and ours, leave it and warn).

| Plugin | Key | Value | Why | Needs restart |
|---|---|---|---|---|
| CraftEngine | `misc.delay-configuration-load` | `false` | Spawn chunks of the TFC world must generate with CE configs loaded and worldgen hooked | yes |
| CraftEngine | `block.serverside-blocks` | ≥ our state count + margin (from the datagen report) | Enough real server blocks for every TFC state | yes |
| CraftEngine | `resource-pack.merge-external-zip-files` | contains `BetterModel/build.zip` (default already) | BetterModel pack merged into CE's pack | no |
| BetterHud | `default-hud` | remove `test_hud` | Demo HUD otherwise shown to everyone | no (reload) |
| BetterHud | `merge-with-external-resources` | `true` (default) | BetterHud merges into CE's pack | no |
| BetterHud | `pack-type` | `zip` or `folder` (not `none`) | Required for CE merge | no |
| BetterHud | `enable-self-host` | `false` | CE hosts the single merged pack | no |
| BetterModel | `pack-type` | `zip`, `build-folder-location` default | Path CE merges by default | no |

If a restart-requiring key changed, log a clear "restart required" banner, and run in a degraded mode that doesn't generate TFC chunks until the restart.

## 7. Runtime reload (`/tfp reload [content|data]`)
- `data`: reload our registries (async parse, atomic swap). No client-plugin involvement.
- `content`: re-run the installer, then
  1. `BetterModelBukkit.platform().reload()` (async; our `ModelAssetsEvent` re-registers models; afterwards re-apply visual state through `CreateEntityTrackerEvent`),
  2. trigger a CraftEngine full reload (`/ce reload all` equivalent). BetterHud reloads and merges inside CE's pack build, so call `BetterHudAPI.inst().reload()` separately only when CE isn't rebuilding,
  3. tell online players a new pack is being sent.
- Never hot-reload CraftEngine itself, and never `/reload` the server: CE shuts the server down.

## 8. Tests
- Unit: installer manifest logic (untouched → overwrite, edited → `.tfp-new`, deleted → skip), YAML single-key edits preserve comments, version gate.
- Self-test harness (`plugin-testing`): fresh server folder → first start → assert files present, config keys set, restart banner logic → second start → assert CE blocks load, BetterModel models registered, BetterHud placeholders resolve.
