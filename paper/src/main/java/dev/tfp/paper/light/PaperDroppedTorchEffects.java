/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (onEntityItemUpdate),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (dropWithContext, playSound)
 */
package dev.tfp.paper.light;

import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.light.TorchRules.Kind;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootDrop;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.loot.LootTable;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link DroppedTorchListener.Effects}: TFC's {@code dropWithContext(TORCH, pos, burnt_out)} once per
 * extinguished torch (rolled on the CPU pool, dropped back on the region of the position), the extinguish sound and the
 * strong fire start through {@link FireStarting}. Without the loot service or the torch table (until PAR-HEAT-21 loads
 * the torch blocks) the torches go out without loot; without the fire starting service nothing is lit.
 */
public final class PaperDroppedTorchEffects implements DroppedTorchListener.Effects {
    private final Scheduler scheduler;
    private final Supplier<Optional<LootService>> loot;
    private final Supplier<Optional<FireStarting>> fire;

    public PaperDroppedTorchEffects(Scheduler scheduler, Supplier<Optional<LootService>> loot,
            Supplier<Optional<FireStarting>> fire) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.loot = Objects.requireNonNull(loot, "loot");
        this.fire = Objects.requireNonNull(fire, "fire");
    }

    /** TFC's loot parameters for a burnt-out dropped torch: the torch block, an empty tool, {@code burnt_out}. */
    static LootContext context() {
        return LootContext.builder().block(Kind.TORCH.id(), Map.of()).tool(LootContext.Tool.EMPTY).burntOut(true)
            .build();
    }

    @Override
    public void burntOut(Location at, int count) {
        Optional<LootService> service = loot.get();
        Optional<LootTable> table = service.flatMap(s -> s.tables().forBlock(Kind.TORCH.id()));
        if (table.isEmpty() || count < 1) {
            return;
        }
        LootService s = service.get();
        LootContext ctx = context();
        scheduler.cpu(() -> {
            List<LootDrop> drops = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                drops.addAll(s.roll(table.get(), ctx));
            }
            if (drops.isEmpty()) {
                return;
            }
            scheduler.region(at, () -> {
                for (ItemStack stack : s.stacks(drops, null)) {
                    at.getWorld().dropItemNaturally(at, stack);
                }
            });
        });
    }

    @Override
    public void extinguishSound(Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f + r.nextFloat(),
            r.nextFloat() + 0.7f + 0.3f);
    }

    @Override
    public void strongFire(Block block) {
        fire.get().ifPresent(f -> f.start(block, BlockFace.UP, null, ItemStack.empty(),
            FireStarting.Starter.fixed(FireStart.Strength.STRONG, FireStart.NO_FIREPIT), r -> { }));
    }
}
