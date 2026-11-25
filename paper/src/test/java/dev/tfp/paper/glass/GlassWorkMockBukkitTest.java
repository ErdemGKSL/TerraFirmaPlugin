/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.glass;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.glass.GlassOperation;
import dev.tfp.core.glass.GlassRecipes;
import dev.tfp.core.glass.GlassState;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-PROC-16: the blowpipe's operations, the batch on a blowpipe and the powder bowl side, on MockBukkit. */
class GlassWorkMockBukkitTest extends MockServerTest {
    /** Stand-in materials for the CraftEngine items. */
    private static final Map<Material, String> IDS = Map.of(
        Material.BLAZE_ROD, "tfp:blowpipe_with_glass",
        Material.STICK, "tfp:blowpipe",
        Material.BONE, "tfp:ceramic_blowpipe_with_glass",
        Material.WHITE_WOOL, "tfp:wool_cloth",
        Material.SHEARS, "tfp:jacks",
        Material.PAPER, "tfp:paddle",
        Material.IRON_SWORD, "tfp:gem_saw",
        Material.SAND, "tfp:silica_glass_batch",
        Material.GLOWSTONE_DUST, "tfp:powder/soda_ash",
        Material.GLASS_BOTTLE, "tfp:silica_glass_bottle");

    private PlayerMock player;
    private GlassWork work;
    private final AtomicReference<Double> temperature = new AtomicReference<>(1000.0);
    private final AtomicReference<Double> random = new AtomicReference<>(0.5);

    @BeforeEach
    void setUp() {
        work = new GlassWork(new GlassWork.Services(
            s -> s == null || s.isEmpty() ? "" : IDS.getOrDefault(s.getType(), s.getType().getKey().toString()),
            s -> temperature.get(),
            id -> IDS.entrySet().stream().filter(e -> e.getValue().equals(id)).findFirst()
                .map(e -> new ItemStack(e.getKey())).orElse(null),
            out -> {
                String id = out.item().replaceFirst("^tfc:", "tfp:");
                return IDS.entrySet().stream().filter(e -> e.getValue().equals(id)).findFirst()
                    .map(e -> new ItemStack(e.getKey(), out.count())).orElse(new ItemStack(Material.DIRT, out.count()));
            },
            GlassRecipes.ALL, () -> random.get(), Messages.fromJar()));
        server.getPluginManager().registerEvents(new GlassListener(work), plugin);
        player = server.addPlayer("Bot");
    }

    private ItemStack pipe(Material material, GlassState state) {
        ItemStack stack = new ItemStack(material);
        GlassPdc.write(stack, state);
        return stack;
    }

    private static GlassState silica(GlassOperation... steps) {
        return new GlassState(List.of(steps), "tfc:silica_glass_batch");
    }

    private void rightClick(EquipmentSlot hand) {
        ItemStack item = player.getInventory().getItem(hand);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, item, null, BlockFace.SELF, hand);
        server.getPluginManager().callEvent(e);
    }

    private void use(ItemStack otherHand) {
        player.getInventory().setItemInOffHand(otherHand);
        player.setCooldown(player.getInventory().getItemInMainHand(), 0);
        rightClick(EquipmentSlot.HAND);
    }

    private GlassState state() {
        return GlassPdc.read(player.getInventory().getItemInMainHand()).orElseThrow();
    }

    @Test
    void thePdcRoundTripsTheBatchAndTheSteps() {
        ItemStack stack = pipe(Material.BLAZE_ROD, silica(GlassOperation.BLOW, GlassOperation.PINCH));
        assertThat(GlassPdc.read(stack)).hasValue(silica(GlassOperation.BLOW, GlassOperation.PINCH));
        GlassPdc.write(stack, GlassState.EMPTY);
        assertThat(GlassPdc.read(stack)).isEmpty();
        assertThat(GlassPdc.read(new ItemStack(Material.STICK))).isEmpty();
        assertThat(GlassPdc.read(null)).isEmpty();
    }

    @Test
    void anEmptyHandBlowsAndAToolGivesItsOperation() {
        player.getInventory().setItemInMainHand(pipe(Material.BLAZE_ROD, silica()));
        use(null);
        assertThat(state().steps()).containsExactly(GlassOperation.BLOW);
        use(new ItemStack(Material.SHEARS));
        use(new ItemStack(Material.WHITE_WOOL));
        use(new ItemStack(Material.PAPER));
        assertThat(state().steps()).containsExactly(GlassOperation.BLOW, GlassOperation.PINCH, GlassOperation.ROLL, GlassOperation.FLATTEN);
    }

    @Test
    void anUnrelatedItemInTheOtherHandDoesNothing() {
        player.getInventory().setItemInMainHand(pipe(Material.BLAZE_ROD, silica()));
        use(new ItemStack(Material.DIRT));
        assertThat(state().steps()).isEmpty();
    }

    @Test
    void aColdBlowpipeRefusesAndTellsThePlayer() {
        temperature.set(100.0); // below faint red
        player.getInventory().setItemInMainHand(pipe(Material.BLAZE_ROD, silica()));
        use(null);
        assertThat(state().steps()).isEmpty();
        assertThat(player.nextComponentMessage()).isNull(); // the action bar is not a chat message
        // the saw needs no heat
        use(new ItemStack(Material.IRON_SWORD));
        assertThat(state().steps()).containsExactly(GlassOperation.SAW);
    }

    @Test
    void theLampGlassSequenceGivesTheResultAndEmptiesTheBlowpipe() {
        // lamp glass: blow, pinch, flatten, blow, saw
        player.getInventory().setItemInMainHand(pipe(Material.BLAZE_ROD, silica()));
        use(null);
        use(new ItemStack(Material.SHEARS));
        use(new ItemStack(Material.PAPER));
        use(null);
        assertThat(state().steps()).hasSize(4);
        use(new ItemStack(Material.IRON_SWORD));
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.STICK); // tfp:blowpipe
        assertThat(GlassPdc.read(player.getInventory().getItemInMainHand())).isEmpty();
        assertThat(player.getInventory().contains(Material.DIRT)).isTrue(); // tfc:lamp_glass has no stand-in: dirt
    }

    @Test
    void theClayBlowpipeBreaksWithItsChance() {
        random.set(0.05);
        player.getInventory().setItemInMainHand(pipe(Material.BONE, silica(GlassOperation.BLOW, GlassOperation.PINCH)));
        use(new ItemStack(Material.IRON_SWORD));
        // the broken blowpipe leaves the hand empty and the result takes the first free slot, the held one (TFC giveItemToPlayer)
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.GLASS_BOTTLE);
        assertThat(player.getInventory().contains(Material.BONE)).isFalse();
        assertThat(player.getInventory().contains(Material.STICK)).isFalse();
    }

    @Test
    void theOffHandBlowpipeWorksToo() {
        player.getInventory().setItemInOffHand(pipe(Material.BLAZE_ROD, silica()));
        player.getInventory().setItemInMainHand(new ItemStack(Material.SHEARS));
        rightClick(EquipmentSlot.OFF_HAND);
        assertThat(GlassPdc.read(player.getInventory().getItemInOffHand()).orElseThrow().steps()).containsExactly(GlassOperation.PINCH);
    }

    @Test
    void theOperationStartsACooldown() {
        player.getInventory().setItemInMainHand(pipe(Material.BLAZE_ROD, silica()));
        use(null);
        assertThat(player.hasCooldown(player.getInventory().getItemInMainHand())).isTrue();
        rightClick(EquipmentSlot.HAND); // still cooling down: nothing changes
        assertThat(state().steps()).hasSize(1);
    }

    @Test
    void aBatchPutOnASingleBlowpipeLoadsIt() {
        org.bukkit.inventory.Inventory chest = server.createInventory(null, 9);
        chest.setItem(0, new ItemStack(Material.STICK));
        player.openInventory(chest);
        player.setItemOnCursor(new ItemStack(Material.SAND, 3));
        InventoryClickEvent e = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0,
            ClickType.RIGHT, InventoryAction.SWAP_WITH_CURSOR);
        server.getPluginManager().callEvent(e);
        ItemStack loaded = chest.getItem(0);
        assertThat(loaded.getType()).isEqualTo(Material.BLAZE_ROD);
        assertThat(GlassPdc.read(loaded)).hasValue(silica());
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(2);
    }

    @Test
    void aStackOfBlowpipesOrANonBatchIsLeftAlone() {
        assertThat(work.loadBatch(new ItemStack(Material.STICK, 2), new ItemStack(Material.SAND), true)).isEmpty();
        assertThat(work.loadBatch(new ItemStack(Material.STICK), new ItemStack(Material.DIRT), true)).isEmpty();
        assertThat(work.loadBatch(new ItemStack(Material.STICK), new ItemStack(Material.SAND), false)).isEmpty();
        assertThat(work.loadBatch(new ItemStack(Material.BLAZE_ROD), new ItemStack(Material.SAND), true)).isEmpty();
    }

    @Test
    void thePowderBowlAddsAPowderStep() {
        GlassBowl bowl = new GlassBowl();
        ItemStack stack = pipe(Material.BLAZE_ROD, silica(GlassOperation.BLOW));
        assertThat(bowl.working(stack)).isTrue();
        assertThat(bowl.working(new ItemStack(Material.STICK))).isFalse();
        assertThat(bowl.apply(stack, "tfc:soda_ash")).isTrue();
        assertThat(GlassPdc.read(stack).orElseThrow().steps()).containsExactly(GlassOperation.BLOW, GlassOperation.SODA_ASH);
        assertThat(bowl.apply(new ItemStack(Material.STICK), "tfc:soda_ash")).isFalse();
        assertThat(bowl.apply(stack, "tfc:nothing")).isFalse();
    }
}
