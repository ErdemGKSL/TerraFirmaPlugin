/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java, TFCRabbit.java and TFCArmadillo.java @ c3b4d9536
 */
package dev.tfp.paper.wildraid;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.berry.BerryBushDevice;
import dev.tfp.paper.berry.PaperBerryEffects;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FAUNA-24: what PAR-FAUNA-09 left undone for the wild animals. The fox takes berries from fruiting bushes
 * ({@link FoxBerryGoal}), the rabbit raids gardens ({@link RabbitRaidGoal}) and the armadillo drops scutes on the calendar
 * ({@link ArmadilloScuteBehaviour}); the vanilla goals those replace were removed by the prey module, and the vanilla scute drop
 * is cancelled here. Must be listed after the prey, berry, device, tags and animal modules.
 */
public final class WildRaidModule implements FeatureModule, Listener {
    public static final String ID = "wild_raid";
    static final String FOX = "tfc:fox";
    static final String RABBIT = "tfc:rabbit";
    static final String FOX_TAG = "tfc:fox_raidable";
    static final String RABBIT_TAG = "tfc:rabbit_raidable";
    static final String FARMLAND_TAG = "tfc:farmlands";

    @Override
    public String id() {
        return ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enable(ModuleContext context) {
        var animals = ((TfpPlugin) context.plugin()).animals();
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        PaperBerryEffects effects = new PaperBerryEffects(context.scheduler(), blocks, context.integrations().customItems(),
                () -> context.integrations().blockVisuals(), tags, () -> devices,
                () -> (DeviceKind<BerryState>) devices.kinds().find(BerryBushDevice.blockId(
                        dev.tfp.core.berry.BushSpecies.SNOWBERRY)).orElse(null),
                () -> context.services().get(CatchUpService.class));
        BerryPicker picker = new BerryPicker(devices, blocks, new BerryBushDevice.Services(effects,
                () -> context.clock().get(), BerryRules.Settings.DEFAULT));
        RaidSites sites = new WorldSites(tags, blocks, picker);
        RaidSites.Walker walker = new RaidSites.Walker() {
            @Override
            public boolean moveTo(Mob mob, org.bukkit.Location to, double speed) {
                return mob.getPathfinder().moveTo(to, speed);
            }

            @Override
            public boolean hasPath(Mob mob) {
                return mob.getPathfinder().hasPath();
            }
        };
        animals.behaviours().register(goalBehaviour(FOX, 10, mob -> new FoxBerryGoal(mob, sites, walker, ThreadLocalRandom.current())));
        animals.behaviours().register(goalBehaviour(RABBIT, 5, mob -> new RabbitRaidGoal(mob, sites, walker, ThreadLocalRandom.current())));
        animals.behaviours().register(new ArmadilloScuteBehaviour(() -> context.clock().get(), ThreadLocalRandom.current()));
        context.listen(this);
        context.logger().info("Wild raids: fox berries, rabbit garden raids, armadillo scutes on the calendar");
    }

    /** One behaviour that keeps the vanilla goals and adds one goal of ours. */
    static SpeciesBehaviour goalBehaviour(String species, int priority,
            java.util.function.Function<Mob, com.destroystokyo.paper.entity.ai.Goal<Mob>> goal) {
        return new SpeciesBehaviour() {
            @Override
            public Set<String> species() {
                return Set.of(species);
            }

            @Override
            public boolean keepVanillaGoals() {
                return true;
            }

            @Override
            public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
                return List.of(new GoalSpec(priority, goal.apply(mob)));
            }
        };
    }

    /** TFC's armadillo scutes replace the vanilla timer: the vanilla drop of one of our armadillos is cancelled. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        if (event.getEntityType() == EntityType.ARMADILLO
                && event.getItemDrop().getItemStack().getType() == Material.ARMADILLO_SCUTE
                && AnimalPdc.read(event.getEntity().getPersistentDataContainer())
                        .filter(r -> r.species().equals(ArmadilloScuteBehaviour.SPECIES)).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** The tags and the berry module behind {@link RaidSites}. */
    private record WorldSites(Tags tags, CustomBlocks blocks, BerryPicker picker) implements RaidSites {
        @Override
        public boolean farmland(Block block) {
            return tags.is(block, FARMLAND_TAG);
        }

        @Override
        public boolean rabbitRaidable(Block block) {
            return tags.is(block, RABBIT_TAG);
        }

        /**
         * A fruiting stationary bush: the berry module's own bush ids (the generated {@code tfc:fox_raidable} tag names the plant
         * ids of TFC's block registry, not our {@code tfp:berry_bush/<species>}), or any block of the tag whose state is fruiting.
         */
        @Override
        public boolean foxFruiting(Block block) {
            Optional<BlockStateRef> state = blocks.stateAt(block);
            if (state.isEmpty()) {
                return false;
            }
            return BerryPicker.fruiting(state.get())
                    || tags.is(state.get(), FOX_TAG) && "fruiting".equals(state.get().properties().get("lifecycle"));
        }

        @Override
        public boolean mayGrief(Mob mob) {
            return Boolean.TRUE.equals(mob.getWorld().getGameRuleValue(GameRule.MOB_GRIEFING));
        }

        @Override
        public Optional<ItemStack> pick(Block bush) {
            return picker.pick(bush);
        }

        @Override
        public void destroyCrop(Block crop) {
            if (blocks.stateAt(crop).isPresent()) {
                blocks.remove(crop);
            } else {
                crop.breakNaturally();
            }
        }
    }
}
