/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/livestock/DairyAnimal.java, WoolyAnimal.java,
 * ProducingAnimal.java, OviparousAnimal.java (makeEgg) @ c3b4d9536
 */
package dev.tfp.paper.products;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestock.products.EggRules;
import dev.tfp.core.livestock.products.ProductKind;
import dev.tfp.core.livestock.products.ProductRules;
import dev.tfp.core.livestock.products.ProductSpec;
import dev.tfp.core.livestock.products.ProductState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.ProductPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.fluid.FluidContainerItems;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.feature.EggPdc;
import dev.tfp.paper.tooltip.feature.FeatureData;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Animal products (PAR-FAUNA-06): a bucket on a ready female dairy animal fills with milk, shears on a wool animal give
 * wool, and a ready hen lays an egg on its check (a fertilized hen lays a fertile egg with a hatch day, which the nest box
 * of PAR-FAUNA-07 hatches). The cooldown is TFC's {@code producedTick} (player ticks), kept in the animal's PDC
 * ({@link ProductPdc}); a shorn animal wears the model without wool until the cooldown ends.
 */
public final class ProductsModule implements FeatureModule, Listener {
    static final int MILK_AMOUNT = 1000;
    static final String SHEARS_TAG = "c:tools/shear";

    private ModuleContext ctx;
    private Tags tags;

    @Override
    public String id() {
        return "products";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        animals.behaviours().register(new LayingBehaviour(animals));
        context.listen(this);
    }

    private AnimalService animals() {
        return ((TfpPlugin) ctx.plugin()).animals();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        AnimalService animals = animals();
        Entity target = event.getRightClicked();
        Optional<AnimalRecord> record = animals.record(target);
        ClockSnapshot now = ctx.clock().get();
        if (record.isEmpty() || !now.isSet()) {
            return;
        }
        Optional<ProductSpec> spec = ProductPdc.specOf(record.get().species());
        Optional<LivestockSpecies> species = animals.table().find(record.get().species()).flatMap(Carrier::livestock);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (spec.isEmpty() || species.isEmpty() || held.isEmpty()) {
            return;
        }
        if (spec.get().kind() == ProductKind.MILK) {
            milk(event, player, held, target, record.get(), spec.get(), species.get(), now);
        } else if (spec.get().kind() == ProductKind.SHEAR && tags.is(held, SHEARS_TAG)) {
            shear(event, player, held, target, record.get(), spec.get(), species.get(), now);
        }
    }

    private void milk(PlayerInteractEntityEvent event, Player player, ItemStack held, Entity target, AnimalRecord record,
            ProductSpec spec, LivestockSpecies species, ClockSnapshot now) {
        Optional<FluidContainerKind> kind = tags.itemId(held).flatMap(FluidContainerItems::kindOf);
        if (kind.isEmpty() || !ItemFluids.content(held).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Optional<ProductRules.Harvest> harvest = ProductRules.milk(spec, species, record.state(), ProductPdc.read(target),
                now.playerTicks());
        if (harvest.isEmpty()) {
            hint(player, "products.not_ready");
            return;
        }
        if (ItemFluids.fill(held, new FluidAmount(TfcFluids.MILK, MILK_AMOUNT), kind.get(), f -> true) == 0) {
            return;
        }
        commit(target, record, harvest.get().animal(), harvest.get().product());
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_COW_MILK, 1f, 1f);
    }

    private void shear(PlayerInteractEntityEvent event, Player player, ItemStack held, Entity target, AnimalRecord record,
            ProductSpec spec, LivestockSpecies species, ClockSnapshot now) {
        event.setCancelled(true);
        Optional<ProductRules.Shearing> shearing = ProductRules.shear(spec, species, record.state(),
                ProductPdc.read(target), now.playerTicks());
        if (shearing.isEmpty()) {
            hint(player, "products.not_ready");
            return;
        }
        Optional<ItemStack> wool = ctx.integrations().customItems().create("tfp:wool", shearing.get().wool());
        if (wool.isEmpty()) {
            return;
        }
        target.getWorld().dropItemNaturally(target.getLocation().add(0, 0.5, 0), wool.get());
        if (player.getGameMode() != GameMode.CREATIVE && held.getItemMeta() instanceof Damageable d) {
            d.setDamage(d.getDamage() + 1);
            held.setItemMeta(d);
        }
        commit(target, record, shearing.get().animal(), shearing.get().product());
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
    }

    /** Writes the animal and its product state after a harvest, and refreshes the model (wool gone). */
    private void commit(Entity target, AnimalRecord record, dev.tfp.core.livestock.AnimalState animal, ProductState product) {
        AnimalPdc.write(target.getPersistentDataContainer(), record.withState(animal));
        ProductPdc.write(target, product);
        animals().refreshModel(target);
    }

    private void hint(Player player, String key) {
        String locale = player.locale().toString().toLowerCase(Locale.ROOT);
        player.sendActionBar(net.kyori.adventure.text.Component.text(ctx.messages().get(locale, key)));
    }

    /** Egg laying and the wool growing back, on each staggered check. */
    private final class LayingBehaviour implements SpeciesBehaviour {
        private final AnimalService animals;

        LayingBehaviour(AnimalService animals) {
            this.animals = animals;
        }

        @Override
        public Set<String> species() {
            return dev.tfp.core.animal.CarrierTable.get().all().stream().filter(c -> c.livestock().isPresent())
                    .map(Carrier::id).filter(id -> ProductPdc.specOf(id).isPresent()).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public void onCheck(Check c) {
            Optional<ProductSpec> spec = ProductPdc.specOf(c.record().species());
            Optional<LivestockSpecies> species = animals.table().find(c.record().species()).flatMap(Carrier::livestock);
            ClockSnapshot now = ctx.clock().get();
            if (spec.isEmpty() || species.isEmpty() || !now.isSet()) {
                return;
            }
            if (spec.get().kind() == ProductKind.SHEAR) {
                // the wool grew back: swap the sheared model for the normal one
                if (!ProductPdc.isSheared(c.entity(), c.record(), now.playerTicks())
                        && ProductPdc.read(c.entity()).producedTick() > 0L) {
                    animals.refreshModel(c.entity());
                }
            } else if (spec.get().kind() == ProductKind.EGG) {
                lay(c, spec.get(), species.get(), now);
            }
        }

        private void lay(Check c, ProductSpec spec, LivestockSpecies species, ClockSnapshot now) {
            ProductState product = ProductPdc.read(c.entity());
            if (!ProductRules.isReady(spec, species, c.record().state(), product, now.playerTicks())) {
                return;
            }
            EggRules.Laying laying = EggRules.makeEgg(species, c.record().state(), now.playerTicks(), now.calendarTicks(),
                    ThreadLocalRandom.current().nextBoolean());
            ItemStack egg = new ItemStack(Material.EGG);
            laying.egg().ifPresent(f -> EggPdc.write(egg, new FeatureData.Egg(true, f.hatchDay())));
            c.entity().getWorld().dropItemNaturally(c.entity().getLocation(), egg);
            AnimalPdc.write(c.entity().getPersistentDataContainer(),
                    c.record().withState(laying.hen().withFertilized(false)));
            ProductPdc.write(c.entity(), new ProductState(now.playerTicks()));
        }
    }
}
