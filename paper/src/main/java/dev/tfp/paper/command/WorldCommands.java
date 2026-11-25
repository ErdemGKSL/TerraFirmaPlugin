/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TFCCommands.java @ c3b4d9536 (the command glue)
 */
package dev.tfp.paper.command;

import dev.tfp.core.command.BlockSelector;
import dev.tfp.core.command.ClearWorldCommand;
import dev.tfp.core.command.ClearWorldCommand.Kind;
import dev.tfp.core.command.CountBlockCommand;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code /tfp count} and {@code /tfp clearworld} (TFC {@code CountBlockCommand}, {@code ClearWorldCommand}). The rules are
 * {@code core/command}; this is the world side. Both walk the square chunk by chunk on each chunk's region thread inside a
 * time slice ({@link ChunkJob}), reading the live block when the column's turn comes, so nothing runs on a thread that does
 * not own the block and no tick holds for a whole square. The reply comes when the last chunk is done.
 */
public final class WorldCommands {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final BiPredicate<BlockStateRef, String> blockTag;
    private final BiPredicate<Material, String> vanillaTag;
    private final LongSupplier nanos;

    /**
     * @param blockTag   whether a custom block state is in a tag (such as {@code tfc:prospectable})
     * @param vanillaTag whether a vanilla block is in a tag
     */
    public WorldCommands(Scheduler scheduler, CustomBlocks blocks, BiPredicate<BlockStateRef, String> blockTag,
            BiPredicate<Material, String> vanillaTag, LongSupplier nanos) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.blockTag = blockTag;
        this.vanillaTag = vanillaTag;
        this.nanos = nanos;
    }

    /** {@code /tfp count}: the blocks matching {@code request.block()} in the square around {@code at}, any thread. */
    public CompletableFuture<Reply> count(Location at, CountBlockCommand.Request request) {
        World world = at.getWorld();
        BlockSelector selector = request.block();
        LongAdder found = new LongAdder();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        return ChunkJob.run(scheduler, world, ChunkJob.Square.around(at.getBlockX(), at.getBlockZ(), request.radius()),
                nanos, ChunkJob.SLICE_NANOS, (x, z) -> {
                    int n = 0;
                    for (int y = minY; y < maxY; y++) {
                        if (matches(selector, world.getBlockAt(x, y, z))) {
                            n++;
                        }
                    }
                    found.add(n);
                }).thenApply(r -> skipNote(Reply.ok("command.data.count.done", found.sum()), r));
    }

    /** Whether {@code block} matches {@code selector}: its custom id and state, or its vanilla id and state. */
    boolean matches(BlockSelector selector, Block block) {
        Optional<BlockStateRef> custom = blocks.stateAt(block);
        if (custom.isPresent()) {
            BlockStateRef ref = custom.get();
            return selector.matches(ref.id(), tagSet(selector, blockTag.test(ref, selector.name())), ref.properties());
        }
        Material type = block.getType();
        String id = type.getKey().toString();
        boolean tagged = selector.kind() == BlockSelector.Kind.TAG && vanillaTag.test(type, selector.name());
        if (selector.kind() == BlockSelector.Kind.BLOCK && !selector.name().equals(id)) {
            return false;
        }
        Map<String, String> properties = selector.properties().isEmpty() ? Map.of() : propertiesOf(block);
        return selector.matches(id, tagSet(selector, tagged), properties);
    }

    private static Set<String> tagSet(BlockSelector selector, boolean inTag) {
        return selector.kind() == BlockSelector.Kind.TAG && inTag ? Set.of(selector.name()) : Set.of();
    }

    /** The vanilla state's properties from {@code minecraft:name[a=b,c=d]}. */
    static Map<String, String> propertiesOf(Block block) {
        String s = block.getBlockData().getAsString();
        int open = s.indexOf('[');
        if (open < 0) {
            return Map.of();
        }
        Map<String, String> out = new java.util.TreeMap<>();
        for (String pair : s.substring(open + 1, s.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return out;
    }

    /** {@code /tfp clearworld}: removes the preset's blocks below each column's surface, any thread. */
    public CompletableFuture<Reply> clear(Location at, ClearWorldCommand.Request request) {
        World world = at.getWorld();
        ClearWorldCommand.Preset preset = request.preset();
        LongAdder removed = new LongAdder();
        int minY = world.getMinHeight();
        return ChunkJob.run(scheduler, world, ChunkJob.Square.around(at.getBlockX(), at.getBlockZ(), request.radius()),
                nanos, ChunkJob.SLICE_NANOS, (x, z) -> {
                    // TFC: getHeight(WORLD_SURFACE) is the first free height; Bukkit's is the highest block.
                    int height = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1;
                    for (int y = minY; y < height; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        Kind kind = kindOf(block);
                        if (kind != Kind.AIR && preset.removes(kind)) {
                            remove(block);
                            removed.increment();
                        }
                    }
                }).thenApply(r -> skipNote(Reply.ok("command.data.clear.done", removed.sum()), r));
    }

    /** TFC's block sets as the generated content spells them; air first. */
    Kind kindOf(Block block) {
        Material type = block.getType();
        if (type.isAir()) {
            return Kind.AIR;
        }
        return blocks.stateAt(block).map(ref -> BlockKinds.ofCustom(ref.id())).orElse(Kind.OTHER);
    }

    /** Air with no physics (TFC flags 2 | 16); a custom block goes through its adapter so its data goes with it. */
    private void remove(Block block) {
        if (!blocks.remove(block)) {
            block.setType(Material.AIR, false);
        }
    }

    private static Reply skipNote(Reply reply, ChunkJob.Result r) {
        return r.skipped() == 0 ? reply : reply.withDetails(java.util.List.of(
                r.skipped() + " of " + (r.worked() + r.skipped()) + " chunks were not loaded and were skipped"));
    }
}
