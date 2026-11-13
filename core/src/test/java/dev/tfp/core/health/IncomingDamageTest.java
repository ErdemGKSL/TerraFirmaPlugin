/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
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

/**
 * PAR-SURV-05: TFC's {@code onLivingHurt} over the exported {@code entity_damage_resistance} and
 * {@code item_damage_resistance} data. Expected values are TFC's arithmetic ({@code e^(-0.01 x resistance)}) with the
 * resistances of TFC's generated JSON at c3b4d9536.
 */
class IncomingDamageTest {
    private static DamageResistances resistances;

    /** The vanilla tags the rows need, plus TFC's weapon tags for a few tools. */
    private static final Map<String, Set<String>> TAGS = Map.of(
            "minecraft:zombies", Set.of("minecraft:zombie", "minecraft:husk", "minecraft:drowned"),
            "minecraft:skeletons", Set.of("minecraft:skeleton", "minecraft:stray"),
            IncomingDamage.ITEM_PIERCING, Set.of("minecraft:iron_pickaxe", "tfp:metal/knife/steel"),
            IncomingDamage.ITEM_SLASHING, Set.of("minecraft:iron_sword", "tfp:metal/sword/steel"),
            IncomingDamage.ITEM_CRUSHING, Set.of("minecraft:iron_shovel", "tfp:metal/mace/steel"));

    static final TagLookup TAG_LOOKUP = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return TAGS.getOrDefault(tag, Set.of()).contains(id);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return TAGS.containsKey(tag);
        }
    };

    private static final List<String> STEEL = List.of("tfp:metal/helmet/steel", "tfp:metal/chestplate/steel",
            "tfp:metal/greaves/steel", "tfp:metal/boots/steel");

    @BeforeAll
    static void load() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        resistances = DamageResistances.fromRegistry(DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data)));
    }

    private static IncomingDamage.Hit hit(float amount, String damageType, String attacker, String weapon,
            String target, List<String> armor, Float modifier) {
        return new IncomingDamage.Hit(amount, damageType, Optional.ofNullable(attacker), Optional.ofNullable(weapon),
                1f, target, armor, Optional.ofNullable(modifier));
    }

    private static float exp(float resistance) {
        return (float) Math.pow(Math.E, -0.01 * resistance);
    }

    @Test
    void loadsTfcTables() {
        assertThat(resistances.entities()).hasSize(2);
        assertThat(resistances.items()).hasSize(11);
        assertThat(resistances.item("tfp:metal/chestplate/steel", TAG_LOOKUP))
                .contains(PhysicalResistances.ARMOR.get("steel"));
        assertThat(resistances.item("minecraft:leather_boots", TAG_LOOKUP))
                .contains(PhysicalResistances.ARMOR.get("leather"));
        assertThat(resistances.item("minecraft:chainmail_helmet", TAG_LOOKUP))
                .contains(PhysicalResistances.ARMOR.get("chainmail"));
        assertThat(resistances.item("minecraft:iron_chestplate", TAG_LOOKUP)).isEmpty();
        assertThat(resistances.entity("minecraft:zombie", TAG_LOOKUP)).contains(PhysicalResistances.ZOMBIES);
        assertThat(resistances.entity("minecraft:stray", TAG_LOOKUP)).contains(PhysicalResistances.SKELETONS);
        assertThat(resistances.entity("minecraft:cow", TAG_LOOKUP)).isEmpty();
        // every exported armour material matches the core table
        for (var e : PhysicalResistances.ARMOR.entrySet()) {
            String piece = switch (e.getKey()) {
                case "leather", "chainmail" -> "minecraft:" + e.getKey() + "_chestplate";
                default -> "tfp:metal/chestplate/" + e.getKey();
            };
            assertThat(resistances.item(piece, TAG_LOOKUP)).as(piece).contains(e.getValue());
        }
    }

    @Test
    void zombieOnSteelChestplateUsesTheHighestResistance() {
        // A bare-handed zombie: mob_attack is untyped, the zombie holds nothing and no entity tag types it.
        var r = IncomingDamage.apply(hit(3f, "minecraft:mob_attack", "minecraft:zombie", null, "minecraft:player",
                List.of("tfp:metal/chestplate/steel"), 1f), resistances, TAG_LOOKUP);
        assertThat(r.type()).isEmpty();
        assertThat(r.resistance()).isEqualTo(30f);
        assertThat(r.multiplier()).isEqualTo(exp(30f));
        assertThat(r.amount()).isCloseTo(3f * 0.7408182f, within(1e-6f));
    }

    @Test
    void steelChestplateVsCrushAndPierce() {
        var crush = IncomingDamage.apply(hit(10f, "minecraft:mob_attack", "minecraft:zombie", "minecraft:iron_shovel",
                "minecraft:player", List.of("tfp:metal/chestplate/steel"), 1f), resistances, TAG_LOOKUP);
        assertThat(crush.type()).contains(PhysicalDamageType.CRUSHING);
        assertThat(crush.resistance()).isEqualTo(25f);
        var pierce = IncomingDamage.apply(hit(10f, "minecraft:arrow", "minecraft:skeleton", "minecraft:bow",
                "minecraft:player", List.of("tfp:metal/chestplate/steel"), 1f), resistances, TAG_LOOKUP);
        assertThat(pierce.type()).contains(PhysicalDamageType.PIERCING);
        assertThat(pierce.resistance()).isEqualTo(30f);
        assertThat(pierce.amount()).isEqualTo(10f * exp(30f));
        var slash = IncomingDamage.apply(hit(10f, "minecraft:player_attack", "minecraft:player",
                "tfp:metal/sword/steel", "minecraft:player", STEEL, 1f), resistances, TAG_LOOKUP);
        assertThat(slash.type()).contains(PhysicalDamageType.SLASHING);
        assertThat(slash.resistance()).isEqualTo(4 * 16.5f);
    }

    @Test
    void entityResistanceOnlyForTypedSources() {
        // a sword on a zombie: slashing 0; a pickaxe: piercing -25 (vulnerable); a fist: untyped, no natural resistance
        assertThat(IncomingDamage.apply(hit(10f, "minecraft:player_attack", "minecraft:player", "minecraft:iron_pickaxe",
                "minecraft:zombie", List.of(), null), resistances, TAG_LOOKUP).amount()).isEqualTo(10f * exp(-25f));
        assertThat(IncomingDamage.apply(hit(10f, "minecraft:player_attack", "minecraft:player", "minecraft:iron_shovel",
                "minecraft:husk", List.of(), null), resistances, TAG_LOOKUP).amount()).isEqualTo(10f * exp(50f));
        assertThat(IncomingDamage.apply(hit(10f, "minecraft:player_attack", "minecraft:player", null,
                "minecraft:zombie", List.of(), null), resistances, TAG_LOOKUP).amount()).isEqualTo(10f);
        // skeletons are immune to piercing
        assertThat(IncomingDamage.apply(hit(10f, "minecraft:arrow", "minecraft:player", "minecraft:bow",
                "minecraft:skeleton", List.of(), null), resistances, TAG_LOOKUP).amount()).isEqualTo(0f);
    }

    @Test
    void damageTypeTagsWinOverTheWeapon() {
        // fall is crushing even when a causing entity holds a sword (TFC checks the damage type tags first)
        assertThat(IncomingDamage.typeForSource("minecraft:fall", Optional.of("minecraft:player"),
                Optional.of("minecraft:iron_sword"), TAG_LOOKUP)).contains(PhysicalDamageType.CRUSHING);
        // no causing entity: the weapon is not read
        assertThat(IncomingDamage.typeForSource("minecraft:generic", Optional.empty(),
                Optional.of("minecraft:iron_sword"), TAG_LOOKUP)).isEmpty();
    }

    @Test
    void playerHealthModifierAndForgingBonus() {
        var r = IncomingDamage.apply(new IncomingDamage.Hit(10f, "minecraft:mob_attack", Optional.of("minecraft:zombie"),
                Optional.empty(), 1.5f, "minecraft:player", STEEL, Optional.of(0.85f)), resistances, TAG_LOOKUP);
        assertThat(r.resistance()).isEqualTo(120f);
        assertThat(r.amount()).isEqualTo(10f * 1.5f * exp(120f) / 0.85f);
        assertThat(r.amount()).isEqualTo(HealthScale.playerDamage(15f, exp(120f), 0.85f));
    }

    @Test
    void killCommandIsUnchanged() {
        var r = IncomingDamage.apply(hit(Float.MAX_VALUE, "minecraft:generic_kill", null, null, "minecraft:player",
                STEEL, 0.5f), resistances, TAG_LOOKUP);
        assertThat(r.unchanged()).isTrue();
        assertThat(r.amount()).isEqualTo(Float.MAX_VALUE);
    }
}
