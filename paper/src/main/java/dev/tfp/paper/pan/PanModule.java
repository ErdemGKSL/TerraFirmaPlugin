/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pan;

import dev.tfp.core.pan.PanDeposits;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** PAR-PROC-12: panning ({@link PanListener}). Needs loot and the registries; listed after {@code loot}. */
public final class PanModule implements FeatureModule {
    public static final String ID = "pan";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        LootService loot = context.services().require(LootService.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        CustomItems items = context.integrations().customItems();
        Supplier<Registries> registries = () -> context.registryService().registries();
        context.listen(new PanListener(new Env(context, loot, blocks, items), versioned(registries), context.scheduler()));
    }

    private record Env(ModuleContext context, LootService loot, CustomBlocks blocks, CustomItems items)
            implements PanListener.Env {
        @Override
        public Optional<String> itemId(ItemStack stack) {
            return items.idOf(stack);
        }

        @Override
        public Optional<ItemStack> create(String id) {
            return items.create(id, 1);
        }

        @Override
        public Optional<String> blockId(Block block) {
            return blocks.stateAt(block).map(BlockStateRef::id);
        }

        @Override
        public void removeBlock(Block block) {
            blocks.remove(block);
        }

        /** TFC {@code ANY_INFINITE_WATER} at the player's block: water, or a waterlogged block. */
        @Override
        public boolean inInfiniteWater(Player player) {
            Block at = player.getLocation().getBlock();
            return at.getType() == Material.WATER || at.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
        }

        @Override
        public void sound(Player player) {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BRUSH_BRUSHING_GRAVEL, SoundCategory.PLAYERS, 1f, 1f);
        }

        @Override
        public void message(Player player, String key) {
            player.sendActionBar(Component.text(context.messages().get(player, key)));
        }

        @Override
        public CompletableFuture<List<ItemStack>> roll(Player player, String tableId, ItemStack pan) {
            return loot.tables().get(tableId).map(table -> {
                LootContext ctx = LootContext.builder().thisEntity(true).tool(loot.tool(pan)).build();
                return CompletableFuture.supplyAsync(() -> loot.roll(table, ctx), context.scheduler().cpuExecutor())
                    .thenApply(drops -> loot.stacks(drops, null));
            }).orElseGet(() -> CompletableFuture.completedFuture(List.of()));
        }
    }

    /** The deposit table rebuilt when the registries change version. Thread-safe. */
    static Supplier<PanDeposits> versioned(Supplier<Registries> registries) {
        record Entry(long version, PanDeposits value) {
        }
        PanDeposits empty = PanDeposits.of(java.util.Map.of());
        AtomicReference<Entry> cache = new AtomicReference<>(new Entry(0, empty));
        Function<RegistrySnapshot, PanDeposits> build = PanDeposits::fromRegistry;
        return () -> {
            Registries r = registries.get();
            if (r == null || r.version() == 0) {
                return empty;
            }
            Entry e = cache.get();
            if (e.version() == r.version()) {
                return e.value();
            }
            PanDeposits value = build.apply(r.get());
            cache.set(new Entry(r.version(), value));
            return value;
        };
    }
}
