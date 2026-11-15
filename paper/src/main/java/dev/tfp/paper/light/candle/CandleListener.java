/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (useOn: the weak start on a candle),
 *       src/main/java/net/dries007/tfc/common/items/CandleBlockItem.java @ c3b4d9536 (useOn),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.light.candle;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.light.candle.CandleRules.Family;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The item side of PAR-HEAT-11, before CraftEngine places the held item:
 *
 * <ul>
 *   <li>TFC {@code TorchItem.useOn}: a held lit torch first tries a weak fire start on the clicked block; on a candle,
 *       candle cake, carved pumpkin or jack-o'-lantern that lights it ({@link CandleService#light}) and the torch is not
 *       placed.</li>
 *   <li>TFC {@code CandleBlockItem.useOn}: a candle on a cake with no bite taken becomes the candle cake.</li>
 *   <li>TFC {@code TFCPumpkinBlock.useItemOn}: a knife or shears on TFC's pumpkin carves it (a block use: skipped while
 *       sneaking with an item, as vanilla).</li>
 * </ul>
 *
 * The torch and the candle are item uses, which vanilla runs in neither spectator nor adventure (without
 * {@code can_place_on}); the carving is a block use, which runs in adventure too.
 */
public final class CandleListener implements Listener {
    /** The lit torch item (PAR-HEAT-10). */
    public static final String TORCH_ITEM = "tfp:torch";

    /** TFC {@code StartFireEvent.startFire(level, pos, state, face, player, stack, WEAK, -1)}. */
    @FunctionalInterface
    public interface WeakStart {
        void start(Block block, BlockFace face, Player player, ItemStack stack);
    }

    private final Function<ItemStack, Optional<String>> itemId;
    private final Predicate<ItemStack> carvingTool;
    private final CustomBlocks blocks;
    private final CandleService service;
    private final CandleEffects effects;
    private final WeakStart start;
    private final BooleanSupplier carvingEnabled;
    private final Supplier<CandleService.Rotting> rotting;

    /**
     * @param carvingTool    TFC {@code #c:tools/knife} or {@code #c:tools/shear}
     * @param carvingEnabled TFC {@code enablePumpkinCarving}
     * @param rotting        the pumpkin's rot (PAR-FARM-05); never rotten until it exists
     */
    public CandleListener(Function<ItemStack, Optional<String>> itemId, Predicate<ItemStack> carvingTool,
            CustomBlocks blocks, CandleService service, CandleEffects effects, WeakStart start,
            BooleanSupplier carvingEnabled, Supplier<CandleService.Rotting> rotting) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.carvingTool = Objects.requireNonNull(carvingTool, "carvingTool");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.service = Objects.requireNonNull(service, "service");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.start = Objects.requireNonNull(start, "start");
        this.carvingEnabled = Objects.requireNonNull(carvingEnabled, "carvingEnabled");
        this.rotting = Objects.requireNonNull(rotting, "rotting");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || held.getType().isAir()) {
            return;
        }
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR) {
            return;
        }
        Block block = event.getClickedBlock();
        // TFCPumpkinBlock.useItemOn: a block use, skipped while sneaking with an item
        if (!player.isSneaking() && carvingTool.test(held)) {
            CandleService.Carve carve = service.carve(block, event.getBlockFace().name().toLowerCase(Locale.ROOT),
                horizontal(player.getLocation().getYaw()), player, event.getHand(), carvingEnabled.getAsBoolean(), rotting.get());
            if (carve != CandleService.Carve.PASS) {
                event.setCancelled(true);
                return;
            }
        }
        if (mode == GameMode.ADVENTURE) {
            return; // ItemStack.useOn: no item use on blocks without can_place_on
        }
        Optional<String> id = itemId.apply(held);
        if (id.isEmpty()) {
            return;
        }
        if (id.get().equals(TORCH_ITEM)) {
            if (lightsWithTorch(block)) {
                event.setCancelled(true); // TFC: startFire succeeded, so the torch is not placed
                start.start(block, event.getBlockFace(), player, held.clone());
            }
            return;
        }
        if (CandleRules.candleCakeOf(id.get()).isPresent()
                && service.addCandle(block, player, event.getHand(), id.get())) {
            event.setCancelled(true);
        }
    }

    /** Whether the weak start of a held torch lights {@code block} (TFC {@code onFireStart}'s branches). */
    boolean lightsWithTorch(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return effects.carvedPumpkin(block).isPresent() && blocks.exists(CandleRules.JACK_O_LANTERN);
        }
        Optional<Family> family = CandleRules.family(state.get().id());
        return family.isPresent()
            && CandleRules.lights(family.get(), "true".equals(state.get().properties().get(CandleRules.WATERLOGGED)));
    }

    /** Vanilla {@code Direction.fromYRot}: the horizontal facing of a yaw (0 south, 90 west, 180 north, 270 east). */
    static String horizontal(float yaw) {
        return switch (Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4)) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
    }
}
