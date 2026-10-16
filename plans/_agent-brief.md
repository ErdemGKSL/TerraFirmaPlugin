# M1 agent brief (Opus subagents)

Read first: CLAUDE.md, ARCHITECTURE.md (§3 module rules, §4 principles, §5 threading), plans/README.md, plans/01-m1-skeleton.md (your task entries), and the skill(s) named on each task.

Rules:
- Java 25: `export JAVA_HOME=/opt/jdk/25`. Build/test: `./gradlew :<module>:test`, full: `./gradlew check`.
- Every Java file starts with the EUPL header (see build-logic CheckLicenseHeaders). Ported TFC files need the `file: src/main/java/net/dries007/tfc/<path> @ c3b4d9536` line.
- Platform-free rules: core/ and worldgen/ import no Bukkit/Paper/CE/BM/BH types (enforced by `verifyPlatformFree` and ArchitectureTest in :dist).
- Feature code reaches CE/BM/BH only through integration/* (ArchitectureTest rule to be added in M1-INT-01).
- No BukkitScheduler. Use dev.tfp.paper.scheduler.Scheduler.
- Tests must pass with `./gradlew check`. Don't weaken or delete tests to pass.
- Don't commit or push. Leave the working tree with your files; the orchestrator commits, one commit per task ID, with the trailer
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` / `Claude-Session: https://claude.ai/code/session_018kkWhFt2SBQHZwsVQ8uVdY`.
- Mark your tasks `[x]` in plans/01-m1-skeleton.md only when their "Done when" is met by a test or a recorded run. Otherwise leave `[ ]` and say why.
- Report: task IDs done, files touched, test counts (from build/test-results), anything unfinished and why. Under 200 words.
