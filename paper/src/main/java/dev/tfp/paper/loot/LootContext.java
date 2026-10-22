/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An immutable snapshot of everything a loot table may ask (vanilla's {@code LootParams} plus TFC's {@code tfc:isolated},
 * {@code tfc:burnt_out} and {@code tfc:sluice} parameters). Taken on the owner thread, read on any thread. A {@code null}
 * field means the parameter is absent, as in vanilla.
 *
 * @param blockId the broken block's id (block tables), or null
 * @param blockState the broken block's state properties (empty when none)
 * @param tool the tool; {@link Tool#EMPTY} is an empty hand, null means no tool parameter at all
 * @param explosionRadius {@code explosion_radius}: set when an explosion with decay destroyed the block, else null
 * @param isolated TFC's {@code tfc:isolated} (a raw rock broken by the isolation check)
 * @param burntOut TFC's {@code tfc:burnt_out} (a torch that burnt out)
 * @param sluice TFC's {@code tfc:sluice} (deposit loot rolled by a sluice)
 * @param blockEntity whether the block had a block entity (copy_components/copy_fluid sources)
 * @param cropYield the crop block entity's yield in {@code [0, 1]}, or null when the block is not a crop
 * @param thisAnimal the killed entity when it is a TFC animal, else null
 * @param thisEntity whether a {@code this_entity} parameter is present at all
 * @param lastDamagePlayerAttack the attack damage attribute of the last damaging player, or null when no player
 * @param damageTags the damage-type tags of the killing damage source (without {@code #}), or null when there is none
 * @param attacker what killed the entity, for {@code tfc:not_predated}
 */
public record LootContext(String blockId, Map<String, String> blockState, Tool tool, Float explosionRadius,
        boolean isolated, boolean burntOut, boolean sluice, boolean blockEntity, Float cropYield, Animal thisAnimal,
        boolean thisEntity, Double lastDamagePlayerAttack, Set<String> damageTags, Attacker attacker) {

    public LootContext {
        blockState = Map.copyOf(Objects.requireNonNull(blockState, "blockState"));
        damageTags = damageTags == null ? null : Set.copyOf(damageTags);
        Objects.requireNonNull(attacker, "attacker");
        if (thisAnimal != null) {
            thisEntity = true;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code killed_by_player}: a player dealt the last damage. */
    public boolean killedByPlayer() {
        return lastDamagePlayerAttack != null;
    }

    /** What dealt the killing blow ({@code attacking_entity}). */
    public enum Attacker {
        /** No attacking entity parameter. */
        NONE,
        PLAYER,
        /** An entity in {@code tfc:land_predators} or {@code tfc:ocean_predators}. */
        PREDATOR,
        OTHER
    }

    /** The parts of a TFC animal ({@code TFCAnimalProperties}) that loot reads. */
    public record Animal(float geneticSize, float familiarity, boolean male) {}

    /**
     * A snapshot of the tool stack: its item id (null for an empty hand), its enchantment levels by id, and a tag test for
     * the item ({@code tag} without {@code #}, any thread).
     */
    public record Tool(String itemId, Map<String, Integer> enchantments, Predicate<String> inTag) {
        public static final Tool EMPTY = new Tool(null, Map.of(), tag -> false);

        public Tool {
            enchantments = Map.copyOf(Objects.requireNonNull(enchantments, "enchantments"));
            Objects.requireNonNull(inTag, "inTag");
        }

        public static Tool of(String itemId, Map<String, Integer> enchantments, Predicate<String> inTag) {
            return new Tool(itemId, enchantments, inTag);
        }

        public boolean isEmpty() {
            return itemId == null;
        }

        public int level(String enchantment) {
            return enchantments.getOrDefault(enchantment, 0);
        }
    }

    public static final class Builder {
        private String blockId;
        private Map<String, String> blockState = Map.of();
        private Tool tool = Tool.EMPTY;
        private Float explosionRadius;
        private boolean isolated;
        private boolean burntOut;
        private boolean sluice;
        private boolean blockEntity;
        private Float cropYield;
        private Animal thisAnimal;
        private boolean thisEntity;
        private Double lastDamagePlayerAttack;
        private Set<String> damageTags;
        private Attacker attacker = Attacker.NONE;

        private Builder() {}

        public Builder block(String id, Map<String, String> state) {
            this.blockId = id;
            this.blockState = state;
            return this;
        }

        public Builder tool(Tool tool) {
            this.tool = tool;
            return this;
        }

        public Builder explosionRadius(Float radius) {
            this.explosionRadius = radius;
            return this;
        }

        public Builder isolated(boolean value) {
            this.isolated = value;
            return this;
        }

        public Builder burntOut(boolean value) {
            this.burntOut = value;
            return this;
        }

        public Builder sluice(boolean value) {
            this.sluice = value;
            return this;
        }

        public Builder blockEntity(boolean value) {
            this.blockEntity = value;
            return this;
        }

        public Builder cropYield(Float yield) {
            this.cropYield = yield;
            return this;
        }

        public Builder animal(Animal animal) {
            this.thisAnimal = animal;
            return this;
        }

        public Builder thisEntity(boolean present) {
            this.thisEntity = present;
            return this;
        }

        public Builder lastDamagePlayer(Double attackDamage) {
            this.lastDamagePlayerAttack = attackDamage;
            return this;
        }

        public Builder damageTags(Set<String> tags) {
            this.damageTags = tags;
            return this;
        }

        public Builder attacker(Attacker value) {
            this.attacker = value;
            return this;
        }

        public LootContext build() {
            return new LootContext(blockId, blockState, tool, explosionRadius, isolated, burntOut, sluice, blockEntity,
                    cropYield, thisAnimal, thisEntity, lastDamagePlayerAttack, damageTags, attacker);
        }
    }
}
