# M1-HUD-FIRST-START: BetterHud first start and the served pack

**Status:** verified on paper 1.21.11 and 26.3 (2026-10-17). 1.21.4-1.21.10 not supported (see ledger).

## Findings

- BetterHud's demo HUD (`test_hud`) is removed on the first start: `default-hud: []` (config edit applied after BetterHud enables).
- The top-centre bar and the missing-glyph row were BetterHud output. With BetterHud removed from the server, both disappear (`nobh-1.21.11.png`).
- The merge race was the cause of the glyph boxes: the CraftEngine pack was built before BetterHud's merge, so the served pack had no `assets/betterhud` entries. Fixed by waiting for BetterHud's reload before the build. After the fix the served pack has 836 `assets/betterhud` entries on both versions, and the top row shows the compass strip (`merge-fixed-1.21.11.png`).
- Headless: the server sets `java.awt.headless=true` in the bootstrap, because CraftEngine and BetterHud pack images need AWT and fail with `NoClassDefFoundError` on a server without a display.

## PacketEvents version-check error

- "Failed to parse packetevents version!" (HTTP 403 from api.github.com) is logged by PacketEvents' UpdateChecker on every start. Its stack is only the update checker; the plugin loads and its self-test passes.
- Cause: the sandbox has no GitHub API access. It is not a plugin defect and needs no code change.

## Evidence

- Runs: `spikes/run/m1/merge2-paper-1.21.11`, `spikes/run/m1/merge2-paper-26.3` (self-test 14/0/0, rc 0).
- Client screenshots: `client-paper-1.21.11` and the merge-fixed screenshot. The 26.3 client screenshot is `/tmp/claude-0/-home-user-TerraFirmaPlugin/7076116e-0f7a-5b74-a249-6fd913ce4602/scratchpad/client-after-26/merge-fixed-26.3.png`. It shows the compass strip at top centre, with no glyph boxes.
- Known limits: 1.21.4-1.21.10 not verified.
