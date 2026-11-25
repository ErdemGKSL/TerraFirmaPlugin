/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bowl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bowl.BowlRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-POT-09 on MockBukkit with fakes: the four uses of TFC's {@code BowlBlock.useItemOn} in order, the 16-item limit, the
 * powders-only slot, the glass hook, removal and the placement of the vanilla bowl.
 *
 * <p>Stand-ins (MockBukkit has no custom items): redstone and gunpowder are powders, sugar is the salt powder
 * ({@code tfp:powder/salt}), glowstone dust is soda ash (a glass powder), beef is a saltable food, a stick is a held glass batch.
 */
class BowlDevicesMockBukkitTest extends MockServerTest {
    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

        @Override
        public ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }

    static final class Effects implements BowlEffects {
        final Map<String, ItemStack> shown = new HashMap<>();
        int sand;
        final List<String> placed = new ArrayList<>();

        @Override
        public void show(Block block, ItemStack content) {
            shown.put(key(block), content == null ? ItemStack.empty() : content.clone());
        }

        @Override
        public void hide(Block block) {
            shown.remove(key(block));
        }

        @Override
        public void sand(Block block) {
            sand++;
        }

        @Override
        public void placed(Block block, boolean wooden) {
            placed.add(key(block) + (wooden ? ":wood" : ":stone"));
        }

        static String key(Block b) {
            return b.getX() + "," + b.getY() + "," + b.getZ();
        }
    }

    static final class Glass implements BowlGlass {
        final List<String> applied = new ArrayList<>();

        @Override
        public boolean working(ItemStack held) {
            return held.getType() == Material.STICK;
        }

        @Override
        public boolean apply(ItemStack held, String operationId) {
            applied.add(operationId);
            return true;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(BowlDevices.CERAMIC, BowlDevices.WOODEN);
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();
    private final Glass glass = new Glass();
    private DeviceService service;
    private BowlDevices bowls;
    private World world;
    private PlayerMock player;
    private int next;

    private static boolean tag(ItemStack s, String tag) {
        return switch (tag) {
            case BowlDevices.POWDERS_TAG -> s.getType() == Material.REDSTONE || s.getType() == Material.GUNPOWDER
                || s.getType() == Material.SUGAR || s.getType() == Material.GLOWSTONE_DUST;
            case BowlDevices.SALTABLE_TAG -> s.getType() == Material.BEEF;
            default -> false;
        };
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("bowl");
        CustomItems items = new CustomItems() {
            @Override
            public boolean exists(String id) {
                return false;
            }

            @Override
            public Optional<ItemStack> create(String id, int amount) {
                return Optional.empty();
            }

            @Override
            public Optional<String> idOf(ItemStack stack) {
                return switch (stack.getType()) {
                    case SUGAR -> Optional.of(BowlRules.SALT);
                    case GLOWSTONE_DUST -> Optional.of("tfp:powder/soda_ash");
                    default -> Optional.empty();
                };
            }
        };
        BowlFood food = new BowlFood() {
            @Override
            public boolean canSalt(ItemStack stack) {
                return !(stack.hasItemMeta() && stack.getItemMeta().hasDisplayName());
            }

            @Override
            public void salt(ItemStack stack) {
                stack.editMeta(m -> m.setDisplayName("salted"));
            }
        };
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 0L, new TableCodec(),
            new dev.tfp.paper.integration.FakeCustomItems(), Logger.getLogger("test"));
        bowls = new BowlDevices(BowlDevicesMockBukkitTest::tag, items, effects, food, glass);
        bowls.kinds().forEach(service::register);
        player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 65, 5.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private Block bowl(String id) {
        Block b = world.getBlockAt(next += 2, 65, 0);
        blocks.place(b, BlockStateRef.of(id), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        return b;
    }

    private boolean click(Block b, ItemStack held) {
        return click(b, held, EquipmentSlot.HAND);
    }

    private boolean click(Block b, ItemStack held, EquipmentSlot hand) {
        player.getInventory().setItem(hand, held);
        return service.use(b, player, hand, held);
    }

    private ItemStack content(Block b) {
        DeviceRecord<?> r = service.catchUp(b).orElseThrow();
        return service.stacks().decode(r.slots().get(0));
    }

    private int heldCount() {
        ItemStack h = player.getInventory().getItemInMainHand();
        return h.getType().isAir() ? 0 : h.getAmount();
    }

    @Test
    void bothKindsAreRegisteredForTheirBlocks() {
        assertThat(bowls.kinds()).extracting(k -> k.id()).containsExactly("tfp:ceramic/bowl", "tfp:wooden_bowl");
        assertThat(bowls.kinds()).allSatisfy(k -> {
            assertThat(k.slots()).isEqualTo(1);
            assertThat(k.slotLimit(0)).isEqualTo(16);
        });
    }

    @Test
    void aHeldPowderGoesInAndTheHandIsEmptied() {
        Block b = bowl(BowlDevices.CERAMIC);
        assertThat(click(b, new ItemStack(Material.REDSTONE, 5))).isTrue();
        assertThat(content(b).getType()).isEqualTo(Material.REDSTONE);
        assertThat(content(b).getAmount()).isEqualTo(5);
        assertThat(heldCount()).isZero();
        assertThat(effects.shown.get(Effects.key(b)).getAmount()).isEqualTo(5);
        assertThat(effects.sand).isEqualTo(1);
    }

    @Test
    void theBowlHoldsAtMostSixteenAndTheRestStaysInTheHand() {
        Block b = bowl(BowlDevices.WOODEN);
        click(b, new ItemStack(Material.REDSTONE, 20));
        assertThat(content(b).getAmount()).isEqualTo(16);
        assertThat(heldCount()).isEqualTo(4);
        click(b, new ItemStack(Material.REDSTONE, 4));
        assertThat(content(b).getAmount()).as("full: the click is used, nothing moves").isEqualTo(16);
        assertThat(heldCount()).isEqualTo(4);
    }

    @Test
    void anotherPowderIsRefusedButTheClickIsUsed() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.REDSTONE, 3));
        assertThat(click(b, new ItemStack(Material.GUNPOWDER, 2))).isTrue();
        assertThat(content(b).getType()).isEqualTo(Material.REDSTONE);
        assertThat(content(b).getAmount()).isEqualTo(3);
        assertThat(heldCount()).isEqualTo(2);
    }

    @Test
    void anItemOutsideThePowderTagIsNotTaken() {
        Block b = bowl(BowlDevices.CERAMIC);
        assertThat(click(b, new ItemStack(Material.STONE, 2))).isFalse();
        assertThat(content(b).getType().isAir()).isTrue();
        assertThat(heldCount()).isEqualTo(2);
    }

    @Test
    void anEmptyMainHandTakesOneAndSneakingTakesSixteen() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.REDSTONE, 16));
        assertThat(click(b, ItemStack.empty())).isTrue();
        assertThat(content(b).getAmount()).isEqualTo(15);
        assertThat(player.getInventory().contains(Material.REDSTONE, 1)).isTrue();
        player.setSneaking(true);
        click(b, ItemStack.empty());
        assertThat(content(b).getType().isAir()).as("only 15 were left").isTrue();
        assertThat(effects.shown.get(Effects.key(b)).getType().isAir()).isTrue();
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.REDSTONE) {
                total += s.getAmount();
            }
        }
        assertThat(total).isEqualTo(16);
    }

    @Test
    void anEmptyOffHandDoesNothing() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.REDSTONE, 4));
        assertThat(click(b, ItemStack.empty(), EquipmentSlot.OFF_HAND)).isFalse();
        assertThat(content(b).getAmount()).isEqualTo(4);
    }

    @Test
    void saltSaltsAsManyFoodsAsItHolds() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.SUGAR, 8));
        assertThat(click(b, new ItemStack(Material.BEEF, 20))).isTrue();
        assertThat(heldCount()).isEqualTo(12);
        assertThat(content(b).getType().isAir()).isTrue();
        ItemStack salted = null;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.BEEF && s.hasItemMeta() && s.getItemMeta().hasDisplayName()) {
                salted = s;
            }
        }
        assertThat(salted).isNotNull();
        assertThat(salted.getAmount()).isEqualTo(8);
    }

    @Test
    void saltedFoodAndFoodWithoutSaltPass() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.SUGAR, 8));
        ItemStack done = new ItemStack(Material.BEEF, 3);
        done.editMeta(m -> m.setDisplayName("salted"));
        assertThat(click(b, done)).isFalse();
        Block other = bowl(BowlDevices.WOODEN);
        click(other, new ItemStack(Material.REDSTONE, 8));
        assertThat(click(other, new ItemStack(Material.BEEF, 3))).isFalse();
        assertThat(content(other).getAmount()).isEqualTo(8);
    }

    @Test
    void aGlassBatchTakesOnePowderThatMatchesAnOperation() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.GLOWSTONE_DUST, 3));
        assertThat(click(b, new ItemStack(Material.STICK))).isTrue();
        assertThat(glass.applied).containsExactly("tfc:soda_ash");
        assertThat(content(b).getAmount()).isEqualTo(2);
        assertThat(player.getCooldown(new ItemStack(Material.STICK))).isGreaterThan(0);
    }

    @Test
    void aGlassBatchWithoutAMatchingPowderPassesAndTakesNothing() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.REDSTONE, 3));
        assertThat(click(b, new ItemStack(Material.STICK))).isFalse();
        assertThat(glass.applied).isEmpty();
        assertThat(content(b).getAmount()).isEqualTo(3);
    }

    @Test
    void removingTheBowlRemovesTheLayer() {
        Block b = bowl(BowlDevices.CERAMIC);
        click(b, new ItemStack(Material.REDSTONE, 3));
        DataMap data = store.data.remove(FakeCustomBlocks.at(b));
        service.removed(world, b.getX(), b.getY(), b.getZ(), BowlDevices.CERAMIC, data);
        assertThat(effects.shown).doesNotContainKey(Effects.key(b));
    }

    // ---- the vanilla bowl is the wooden bowl item ------------------------------------------------------

    private PlayerInteractEvent rightClick(Block clicked, ItemStack held) {
        player.getInventory().setItemInMainHand(held);
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, clicked, BlockFace.UP, EquipmentSlot.HAND);
    }

    @Test
    void aVanillaBowlOnASturdyBlockPlacesTheWoodenBowlAndIsUsedUp() {
        Block stone = world.getBlockAt(10, 64, 4);
        stone.setType(Material.STONE);
        BowlPlacement placement = new BowlPlacement(blocks, effects);
        placement.onInteract(rightClick(stone, new ItemStack(Material.BOWL, 2)));
        Block above = stone.getRelative(BlockFace.UP);
        assertThat(blocks.stateAt(above).map(BlockStateRef::id)).contains(BowlDevices.WOODEN);
        assertThat(heldCount()).isEqualTo(1);
        assertThat(effects.placed).containsExactly(Effects.key(above) + ":wood");
    }

    @Test
    void sneakingOrCreativeOrNoFloorChangesWhatHappens() {
        BowlPlacement placement = new BowlPlacement(blocks, effects);
        Block stone = world.getBlockAt(10, 64, 4);
        stone.setType(Material.STONE);
        player.setSneaking(true);
        placement.onInteract(rightClick(stone, new ItemStack(Material.BOWL, 2)));
        assertThat(blocks.stateAt(stone.getRelative(BlockFace.UP))).as("sneaking opens the salad screen instead").isEmpty();
        player.setSneaking(false);
        Block air = world.getBlockAt(20, 64, 4);
        air.setType(Material.AIR);
        placement.onInteract(rightClick(air, new ItemStack(Material.BOWL, 2)));
        assertThat(blocks.stateAt(air.getRelative(BlockFace.UP))).as("nothing sturdy below").isEmpty();
        player.setGameMode(GameMode.CREATIVE);
        placement.onInteract(rightClick(stone, new ItemStack(Material.BOWL, 2)));
        assertThat(blocks.stateAt(stone.getRelative(BlockFace.UP)).map(BlockStateRef::id)).contains(BowlDevices.WOODEN);
        assertThat(heldCount()).as("creative keeps the stack").isEqualTo(2);
    }

    @Test
    void anotherItemIsNotPlaced() {
        BowlPlacement placement = new BowlPlacement(blocks, effects);
        Block stone = world.getBlockAt(10, 64, 4);
        stone.setType(Material.STONE);
        placement.onInteract(rightClick(stone, new ItemStack(Material.STICK, 2)));
        assertThat(blocks.stateAt(stone.getRelative(BlockFace.UP))).isEmpty();
    }
}
