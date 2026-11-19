/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java (mobInteract, eatFood),
 * TFCAnimal.java @ c3b4d9536
 */
package dev.tfp.paper.livestock;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Livestock interactions (PAR-FAUNA-05): right-click with the species' food ({@code tfp:<species>_food}) feeds a hungry
 * animal once a day and raises its familiarity ({@link LivestockRules#feed}); mating, pregnancy and birth run on the
 * animals' staggered check ({@link LivestockBehaviour}).
 */
public final class LivestockModule implements FeatureModule, Listener {
    private ModuleContext ctx;
    private Tags tags;

    @Override
    public String id() {
        return "livestock";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        animals.behaviours().register(new LivestockBehaviour(animals, context.scheduler(), context.clock()::get));
        context.listen(this);
    }

    /** The food tag of a species: {@code tfp:cow_food}; both camels share {@code tfp:camel_food}. */
    static String foodTag(Carrier carrier) {
        String name = carrier.name();
        if (name.endsWith("_camel")) {
            name = "camel";
        }
        return "tfp:" + name + "_food";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        AnimalService animals = ((TfpPlugin) ctx.plugin()).animals();
        Entity target = event.getRightClicked();
        Optional<AnimalRecord> record = animals.record(target);
        if (record.isEmpty()) {
            return;
        }
        Optional<Carrier> carrier = animals.table().find(record.get().species());
        Optional<LivestockSpecies> species = carrier.flatMap(Carrier::livestock);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        ClockSnapshot now = ctx.clock().get();
        if (species.isEmpty() || held.isEmpty() || !now.isSet() || !tags.is(held, foodTag(carrier.get()))) {
            return;
        }
        event.setCancelled(true);
        LivestockRules.FeedOutcome outcome = LivestockRules.feed(record.get().state(), species.get(), now.playerTicks());
        if (!outcome.accepted()) {
            hint(player, "livestock.not_hungry");
            return;
        }
        AnimalPdc.write(target.getPersistentDataContainer(), record.get().withState(outcome.state()));
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
        hint(player, "livestock.fed");
    }

    private void hint(Player player, String key) {
        String locale = player.locale().toString().toLowerCase(Locale.ROOT);
        player.sendActionBar(Component.text(ctx.messages().get(locale, key)));
    }
}
