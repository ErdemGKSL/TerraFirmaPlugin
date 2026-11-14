/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item.info;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.health.IncomingDamage;
import dev.tfp.core.item.info.StaticLore.Line;
import dev.tfp.core.item.info.StaticLore.LoreColor;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PAR-INFRA-46: the static tooltip lines per item id against TFC at c3b4d9536. Golden rows were exported from
 * {@code /opt/tfc} by a scratch script (the {@code Rock} enum's display categories; {@code 1 - e^(-0.01 r)} of every
 * {@code item_damage_resistance} file in float arithmetic, formatted {@code %.0f%%}; the {@code fuel} files of
 * charcoal, peat, oak logs and coal).
 */
class ItemInfoGoldenTest {
    private static RegistrySnapshot snapshot;

    /** The weapon tags as the item tag data resolves them for a few TFC weapons (vanilla tag members included). */
    private static final Map<String, Set<String>> TAGS = Map.of(
            IncomingDamage.ITEM_PIERCING, Set.of("tfp:metal/knife/copper", "tfp:metal/javelin/copper",
                    "minecraft:iron_pickaxe"),
            IncomingDamage.ITEM_SLASHING, Set.of("tfp:metal/sword/copper", "tfp:metal/axe/copper"),
            IncomingDamage.ITEM_CRUSHING, Set.of("tfp:metal/mace/copper", "tfp:metal/hammer/copper",
                    "tfp:metal/knife/copper"));

    private static final TagLookup WEAPON_TAGS = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return type == TagType.ITEM && TAGS.getOrDefault(tag, Set.of()).contains(id);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return TAGS.containsKey(tag);
        }
    };

    @BeforeAll
    static void load() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
    }

    @Test
    void weaponLinesFollowTfcTagOrder() {
        assertThat(DamageInfo.dealsKey("tfp:metal/sword/copper", WEAPON_TAGS)).contains("tooltip.deals_damage.slashing");
        assertThat(DamageInfo.dealsKey("tfp:metal/mace/copper", WEAPON_TAGS)).contains("tooltip.deals_damage.crushing");
        assertThat(DamageInfo.dealsKey("tfp:metal/javelin/copper", WEAPON_TAGS))
                .contains("tooltip.deals_damage.piercing");
        // in two tags: piercing is checked first, as TFC getTypeForItem does
        assertThat(DamageInfo.dealsKey("tfp:metal/knife/copper", WEAPON_TAGS)).contains("tooltip.deals_damage.piercing");
        assertThat(DamageInfo.dealsKey("tfp:metal/ingot/copper", WEAPON_TAGS)).isEmpty();
        assertThat(DamageInfo.dealsKey(null, WEAPON_TAGS)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "tfp:metal/chestplate/bismuth_bronze, 8%, 10%, 14%",
        "tfp:metal/helmet/black_bronze, 8%, 14%, 10%",
        "tfp:metal/greaves/black_steel, 28%, 36%, 39%",
        "tfp:metal/boots/blue_steel, 39%, 39%, 46%",
        "tfp:metal/chestplate/bronze, 8%, 12%, 12%",
        "minecraft:chainmail_chestplate, 8%, 8%, 2%",
        "tfp:metal/chestplate/copper, 6%, 10%, 10%",
        "minecraft:leather_boots, 3%, 0%, 0%",
        "tfp:metal/chestplate/red_steel, 39%, 46%, 39%",
        "tfp:metal/chestplate/steel, 15%, 26%, 22%",
        "tfp:metal/helmet/wrought_iron, 12%, 18%, 18%",
    })
    void armourResistancesMatchTfc(String item, String slashing, String piercing, String crushing) {
        DamageResistances tables = DamageResistances.fromRegistry(snapshot);
        Optional<List<DamageInfo.Share>> shares = DamageInfo.resistances(item, tables, snapshot.tags());
        assertThat(shares).isPresent();
        assertThat(shares.get()).extracting(DamageInfo.Share::percent).containsExactly(slashing, piercing, crushing);
        assertThat(shares.get()).noneMatch(DamageInfo.Share::immune);
    }

    @Test
    void noResistanceAndImmunity() {
        DamageResistances tables = DamageResistances.fromRegistry(snapshot);
        assertThat(DamageInfo.resistances("minecraft:iron_chestplate", tables, snapshot.tags())).isEmpty();
        assertThat(DamageInfo.resistances("tfp:metal/sword/copper", tables, snapshot.tags())).isEmpty();
        assertThat(DamageInfo.Share.of(0f)).isEqualTo(new DamageInfo.Share(false, "0%"));
        assertThat(DamageInfo.Share.of(50f).percent()).isEqualTo("39%");
        // 1 - e^-14 is 0.99999917 >= 0.999999: TFC shows "Immune"
        assertThat(DamageInfo.Share.of(1400f).immune()).isTrue();
        assertThat(DamageInfo.Share.of(Float.POSITIVE_INFINITY).immune()).isTrue();
        assertThat(DamageInfo.Share.of(1000f)).isEqualTo(new DamageInfo.Share(false, "100%"));
    }

    @Test
    void fuelLookupByItemAndTag() {
        FuelInfo fuels = FuelInfo.fromRegistry(snapshot);
        TagLookup tags = snapshot.tags();
        Fuel charcoal = fuels.get("minecraft:charcoal", tags).orElseThrow();
        assertThat(charcoal.temperature()).isEqualTo(1350f);
        assertThat(charcoal.duration()).isEqualTo(1800);
        Fuel peat = fuels.get("tfp:peat", tags).orElseThrow();
        assertThat(peat.temperature()).isEqualTo(600f);
        assertThat(peat.duration()).isEqualTo(2500);
        assertThat(fuels.get("minecraft:coal", tags).orElseThrow().temperature()).isEqualTo(1415f);
        assertThat(fuels.get("tfp:ore/bituminous_coal", tags).orElseThrow().temperature()).isEqualTo(1415f);
        // oak logs come through the #tfc:oak_logs tag
        String oakLog = snapshot.tags().members(TagType.ITEM, "tfp:oak_logs").stream().findFirst().orElseThrow();
        Fuel oak = fuels.get(oakLog, tags).orElseThrow();
        assertThat(oak.temperature()).isEqualTo(728f);
        assertThat(oak.duration()).isEqualTo(2250);
        assertThat(oak.purity()).isEqualTo(1f);
        assertThat(fuels.get("tfp:metal/ingot/copper", tags)).isEmpty();
        assertThat(fuels.get(null, tags)).isEmpty();
        // a vanilla tag the registry does not resolve comes through the caller's lookup (the server's tags)
        TagLookup leaves = new TagLookup() {
            @Override
            public boolean contains(TagType type, String tag, String id) {
                return tag.equals("minecraft:leaves") && id.equals("minecraft:oak_leaves");
            }

            @Override
            public boolean exists(TagType type, String tag) {
                return tag.equals("minecraft:leaves");
            }
        };
        Fuel leaf = fuels.get("minecraft:oak_leaves", leaves).orElseThrow();
        assertThat(leaf.temperature()).isEqualTo(100f);
        assertThat(leaf.duration()).isEqualTo(600);
        // the same index the firepit uses agrees on every item it names
        FirepitFuels firepit = FirepitFuels.fromRegistry(snapshot);
        assertThat(firepit.get("minecraft:charcoal")).contains(charcoal);
        assertThat(firepit.get(oakLog)).contains(oak);
    }

    @Test
    void depositsAreTheDepositData() {
        DepositItems deposits = DepositItems.fromRegistry(snapshot);
        assertThat(deposits.size()).isEqualTo(84); // TFC's 84 deposit files
        assertThat(deposits.contains("tfp:deposit/native_copper/tuff", snapshot.tags())).isTrue();
        assertThat(deposits.contains("tfp:deposit/cassiterite/granite", snapshot.tags())).isTrue();
        assertThat(deposits.contains("tfp:rock/gravel/granite", snapshot.tags())).isFalse();
        assertThat(deposits.contains(null, snapshot.tags())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "tfp:rock/raw/granite, felsic_igneous_intrusive",
        "tfp:rock/bricks/chalk, sedimentary",
        "tfp:rock/raw/diorite, intermediate_igneous_intrusive",
        "tfp:rock/cobble/gabbro, mafic_igneous_intrusive",
        "tfp:rock/smooth/rhyolite, felsic_igneous_extrusive",
        "tfp:rock/gravel/basalt, mafic_igneous_extrusive",
        "tfp:rock/loose/andesite, intermediate_igneous_extrusive",
        "tfp:rock/mossy_loose/dacite, intermediate_igneous_extrusive",
        "tfp:rock/hardened/quartzite, metamorphic",
        "tfp:rock/spike/marble, metamorphic",
        "tfp:rock/button/shale, sedimentary",
        "tfp:rock/pressure_plate/claystone, sedimentary",
        "tfp:rock/aqueduct/limestone, sedimentary",
        "tfp:rock/chiseled/conglomerate, sedimentary",
        "tfp:rock/cracked_bricks/dolomite, sedimentary",
        "tfp:rock/mossy_bricks/chert, sedimentary",
        "tfp:rock/mossy_cobble/tuff, sedimentary",
        "tfp:rock/raw/slate_slab, metamorphic",
        "tfp:rock/bricks/phyllite_stairs, metamorphic",
        "tfp:rock/mossy_cobble/schist_wall, metamorphic",
        "tfp:rock/anvil/granite, felsic_igneous_intrusive",
        "tfp:rock/magma/basalt, mafic_igneous_extrusive",
        "tfp:brick/gneiss, metamorphic",
        "tfp:brick/chalk, sedimentary",
    })
    void rockDisplayCategoryLore(String item, String category) {
        assertThat(StaticLore.of(item))
                .contains(new Line("tooltip.rock_display_category." + category, LoreColor.GRAY));
    }

    @Test
    void everyTfcRockHasItsDisplayCategory() {
        // Rock.java at c3b4d9536, in enum order
        String rows = """
                granite felsic_igneous_intrusive
                diorite intermediate_igneous_intrusive
                gabbro mafic_igneous_intrusive
                shale sedimentary
                claystone sedimentary
                limestone sedimentary
                conglomerate sedimentary
                dolomite sedimentary
                chert sedimentary
                chalk sedimentary
                tuff sedimentary
                rhyolite felsic_igneous_extrusive
                basalt mafic_igneous_extrusive
                andesite intermediate_igneous_extrusive
                dacite intermediate_igneous_extrusive
                quartzite metamorphic
                slate metamorphic
                phyllite metamorphic
                schist metamorphic
                gneiss metamorphic
                marble metamorphic
                """;
        List<String> lines = rows.lines().toList();
        assertThat(RockDisplayCategory.ROCKS).hasSize(lines.size());
        for (String row : lines) {
            String[] p = row.split(" ");
            assertThat(RockDisplayCategory.ofRock(p[0]).map(RockDisplayCategory::serializedName)).as(p[0]).contains(p[1]);
        }
        assertThat(RockDisplayCategory.MAFIC_IGNEOUS_INTRUSIVE.category()).isEqualTo(RockCategory.IGNEOUS_INTRUSIVE);
        assertThat(RockDisplayCategory.FELSIC_IGNEOUS_EXTRUSIVE.category()).isEqualTo(RockCategory.IGNEOUS_EXTRUSIVE);
    }

    @ParameterizedTest
    @CsvSource({
        "tfp:stone/axe_head/igneous_extrusive, igneous_extrusive",
        "tfp:stone/hammer_head/igneous_intrusive, igneous_intrusive",
        "tfp:stone/hoe_head/metamorphic, metamorphic",
        "tfp:stone/javelin_head/sedimentary, sedimentary",
        "tfp:stone/knife_head/sedimentary, sedimentary",
        "tfp:stone/shovel_head/metamorphic, metamorphic",
    })
    void stoneHeadRockCategoryLore(String item, String category) {
        assertThat(StaticLore.of(item)).contains(new Line("tooltip.rock_category." + category, LoreColor.GRAY));
    }

    @ParameterizedTest
    @CsvSource({
        "tfp:silica_glass_batch, silica, AQUA",
        "tfp:hematitic_glass_batch, hematitic, RED",
        "tfp:olivine_glass_batch, olivine, GREEN",
        "tfp:volcanic_glass_batch, volcanic, DARK_PURPLE",
        "tfp:silica_glass_bottle, silica, AQUA",
        "tfp:hematitic_glass_bottle, hematitic, RED",
        "tfp:olivine_glass_bottle, olivine, GREEN",
        "tfp:volcanic_glass_bottle, volcanic, DARK_PURPLE",
    })
    void glassTypeLore(String item, String type, LoreColor color) {
        assertThat(StaticLore.of(item)).contains(new Line("tooltip.glass." + type, color));
    }

    @ParameterizedTest
    @CsvSource({
        "tfp:stone/axe/igneous_extrusive", // finished stone tools carry no lore in TFC
        "tfp:stone/knife/sedimentary",
        "tfp:stone/axe_head/granite",
        "tfp:rock/rope_anchor/granite", // TFC registers no item for it
        "tfp:rock/anvil/chalk", // anvils and magma exist for igneous rocks only
        "tfp:rock/magma/marble",
        "tfp:rock/gravel/granite_slab", // gravel has no decorations
        "tfp:rock/raw/unobtainium",
        "tfp:rock/raw",
        "tfp:brick/granite/extra",
        "tfp:glass_batch",
        "tfp:lamp_glass",
        "tfp:metal/sword/copper",
        "minecraft:stone",
        "tfc:rock/raw/granite",
    })
    void noLore(String item) {
        assertThat(StaticLore.of(item)).isEmpty();
    }

    @Test
    void nullHasNoLore() {
        assertThat(StaticLore.of(null)).isEmpty();
    }
}
