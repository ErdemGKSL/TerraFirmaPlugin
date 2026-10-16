# M0-NET-01: Packet layer: biome remap + item lore
**Status:** partial — 2026-10-16. Paper 26.3 verified. Paper 1.21.11 NOT verified (not run). Folia not run.

## Setup
- Spike: `spikes/net-01` on Paper 26.3-196-7ec11b2 with PacketEvents 2.14.0, CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449.
- Datapack `tfpnet` from `spikes/net-01/build_datapack.py`: two plains biome variants, `tfpspike:plains_summer` and `tfpspike:plains_autumn`. A `ChunkGenerator` BiomeProvider returns the summer biome.
- Driver: `spikes/net-01/run-client-check.sh 26.3 <port>`. A real vanilla client joins through `client/session.sh`; screenshots through `client/shot.sh`. Commands: `/net01 season summer|autumn`, `give`, `creativetest`, `pipeline`, `report`.
- The data was produced in an earlier session. In this session I read the report and viewed the three screenshots; I did not rerun the spike.
- Listener: one `PacketListener` at `PacketListenerPriority.NORMAL`. Chunk biome rewrite via `WrapperPlayServerChunkData`; item lore and `custom_model_data` via `WrapperPlayServerSetSlot` and `WrapperPlayServerWindowItems`; creative strip via `WrapperPlayClientCreativeInventoryAction`.

## Results (26.3, Paper)
### Biome remap: verified
- Biome network ids come from the per-user synchronized registry (`User.getRegistry(worldgen/biome)`, `getByName`). No NMS. Summer = 43, autumn = 42, registry size 69.
- Screenshots in `/tmp/claude-0/`, viewed:
  - `net01-26.3-summer.png`: green grass.
  - `net01-26.3-autumn.png`: golden/orange grass.
  - `net01-26.3-summer-again.png`: green again after a resend.
- Remapped 4x4x4 biome cells: 259584 over 603 chunk packets.
- Chunk listener time: avg 1153 us, max 271708 us. The max includes the first chunk per user, which pays class loading. The average covers all chunk packets, with and without the remap.

### Item lore / custom_model_data strip: partly verified
- Creative return (`creativetest`): server stack = IRON_INGOT x5, `lore=null`, `cmd=null`, `pdcKeys=[]`, `stripped=1`.
- Caveat: this was injected through `PacketEvents.getPlayerManager().receivePacket(...)`. It is not a real creative click on the wire, so the Netty decoder order on the real path was not exercised.
- Item packets: 6 packets, avg 39308 us. UNVERIFIED: 6 samples, probably dominated by first-call / class loading, not repeated.

### Threads
- Listener threads: `chunk` 603, `set_slot` 4, `window_items` 2, `creative` 1. All on `Netty Epoll IO` threads. None on the server thread or a region thread.

### Pipeline order
From the report, head to tail:
`FlushConsolidationHandler, timeout, splitter, decompress, FlowControlHandler, craftengine_decoder, pe-decoder-packetevents, decoder, prepender, compress, craftengine_encoder, pe-encoder-packetevents, encoder, unbundler, hackfix, craftengine_player_channel_handler, bettermodel_channel_handler, betterhud, packet_handler, tail`

Netty runs outbound events from the tail towards the head. The encoder-side outbound order is therefore:
1. `encoder` (vanilla): Packet to ByteBuf.
2. `pe-encoder-packetevents`: `PacketEventsEncoder extends ChannelOutboundHandlerAdapter` (verified in the jar). It runs after the vanilla serializer, so it sees ByteBuf.
3. `craftengine_encoder`: `BukkitNetworkManager$PluginChannelEncoder extends MessageToMessageEncoder<ByteBuf>` (verified in the jar).
4. `compress`, then `prepender` (vanilla framing), then the socket.

What this lets me conclude:
- Both plugin encoders run before compression and length-prefixing. Neither sees the final framed bytes; `compress` and `prepender` are the last writers.
- `craftengine_encoder` runs after `pe-encoder-packetevents` outbound. So CE sees the bytes after PacketEvents' rewrite (the chunk remap, the lore and `custom_model_data` edits). This is inferred from list position.

What I cannot conclude:
- Whether CE's item interception sees a decorated item from PacketEvents. This is not measured; no CE item check was run.
- What happens when both plugins rewrite the same packet. The `markForReEncode` handoff between the two is not tested.
- The inbound creative order. By position, `craftengine_decoder` sees raw bytes before `pe-decoder-packetevents` and vanilla `decoder`. Not exercised.

## Decision / recommendation
**Use PacketEvents** for the packet layer, for the Paper 26.3 facts above. It rewrote chunk biome palettes and item lore/CMD on Netty threads, the real client showed the colour switch after a resend, and the creative-return strip works through PacketEvents' receive path. No NMS is needed: ids come from the PacketEvents registry API.

The decision is provisional until the 1.21.11 run and a CE-order measurement exist. The plan's Done-when requires both versions.

## Open / UNVERIFIED
1. **Paper 1.21.11: not verified.** `/tmp/claude-0/net01-1.21.11-report.txt` does not exist. `/tmp/claude-0/net01-run-1.21.11.out` has one line (the datapack step), with no session, screenshots or report. Not run, because the machine is shared.
2. Creative strip is verified only through PacketEvents' injected receive path, not a real creative click on the wire.
3. Item packet timing (39308 us avg, 6 samples): UNVERIFIED, likely class-load dominated, not repeated.
4. Chunk timing: the 271708 us max includes first-chunk class loading. There is no steady-state per-packet figure.
5. Folia: not run. Threading was only checked on Paper, where every listener ran on Netty IO threads.
6. CE item interception order: inferred from pipeline position only. Not measured.
7. The HUD row at the top of the three screenshots shows missing-glyph boxes. Observed, not investigated, not part of the grass result.
