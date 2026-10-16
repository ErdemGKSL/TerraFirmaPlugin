# M0-NET-01: Packet layer: biome remap + item lore
**Status:** done — 2026-10-16. Paper 1.21.11, Folia 1.21.11, Folia 26.3 run this session. Paper 26.3 facts from an earlier session (not rerun). Open items at the bottom.

## Setup
- Spike: `spikes/net-01` with PacketEvents 2.14.0, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449. Paper 26.3-196-7ec11b2 for the earlier run; Paper/Folia 1.21.11 and Folia 26.3 for this run.
- Datapack `tfpnet` from `spikes/net-01/build_datapack.py`: two plains biome variants, `tfpspike:plains_summer` and `tfpspike:plains_autumn`. A `ChunkGenerator` BiomeProvider returns the summer biome.
- Driver: `spikes/net-01/run-client-check.sh <ver> <port> [display]` (Paper). Folia was run by hand with the same steps: `client/server.sh folia <ver> <dir> <port> harness.jar net-01.jar`, `client/session.sh`, RCON `net01 ...`, `client/shot.sh`.
- Display `:105` (Xvfb). Ports: Paper 1.21.11 26400, Folia 26.3 26600, Folia 1.21.11 26700. `-Dtfp.assertThreads=true` set on all servers.
- Real vanilla client joins through `client/session.sh`. Commands: `net01 season summer|autumn`, `give`, `creativetest`, `pipeline`, `report`.
- Listener: one `PacketListener` at `PacketListenerPriority.NORMAL`. Chunk biome rewrite via `WrapperPlayServerChunkData`; item lore and `custom_model_data` via `WrapperPlayServerSetSlot` and `WrapperPlayServerWindowItems`; creative strip via `WrapperPlayClientCreativeInventoryAction`.

## Results
### Per target
| Target | Biome ids (summer / autumn / registry) | Switch seen after resend | Creative strip | Chunk packets, avg / max (us) | Item packets, avg (us, n=6) | Listener threads | Thread errors |
|---|---|---|---|---|---|---|---|
| Paper 26.3 (earlier session) | 43 / 42 / 69 | yes | `stripped=1`, lore/cmd null | 1153 / 271708 (incl. first chunk) | 39308 | Netty IO only | not recorded |
| Paper 1.21.11 (this session) | 42 / 41 / 67 | yes | `stripped=1`, lore/cmd null | 219 / 54417 | 1833 | Netty IO only (chunk 603, set_slot 4, window_items 2, creative 1) | none in server log |
| Folia 26.3 (this session) | 43 / 42 / 69 | yes | `stripped=1`, lore/cmd null | 344 / 100684 | 897 | Netty IO only (chunk 630, set_slot 4, window_items 2, creative 1) | "not owned": 0, "Thread failed": 0 |
| Folia 1.21.11 (this session) | 42 / 41 / 67 | yes | `stripped=1`, lore/cmd null | 265 / 61226 | 1122 | Netty IO only (chunk 609, set_slot 4, window_items 2, creative 1) | "not owned": 0, "Thread failed": 0 |

Remapped 4x4x4 biome cells: 259584 on every run.

### Screenshots (viewed with Read)
- Paper 1.21.11: `/tmp/claude-0/net01-1.21.11-summer.png` green; `net01-1.21.11-autumn.png` golden/orange with orange-red leaves; `net01-1.21.11-summer-again.png` green again.
- Folia 26.3: `/tmp/claude-0/net01-folia-26.3-summer.png` green; `net01-folia-26.3-autumn.png` golden; `net01-folia-26.3-summer-again.png` green.
- Folia 1.21.11: `/tmp/claude-0/net01-folia-1.21.11-summer.png` green; `net01-folia-1.21.11-autumn.png` golden; `net01-folia-1.21.11-summer-again.png` green.
- Paper 26.3 (earlier): `net01-26.3-summer.png`, `net01-26.3-autumn.png`, `net01-26.3-summer-again.png`, same colours.
- Reports: `/tmp/claude-0/net01-1.21.11-report.txt`, `net01-folia-26.3-report.txt`, `net01-folia-1.21.11-report.txt`, `net01-26.3-report.txt`.
- Server logs: `spikes/logs/net01-1.21.11-server.log`, `net01-folia-26.3-server.log`, `net01-folia-1.21.11-server.log`.

### Biome remap: verified
- Biome network ids come from the per-user synchronized registry (`User.getRegistry(worldgen/biome)`, `getByName`). No NMS. The ids differ per version (26.3: 43/42 of 69; 1.21.11: 42/41 of 67). Ids must be looked up at runtime, not hard-coded.
- The colour switch shows on both versions and on both Paper and Folia, after a chunk resend.
- The chunk timings include first-chunk class loading in the max. Averages are per packet over all chunk packets, with the remap. No separate with/without-remap baseline was measured.

### Item lore / custom_model_data strip: verified via injected path
- Creative return (`creativetest`): server stack = IRON_INGOT x5, `lore=null`, `cmd=null`, `pdcKeys=[]`, `stripped=1`, on all four runs.
- Caveat: injected through `PacketEvents.getPlayerManager().receivePacket(...)`. Not a real creative click on the wire, so the Netty decoder order on the real path was not exercised.
- Item packet timings are 6 samples per run: 897 to 1833 us avg. Too few samples for a steady-state figure.

### Threads (Folia threading shown)
- Every listener ran on a `Netty Epoll IO` thread on all four targets. None ran on a region or global thread.
- With `tfp.assertThreads=true`, no "not owned" or "Thread failed" lines in the Folia logs. The only exception in each log is PacketEvents' update checker (HTTP 403 from api.github.com), unrelated to the listeners.

### Pipeline order
From the report, head to tail (identical on 1.21.11 and 26.3):
`FlushConsolidationHandler, timeout, splitter, decompress, FlowControlHandler, craftengine_decoder, pe-decoder-packetevents, decoder, prepender, compress, craftengine_encoder, pe-encoder-packetevents, encoder, unbundler, hackfix, craftengine_player_channel_handler, bettermodel_channel_handler, betterhud, packet_handler, tail`

Outbound order (tail to head, so the encoder side runs in this order):
1. `encoder` (vanilla): Packet to ByteBuf.
2. `pe-encoder-packetevents`: `PacketEventsEncoder extends ChannelOutboundHandlerAdapter` (verified in the jar). Sees ByteBuf.
3. `craftengine_encoder`: `BukkitNetworkManager$PluginChannelEncoder extends MessageToMessageEncoder<ByteBuf>` (verified in the jar).
4. `compress`, then `prepender`, then the socket.

What this lets me conclude:
- Both plugin encoders run before compression and framing.
- `craftengine_encoder` runs after `pe-encoder-packetevents` outbound, so CE sees the bytes after PacketEvents' rewrite. Inferred from list position.

What I cannot conclude:
- Whether CE's item interception sees a decorated item from PacketEvents. Not measured; no CE item check was run.
- What happens when both plugins rewrite the same packet. The `markForReEncode` handoff is not tested.
- The inbound creative order. Not exercised.

## Decision / recommendation
**Use PacketEvents** for the packet layer. All targets (Paper 1.21.11, Folia 26.3, Folia 1.21.11, and Paper 26.3 from the earlier run) show the grass switch after a chunk resend and the creative-return strip. Ids come from the PacketEvents registry API, no NMS. Listeners run on Netty IO threads, so the packet work must not touch Bukkit state directly; it must hand off to the region or global thread when it does.

Remaining work that PacketEvents does not settle: the real creative-click path (inbound) and the CE item-interception order must be measured in a later spike before the item-lore code is used with CE items.

## Open / UNVERIFIED
1. **Creative strip is verified only through PacketEvents' injected receive path**, not a real creative click on the wire.
2. **CE item interception order:** inferred from pipeline position only. Not measured.
3. **Item packet timing:** 6 samples per run, not repeated. Likely dominated by first-call / class loading. UNVERIFIED as a steady-state number.
4. **Chunk timing:** the maxima include first-chunk class loading. No with/without-remap baseline.
5. **Paper 26.3 was not rerun this session.** Its facts come from the earlier session (see Setup).
6. **HUD row glyphs:** the top row of the screenshots shows missing-glyph boxes on all four targets. Observed, not investigated, not part of the grass result.
7. **Environment noise:** PacketEvents prints "Failed to check for updates" (HTTP 403) on every run. Harmless to this spike. `client/session.sh` calls `xdpyinfo`, which is not installed here, so it always tries to start Xvfb on its display (fails silently because `:105` already exists).
