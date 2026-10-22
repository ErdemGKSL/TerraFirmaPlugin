#!/usr/bin/env bash
# Licensed under the EUPL, Version 1.2.
#
# Shares build products that are identical between git worktrees through a content-addressed store.
#
#   tools/worktree/share-build.sh [--worktree DIR] [--force]   link this worktree's shared dirs (idempotent)
#   tools/worktree/share-build.sh --status [--worktree DIR]    print the input hash and the link state
#   tools/worktree/share-build.sh --prune [--yes] [--stale-hours N]
#                                                              list (and with --yes remove) unreferenced store
#                                                              entries and stale, clean worktrees
#
# Shared today: content/generated (datagen output, ~110 MB). Store layout: $STORE/content-generated/<hash>/content/...
# where <hash> covers every input of :tools:datagen:generateContent (datagen + core sources, build logic, version
# catalog, docs/hud/catalogue.md, the TFC checkout's HEAD, dirty diff and untracked files). An entry is built once
# (under flock, into a temp dir, renamed into place), then made read-only and never changed: changed inputs give a
# new hash and a new entry. Gradle never writes into an entry: generateContent replaces the link by a private
# directory when it runs. See tools/worktree/README.md.
set -euo pipefail

SCHEMA=1
KIND=content-generated
TFC=${TFC_CHECKOUT:-/opt/tfc}
[[ -z "${JAVA_HOME:-}" && -d /opt/jdk/25 ]] && export JAVA_HOME=/opt/jdk/25

die() { echo "share-build: $*" >&2; exit 1; }
say() { echo "share-build: $*"; }

MODE=link WT= FORCE=0 YES=0 STALE_HOURS=24
while (($#)); do
  case $1 in
    --worktree) WT=$2; shift ;;
    --force) FORCE=1 ;;
    --status) MODE=status ;;
    --prune) MODE=prune ;;
    --yes) YES=1 ;;
    --stale-hours) STALE_HOURS=$2; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) die "unknown argument $1" ;;
  esac
  shift
done

WT=$(git -C "${WT:-.}" rev-parse --show-toplevel) || die "not inside a git worktree"
MAIN=$(git -C "$WT" worktree list --porcelain | awk '/^worktree /{print substr($0, 10); exit}')
STORE=${TFP_SHARED_STORE:-$(dirname "$MAIN")/.cache/tfp-shared}
mkdir -p "$STORE/$KIND" "$STORE/locks"
# .gitignore's "content/generated/" matches directories only, so git would list the link as untracked. The common
# info/exclude covers every worktree, including ones checked out before .gitignore learned the link form.
EXCLUDE=$(git -C "$WT" rev-parse --path-format=absolute --git-common-dir)/info/exclude
grep -qxF /content/generated "$EXCLUDE" 2>/dev/null || { mkdir -p "${EXCLUDE%/*}"; echo /content/generated >> "$EXCLUDE"; }

# Paths (relative to the worktree) whose content decides the datagen output.
INPUTS=(tools/datagen/src/main tools/datagen/build.gradle.kts core/src/main core/build.gradle.kts
  build-logic/src build-logic/build.gradle.kts build-logic/settings.gradle.kts gradle/libs.versions.toml
  gradle/wrapper/gradle-wrapper.properties settings.gradle.kts build.gradle.kts gradle.properties
  docs/hud/catalogue.md)

input_hash() { # $1 = worktree
  local wt=$1
  {
    echo "schema $SCHEMA kind $KIND"
    (cd "$wt" && for p in "${INPUTS[@]}"; do [[ -e $p ]] && echo "$p"; done | xargs -r -I{} find {} -type f -print0 \
      | sort -z | xargs -0 -r sha256sum)
    local g=(git -c safe.directory='*' -C "$TFC")
    echo "tfc $("${g[@]}" rev-parse HEAD 2>/dev/null || echo none)"
    "${g[@]}" diff HEAD --binary 2>/dev/null | sha256sum
    (cd "$TFC" 2>/dev/null && "${g[@]}" ls-files -o --exclude-standard -z | sort -z | xargs -0 -r sha256sum) || true
  } | sha256sum | cut -c1-24
}

# True when a Gradle JVM (wrapper client, daemon, worker) has its working directory inside $1 (the wrapper client
# lives for the whole build), or a Gradle JVM has a build output open under $1. A daemon's idle locks under .gradle/ do not count.
gradle_busy() {
  local wt=$1 pid cwd
  for pid in $(pgrep -x java || true); do
    tr '\0' ' ' 2>/dev/null < "/proc/$pid/cmdline" | grep -qi gradle || continue
    cwd=$(readlink "/proc/$pid/cwd" 2>/dev/null) || continue
    [[ $cwd == "$wt" || $cwd == "$wt"/* ]] && return 0
    ls -l "/proc/$pid/fd" 2>/dev/null | grep -o -- "-> $wt/.*" | grep -qv -- "/\.gradle/" && return 0
  done
  return 1
}

# True when any process (not this script) has its working directory inside $1.
any_busy() {
  local wt=$1 d cwd
  for d in /proc/[0-9]*; do
    [[ ${d#/proc/} == "$$" || ${d#/proc/} == "$BASHPID" ]] && continue
    cwd=$(readlink "$d/cwd" 2>/dev/null) || continue
    [[ $cwd == "$wt" || $cwd == "$wt"/* ]] && return 0
  done
  return 1
}

# Top-level entries datagen owns (OutputTree.OWNED); a private copy holding anything else is never adopted.
OWNED=" data craftengine betterhud visuals datapack advancements guide lang reports .datagen-manifest.json third-party.json "
only_owned() { # $1 = content/generated
  [[ $(ls -A "$1") == content ]] || return 1
  local f
  for f in $(ls -A "$1/content"); do [[ $OWNED == *" $f "* ]] || return 1; done
}

gradle() { (cd "$1" && shift && ./gradlew --console=plain -q -Ptfc.checkout="$TFC" "$@"); }

seal() { # $1 = temp dir that becomes entry $2
  local tmp=$1 hash=$2 src=$3
  printf 'kind=%s\nhash=%s\nschema=%s\ncreated=%s\nsource=%s\ncommit=%s\n' "$KIND" "$hash" "$SCHEMA" \
    "$(date -u +%FT%TZ)" "$src" "$(git -C "$src" rev-parse HEAD)" > "$tmp/.tfp-shared-entry"
  chmod 755 "$tmp"
  chmod -R a-w "$tmp"
  mv -T "$tmp" "$STORE/$KIND/$hash"
}

# Swaps $WT/content/generated for a symlink to $1. The old directory is moved aside first, then removed.
link_to() {
  local target=$1 cur=$WT/content/generated new=$WT/content/.generated.tfp-new.$$
  ln -s "$target" "$new"
  if [[ -d $cur && ! -L $cur ]]; then
    local old=$WT/content/.generated.tfp-old.$$
    mv -T "$cur" "$old"
    mv -T "$new" "$cur"
    rm -rf "$old"
  else
    mv -T "$new" "$cur"
  fi
}

do_link() {
  local hash entry cur tmp
  hash=$(input_hash "$WT")
  entry=$STORE/$KIND/$hash
  cur=$WT/content/generated
  if [[ -L $cur && $(readlink "$cur") == "$entry" && -f $entry/.tfp-shared-entry ]]; then
    say "content/generated already shares $entry"; return 0
  fi
  if ((!FORCE)) && gradle_busy "$WT"; then
    die "a Gradle build runs in $WT; retry when it has finished"
  fi
  exec {lock}>"$STORE/locks/$KIND-$hash.lock"
  flock "$lock"
  if [[ ! -f $entry/.tfp-shared-entry ]]; then
    tmp=$(mktemp -d "$STORE/$KIND/.tmp-$hash.XXXXXX")
    if [[ -d $cur && ! -L $cur ]] && only_owned "$cur" && gradle "$WT" :tools:datagen:verifyContent >/dev/null 2>&1; then
      # The private copy equals a fresh generation of these inputs: adopt it instead of generating again.
      say "adopting the verified private content/generated as entry $hash"
      mv -T "$cur" "$tmp/gen"
      mv -T "$tmp/gen/content" "$tmp/content" && rmdir "$tmp/gen"
      ln -s "$entry" "$cur"  # the entry appears below; the link dangles only until then
    elif grep -q 'tfp.contentDir' "$WT/tools/datagen/build.gradle.kts"; then
      say "generating entry $hash (one datagen run; other worktrees with the same inputs wait and reuse it)"
      gradle "$WT" :tools:datagen:generateContent -Ptfp.contentDir="$tmp/content" \
        || { rm -rf "$tmp"; die "datagen failed for $WT"; }
    else
      rm -rf "$tmp"
      die "$WT predates tfp.contentDir and has no verified content/generated; rebase it, or run generateContent first"
    fi
    seal "$tmp" "$hash" "$WT"
    say "stored $entry ($(du -sh "$entry" | cut -f1))"
  fi
  if [[ -L $cur ]]; then
    link_to "$entry"
  elif [[ -d $cur ]]; then
    if ((FORCE)) || diff -rq "$cur" "$entry" --exclude=.tfp-shared-entry >/dev/null 2>&1; then
      link_to "$entry"
    else
      flock -u "$lock"
      die "content/generated differs from the shared entry for this worktree's inputs (stale, or regenerated with" \
        "local changes); left alone. Run generateContent, or pass --force to replace it with $entry"
    fi
  else
    mkdir -p "$WT/content"
    link_to "$entry"
  fi
  flock -u "$lock"
  say "content/generated -> $entry"
}

do_status() {
  local hash cur
  hash=$(input_hash "$WT")
  cur=$WT/content/generated
  echo "worktree: $WT"
  echo "store:    $STORE"
  echo "hash:     $hash ($([[ -f $STORE/$KIND/$hash/.tfp-shared-entry ]] && echo present || echo absent))"
  if [[ -L $cur ]]; then echo "content/generated -> $(readlink "$cur")"
  elif [[ -d $cur ]]; then echo "content/generated: private directory ($(du -sh "$cur" | cut -f1))"
  else echo "content/generated: missing"; fi
}

worktrees() { git -C "$MAIN" worktree list --porcelain | awk '/^worktree /{print substr($0, 10)}'; }

do_prune() {
  local act="would remove" wt e refs="" lock now age head br
  ((YES)) && act="removing"
  # 1. Store entries that no worktree links to.
  while read -r wt; do
    [[ -L $wt/content/generated ]] && refs+=$(readlink "$wt/content/generated")$'\n'
  done < <(worktrees)
  for e in "$STORE/$KIND"/*/ "$STORE/$KIND"/.tmp-*/; do
    [[ -d $e ]] || continue
    e=${e%/}
    local h=${e##*/}; h=${h#.tmp-}; h=${h%%.*}
    grep -qxF "$e" <<<"$refs" && continue
    exec {lock}>"$STORE/locks/$KIND-$h.lock"
    flock -n "$lock" || { exec {lock}>&-; say "skip $e (in use by a running share-build)"; continue; }
    echo "$act store entry $e ($(du -sh "$e" | cut -f1))"
    ((YES)) && { chmod -R u+w "$e"; rm -rf "$e"; }
    exec {lock}>&-
  done
  # 2. Worktree admin entries whose directory is gone.
  if ((YES)); then git -C "$MAIN" worktree prune --verbose; else git -C "$MAIN" worktree prune --dry-run --verbose; fi
  # 3. Stale worktrees: not the main one, not locked, no process inside, clean, HEAD kept by another ref, idle long.
  now=$(date +%s)
  while read -r wt; do
    [[ $wt == "$MAIN" || ! -d $wt ]] && continue
    git -C "$MAIN" worktree list --porcelain | awk -v w="$wt" '$0=="worktree "w{f=1;next} /^worktree /{f=0} f&&/^locked/{x=1} END{exit !x}' \
      && { echo "keep $wt (locked)"; continue; }
    age=$(( (now - $(stat -c %Y "$(git -C "$wt" rev-parse --git-dir)/index" 2>/dev/null || echo "$now")) / 3600 ))
    ((age < STALE_HOURS)) && { echo "keep $wt (active ${age}h ago)"; continue; }
    any_busy "$wt" && { echo "keep $wt (a process runs inside)"; continue; }
    [[ -n $(git -C "$wt" status --porcelain 2>/dev/null) ]] && { echo "keep $wt (uncommitted changes)"; continue; }
    head=$(git -C "$wt" rev-parse HEAD)
    br=$(git -C "$wt" symbolic-ref -q HEAD || true)
    if [[ -z $(git -C "$MAIN" for-each-ref --contains "$head" --format='%(refname)' refs/heads refs/remotes \
      | grep -vxF "${br:-none}") ]]; then echo "keep $wt (commits not on any other branch)"; continue; fi
    echo "$act stale worktree $wt (clean, idle ${age}h)"
    ((YES)) && git -C "$MAIN" worktree remove "$wt"
  done < <(worktrees)
  ((YES)) || echo "(dry run; pass --yes to remove)"
}

case $MODE in
  link) do_link ;;
  status) do_status ;;
  prune) do_prune ;;
esac
