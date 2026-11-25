/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/mixin/TadpoleMixin.java @ c3b4d9536 (ageUp)
 */
package dev.tfp.paper.worldrules;

import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.predator.PredatorSpecies;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tadpole;

/**
 * PAR-DATA-14: TFC's hooks into vanilla world rules that no feature owns, one listener per rule:
 * <ul>
 *   <li>{@link HardenedRockListener}: lava + water forms hardened gabbro, rhyolite or basalt;</li>
 *   <li>{@link SnowLayerListener}: breaking a snow layer takes one layer; TFC leaves hold a single layer;</li>
 *   <li>{@link EntityRulesListener}: no tadpole feeding, tadpoles grow into TFC's frog, no predators in boats;</li>
 *   <li>{@link BambooListener}: vanilla bamboo grows with {@code plantLongGrowthChance};</li>
 *   <li>{@link FluidSourceListener}: waterlogged aqueducts and sluice-fed water make no new sources.</li>
 * </ul>
 * The other hooks of the item live with their owners: drunk chat (PAR-SURV-08), TFC mob armour (PAR-DATA-18), snow on
 * leaves from the weather (M2-WTH-02); vines and the motion-blocking heightmap need nothing because TFC leaves are full
 * server-side cubes here. Piglin currency is PAR-DATA-22.
 */
public final class WorldRulesModule implements FeatureModule {
    public static final String ID = "world_rules";
    static final String FROG = "tfc:frog";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        Scheduler scheduler = ctx.scheduler();
        CustomBlocks blocks = ctx.integrations().customBlocks();
        Optional<Tags> tags = ctx.services().get(Tags.class);
        Predicate<Block> custom = b -> blocks.stateAt(b).isPresent();
        Predicate<Block> tfcLeaves = b -> blocks.stateAt(b)
                .map(s -> tags.map(t -> t.is(s, "minecraft:leaves")).orElse(s.id().startsWith("tfp:wood/leaves/")))
                .orElse(false);
        AnimalService animals = ((TfpPlugin) ctx.plugin()).animals();
        Predicate<Entity> predator = e -> animals.record(e).map(r -> PredatorSpecies.ids().contains(r.species()))
                .orElse(false);

        ctx.listen(new HardenedRockListener(id -> ctx.integrations().blockVisuals().blockData(BlockStateRef.of(id))));
        ctx.listen(new SnowLayerListener(scheduler, custom, tfcLeaves));
        ctx.listen(new EntityRulesListener(predator, tadpole -> toFrog(animals, scheduler, tadpole)));
        ctx.listen(new BambooListener(ThreadLocalRandom::current));
        ctx.listen(new FluidSourceListener(scheduler, FluidSourceListener::sample, blocks::stateAt));
    }

    /**
     * {@code TadpoleMixin.ageUp}: TFC's frog takes the tadpole's place and rotation, with its no-AI flag, custom name and
     * persistence; the grow-up sound plays and the tadpole is removed. Tadpole thread.
     */
    static boolean toFrog(AnimalService animals, Scheduler scheduler, Tadpole tadpole) {
        Optional<Carrier> frog = animals.table().find(FROG);
        if (frog.isEmpty()) {
            return false;
        }
        Location at = tadpole.getLocation();
        CompletableFuture<Entity> spawned = animals.spawn(at, frog.get(), AnimalSpawnRoll.AgeHint.ADULT, null);
        if (spawned.isCompletedExceptionally()) {
            return false; // the calendar is not loaded: vanilla's frog
        }
        boolean noAi = !tadpole.hasAI();
        Component name = tadpole.customName();
        boolean nameVisible = tadpole.isCustomNameVisible();
        tadpole.getWorld().playSound(at, Sound.ENTITY_TADPOLE_GROW_UP, SoundCategory.NEUTRAL, 0.15f, 1.0f);
        tadpole.remove();
        spawned.thenAccept(e -> scheduler.entity(e, () -> {
            if (e instanceof Mob mob) {
                mob.setAI(!noAi);
                mob.setPersistent(true);
                mob.setRemoveWhenFarAway(false);
                if (name != null) {
                    mob.customName(name);
                    mob.setCustomNameVisible(nameVisible);
                }
            }
        }, () -> {
        }));
        return true;
    }
}
