/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DeviceBlock.java @ c3b4d9536 (device block + entity),
 *       src/main/java/net/dries007/tfc/common/blocks/EntityBlockExtension.java @ c3b4d9536 (newBlockEntity)
 */
package dev.tfp.integration.craftengine.device;

import dev.tfp.integration.craftengine.CraftEngineBehaviours;
import dev.tfp.paper.device.DeviceBridge;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The CraftEngine block behaviour {@code tfp:device} (PAR-INFRA-02): gives the block a {@link DeviceController}
 * (TFC {@code EntityBlockExtension.newBlockEntity}) and forwards its callbacks, in Bukkit terms, to the device router
 * ({@link DeviceBridge#router()}, the Paper {@code DeviceService}). Feature code never sees this class; a device kind
 * is chosen by the block id on the Paper side.
 *
 * <ul>
 *   <li>{@link #useOnBlock}: a right click; consumed → {@code SUCCESS_AND_CANCEL} (the Bukkit event is cancelled, no
 *       vanilla item use follows), otherwise {@code PASS}.</li>
 *   <li>{@link #neighborChanged}: a neighbour changed (NMS callback, region thread).</li>
 *   <li>Removal: {@link DeviceController#onRemove}.</li>
 *   <li>Left click and placement come from Bukkit events in the Paper module.</li>
 * </ul>
 * No random tick, no ticker (lazy catch-up). Holds no state: CraftEngine rebuilds behaviours on every reload.
 */
public final class DeviceBlockBehaviour extends BukkitBlockBehavior implements EntityBlock {
    /** {@code type: tfp:device} in block YAML. */
    public static final String PATH = "device";
    /** No options; runs on CraftEngine's config pool, so it touches no world. */
    public static final BlockBehaviorFactory<DeviceBlockBehaviour> FACTORY = (block, section) -> new DeviceBlockBehaviour(block);

    private static final Logger LOG = Logger.getLogger("TerraFirmaPlugin");

    DeviceBlockBehaviour(BlockDefinition block) {
        super(block);
    }

    /**
     * Adds {@code tfp:device} to {@code behaviours} and installs the CraftEngine device store. Call while the adapter
     * is built, before our {@code onLoad} registers the behaviours with CraftEngine.
     */
    public static void register(CraftEngineBehaviours behaviours) {
        behaviours.block(PATH, FACTORY);
        DeviceBridge.installStore(new CraftEngineDeviceStore());
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new DeviceController(blockEntity);
    }

    @Override
    public void initControllerId(int id) {
        // the store finds our controller by type, so the index is not needed
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        if (context.getPlayer() == null || !(context.getPlayer().platformPlayer() instanceof Player player)
                || !(context.getLevel().platformWorld() instanceof World world)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        EquipmentSlot hand = context.getHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack item = player.getInventory().getItem(hand);
        boolean consumed = DeviceBridge.router().use(world.getBlockAt(pos.x(), pos.y(), pos.z()), player, hand, item);
        return consumed ? InteractionResult.SUCCESS_AND_CANCEL : InteractionResult.PASS;
    }

    /** args (all supported versions): 0 state, 1 Level, 2 pos, ... */
    @Override
    public void neighborChanged(Object thisBlock, Object[] args) {
        try {
            World world = worldOf(args[1]);
            if (world == null) {
                return;
            }
            BlockPos pos = LocationUtils.fromBlockPos(args[2]);
            DeviceBridge.router().neighbourChanged(world.getBlockAt(pos.x(), pos.y(), pos.z()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "tfp:device neighbour update failed", e);
        }
    }

    /** The Bukkit world of an NMS level (a handful of worlds: a linear scan, no NMS access). */
    private static World worldOf(Object level) {
        for (World w : Bukkit.getWorlds()) {
            if (BukkitAdaptor.adapt(w).minecraftWorld() == level) {
                return w;
            }
        }
        return null;
    }
}
