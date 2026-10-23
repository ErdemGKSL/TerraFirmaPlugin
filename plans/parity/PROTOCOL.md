# Parity worker protocol

The exact rules for one worker (human or agent) taking one item of `plans/parity/` from `open` to `done`. Any agent can start
cold from this file. Repository rules in `CLAUDE.md` still apply; where they are stricter, they win.

## 0. Before anything

1. Read `CLAUDE.md`, `ARCHITECTURE.md` (sections for the modules you touch) and this file.
2. Environment: `export JAVA_HOME=/opt/jdk/25`. TFC source: `/opt/tfc` at the commit pinned in `docs/porting/ledger.md`
   (`c3b4d9536b82e5a9746dd793c586720a74108057`). Never vendor TFC into the repo.
3. `git pull --rebase --autostash origin <branch>` so you start from the latest tree. Other agents and workflows commit in the
   same tree: never use `git add -A`, `git add .`, `git commit -a`, `git stash` without `--autostash` semantics, `reset --hard`,
   `checkout -- <path>` on files you did not change, or any history rewrite.
4. After creating or entering a worktree, run `tools/worktree/share-build.sh`. It links `content/generated` to a shared,
   read-only copy keyed by the datagen inputs, so worktrees do not each hold ~110 MB. `generateContent` turns the link back
   into a private copy by itself. See `tools/worktree/README.md`.

## 1. Pick an item

1. Open `STATUS.md`. Take the lowest wave that still has `open` items. Within it take any `open` item whose `deps` are all
   `done` (check the dep rows) and that nobody claimed (step 3).
2. An item of a later wave may be taken early when all its deps are `done` and no item in progress owns a glob that overlaps
   its `files_owned` (compare the literal prefixes of the globs). When in doubt, wait for the wave.
3. Claim it: add the line `> Claimed: <session or name>, <date>` under the item's `# heading` (the body, not the front matter),
   commit only that file (`git commit -m "Claim PAR-X-nn" <path>`), pull --rebase, push. If the push shows someone else claimed
   it first, drop it and pick another. The loop orchestrator may assign items instead; then no claim commit is needed.

## 2. Understand it

1. Read the item file completely. `plan` starts with what exists now; `aliases` point to the old plan entries and
   `plans/gaps.md` notes for history and in-game findings.
2. Load the skills the work needs before writing code (table in `CLAUDE.md`): always `tfc-feature-porting` for gameplay;
   `paper-folia-threading` for anything scheduled, async, packets or persistence; the `craftengine*` sub-skills for blocks,
   items, visuals, GUIs; `bettermodel`, `betterhud`, `model-authoring`, `image-generation`, `plugin-testing` as relevant.
3. Read every path in `tfc_refs` in `/opt/tfc`. Keep TFC's constants, formulas and order of random draws.
4. If your item hits something listed in `NOT-PARITY.md` (or carries a `see_not_parity` line), build the substitute listed
   there and do not try to reach TFC's exact behaviour; record no follow-up item for that difference.

## 3. Stay inside `files_owned`

You may create, edit and delete only files matched by the item's `files_owned` globs, plus these **shared hubs**, where you
only append or edit your own lines and never reformat or reorder other lines:

| shared hub | allowed change |
|---|---|
| `plans/parity/items/<AREA>/<your id>.md` | status, claim line, plan notes |
| `plans/parity/STATUS.md` | your row's status only |
| `docs/porting/ledger.md` | add or update your feature row; add divergence rows |
| `paper/src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule` | append one line per new module (after PAR-INFRA-01) |
| `tools/datagen/src/main/java/dev/tfp/datagen/Datagen.java` | add one `run` line in `generate` for your generator |
| `itest/src/main/java/dev/tfp/itest/SelfTestSuite.java` | add your case to the list |
| `paper/src/main/resources/lang/*.json` and generated lang outputs | add your keys |
| `content/generated/**` | only by rerunning datagen (`./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc`, checked by `verifyContent`); never by hand |
| `ARCHITECTURE.md`, `docs/gui/catalogue.md`, `docs/hud/catalogue.md`, `docs/art/image-list.md` | your own section or rows |
| `art/raw/<asset-id>/**` | new art for your item (image-generation skill) |
| `paper/src/test/java/dev/tfp/paper/boot/BootstrapTest.java` | in `commandTreeStructure`, add your `/tfp debug` literal(s) to the debug-children list (own line) |
| `tools/datagen/src/test/java/dev/tfp/datagen/CeYamlCheck.java` | in `assertValid`, add your new `configuration/*.yml` top-level section name to the allowed list (own line) |

Until PAR-INFRA-01 is done, wiring in `paper/.../TfpPlugin.java` and `boot/TfpCommands.java` is a shared hub too (add your
lines only). After it is done, wire through a `FeatureModule` instead: one class of your own implementing
`dev.tfp.paper.module.FeatureModule` (listeners, services via `ModuleContext.services()`, `/tfp debug` literals via
`DebugTree`) plus your one line in the service file above; see ARCHITECTURE.md §12.1.

If the work needs another file: do not edit it. Either (a) finish what fits and add a follow-up item (next free id in the
area, its own `files_owned`, a wave after yours) or (b) set the item to `blocked` with the reason as the first `plan` line and
the blocking file/item. Report it in your final message.

## 4. Build it

1. Rules in `core/` (no Bukkit/Paper/CE/BM/BH imports), immutable values, the EUPL header and the `Ported from
   TerraFirmaCraft ... @ c3b4d9536` line on ported files (`./gradlew check` enforces both).
2. Data from `tools/datagen` reading the TFC tree; never hand-written copies of TFC JSON; never edit generated output by hand.
3. Time-based state is lazy (timestamps + catch-up), never ticking per block or per item.
4. World reactions: decide async, apply sync (callbacks snapshot and enqueue; a pure decision; the region commit queue applies
   real changes under the budget). No BukkitScheduler; use `dev.tfp.paper.scheduler.Scheduler`.
5. Feature code reaches CraftEngine/BetterModel/BetterHud only through `integration/*` adapters.
6. Particles only for explicit actions. 2D art only through the imagegen MCP and the `image-generation` skill; 3D models only
   through `model-authoring`.
7. Game modes (owner rule, 2026-10-23): every player-facing feature must behave correctly per game mode, the way vanilla and
   TFC do. In creative and spectator the TFC needs and their HUD are off: no thirst, hunger, nutrition or saturation drain, no
   temperature damage or effects, no heavy/overburdened effects, and the health, hunger, thirst, air and armour bars and the
   held-item popups are hidden (spectator also hides hotbar-bound HUD, like vanilla). Adventure behaves like survival. Switching
   game mode updates the HUD and pauses/resumes the needs at once (lazy state: no catch-up of the time spent in creative or
   spectator). Think about these edge cases in every item (game mode, death and respawn, dimension and world change, flying,
   vehicles, being in water or lava, invulnerability, peaceful difficulty) and add a test for the ones the item touches.

## 5. Test it

1. Write the tests in `tests`: core golden values against TFC (rows exported from `/opt/tfc` by a scratch script kept outside
   the repo), lazy-vs-stepped for time-based rules, MockBukkit for listeners/PDC/commands, datagen tests on the real checkout,
   an itest case when the item names one.
2. `./gradlew check` must pass. Do not weaken or delete tests to pass.
3. Server runs (`:itest:runSelfTest`, `spikes/run-matrix.sh`, headless client sessions in `spikes/client/`) are delegated to
   a cheap model (Haiku subagent) with exact commands, files to read and a report format; the main session judges results.

## 6. In-game screenshot check (every item with `ui: true`)

1. A cheap-model agent (Haiku) starts the headless Paper 1.21.11 server and client (`spikes/client/`, CraftEngine self-host on
   127.0.0.1 so the pack applies, see PAR-REL-01 / `docs/testing/headless.md`), runs the item's `in_game_steps` over RCON with
   the bot named `Bot`, and captures the screenshots the steps ask for (GUI scale 2 and 3 for HUD/GUI items; 8x crops for
   pixel checks).
2. The main session (Opus) looks at every screenshot and decides pass or fail against `done_criteria`. A cheap model never
   judges.
3. Store the evidence in `docs/verification/<ID>/`: small PNG crops (under 200 KB each) and `verdict.md` (date, HEAD commit,
   server/client versions, the steps run, pass/fail per criterion). These paths are implicitly owned by the item.
4. Fail: fix within `files_owned` and repeat, or record a follow-up item.
5. Deferred in-game check (manager decision 2026-10-23, from PAR-INFRA-13/PAR-INFRA-23): when an item's in-game check can
   only run after its dependents exist (the blocks or devices it serves are added or loaded by later items that depend on
   it), the check becomes a separate verification item, or a pending line in an existing one, that depends on those later
   items. Move the screenshot criterion and its `in_game_steps` there, set `ui: false` on the item, and replace the criterion
   with `Deferred check tracked in <id>`. The deferred check does not block the item: never make the item wait for the
   verification item (that is a dep cycle). Reviewers must accept `deferred check tracked in <id>` for such a criterion when
   `<id>` exists, holds the moved steps and criterion, and does not list the item in its `deps`; a failure found there is
   fixed through that item (or a follow-up), not by reopening the original one unless its own criteria break.

## 7. Record it

1. `docs/porting/ledger.md`: update the feature row (status `rules` / `playable` / `visual-complete`, TFC files, tests).
   Every behaviour that differs from TFC gets a row in the **Divergences** table (TFC behaviour, ours, reason, revisit?).
2. Item file: set `status: done` (or `blocked`), remove the claim line, add a final plan line `Done: <commit summary, tests,
   verification path>`.
3. `STATUS.md`: set your row's status to the same value in the same commit.
4. Do not edit the old phase plans or `plans/gaps.md`; they are history. Follow-up findings become new items.

## 8. Commit and push

1. Stage explicit paths only: `git add <path> <path> ...` (the files you changed, the regenerated outputs, the item, STATUS.md,
   the ledger).
2. One commit per item (plus the claim commit). Message: `PAR-<AREA>-<nn>: <title>` and a short body, ending with the
   trailer lines the session requires, for example:

   ```
   Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
   Claude-Session: https://claude.ai/code/session_018kkWhFt2SBQHZwsVQ8uVdY
   ```
3. `git pull --rebase --autostash origin <branch>`, then `git push -u origin <branch>`. Retry network failures up to 4 times
   with 2, 4, 8, 16 s back-off. No force push, no pull request unless asked.
4. Rebase conflicts: in `content/generated/**` take the incoming side, rerun datagen, stage the result; in `STATUS.md` and the
   ledger keep both sides (rows are independent); in a file you do not own, keep theirs and re-apply only your hub lines.

## 9. Final report

Under 200 words: item id, status, commits, tests added and their counts, verification verdict and path for `ui` items,
divergences recorded, follow-up items created, anything blocked and why.
