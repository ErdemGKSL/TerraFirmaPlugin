/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-08 on MockBukkit with TFC's generated barrel recipes (brined, pickled, preserved_in_vinegar, limewater, brine)
 * on the real recipe runtime and food PDC: filling by bucket, putting meat in, sealing, the lazy completion, the on-seal and
 * on-unseal traits, the instant and instant fluid recipes, the sealed barrel refusing changes.
 */
class BarrelMockBukkitTest extends MockServerTest {
    static final String OAK = "tfp:wood/barrel/oak";

    @TempDir
    Path dir;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK);
    private final BarrelTestWorld.MemoryStore store = new BarrelTestWorld.MemoryStore();
    private final BarrelTestWorld.TableCodec stacks = new BarrelTestWorld.TableCodec();
    private final AtomicLong clock = new AtomicLong(10_000);
    private final List<String> sounds = new ArrayList<>();
    private final List<Boolean> seals = new ArrayList<>();
    private final List<ItemStack> spawned = new ArrayList<>();
    private BarrelTestWorld data;
    private DeviceService service;
    private Block barrel;
    private PlayerMock player;

    private final BarrelEffects effects = new BarrelEffects() {
        @Override
        public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
            seals.add(sealed);
            assertThat(wanted).isEqualTo(BarrelDevice.state(OAK, sealed));
        }

        @Override
        public void recipe(Block block, String sound) {
            sounds.add(sound);
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }
    };

    @BeforeEach
    void setUp() {
        data = new BarrelTestWorld(dir).load(clock::get);
        World world = server.addSimpleWorld("barrel");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, data.items,
            Logger.getLogger("test"));
        BarrelRecipeBook book = new BarrelRecipeBook(data.recipes, data.tanks(), BarrelRules.DEFAULT_CAPACITY);
        service.register(BarrelDevice.kind(new BarrelDevice.Services(book, s -> true,
            s -> s.getType() == Material.STICK, effects), stacks));
        barrel = world.getBlockAt(0, 64, 0);
        blocks.place(barrel, BarrelDevice.state(OAK, false), true);
        store.data.put(FakeCustomBlocks.at(barrel), new DataMap());
        player = server.addPlayer();
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<BarrelData> record() {
        DeviceKind<BarrelData> kind = (DeviceKind<BarrelData>) service.kinds().find(OAK).orElseThrow();
        return kind.codec().decode(store.read(barrel).orElseThrow());
    }

    private BarrelContents contents() {
        return BarrelDevice.contents(record(), stacks);
    }

    private void hold(ItemStack stack) {
        player.getInventory().setItem(EquipmentSlot.HAND, stack);
    }

    private ItemStack held() {
        return player.getInventory().getItem(EquipmentSlot.HAND);
    }

    private boolean click() {
        return service.use(barrel, player, EquipmentSlot.HAND, held());
    }

    private void toggleSeal() {
        hold(null);
        player.setSneaking(true);
        assertThat(click()).isTrue();
        player.setSneaking(false);
    }

    private ItemStack woodenBucket(String fluid, int mb) {
        ItemStack s = data.items.create("tfp:wooden_bucket", 1).orElseThrow();
        if (mb > 0) {
            ItemFluids.fill(s, new FluidAmount(fluid, mb), 1000, true);
        }
        return s;
    }

    private ItemStack jug(String fluid, int mb) {
        ItemStack s = data.items.create("tfp:ceramic/jug", 1).orElseThrow();
        ItemFluids.fill(s, new FluidAmount(fluid, mb), 100, true);
        return s;
    }

    private ItemStack meat(int count, FoodTrait... traits) {
        ItemStack s = new ItemStack(Material.BEEF, count);
        FoodPdc.write(s, new FoodState(clock.get(), List.of(traits)));
        return s;
    }

    private static List<FoodTrait> traits(ItemStack stack) {
        return FoodPdc.read(stack).orElseThrow().traits();
    }

    @Test
    void briningMeatInASealedBarrelAddsTheBrinedTrait() {
        hold(woodenBucket("tfp:brine", 1000));
        assertThat(click()).as("the bucket empties into the barrel").isTrue();
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("tfp:brine", 1000));
        assertThat(ItemFluids.content(held()).isEmpty()).as("the bucket is empty").isTrue();

        hold(meat(4));
        assertThat(click()).as("meat goes into the item slot").isTrue();
        assertThat(contents().main().getAmount()).isEqualTo(4);

        toggleSeal();
        assertThat(seals).containsExactly(true);
        assertThat(record().state().sealed()).isTrue();
        assertThat(record().state().recipeTick()).as("brined starts at the seal").isEqualTo(10_000);

        clock.addAndGet(3600);
        service.catchUp(barrel);
        assertThat(traits(contents().main())).as("not before duration + 1").doesNotContain(FoodTrait.BRINED);

        clock.addAndGet(1);
        service.catchUp(barrel);
        BarrelContents done = contents();
        assertThat(done.main().getType()).isEqualTo(Material.BEEF);
        assertThat(done.main().getAmount()).isEqualTo(4);
        assertThat(traits(done.main())).containsExactly(FoodTrait.BRINED);
        assertThat(done.fluid()).as("125 mB per item").isEqualTo(new FluidAmount("tfp:brine", 500));
        assertThat(record().state().recipeTick()).as("no next recipe in brine: the tick is kept")
            .isEqualTo(10_000);

        toggleSeal();
        assertThat(record().state().sealed()).isFalse();
        assertThat(record().state().recipeTick()).isEqualTo(BarrelRules.NO_TICK);
        hold(null);
        assertThat(click()).as("an empty hand takes the meat").isTrue();
        ItemStack out = null;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == Material.BEEF) {
                out = s;
            }
        }
        assertThat(out).isNotNull();
        assertThat(traits(out)).containsExactly(FoodTrait.BRINED);
    }

    @Test
    void aLongUnloadedBarrelRunsTheWholeChainWithTheVinegarOnSeal() {
        // brined meat in vinegar: pickled after the duration, then preserved_in_vinegar's on_seal adds the vinegar trait
        hold(woodenBucket("tfp:vinegar", 1000));
        click();
        hold(meat(2, FoodTrait.BRINED));
        click();
        toggleSeal();
        clock.addAndGet(100_000);
        service.catchUp(barrel);
        BarrelContents c = contents();
        assertThat(traits(c.main())).containsExactlyInAnyOrder(FoodTrait.BRINED, FoodTrait.PICKLED, FoodTrait.VINEGAR);
        assertThat(c.fluid()).isEqualTo(new FluidAmount("tfp:vinegar", 750));

        toggleSeal();
        assertThat(traits(contents().main())).as("on_unseal removes the vinegar trait")
            .containsExactlyInAnyOrder(FoodTrait.BRINED, FoodTrait.PICKLED);
    }

    @Test
    void sealingPickledMeatInVinegarAppliesTheOnSealTrait() {
        hold(woodenBucket("tfp:vinegar", 1000));
        click();
        hold(meat(3, FoodTrait.BRINED, FoodTrait.PICKLED));
        click();
        toggleSeal();
        assertThat(traits(contents().main())).contains(FoodTrait.VINEGAR);
        clock.addAndGet(1_000_000);
        service.catchUp(barrel);
        assertThat(traits(contents().main())).as("infinite: nothing completes").contains(FoodTrait.VINEGAR);
        assertThat(contents().fluid()).isEqualTo(new FluidAmount("tfp:vinegar", 1000));
    }

    @Test
    void aSealedBarrelTakesNoFluidAndGivesNoItem() {
        hold(meat(1));
        click();
        toggleSeal();
        hold(woodenBucket("tfp:brine", 1000));
        assertThat(click()).as("consumed, nothing moves (TFC opens the GUI)").isTrue();
        assertThat(record().state().fluid().isEmpty()).isTrue();
        assertThat(ItemFluids.content(held())).isEqualTo(new FluidAmount("tfp:brine", 1000));
        hold(null);
        click();
        assertThat(contents().main().getAmount()).isEqualTo(1);
    }

    @Test
    void anInstantRecipeConvertsTheTankWhenTheItemsCoverIt() {
        hold(new ItemStack(Material.WATER_BUCKET));
        click();
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(held().getType()).as("the vanilla bucket comes back empty").isEqualTo(Material.BUCKET);

        hold(data.items.create("tfp:powder/lime", 1).orElseThrow());
        click();
        assertThat(record().state().fluid()).as("1 lime for 1000 mB: fewer items than fluid multiples, no recipe")
            .isEqualTo(new FluidAmount("minecraft:water", 1000));
        hold(data.items.create("tfp:powder/lime", 1).orElseThrow());
        click();
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("tfp:limewater", 1000));
        assertThat(contents().mainEmpty()).as("both lime used").isTrue();
        assertThat(sounds).containsExactly(BarrelRecipeView.DEFAULT_SOUND);
    }

    @Test
    void creativeKeepsASingleHeldContainerAndStillShrinksAStack() {
        // TFC FluidHelpers.with: creative skips setItemInHand; a stack > 1 is shrunk in place before that check
        player.setGameMode(GameMode.CREATIVE);
        hold(new ItemStack(Material.WATER_BUCKET));
        assertThat(click()).isTrue();
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(held().getType()).as("creative: the held water bucket is unchanged").isEqualTo(Material.WATER_BUCKET);
        assertThat(held().getAmount()).isEqualTo(1);
        assertThat(given()).as("creative with a single container: TFC gives nothing").isEmpty();

        ItemStack two = woodenBucket("tfp:brine", 0);
        two.setAmount(2);
        hold(two);
        // the barrel holds water; two empty wooden buckets: one is filled and given, the held stack loses one
        assertThat(click()).isTrue();
        assertThat(held().getAmount()).as("creative still shrinks a larger stack, as TFC").isEqualTo(1);
        List<ItemStack> given = given();
        assertThat(given).as("the filled container is given to the player").hasSize(1);
        assertThat(given.get(0).getAmount()).isEqualTo(1);
        assertThat(data.items.idOf(given.get(0))).contains("tfp:wooden_bucket");
        assertThat(ItemFluids.content(given.get(0))).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(record().state().fluid().isEmpty()).as("the barrel tank is drained").isTrue();
    }

    /** Every non-empty inventory stack apart from the main hand. */
    private List<ItemStack> given() {
        List<ItemStack> out = new ArrayList<>();
        ItemStack[] all = player.getInventory().getContents();
        int heldSlot = player.getInventory().getHeldItemSlot();
        for (int i = 0; i < all.length; i++) {
            if (i != heldSlot && all[i] != null && !all[i].getType().isAir()) {
                out.add(all[i]);
            }
        }
        return out;
    }

    @Test
    void vinegarAddedToSaltWaterMakesBrine() {
        hold(woodenBucket("tfp:salt_water", 900));
        click();
        hold(jug("tfp:vinegar", 100));
        assertThat(click()).as("the jug cannot pour vinegar into salt water, so it goes into the item slot").isTrue();
        BarrelContents c = contents();
        assertThat(c.fluid()).as("900 salt water + 100 vinegar").isEqualTo(new FluidAmount("tfp:brine", 1000));
        assertThat(ItemFluids.content(c.main()).isEmpty()).as("the jug is drained").isTrue();
        assertThat(data.items.idOf(c.main())).contains("tfp:ceramic/jug");
    }

    @Test
    void anEmptyContainerFillsFromTheBarrelAndSealingDropsNothingElse() {
        hold(woodenBucket("tfp:brine", 1000));
        click();
        ItemStack emptyJug = data.items.create("tfp:ceramic/jug", 1).orElseThrow();
        hold(emptyJug);
        assertThat(click()).isTrue();
        assertThat(ItemFluids.content(held())).isEqualTo(new FluidAmount("tfp:brine", 100));
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("tfp:brine", 900));
        toggleSeal();
        assertThat(spawned).as("the container slots are empty").isEmpty();
    }
}
