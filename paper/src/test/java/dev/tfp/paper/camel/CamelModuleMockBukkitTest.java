/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.camel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.itemsize.BurdenApplier;
import dev.tfp.paper.mount.MountSupport;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-16 on MockBukkit: TFC's camel attributes, old age and the thrown overburdened rider. */
class CamelModuleMockBukkitTest extends MockServerTest {
    private World world;
    private CamelModule module;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new CamelModule();
        module.initForTest((p, key) -> {
            messages.add(key);
            return key;
        });
    }

    private Camel camel(String species) {
        Camel camel = world.spawn(new Location(world, 0, 64, 0), Camel.class);
        org.mockbukkit.mockbukkit.entity.LivingEntityMock mock = (org.mockbukkit.mockbukkit.entity.LivingEntityMock) camel;
        for (Attribute a : new Attribute[] {Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH,
            Attribute.STEP_HEIGHT}) {
            if (camel.getAttribute(a) == null) {
                try {
                    mock.registerAttribute(a);
                } catch (NullPointerException noDefault) {
                    // MockBukkit has no default for this attribute: the carrier has none and the module skips it
                }
            }
        }
        AnimalState state = new AnimalState(false, 0.5f, 0, 0, AnimalState.NO_OLD_TICK, 0, AnimalState.NEVER, 0, false,
                AnimalState.NOT_PREGNANT);
        module.behaviour().onAdd(camel, new AnimalRecord(species, state, 0, "", false, AnimalRecord.NO_GROW_TICK));
        return camel;
    }

    private void check(Camel camel, LivestockAge age) {
        AnimalRecord r = new AnimalRecord("tfc:dromedary_camel", new AnimalState(false, 0.5f, 0, 0, AnimalState.NO_OLD_TICK,
                0, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT), 0, "", false, AnimalRecord.NO_GROW_TICK);
        module.behaviour().onCheck(new SpeciesBehaviour.Check(camel, r, age, 0));
    }

    @Test
    void bothCamelsGetTheDromedaryAttributes() {
        for (String species : CamelModule.SPECIES) {
            Camel c = camel(species);
            assertThat(c.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(32.0);
            assertThat(c.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue()).isEqualTo((double) 0.1f);
            if (c.getAttribute(Attribute.JUMP_STRENGTH) != null) {
                assertThat(c.getAttribute(Attribute.JUMP_STRENGTH).getBaseValue()).isEqualTo((double) 0.42f);
            }
            if (c.getAttribute(Attribute.STEP_HEIGHT) != null) {
                assertThat(c.getAttribute(Attribute.STEP_HEIGHT).getBaseValue()).isEqualTo(1.5);
            }
        }
    }

    @Test
    void anOldCamelMovesAtHalfSpeed() {
        Camel c = camel("tfc:dromedary_camel");
        check(c, LivestockAge.ADULT);
        double adult = c.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        check(c, LivestockAge.OLD);
        check(c, LivestockAge.OLD); // the modifier is added once
        assertThat(c.getAttribute(Attribute.MOVEMENT_SPEED).getValue()).isEqualTo(adult * 0.5);
    }

    @Test
    void anOverburdenedRiderIsThrownAndAnOrdinaryOneStays() {
        Camel c = camel("tfc:bactrian_camel");
        Player fine = server.addPlayer();
        c.addPassenger(fine);
        check(c, LivestockAge.ADULT);
        assertThat(c.getPassengers()).contains(fine);
        fine.getAttribute(Attribute.MOVEMENT_SPEED).addTransientModifier(new AttributeModifier(BurdenApplier.MODIFIER, -7.5,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        check(c, LivestockAge.ADULT);
        assertThat(c.getPassengers()).isEmpty();
        assertThat(messages).contains(MountSupport.OVERBURDENED_KEY);
    }
}
