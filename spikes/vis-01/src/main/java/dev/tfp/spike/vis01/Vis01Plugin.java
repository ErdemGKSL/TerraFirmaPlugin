package dev.tfp.spike.vis01;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.core.block.AutoStateGroup;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.pack.allocator.BlockStateCandidate;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-VIS-01 helper: logs the CE auto_state pool capacity after every reload and places a test grid.
 * {@code /vis01 grid <x> <y> <z>}: row z = our blocks, row z+3 = the vanilla blocks we repurposed
 * (they must look like their mapping targets, not like our blocks).
 */
public final class Vis01Plugin extends JavaPlugin implements Listener {
    static final List<String> VANILLA_ORIGINALS = List.of(
            "minecraft:andesite", "minecraft:copper_ore", "minecraft:chiseled_stone_bricks",
            "minecraft:brick_slab[type=double]", "minecraft:mud_brick_slab[type=double]",
            "minecraft:stone_brick_slab[type=double]", "minecraft:oak_button[face=floor,facing=north]",
            "minecraft:redstone_ore");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                e -> e.registrar().register("vis01", "M0-VIS-01 spike", new Cmd()));
    }

    @EventHandler
    public void onReload(CraftEngineReloadEvent event) {
        List<String> lines = new ArrayList<>();
        lines.add("reload first=" + event.isFirstReload() + " mc=" + Bukkit.getMinecraftVersion());
        for (AutoStateGroup g : AutoStateGroup.values()) {
            List<BlockStateCandidate> c = g.candidates();
            long used = c.stream().filter(BlockStateCandidate::isUsed).count();
            lines.add("pool " + g.id() + " candidates=" + g.candidateCount() + " used=" + used + " free=" + (c.size() - used));
        }
        Map<String, String> ours = new TreeMap<>();
        CraftEngineBlocks.loadedBlocks().forEach((k, def) -> {
            if (k.namespace().equals("tfpspike")) {
                ours.put(k.toString(), def.defaultState().vanillaBlockState().toString());
            }
        });
        lines.add("tfpspike blocks loaded=" + ours.size());
        ours.forEach((k, v) -> lines.add("  " + k + " -> visual " + v));
        lines.forEach(l -> getLogger().info("[vis01] " + l));
        try {
            Files.write(Path.of("vis01-report.txt"), lines);
        } catch (IOException ignored) {
        }
    }

    final class Cmd implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            if (args.length != 4 || !args[0].equals("grid")) {
                src.getSender().sendMessage("/vis01 grid <x> <y> <z>");
                return;
            }
            World world = Bukkit.getWorlds().getFirst();
            int x = Integer.parseInt(args[1]), y = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
            List<Key> ids = CraftEngineBlocks.loadedBlocks().keySet().stream()
                    .filter(k -> k.namespace().equals("tfpspike"))
                    .sorted(java.util.Comparator.comparing(Key::toString)).toList();
            Location origin = new Location(world, x, y, z);
            Bukkit.getRegionScheduler().run(Vis01Plugin.this, origin, t -> {
                // floor
                for (int dx = -1; dx <= ids.size() * 2 + 1; dx++) {
                    for (int dz = -2; dz <= 6; dz++) {
                        world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.SMOOTH_STONE, false);
                        for (int dy = 0; dy < 4; dy++) {
                            world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                        }
                    }
                }
                int i = 0;
                for (Key id : ids) {
                    BlockDefinition def = CraftEngineBlocks.byId(id);
                    CraftEngineBlocks.place(new Location(world, x + i * 2, y, z), def.defaultState(), false);
                    i++;
                }
                i = 0;
                for (String s : VANILLA_ORIGINALS) {
                    world.getBlockAt(x + i * 2, y, z + 3).setBlockData(Bukkit.createBlockData(s), false);
                    i++;
                }
                src.getSender().sendMessage("placed " + ids.size() + " custom + " + VANILLA_ORIGINALS.size() + " vanilla");
                getLogger().info("[vis01] grid order: " + ids);
            });
        }
    }
}
