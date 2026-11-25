/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/TFCEntities.java @ c3b4d9536 (the 18 domestic registrations),
 * common/entities/ai/livestock/LivestockAi.java and OviparousAi.java (makeBrain), livestock/camel/TFCCamelAi.java,
 * livestock/horse/TFCHorse.java and TFCChestedHorse.java (registerGoals), ai/pet/TamableAi.java, same commit
 */
package dev.tfp.core.livestockai;

import dev.tfp.core.prey.Avoidance;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TFC's 18 domestic species and which AI each one runs, with the literals TFC passes to its behaviours. Immutable,
 * thread-safe.
 *
 * <ul>
 *   <li>{@link Brain#LIVESTOCK}: {@code LivestockAi} ({@code TFCAnimal}: pig, cow, goat, yak, alpaca, sheep, musk ox).</li>
 *   <li>{@link Brain#OVIPAROUS}: {@code OviparousAi} (chicken, duck, quail): the livestock brain plus the egg laying of
 *       the nest box (PAR-FAUNA-07).</li>
 *   <li>{@link Brain#CAMEL}: {@code TFCCamelAi} (dromedary, bactrian): the same behaviours at camel speeds.</li>
 *   <li>{@link Brain#HORSE}: vanilla horse goals with the tempt goal replaced by TFC's food (horse, donkey, mule).</li>
 *   <li>{@link Brain#PET}: {@code TamableAi} (cat, dog), ported with the pet commands (PAR-FAUNA-14).</li>
 *   <li>{@link Brain#SMALL_PREY}: {@code TFCRabbit}, vanilla rabbit goals minus TFC's removals (PAR-FAUNA-09).</li>
 * </ul>
 */
public final class LivestockRoster {
    /** Which TFC AI a domestic species runs. */
    public enum Brain {
        LIVESTOCK, OVIPAROUS, CAMEL, HORSE, PET, SMALL_PREY;

        /** True for the brains this item runs as goals in place of the carrier's vanilla goals. */
        public boolean tfcGoals() {
            return this == LIVESTOCK || this == OVIPAROUS || this == CAMEL;
        }
    }

    /**
     * The literals of one brain.
     *
     * @param swimChance  {@code new Swim(0.8F)}
     * @param panicSpeed  {@code new AnimalPanic(2.0F)} / {@code CamelPanic(4.0F)}
     * @param temptAdult  {@code FollowTemptation(e -> e.isBaby() ? 1.5F : 1.25F)} for an adult
     * @param temptBaby   the same for a baby
     * @param followAdult {@code BabyFollowAdult.create(UniformInt.of(5, 16), 1.25F)}
     * @param stroll      {@code RandomStroll.stroll(1.0F)} and {@code SetWalkTargetFromLookTarget.create(1.0F, 3)}
     * @param away        {@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.3F, 15, false)}
     * @param breed       {@code new BreedBehavior<>(1.0F)}
     * @param avoidance   {@code AvoidPredatorAndRammersBehavior.create(true)}
     */
    public record Profile(float swimChance, float panicSpeed, float temptAdult, float temptBaby, float followAdult,
            float stroll, float away, float breed, Avoidance avoidance) {

        /** The tempt speed of an adult or a baby. */
        public float tempt(boolean baby) {
            return baby ? temptBaby : temptAdult;
        }
    }

    /** {@code LivestockAi} and {@code OviparousAi}. */
    public static final Profile LIVESTOCK = new Profile(0.8f, 2.0f, 1.25f, 1.5f, 1.25f, 1.0f, 1.3f, 1.0f,
            Avoidance.PREDATORS_AND_RAMMERS);
    /** {@code TFCCamelAi}: {@code FollowTemptation(e -> e.isBaby() ? 2.5F : 3.5F)}, stroll 2, away 3.2, breed 2. */
    public static final Profile CAMEL = new Profile(0.8f, 4.0f, 3.5f, 2.5f, 2.5f, 2.0f, 3.2f, 2.0f,
            Avoidance.PREDATORS_AND_RAMMERS);
    /** {@code TFCHorse.registerGoals}: {@code new TemptGoal(this, 1.25f, food, false)} at priority 3. */
    public static final float HORSE_TEMPT_SPEED = 1.25f;
    public static final int HORSE_TEMPT_PRIORITY = 3;

    private static final Map<String, Brain> ROSTER = roster();

    private LivestockRoster() {
    }

    private static Map<String, Brain> roster() {
        Map<String, Brain> m = new LinkedHashMap<>();
        // TFCEntities order: livestock, then the rabbit, the mounts and the pets
        for (String s : List.of("pig", "cow", "goat", "yak", "alpaca", "sheep", "musk_ox")) {
            m.put("tfc:" + s, Brain.LIVESTOCK);
        }
        for (String s : List.of("chicken", "duck", "quail")) {
            m.put("tfc:" + s, Brain.OVIPAROUS);
        }
        m.put("tfc:rabbit", Brain.SMALL_PREY);
        for (String s : List.of("donkey", "mule", "horse")) {
            m.put("tfc:" + s, Brain.HORSE);
        }
        for (String s : List.of("dromedary_camel", "bactrian_camel")) {
            m.put("tfc:" + s, Brain.CAMEL);
        }
        for (String s : List.of("cat", "dog")) {
            m.put("tfc:" + s, Brain.PET);
        }
        return Collections.unmodifiableMap(m);
    }

    /** The 18 species ids in TFC's registration order. */
    public static List<String> species() {
        return List.copyOf(ROSTER.keySet());
    }

    /** The species running {@code brain}. */
    public static List<String> species(Brain brain) {
        return ROSTER.entrySet().stream().filter(e -> e.getValue() == brain).map(Map.Entry::getKey).toList();
    }

    public static Optional<Brain> brain(String species) {
        return Optional.ofNullable(ROSTER.get(species));
    }

    /** The literals of a goal-run brain; empty for the brains that keep vanilla goals. */
    public static Optional<Profile> profile(String species) {
        return brain(species).flatMap(b -> switch (b) {
            case LIVESTOCK, OVIPAROUS -> Optional.of(LIVESTOCK);
            case CAMEL -> Optional.of(CAMEL);
            default -> Optional.empty();
        });
    }
}
