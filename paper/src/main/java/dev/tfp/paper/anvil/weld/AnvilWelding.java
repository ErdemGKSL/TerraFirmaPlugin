/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (the hammer branch of interactWithAnvil),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (weld)
 */
package dev.tfp.paper.anvil.weld;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.AnvilTiers;
import dev.tfp.core.anvil.AnvilVisuals;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.anvil.forge.AnvilForgeScreens;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.particle.TfcParticle;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-06: welding on the anvil, for the screen's weld button ({@link AnvilForgeScreens.Welding}) and the hammer click.
 * Edits the record through {@link DeviceService#edit}, tells the player what was missing, and on success plays the anvil sound
 * and the sparks (an explicit action) and redraws the displays. Thread: the one that owns the anvil's block.
 */
public final class AnvilWelding implements AnvilForgeScreens.Welding {
    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final AnvilEffects effects;
    private final Messages messages;
    private final Welder welder;

    public AnvilWelding(DeviceService devices, CustomBlocks blocks, AnvilEffects effects, Messages messages, Welder welder) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.welder = Objects.requireNonNull(welder, "welder");
    }

    private Optional<BlockStateRef> state(Block block) {
        return blocks.stateAt(block).filter(s -> s.id().startsWith(AnvilDevice.METAL_ID + "/") || s.id().startsWith(AnvilDevice.ROCK_ID + "/"));
    }

    /** The anvil's tier, or -1 for a block that is no anvil. */
    private int tier(BlockStateRef state) {
        var t = AnvilTiers.tier(state.id());
        return t.isPresent() ? t.getAsInt() : -1;
    }

    private AnvilEffects.View view(BlockStateRef state, int tier, DeviceRecord<AnvilState> record) {
        var stacks = devices.stacks();
        return new AnvilEffects.View(tier, AnvilVisuals.rotation(state.properties().getOrDefault(AnvilDevice.FACING, "")),
            stacks.decode(record.slot(AnvilSlots.HAMMER)), stacks.decode(record.slot(AnvilSlots.INPUT_MAIN)),
            stacks.decode(record.slot(AnvilSlots.INPUT_SECOND)), stacks.decode(record.slot(AnvilSlots.CATALYST)));
    }

    @SuppressWarnings("unchecked")
    private Optional<DeviceKind<AnvilState>> kind(BlockStateRef state) {
        return devices.kinds().find(state.id()).map(k -> (DeviceKind<AnvilState>) k);
    }

    /** The button is lit when the anvil's two inputs can be welded now; the flux is read from the stored record. */
    @Override
    public boolean canWeld(Block block, ItemStack main, ItemStack second) {
        Optional<BlockStateRef> state = state(block);
        if (state.isEmpty() || tier(state.get()) < 0) {
            return false;
        }
        Optional<DeviceService.Inspection> inspected = devices.inspect(block);
        if (inspected.isEmpty() || inspected.get().record().isEmpty()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        DeviceRecord<AnvilState> record = (DeviceRecord<AnvilState>) inspected.get().record().get();
        ItemStack flux = devices.stacks().decode(record.slot(AnvilSlots.CATALYST));
        return welder.status(tier(state.get()), main, second, flux) == Welder.Status.WELDABLE;
    }

    @Override
    public void weld(Player viewer, Block block) {
        attempt(viewer, block);
    }

    /**
     * TFC {@code AnvilBlockEntity.weld(player)} with its effects.
     *
     * @return what happened; {@link Welder.Result#PASS} when the block is no anvil or nothing was attempted
     */
    public Welder.Result attempt(Player player, Block block) {
        Optional<BlockStateRef> state = state(block);
        if (state.isEmpty() || tier(state.get()) < 0) {
            return Welder.Result.PASS;
        }
        Optional<DeviceKind<AnvilState>> kind = kind(state.get());
        if (kind.isEmpty()) {
            return Welder.Result.PASS;
        }
        int tier = tier(state.get());
        Welder.Outcome[] outcome = new Welder.Outcome[1];
        Optional<DeviceRecord<AnvilState>> after = devices.edit(block, kind.get(), (record, now) -> {
            outcome[0] = welder.weld(tier, record);
            return outcome[0].record();
        });
        if (after.isEmpty() || outcome[0] == null) {
            return Welder.Result.PASS;
        }
        Welder.Outcome o = outcome[0];
        if (o.message() != null) {
            player.sendMessage(Component.text(messages.get(player, o.message())));
        }
        if (o.result() == Welder.Result.SUCCESS) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            TfcParticle.SPARK.play(new Location(block.getWorld(), block.getX() + r.nextDouble(0.2, 0.8),
                block.getY() + r.nextDouble(0.8, 1.0), block.getZ() + r.nextDouble(0.2, 0.8)));
            block.getWorld().playSound(new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5),
                Sound.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.6f, 1.0f);
            effects.show(block, view(state.get(), tier, after.get()));
        }
        return o.result();
    }
}
