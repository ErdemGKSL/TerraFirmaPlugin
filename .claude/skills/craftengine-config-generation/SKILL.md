---
name: craftengine-config-generation
description: How tools/datagen should generate CraftEngine configuration for TFC's huge content matrices (rocks × block types, ore × grade × rock, woods × ~40 types, crops × stages, metals × items). Covers the decision to expand matrices in datagen into literal per-id entries that call our own tfp: templates, CE's YAML extensions ($$ version keys, :: deep keys, #suffix, duplicate merging), template semantics (arguments precedence, overrides vs merges, list concatenation), placeholder grammar and argument types, why not to use config_factory or default: templates, the load pipeline/caching/error reporting, pack.yml/subpacks, and a list of things never to emit. Use this whenever writing or changing datagen output for CraftEngine, authoring a tfp: template, debugging a CE "Found N issue(s)" load error, or deciding how to structure generated CE YAML, even if the user only says "generate the blocks".
---

# Generating CraftEngine config at scale

Source: `references/research-report.md` (CE @ `b3079c5`). Section numbers below (§) refer to it. Read the report's §11 recommendation before changing the generation strategy.

## Decision (locked unless the report's §13 open questions change it)
1. **Datagen expands every matrix** into one small literal entry per id, with only the varying data (ids, textures, numbers, drops). All computation (lookups, conditions, maths) happens in datagen. CE only does plain `${}` substitution.
2. Each entry calls **our own `tfp:` templates** for shared structure: `tfp:settings/*`, `tfp:block_state/*` (pillar, cube_all, ore overlay, our own slab/stairs/wall, crop stages, plant shapes), `tfp:loot_table/*`, `tfp:item/*`.
3. **No `config_factory` for shipped content.** Its known problems:
   - `lang` isn't substituted in a blueprint;
   - instance values aren't evaluated;
   - nested factories/templates are ignored;
   - a shared mutable argument map is used across parser threads;
   - one bad instance kills the whole factory;
   - errors carry no instance index.
4. **Don't reference `default:` templates at runtime.** The on-disk copy is frozen at first install and can be edited by owners. Re-author what we need in `tfp:`. Re-author rather than copy verbatim (CE is GPL-3.0 and we're EUPL-1.2, a legal grey zone).
5. **Emit `lang`/`translations` literally.** Templates don't apply to them.
6. **One file per natural group** (e.g. `configuration/rock/granite.yml`, `configuration/ore/hematite.yml`). CE re-parses only changed files (mtime+size cache), diffs stay small, and our install manifest tracks edits per file.

Shape of a generated entry:
```yaml
# configuration/rock/granite.yml (generated, do not edit)
items:
  tfp:rock/raw/granite:
    template: tfp:item/rock_block
    arguments:
      rock_name: granite
      rock_kind: raw
      rock_hardness: 6.5
```

## Template rules that bite (§3–§5)
- **Always fully qualify template ids** (`tfp:...`). An unqualified `foo/bar` resolves to `minecraft:foo/bar`.
- **The outer argument wins.** An inner template's `arguments:` can't override a name the caller passed. So **prefix argument names by domain** (`slab_texture`, `ore_grade`, `rock_name`), never generic names like `texture`.
- Combination: templates deep-merge in order → `overrides` (**shallow**, replaces whole top-level keys) → extra keys + `merges` (deep). **Lists concatenate** in deep merges and can mutate a list argument by reference, so pass lists last or write them literally.
- Placeholders: `${name}`, `${name:-default}` (the default is **SNBT**: quote strings with `:` `.` `#` `<` or spaces), `${name^}`, `${name^^}`. Built-ins: `${__NAMESPACE__}`, `${__ID__}`. A string that is exactly one placeholder returns the raw object (map/list/number).
- Argument types: `plain`, `object`, `map`, `list`, `null`, `self_increase_int` (stateful, so avoid it), `expression`, `condition`, `when`, `to_upper_case`, `to_lower_case`, `capitalize`. Prefer computing in datagen.
- `debug: true` on an entry logs its fully expanded JSON. Use it when a template result is unclear.

## YAML extensions (§2)
- `$$>=X`, `$$<X`, `$$A~B` (inclusive), `$$X` = **exactly** X. In a mixed map, a matching `$$` value must be a map and is merged in. If **all** keys are `$$…`, the node is a value selector with `$$fallback`; the last match wins.
- `#tag` after a `$$` version **must contain no digits or dots** (they corrupt the version): `$$>=26.1#v_new` is fine, `$$>=26.1#v2` is broken.
- `a::b::c:` is a deep key. **Any** key containing `::` is split.
- Top-level `#suffix` (`items#rocks`) lets one file hold several sections of the same type. **A misspelt top-level key is silently ignored.**
- Duplicate map keys deep-merge silently.

## Never emit
- `enable: false` on any entry. At `b3079c5` it aborts the whole parser for that type (`IdSectionConfigParser.java:72`). Omit the entry instead.
- Tabs. CE "fixes" them by **rewriting the file**, which breaks our manifest.
- Unescaped `${` in text (lore, names, NBT strings). Use `\$` in plain/single-quoted YAML.
- `::` in keys meant literally, or digit/dot `#tags` in `$$` keys.
- Duplicate ids across files (the first in OS walk order wins, nondeterministically).
- Slabs/stairs via CE's default templates: they can only make **5 slabs and 4 stairs per server** (limited vanilla bases). Use our own `tfp:block_state/slab|stairs|wall`, designed with the `craftengine-visuals-pack` skill.

## Pack layout and versions (§10)
- `plugins/CraftEngine/resources/tfp/pack.yml` with `namespace: tfp` and `enable: true`.
- Large version differences: put them in subpacks selected by a `$$` value selector in `pack.yml`, e.g. `subpacks: {$$>=26.1: {v26: true}, $$fallback: {v1_21: true}}`. Small ones: inline `$$` keys.

## Load pipeline and validation (§9)
- Order: templates → config factories → everything else as a parallel DAG (equipments → items → blocks/recipes/furniture …; images → emoji/lang; loot → loot sources; blocks → configured → placed features).
- Errors appear **only in logs**, as `Found <n> issue(s) in file '<path>'` + `[k] <node path> - <message>`. Neither `CraftEngineReloadEvent` nor the API carries them. So:
  1. **datagen validates first**: YAML lint, schema checks, id uniqueness, template/argument existence, model/texture existence, state budgets;
  2. the **self-test harness** asserts zero issue lines for our pack path (log appender) and that every expected id exists via `CraftEngineItems`/`CraftEngineBlocks` after `CraftEngineReloadEvent`.
- Measure load cost from CE's `Loaded <stage> in <ms>ms` lines. No benchmarks exist yet. Expected size: ~4–5k entries, ~50–75k lines.

## Runtime-only features (don't use them for generation)
- Global variables are MiniMessage `<global:id>` text macros, and they drop the namespace.
- JS scripting is runtime-only, with full host access. Keep it off.
- Custom template argument types: `TemplateArguments.register(Key, factory)` in `onLoad`, only if pure datagen can't express something.
