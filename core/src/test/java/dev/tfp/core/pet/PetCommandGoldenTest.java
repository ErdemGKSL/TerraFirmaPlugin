/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the command set, its activities, the server gate and the menu at c3b4d9536.
 *
 * <p>Sources: {@code TamableMammal.Command} (the order RELAX, HOME, SIT, FOLLOW, HUNT, and the activity each one names),
 * {@code TamableMammal.willListenTo} (a SIT needs {@code getHealth() >= 5f}), {@code TFCCat.willListenTo} (a server SIT is
 * refused when {@code nextFloat() < 0.1f}, and the client check skips the roll), {@code PetCommandPacket.handle} (the gate
 * runs before the owner check), and {@code PetCommandScreen} (the menu lists the commands the client check accepts).
 */
class PetCommandGoldenTest {

    @Test
    void commandOrderIsTfcOrderAndTheOrdinalIsTheSavedValue() {
        // TamableMammal.Command declaration order; NBT stores command.ordinal()
        assertThat(PetCommand.values()).containsExactly(PetCommand.RELAX, PetCommand.HOME, PetCommand.SIT, PetCommand.FOLLOW, PetCommand.HUNT);
        assertThat(PetCommand.RELAX.ordinal()).isZero();
        assertThat(PetCommand.HUNT.ordinal()).isEqualTo(4);
        assertThat(PetCommand.fromOrdinal(2)).isEqualTo(PetCommand.SIT);
        assertThatThrownBy(() -> PetCommand.fromOrdinal(5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PetCommand.fromOrdinal(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachCommandSelectsItsTfcActivityAndHomeHasNone() {
        // Command(TFCBrain.IDLE_AT_HOME), Command(null), Command(TFCBrain.SIT), Command(TFCBrain.FOLLOW), Command(TFCBrain.HUNT)
        assertThat(PetCommand.RELAX.activity()).isEqualTo(PetActivity.IDLE_AT_HOME);
        assertThat(PetCommand.HOME.activity()).isNull();
        assertThat(PetCommand.SIT.activity()).isEqualTo(PetActivity.SIT);
        assertThat(PetCommand.FOLLOW.activity()).isEqualTo(PetActivity.FOLLOW);
        assertThat(PetCommand.HUNT.activity()).isEqualTo(PetActivity.HUNT);
    }

    @Test
    void sitNeedsFiveHealthAndExactlyFiveIsEnough() {
        // TamableMammal.willListenTo: command != SIT || getHealth() >= 5f
        assertThat(PetRules.listensInMenu(PetCommand.SIT, 4.99f)).isFalse();
        assertThat(PetRules.listensInMenu(PetCommand.SIT, 5f)).isTrue();
        assertThat(PetRules.listensOnServer(PetSpecies.DOG, PetCommand.SIT, 4.99f, 0f)).isFalse();
        assertThat(PetRules.listensOnServer(PetSpecies.DOG, PetCommand.SIT, 5f, 0f)).isTrue();
    }

    @Test
    void nonSitCommandsIgnoreHealthAndTheRoll() {
        // only the SIT branch of willListenTo reads health; only the cat's SIT branch reads the roll
        for (PetCommand command : List.of(PetCommand.RELAX, PetCommand.HOME, PetCommand.FOLLOW, PetCommand.HUNT)) {
            assertThat(PetRules.listensOnServer(PetSpecies.CAT, command, 0.5f, 0f)).as(command.name()).isTrue();
            assertThat(PetRules.listensInMenu(command, 0.5f)).as(command.name()).isTrue();
        }
    }

    @Test
    void catRefusesSitOnlyWhenTheRollIsBelowTenPercent() {
        // TFCCat.willListenTo: nextFloat() < 0.1f refuses; 0.1f itself is accepted (strict <)
        assertThat(PetSpecies.CAT.sitRefusalChance()).isEqualTo(0.1f);
        assertThat(PetRules.listensOnServer(PetSpecies.CAT, PetCommand.SIT, 20f, 0.0999f)).isFalse();
        assertThat(PetRules.listensOnServer(PetSpecies.CAT, PetCommand.SIT, 20f, 0.1f)).isTrue();
        assertThat(PetRules.listensOnServer(PetSpecies.CAT, PetCommand.SIT, 20f, 0.9f)).isTrue();
    }

    @Test
    void dogNeverRefusesSitForTheRoll() {
        // TamableMammal has no roll; Dog does not override willListenTo
        assertThat(PetSpecies.DOG.sitRefusalChance()).isZero();
        assertThat(PetRules.listensOnServer(PetSpecies.DOG, PetCommand.SIT, 20f, 0f)).isTrue();
    }

    @Test
    void menuIsTheClientCheckSoTheCatRollIsNotShown() {
        // PetCommandScreen loops Command.VALUES with willListenTo(command, true): no roll on the client
        assertThat(PetRules.menu(20f)).containsExactly(PetCommand.RELAX, PetCommand.HOME, PetCommand.SIT, PetCommand.FOLLOW, PetCommand.HUNT);
        assertThat(PetRules.menu(4f)).containsExactly(PetCommand.RELAX, PetCommand.HOME, PetCommand.FOLLOW, PetCommand.HUNT);
        assertThat(PetRules.menu(5f)).contains(PetCommand.SIT);
    }
}
