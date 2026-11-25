/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil.weld;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.WeldingRecipe;
import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.device.DeviceRecord;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.anvil.AnvilClicks;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.anvil.forge.ForgeHeat;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tool.ForgingBonusPdc;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-06 on MockBukkit: welding over a copper anvil (tier 1) with fakes of the CraftEngine adapter and the heat. Stand-ins:
 * iron ingots weld to a gold ingot (the double ingot), copper ingots need tier 2, the golden pickaxe is the hammer, gunpowder the
 * flux.
 */
class AnvilWeldMockBukkitTest extends MockServerTest {
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

    static final IngredientContext<ItemStack> CTX = new IngredientContext<>() {
        @Override
        public String itemId(ItemStack stack) {
            return "minecraft:" + stack.getType().name().toLowerCase();
        }

        @Override
        public TagLookup tags() {
            return new TagLookup() {
                @Override
                public boolean contains(TagType type, String tag, String id) {
                    return false;
                }

                @Override
                public boolean exists(TagType type, String tag) {
                    return false;
                }
            };
        }

        @Override
        public FoodHooks<ItemStack> food() {
            return new FoodHooks<>() {
                @Override
                public boolean isRotten(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean hasTrait(ItemStack stack, String trait) {
                    return false;
                }
            };
        }

        @Override
        public HeatHooks<ItemStack> heat() {
            return stack -> 0f;
        }

        @Override
        public FluidHooks<ItemStack> fluids() {
            return stack -> Optional.empty();
        }
    };

    private static final String COPPER = "tfp:metal/anvil/copper";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(COPPER);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final FakeCustomItems items = new FakeCustomItems();
    private final AtomicInteger shown = new AtomicInteger();
    private final AtomicInteger kept = new AtomicInteger();
    private volatile boolean hot = true;
    private int tick = 1;
    private DeviceService devices;
    private AnvilWelding welding;
    private AnvilWeldListener listener;
    private World world;
    private Block anvil;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("anvilweld");
        WeldBook book = new WeldBook(List.of(
            entry("iron", new Ingredient.Item("minecraft:iron_ingot"), new Ingredient.Item("minecraft:iron_ingot"), 1,
                "minecraft:gold_ingot", WeldingRecipe.Bonus.COPY_BEST),
            entry("copper", new Ingredient.Item("minecraft:copper_ingot"), new Ingredient.Item("minecraft:copper_ingot"), 2,
                "minecraft:gold_block", WeldingRecipe.Bonus.IGNORE),
            entry("worst", new Ingredient.Item("minecraft:iron_nugget"), new Ingredient.Item("minecraft:iron_nugget"), 0,
                "minecraft:gold_nugget", WeldingRecipe.Bonus.COPY_WORST)));
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, items, Logger.getLogger("test"));
        AnvilEffects effects = new AnvilEffects() {
            @Override
            public void show(Block block, View view) {
                shown.incrementAndGet();
            }

            @Override
            public void hide(Block block) {
            }

            @Override
            public void state(Block block, BlockStateRef wanted) {
            }
        };
        ForgeHeat heat = new ForgeHeat() {
            @Override
            public boolean canWork(ItemStack stack) {
                return true;
            }

            @Override
            public void keep(ItemStack from, ItemStack to) {
                kept.incrementAndGet();
            }
        };
        Welder welder = new Welder(new Welder.Env(stacks, book, () -> CTX, s -> hot, heat,
            (e, main) -> new ItemStack(Material.matchMaterial(e.recipe().output().item())), () -> ForgingConfig.DEFAULTS));
        welding = new AnvilWelding(devices, blocks, effects, new Messages(getClass().getClassLoader()), welder);
        AnvilDevice.Services services = new AnvilDevice.Services(s -> s.getType() == Material.GOLDEN_PICKAXE,
            s -> s.getType() == Material.GUNPOWDER, effects, new AnvilClicks());
        devices.register(AnvilDevice.kind(AnvilDevice.METAL_ID, services));
        devices.register(AnvilDevice.kind(AnvilDevice.ROCK_ID, services));
        listener = new AnvilWeldListener(welding, blocks, new dev.tfp.paper.grill.TickScheduler(), s -> s.getType() == Material.GOLDEN_PICKAXE,
            () -> tick);
        anvil = world.getBlockAt(0, 64, 0);
        blocks.place(anvil, BlockStateRef.of(COPPER).with("facing", "east"), true);
        store.data.put(FakeCustomBlocks.at(anvil), new DataMap());
        player = server.addPlayer("Bot");
    }

    private static WeldBook.Entry entry(String id, Ingredient a, Ingredient b, int tier, String out, WeldingRecipe.Bonus bonus) {
        return new WeldBook.Entry(new WeldingRecipe("tfc:welding/" + id, a, b, tier, new CraftOutput(out, 1), bonus),
            new ItemStackProvider(Optional.empty(), List.of()));
    }

    @SuppressWarnings("unchecked")
    private DeviceKind<AnvilState> kind() {
        return (DeviceKind<AnvilState>) devices.kinds().find(COPPER).orElseThrow();
    }

    private DeviceRecord<AnvilState> record() {
        return kind().codec().decode(store.read(anvil).orElseThrow());
    }

    private ItemStack slot(int slot) {
        return stacks.decode(record().slot(slot));
    }

    private void set(int slot, ItemStack stack) {
        devices.edit(anvil, kind(), (r, t) -> r.withSlot(slot, stacks.encode(stack)));
    }

    private void load(Material a, Material b, int flux) {
        set(AnvilSlots.INPUT_MAIN, new ItemStack(a));
        set(AnvilSlots.INPUT_SECOND, new ItemStack(b));
        if (flux > 0) {
            set(AnvilSlots.CATALYST, new ItemStack(Material.GUNPOWDER, flux));
        }
    }

    @Test
    void weldingConsumesOneFluxAndMakesTheResultOfTheTwoInputs() {
        load(Material.IRON_INGOT, Material.IRON_INGOT, 3);
        assertThat(welding.canWeld(anvil, slot(AnvilSlots.INPUT_MAIN), slot(AnvilSlots.INPUT_SECOND))).isTrue();
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.SUCCESS);
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(slot(AnvilSlots.INPUT_SECOND).isEmpty()).isTrue();
        assertThat(slot(AnvilSlots.CATALYST).getAmount()).isEqualTo(2);
        assertThat(kept.get()).as("the heat of both inputs is passed on").isEqualTo(2);
        assertThat(shown.get()).isPositive();
    }

    @Test
    void theLastFluxIsUsedUp() {
        load(Material.IRON_INGOT, Material.IRON_INGOT, 1);
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.SUCCESS);
        assertThat(slot(AnvilSlots.CATALYST).isEmpty()).isTrue();
    }

    @Test
    void withoutFluxItFailsWithTheMessageAndChangesNothing() {
        load(Material.IRON_INGOT, Material.IRON_INGOT, 0);
        assertThat(welding.canWeld(anvil, slot(AnvilSlots.INPUT_MAIN), slot(AnvilSlots.INPUT_SECOND))).isFalse();
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.FAIL);
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(slot(AnvilSlots.INPUT_SECOND).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(PlainTextComponentSerializerHolder.last(player)).isEqualTo("There is no flux in the anvil!");
    }

    @Test
    void tooColdFailsWithTheMessage() {
        load(Material.IRON_INGOT, Material.IRON_INGOT, 2);
        hot = false;
        assertThat(welding.canWeld(anvil, slot(AnvilSlots.INPUT_MAIN), slot(AnvilSlots.INPUT_SECOND))).isFalse();
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.FAIL);
        assertThat(slot(AnvilSlots.CATALYST).getAmount()).isEqualTo(2);
        assertThat(PlainTextComponentSerializerHolder.last(player)).isEqualTo("Not hot enough to weld!");
    }

    @Test
    void aTooLowAnvilTierFailsWithTheMessage() {
        load(Material.COPPER_INGOT, Material.COPPER_INGOT, 2);
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.FAIL);
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.COPPER_INGOT);
        assertThat(PlainTextComponentSerializerHolder.last(player)).isEqualTo("The Anvil is not a high enough tier to weld that!");
    }

    @Test
    void nothingToWeldPasses() {
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.PASS);
        load(Material.IRON_INGOT, Material.COPPER_INGOT, 2);
        assertThat(welding.canWeld(anvil, slot(AnvilSlots.INPUT_MAIN), slot(AnvilSlots.INPUT_SECOND))).isFalse();
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.PASS);
        assertThat(slot(AnvilSlots.CATALYST).getAmount()).isEqualTo(2);
    }

    @Test
    void theBothOrdersWeldAndCopyBestKeepsTheBetterBonus() {
        ItemStack a = new ItemStack(Material.IRON_INGOT);
        ForgingBonusPdc.write(a, ForgingQuality.WELL, ForgingConfig.DEFAULTS);
        ItemStack b = new ItemStack(Material.IRON_INGOT);
        ForgingBonusPdc.write(b, ForgingQuality.PERFECT, ForgingConfig.DEFAULTS);
        set(AnvilSlots.INPUT_MAIN, a);
        set(AnvilSlots.INPUT_SECOND, b);
        set(AnvilSlots.CATALYST, new ItemStack(Material.GUNPOWDER));
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.SUCCESS);
        assertThat(ForgingBonusPdc.read(slot(AnvilSlots.INPUT_MAIN))).isEqualTo(ForgingQuality.PERFECT);
    }

    @Test
    void copyWorstKeepsTheWorseBonusAndNoneCountsAsWorst() {
        ItemStack a = new ItemStack(Material.IRON_NUGGET);
        ForgingBonusPdc.write(a, ForgingQuality.EXPERT, ForgingConfig.DEFAULTS);
        set(AnvilSlots.INPUT_MAIN, a);
        set(AnvilSlots.INPUT_SECOND, new ItemStack(Material.IRON_NUGGET));
        set(AnvilSlots.CATALYST, new ItemStack(Material.GUNPOWDER));
        assertThat(welding.attempt(player, anvil)).isEqualTo(Welder.Result.SUCCESS);
        assertThat(ForgingBonusPdc.read(slot(AnvilSlots.INPUT_MAIN))).isEqualTo(ForgingQuality.NONE);
    }

    @Test
    void copyBestOfABonusAndNoneKeepsTheBonus() {
        ItemStack a = new ItemStack(Material.IRON_INGOT);
        ForgingBonusPdc.write(a, ForgingQuality.WELL, ForgingConfig.DEFAULTS);
        set(AnvilSlots.INPUT_MAIN, a);
        set(AnvilSlots.INPUT_SECOND, new ItemStack(Material.IRON_INGOT));
        set(AnvilSlots.CATALYST, new ItemStack(Material.GUNPOWDER));
        welding.attempt(player, anvil);
        assertThat(ForgingBonusPdc.read(slot(AnvilSlots.INPUT_MAIN))).isEqualTo(ForgingQuality.WELL);
    }

    @Test
    void aSneakingHammerClickWeldsAndConsumesTheClick() {
        load(Material.IRON_INGOT, Material.IRON_INGOT, 2);
        player.setSneaking(true);
        ItemStack hammer = new ItemStack(Material.GOLDEN_PICKAXE);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hammer, anvil, org.bukkit.block.BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.GOLD_INGOT);
        // the other hand of the same click is cancelled without a second attempt
        PlayerInteractEvent off = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hammer, anvil, org.bukkit.block.BlockFace.UP,
            EquipmentSlot.OFF_HAND);
        listener.onInteract(off);
        assertThat(off.isCancelled()).isTrue();
    }

    @Test
    void aHammerClickWithNothingToWeldPassesAndAnUnsneakingOneIsIgnored() {
        player.setSneaking(true);
        ItemStack hammer = new ItemStack(Material.GOLDEN_PICKAXE);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hammer, anvil, org.bukkit.block.BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).isFalse();
        load(Material.IRON_INGOT, Material.IRON_INGOT, 2);
        player.setSneaking(false);
        PlayerInteractEvent plain = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hammer, anvil,
            org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(plain);
        assertThat(plain.isCancelled()).isFalse();
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.IRON_INGOT);
    }

    /** The last chat line a player got, as plain text. */
    static final class PlainTextComponentSerializerHolder {
        static String last(PlayerMock p) {
            var c = p.nextComponentMessage();
            return c == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
        }
    }
}
