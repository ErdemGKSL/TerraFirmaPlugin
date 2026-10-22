# Shared build store for git worktrees

Parallel workers each get a git worktree. Without sharing, every worktree holds its own copy of the datagen output
(`content/generated`, about 110 MB) next to its Gradle outputs. `share-build.sh` keeps one read-only copy per distinct
set of inputs in a content-addressed store and links each worktree to it.

```sh
tools/worktree/share-build.sh                 # after creating or entering a worktree (idempotent, ~0.5 s when linked)
tools/worktree/share-build.sh --status        # input hash, store entry, link state
tools/worktree/share-build.sh --prune         # dry run: unreferenced entries, stale worktrees
tools/worktree/share-build.sh --prune --yes   # remove them
```

Store: `$TFP_SHARED_STORE`, default `<parent of the main checkout>/.cache/tfp-shared` (here `/home/user/.cache/tfp-shared`).
Layout: `content-generated/<hash>/content/...` plus `.tfp-shared-entry` (hash, source worktree, commit, date).

## How it works

- **Hash.** sha256 over every input of `:tools:datagen:generateContent`: `tools/datagen/src/main`, `core/src/main`, their
  build files, `build-logic`, `gradle/libs.versions.toml`, the wrapper and root build files, `docs/hud/catalogue.md`, and
  the TFC checkout (`$TFC_CHECKOUT`, default `/opt/tfc`): HEAD, `git diff HEAD`, untracked files. Editing any of these
  gives a new hash and therefore a new entry. An entry is never changed after it is sealed.
- **Build once.** Under `flock` on `locks/content-generated-<hash>.lock`. A worktree whose private `content/generated`
  holds only datagen-owned files and passes `verifyContent` is adopted (moved, not copied). Otherwise the script runs
  `generateContent -Ptfp.contentDir=<temp dir in the store>`. It writes the marker, makes the tree read-only, and renames
  the temp dir into place. Concurrent runs with the same inputs wait on the lock and reuse the result.
- **Link.** It replaces `content/generated` with a symlink to the entry. A private directory is replaced only when it is
  byte-identical to the entry (`diff -r`). A differing one (stale, or regenerated with local edits) is left alone with a
  message; `--force` replaces it. The script refuses to touch a worktree while a Gradle JVM has its cwd there or has a build
  output open there.
- **Gradle never writes into an entry.** `generateContent` (default output) deletes the symlink, never the target, and
  writes a private directory. It refuses any output path inside a directory carrying `.tfp-shared-entry`. Everything else
  only reads `content/generated` (core/paper test inputs, `verifyContent`, `checkNotice`, the content jar). The `:content`
  jar now packs `content/generated` directly instead of copying it to `content/build/resources` first (another ~110 MB per
  worktree that built the jar). Gradle fingerprints file contents, so swapping a directory for an identical link keeps
  tasks UP-TO-DATE.
- **Read-only** is advisory for root, since root ignores permission bits. The real guard is that no task writes there.

After you change datagen inputs and run `generateContent`, the worktree has a private copy again. Run the script again
to move the result into the store, if it is worth sharing.

## Deliberately not shared

Anything two commits can write differently at the same path, or that Gradle mutates in place: `*/build/classes`, `libs`,
`test-results`, `reports`, `dist/build`, `build-logic/build`, `content/build/libs`, each project's `.gradle/` (execution
history, file hashes, locks), `itest/build/selftest-run` (live server worlds). Sharing these through links would let
concurrent builds clobber each other or serve stale outputs. Those outputs are small (core about 20 MB, dist about 20 MB). The
expensive cross-worktree reuse already happens through `GRADLE_USER_HOME` (`~/.gradle`, shared by all worktrees): the
local build cache (`caches/build-cache-1`, enabled by `org.gradle.caching=true`), dependency jars (`modules-2`,
including the CraftEngine, BetterModel and BetterHud API jars), and run-paper's server jars and downloaded plugins
(`caches/run-task-jars`). The checkout made by `git worktree add` (about 30 MB) is per worktree by nature: workers edit those
files in place.

## Prune

`--prune` lists, and with `--yes` removes:
- store entries no worktree's `content/generated` links to, skipping any entry whose lock is held;
- `git worktree prune` candidates, meaning worktrees whose directory is gone;
- worktrees that are stale: not the main one, not locked, index untouched for `--stale-hours` (default 24), no process with
  its cwd inside, `git status` clean, and HEAD reachable from another branch or remote ref. They are removed with
  `git worktree remove`, without `--force`.

Only links from registered git worktrees count as references. A copy or clone outside `git worktree list` that links into
the store loses its target when prune runs.
