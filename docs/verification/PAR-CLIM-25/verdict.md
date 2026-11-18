# PAR-CLIM-25 verification: vanilla /time set <marker> answers success on Paper 26.3 and 1.21.11

- Date: 2026-11-18. Jar: `dist/build/libs/TerraFirmaPlugin-0.1.0-SNAPSHOT.jar` built from `9df30b0` plus this item's
  change to `CalendarModule.skip` (the event's world held at the time vanilla read, amount measured from it).
- Servers: fresh run dirs from `spikes/client/server.sh` (plain world, no client), Paper **26.3-254** with
  `time.affects-all-worlds` false and true (`config/paper-global.yml`), and Paper **1.21.11-132**; CraftEngine,
  BetterModel, BetterHud and PacketEvents from the run-paper cache. `calendar.yml` with
  `enableTimeStopWhenServerEmpty: false`, so the calendar runs with nobody online.
- Who ran it: this worker drove RCON itself (no subagent tool in this session); the commands only read replies, no
  screenshots are involved (`ui: false`).

## Steps (RCON, transcript in `runs.txt`)

For each marker `day`, `noon`, `night`, `midnight`: `/tfp time set hour 21`, readout, vanilla `/time set <marker>`,
readout; then `/time add 1000`, readout. A readout is `time query day` (26.3, the day timeline) or `time query daytime`
(1.21.11) plus `/tfp time add ticks 1`, whose reply prints the calendar date and hour.

## Results

| command at calendar 21:00 | 26.3, affects-all-worlds false | 26.3, affects-all-worlds true | 1.21.11 |
|---|---|---|---|
| `/time set day` | "Set minecraft:overworld to time marker minecraft:day"; 999; next day 06:59 | same reply; 1001; next day 07:00 | "Set the time to 1000"; 1004; next day 07:00 |
| `/time set noon` | success; 6002; next day 12:00 | success; 6001; 12:00 | "Set the time to 6000"; 6001; 12:00 |
| `/time set night` | success; 13000; next day 19:00 | success; 13001; 19:00 | "Set the time to 13000"; 13000; 19:00 |
| `/time set midnight` | success; 17999; next day 00:00 | success; 18001; 00:00 | "Set the time to 18000"; 18004; 00:00 |
| `/time add 1000` (from 00:00) | "Set minecraft:overworld to 1075000 tick(s)"; 19001; 01:00 | same; 19001; 01:00 | "Set the time to 19001"; 19002; 01:00 |

No "already at time marker" reply and no `NoSuchMethodError`, `NoClassDefFoundError` or `Calendar:` line in any log.
The one `Could not pass event AsyncResourcePackCacheEvent to BetterHud` line on the first 26.3 start is BetterHud's own
pack-cache hook and unrelated. 06:59 / 17999 are readout rounding of the second that passes between the commands.

The night skip (bed) was not rerun here: it needs a client, and its path is the same code (the world is held at the
pre-event time and vanilla adds the amount after the event on both families, which `CalendarModuleMockBukkitTest`
`nightSkipAdvancesTheCalendarToFiveAm` checks); PAR-CLIM-23 covers it in game.

## Verdict

| criterion | result |
|---|---|
| On Paper 26.3 a vanilla `/time set day\|noon\|night\|midnight` answers success and moves the calendar to that time of day; 1.21.11 unchanged | **PASS** |
