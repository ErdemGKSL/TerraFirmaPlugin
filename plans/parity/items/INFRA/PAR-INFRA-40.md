---
id: PAR-INFRA-40
title: "DeviceService.route: one diagnostic line when a device click is dropped (no kind, no block entity record)"
area: INFRA
status: done
wave: 8
deps: [PAR-INFRA-02]
size: S
aliases: ["PAR-PROC-04 review follow-up: a quern click that 'does nothing' could not be told apart from a device the router never reached"]
tfc_refs: []
files_owned:
  - "paper/src/main/java/dev/tfp/paper/device/DeviceService.java"
  - "paper/src/test/java/dev/tfp/paper/device/DeviceServiceRouteLogTest.java"
ui: false
in_game_steps: []
plan:
  - "Current: `DeviceService.route` returns false without a log line when `kindAt` is empty for a block whose CraftEngine behaviour is `tfp:device` (a block id with no registered DeviceKind, e.g. its feature module is disabled) or when `load` returns null (`CraftEngineDeviceStore` finds no device controller / readable record at the position). Both look the same as a handler that passed on purpose (TFC no-op), so in-game 'nothing happens' reports cannot be diagnosed from the server log."
  - "Fix: log once per block position and reason (bounded set, e.g. the last 256 positions) at WARNING: `Device <block id> at <world x,y,z>: click dropped: <no device kind registered | no device block entity or unreadable record>`. A handler's own pass stays silent (that is TFC behaviour, e.g. a quern click on the base's side)."
  - "Done: DeviceService.route logs `Device <id> at <world x,y,z>: click dropped: <reason>` at WARNING once per (position, reason), remembered for the last 256 pairs (access-ordered LRU). No-kind is logged only for USE (the call that comes from the tfp:device behaviour; a left click reaches route for any block); no-record for USE and ATTACK; neighbour/placement drops and handler passes stay silent. Test: paper/src/test/java/dev/tfp/paper/device/DeviceServiceRouteLogTest.java (5 cases)."
tests:
  - "paper: MockBukkit or a fake store: a tfp:device block with no kind and one with a null record each log exactly one line for repeated clicks at the same position; a handler pass logs nothing"
done_criteria:
  - "Dropped device clicks leave one diagnostic line per position and reason; test green; ./gradlew check green"
---

# PAR-INFRA-40: DeviceService.route: one diagnostic line when a device click is dropped

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.

Follow-up of the PAR-PROC-04 review: `DeviceService.java` belongs to PAR-INFRA-02, outside PAR-PROC-04's files.
