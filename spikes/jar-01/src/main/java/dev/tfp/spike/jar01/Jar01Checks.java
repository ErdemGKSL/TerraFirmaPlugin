package dev.tfp.spike.jar01;

import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** The check bodies. Each returns a detail string or throws; expectations use {@link #expect}. */
final class Jar01Checks {
    private Jar01Checks() {}

    @FunctionalInterface
    interface Check {
        String run() throws Exception;
    }

    record Named(String name, Check body) {}

    /** A failed expectation (as opposed to an unexpected exception). */
    static final class Expectation extends Exception {
        private static final long serialVersionUID = 1L;

        Expectation(String message) {
            super(message);
        }
    }

    static void expect(boolean ok, String message) throws Expectation {
        if (!ok) {
            throw new Expectation(message);
        }
    }

    /** Version family rule: "1.21.11" -> "1.21" (major 1 keeps minor); "26.3" -> "26" (major only). */
    static String family(String versionId) {
        String[] parts = versionId.split("\\.");
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[0] + "." + parts[1];
        }
        return parts[0];
    }

    private static boolean is26(String versionId) {
        return "26".equals(family(versionId));
    }

    static List<Named> pure(JavaPlugin plugin) {
        List<Named> list = new ArrayList<>();
        list.add(new Named("serverbuildinfo.ids", () -> {
            ServerBuildInfo info = ServerBuildInfo.buildInfo();
            String id = info.minecraftVersionId();
            expect(id != null && !id.isBlank(), "minecraftVersionId is blank");
            return "id=" + id + " name=" + info.minecraftVersionName() + " brand=" + info.brandName();
        }));
        list.add(new Named("bukkit.getMinecraftVersion", () -> {
            String v = Bukkit.getMinecraftVersion();
            String id = ServerBuildInfo.buildInfo().minecraftVersionId();
            expect(v.equals(id), "Bukkit=" + v + " ServerBuildInfo=" + id);
            return "value=" + v;
        }));
        list.add(new Named("version.family", () -> {
            String id = ServerBuildInfo.buildInfo().minecraftVersionId();
            String fam = family(id);
            expect(fam.equals("1.21") || fam.equals("26"), "unexpected family " + fam + " for " + id);
            return "family=" + fam + " (rule: major 1 -> major.minor, else major)";
        }));
        list.add(new Named("adventure.component", () -> {
            Component styled = Component.text("x", Style.style(NamedTextColor.GOLD, TextDecoration.BOLD));
            TranslatableComponent tr = Component.translatable("block.minecraft.stone");
            expect("x".equals(PlainTextComponentSerializer.plainText().serialize(styled)), "plain text of styled text");
            expect(NamedTextColor.GOLD.equals(styled.style().color()), "color not gold");
            expect(styled.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE, "bold not set");
            expect("block.minecraft.stone".equals(tr.key()), "translatable key " + tr.key());
            return "text+style ok; translatable key=" + tr.key();
        }));
        list.add(new Named("adventure.minimessage", () -> {
            Component mm = MiniMessage.miniMessage().deserialize("<red>hi <bold>x");
            String plain = PlainTextComponentSerializer.plainText().serialize(mm);
            expect("hi x".equals(plain), "plain=" + plain);
            String json = GsonComponentSerializer.gson().serialize(mm);
            expect(json.contains("\"red\"") && json.contains("\"bold\":true"), "json=" + json);
            return "plain=\"" + plain + "\" json has red+bold";
        }));
        list.add(new Named("adventure.gson_plain_roundtrip", () -> {
            Component mm = MiniMessage.miniMessage().deserialize("<red>hi <bold>x");
            String json = GsonComponentSerializer.gson().serialize(mm);
            Component back = GsonComponentSerializer.gson().deserialize(json);
            expect(back.equals(mm), "gson round trip changed the component");
            String plain = PlainTextComponentSerializer.plainText().serialize(back);
            expect("hi x".equals(plain), "plain after round trip=" + plain);
            return "json_len=" + json.length() + " equal=true plain=\"" + plain + "\"";
        }));
        list.add(new Named("adventure.console_send", () -> {
            Bukkit.getConsoleSender().sendMessage(MiniMessage.miniMessage().deserialize("<green>[jar01] console send check"));
            return "sent to console sender";
        }));
        list.add(new Named("registry.biome", () -> probeRegistry(RegistryKey.BIOME, "minecraft:plains")));
        list.add(new Named("registry.block", () -> probeRegistry(RegistryKey.BLOCK, "minecraft:stone")));
        list.add(new Named("registry.item", () -> probeRegistry(RegistryKey.ITEM, "minecraft:stick")));
        list.add(new Named("datacomponent.custom_model_data", () -> {
            ItemStack it = new ItemStack(Material.STICK);
            it.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat(1.5f).addFloat(2f).build());
            CustomModelData cmd = it.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
            expect(cmd != null && cmd.floats().equals(List.of(1.5f, 2f)), "floats=" + (cmd == null ? null : cmd.floats()));
            return "floats=" + cmd.floats();
        }));
        list.add(new Named("datacomponent.lore", () -> {
            ItemStack it = new ItemStack(Material.STICK);
            it.setData(DataComponentTypes.LORE, ItemLore.lore().addLine(Component.text("l1")).addLine(Component.text("l2")).build());
            ItemLore lore = it.getData(DataComponentTypes.LORE);
            expect(lore != null && lore.lines().size() == 2, "lines=" + (lore == null ? null : lore.lines().size()));
            return "lines=" + lore.lines().size() + " first=\"" + PlainTextComponentSerializer.plainText().serialize(lore.lines().get(0)) + "\"";
        }));
        list.add(new Named("datacomponent.custom_name", () -> {
            ItemStack it = new ItemStack(Material.STICK);
            it.setData(DataComponentTypes.CUSTOM_NAME, Component.text("probe-name"));
            Component name = it.getData(DataComponentTypes.CUSTOM_NAME);
            String plain = name == null ? null : PlainTextComponentSerializer.plainText().serialize(name);
            expect("probe-name".equals(plain), "custom name=" + plain);
            return "custom_name=\"" + plain + "\"";
        }));
        list.add(new Named("itemstack.pdc", () -> {
            ItemStack it = new ItemStack(Material.STICK);
            NamespacedKey key = new NamespacedKey(plugin, "probe");
            boolean changed = it.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.STRING, "v1"));
            String read = it.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            expect("v1".equals(read), "read=" + read);
            return "edit changed=" + changed + " read=" + read;
        }));
        list.add(new Named("blockdata.from_string", () -> {
            BlockData bd = Bukkit.createBlockData("minecraft:oak_button[face=floor,facing=north,powered=true]");
            expect(bd instanceof Powerable p && p.isPowered(), "not a powered Powerable: " + bd.getAsString());
            return "getAsString=" + bd.getAsString();
        }));
        list.add(new Named("material.cinnabar", () -> {
            String id = ServerBuildInfo.buildInfo().minecraftVersionId();
            Material m = Material.matchMaterial("minecraft:cinnabar");
            boolean expectFound = is26(id);
            expect(expectFound == (m != null), "matchMaterial=" + (m == null ? "null" : m.name()) + " on " + id);
            return "matchMaterial(minecraft:cinnabar)=" + (m == null ? "null" : m.name()) + " (26.x expected found, 1.21 null)";
        }));
        list.add(new Named("material.oak_button", () -> {
            Material m = Material.matchMaterial("minecraft:oak_button");
            expect(m != null, "oak_button not found");
            return "matchMaterial(minecraft:oak_button)=" + m.name();
        }));
        list.add(new Named("worldgen.classes", () -> {
            ClassLoader cl = Jar01Checks.class.getClassLoader();
            StringBuilder sb = new StringBuilder();
            for (String n : List.of("org.bukkit.generator.ChunkGenerator", "org.bukkit.generator.BiomeProvider",
                    "org.bukkit.generator.LimitedRegion")) {
                Class<?> c = Class.forName(n, false, cl);
                sb.append(c.getSimpleName()).append(" methods=").append(c.getMethods().length).append("; ");
            }
            return sb.toString().trim();
        }));
        list.add(new Named("worldgen.limitedregion.probe", () -> {
            Class<?> lr = Class.forName("org.bukkit.generator.LimitedRegion", false, Jar01Checks.class.getClassLoader());
            StringBuilder sb = new StringBuilder();
            sb.append(probeMethod(lr, "getBlockData", int.class, int.class, int.class));
            sb.append(probeMethod(lr, "getBlockData", org.bukkit.util.Vector.class));
            sb.append(probeMethod(lr, "setBlockData", org.bukkit.util.Vector.class, BlockData.class));
            sb.append(probeMethod(lr, "getBlockState", org.bukkit.util.Vector.class));
            sb.append(probeMethod(lr, "setBlockState", int.class, int.class, int.class, org.bukkit.block.BlockState.class));
            return sb.toString().trim();
        }));
        list.add(new Named("bootstrap.ran", () -> {
            Path p = plugin.getDataFolder().toPath().resolve("bootstrap-ran.txt");
            expect(Files.exists(p), "missing marker " + p);
            return Files.readString(p).trim();
        }));
        list.add(new Named("loader.caffeine.classloader", () -> {
            Class<?> c = Class.forName("com.github.benmanes.caffeine.cache.Caffeine", false, Jar01Checks.class.getClassLoader());
            return "loaded " + c.getName() + " via " + c.getClassLoader().getClass().getName();
        }));
        list.add(new Named("loader.caffeine.cache", Jar01Caffeine::probe));
        return list;
    }

    private static String probeMethod(Class<?> c, String name, Class<?>... params) {
        String sig = name + "(" + java.util.Arrays.stream(params).map(Class::getSimpleName).reduce((a, b) -> a + "," + b).orElse("") + ")";
        try {
            c.getMethod(name, params);
            return sig + "=present; ";
        } catch (NoSuchMethodException e) {
            return sig + "=absent; ";
        }
    }

    private static <T extends Keyed> String probeRegistry(RegistryKey<T> key, String id) throws Expectation {
        Registry<T> reg = RegistryAccess.registryAccess().getRegistry(key);
        T value = reg.get(NamespacedKey.fromString(id));
        expect(value != null, id + " not found");
        int count = 0;
        for (T ignored : reg) {
            count++;
        }
        expect(count > 0, "registry is empty");
        return "lookup " + id + " -> " + value.getKey() + " count=" + count;
    }
}
