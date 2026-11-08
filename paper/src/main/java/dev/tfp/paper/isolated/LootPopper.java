/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536
 */
package dev.tfp.paper.isolated;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.loot.LootTable;
import dev.tfp.paper.neighbour.WorldBlocks;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Effect;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * TFC's {@code Helpers.destroyBlockAndDropBlocksManually} with {@code TFCLoot.ISOLATED} set, as the isolated check calls
 * it: the break level event (2001, particles and sound), the block's loot table rolled with an empty tool and
 * {@code isolated = true} (so a raw rock drops itself), then the block removed. Drops follow the {@code doTileDrops}
 * game rule, as vanilla's {@code Block.popResource}. Owner thread of the block.
 */
final class LootPopper implements IsolatedBlocks.Popper {
    private final LootService loot;
    private final CustomBlocks custom;
    private final Logger logger;
    private final Set<String> noTable = ConcurrentHashMap.newKeySet();
    private final GameRule<Boolean> tileDrops = tileDropsRule();

    LootPopper(LootService loot, CustomBlocks custom, Logger logger) {
        this.loot = loot;
        this.custom = custom;
        this.logger = logger;
    }

    /**
     * The {@code doTileDrops} rule (renamed {@code minecraft:block_drops} in 1.21.11), looked up by name so that no
     * version without the old constant fails to link; null when the server has neither.
     */
    @SuppressWarnings("unchecked")
    static GameRule<Boolean> tileDropsRule() {
        for (GameRule<?> rule : GameRule.values()) {
            if (rule.getType() != Boolean.class) {
                continue;
            }
            String key = rule.getKey().getKey();
            String name = rule.getName();
            if (key.equals("block_drops") || name.equals("block_drops") || key.equals("dotiledrops")
                    || name.equals("doTileDrops")) {
                return (GameRule<Boolean>) rule;
            }
        }
        return null;
    }

    @Override
    public void pop(Block block) {
        World world = block.getWorld();
        BlockStateRef state = WorldBlocks.stateOf(block, custom);
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        world.playEffect(centre, Effect.STEP_SOUND, block.getBlockData());
        if (tileDrops == null || !Boolean.FALSE.equals(world.getGameRuleValue(tileDrops))) {
            Optional<LootTable> table = loot.tables().forBlock(state.id());
            if (table.isPresent()) {
                LootContext context = LootContext.builder()
                        .block(state.id(), state.properties())
                        .tool(LootContext.Tool.EMPTY)
                        .isolated(true)
                        .build();
                loot.dropBlock(block, table.get(), context);
            } else if (noTable.add(state.id())) {
                logger.warning("Isolated " + state.id() + " popped without a loot table (tables not loaded?)");
            }
        }
        if (!custom.remove(block)) {
            block.setType(Material.AIR);
        }
    }
}
