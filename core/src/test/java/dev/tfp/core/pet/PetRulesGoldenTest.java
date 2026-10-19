/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.pet.PetRules.Feedback;
import dev.tfp.core.pet.PetRules.Sender;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the command transitions, the sit timeout, taming, the menu-open gesture and the save flags at
 * c3b4d9536.
 *
 * <p>Sources: {@code TamableMammal.receiveCommand} (RELAX clears sitting and sleeping; SIT sets sitting, clears sleeping and
 * records {@code SIT_TIME = Calendars.SERVER.getTicks()}; HOME sets the home, stores RELAX and then looks up RELAX's
 * activity; FOLLOW and HUNT clear sitting and sleeping and erase {@code SIT_TIME}), {@code TamableAi.wantsToStopSitting}
 * ({@code now > time + 2000} strictly, or a hurt-by memory), {@code TamableAi.beginIdle} (home if near, else IDLE),
 * {@code TamableMammal.mobInteract} (taming: {@code familiarity + 0.06f > 0.15f}, unowned, food, hungry; the open-menu
 * branch: empty hand, shift, owner, not on fire), and {@code addAdditionalSaveData} (petFlags: sleeping 1, sitting 4,
 * interested 8).
 */
class PetRulesGoldenTest {

    private static final PetState.Home HOME_POS = new PetState.Home("minecraft:overworld", 10, 64, -3);
    private static final PetState.Home FAR_POS = new PetState.Home("minecraft:the_nether", -7, 40, 2);
    private static final Sender OWNER = new Sender(true, HOME_POS);
    private static final Sender STRANGER = new Sender(false, FAR_POS);

    private static PetState state(PetCommand command, boolean sitting, boolean sleeping, long sitTick, PetState.Home home) {
        return new PetState(command, sitting, sleeping, sitTick, home);
    }

    @Test
    void freshPetRelaxesWithNoSitAndNoHome() {
        // TamableMammal: private Command command = Command.RELAX; flags 0; no SIT_TIME or HOME memory
        final PetState fresh = PetState.fresh();
        assertThat(fresh.command()).isEqualTo(PetCommand.RELAX);
        assertThat(fresh.sitting()).isFalse();
        assertThat(fresh.sleeping()).isFalse();
        assertThat(fresh.sitTick()).isEqualTo(PetState.NO_SIT_TICK);
        assertThat(fresh.home()).isNull();
    }

    @Test
    void sitStartsSittingAtTheServerTickAndWakesTheSleeper() {
        // receiveCommand SIT: setSitting(true), setSleeping(false), SIT_TIME = now
        final PetState before = state(PetCommand.RELAX, false, true, PetState.NO_SIT_TICK, null);
        final PetRules.Outcome out = PetRules.handle(before, PetSpecies.DOG, 20f, 0f, OWNER, PetCommand.SIT, 5000L);
        assertThat(out.feedback()).isEqualTo(Feedback.NONE);
        assertThat(out.state().command()).isEqualTo(PetCommand.SIT);
        assertThat(out.state().sitting()).isTrue();
        assertThat(out.state().sleeping()).isFalse();
        assertThat(out.state().sitTick()).isEqualTo(5000L);
        assertThat(out.activity()).isEqualTo(PetActivity.SIT);
    }

    @Test
    void relaxStopsSittingAndSleepingButKeepsTheSitTick() {
        // receiveCommand RELAX: setSitting(false), setSleeping(false); SIT_TIME is not erased
        final PetState before = state(PetCommand.SIT, true, true, 1234L, HOME_POS);
        final PetRules.Outcome out = PetRules.handle(before, PetSpecies.DOG, 20f, 0f, OWNER, PetCommand.RELAX, 9999L);
        assertThat(out.state().command()).isEqualTo(PetCommand.RELAX);
        assertThat(out.state().sitting()).isFalse();
        assertThat(out.state().sleeping()).isFalse();
        assertThat(out.state().sitTick()).isEqualTo(1234L);
        assertThat(out.state().home()).isEqualTo(HOME_POS);
        assertThat(out.activity()).isEqualTo(PetActivity.IDLE_AT_HOME);
    }

    @Test
    void homeSetsTheSenderPositionStoresRelaxAndKeepsFlags() {
        // receiveCommand HOME: setMemory(HOME, player pos); command = RELAX; this.command = RELAX; activity of RELAX
        final PetState before = state(PetCommand.SIT, true, false, 100L, FAR_POS);
        final PetRules.Outcome out = PetRules.handle(before, PetSpecies.CAT, 20f, 0.5f, OWNER, PetCommand.HOME, 300L);
        assertThat(out.state().home()).isEqualTo(HOME_POS);
        assertThat(out.state().command()).isEqualTo(PetCommand.RELAX);
        assertThat(out.state().sitting()).isTrue();
        assertThat(out.state().sitTick()).isEqualTo(100L);
        assertThat(out.activity()).isEqualTo(PetActivity.IDLE_AT_HOME);
    }

    @Test
    void followAndHuntStandUpAndEraseTheSitTick() {
        // receiveCommand FOLLOW and HUNT: setSitting(false), setSleeping(false), eraseMemory(SIT_TIME)
        final PetState sitting = state(PetCommand.SIT, true, true, 77L, null);
        final PetRules.Outcome follow = PetRules.handle(sitting, PetSpecies.DOG, 20f, 0f, OWNER, PetCommand.FOLLOW, 80L);
        assertThat(follow.state().command()).isEqualTo(PetCommand.FOLLOW);
        assertThat(follow.state().sitting()).isFalse();
        assertThat(follow.state().sleeping()).isFalse();
        assertThat(follow.state().sitTick()).isEqualTo(PetState.NO_SIT_TICK);
        assertThat(follow.activity()).isEqualTo(PetActivity.FOLLOW);

        final PetRules.Outcome hunt = PetRules.handle(sitting, PetSpecies.DOG, 20f, 0f, OWNER, PetCommand.HUNT, 80L);
        assertThat(hunt.state().command()).isEqualTo(PetCommand.HUNT);
        assertThat(hunt.state().sitTick()).isEqualTo(PetState.NO_SIT_TICK);
        assertThat(hunt.activity()).isEqualTo(PetActivity.HUNT);
    }

    @Test
    void aNonOwnerIsToldAndNothingChanges() {
        // PetCommandPacket.handle passes the gate, then receiveCommand: isOwnedBy(player) false -> tfc.pet.not_owner
        final PetState before = state(PetCommand.RELAX, false, false, PetState.NO_SIT_TICK, null);
        final PetRules.Outcome out = PetRules.handle(before, PetSpecies.DOG, 20f, 0f, STRANGER, PetCommand.SIT, 5000L);
        assertThat(out.feedback()).isEqualTo(Feedback.NOT_OWNER);
        assertThat(out.state()).isEqualTo(before);
        assertThat(out.activity()).isNull();
    }

    @Test
    void theGateIsCheckedBeforeOwnershipSoAStrangerIsToldWillNotListen() {
        // PetCommandPacket.handle: willListenTo first; only then receiveCommand's owner check
        final PetState before = state(PetCommand.RELAX, false, false, PetState.NO_SIT_TICK, null);
        final PetRules.Outcome out = PetRules.handle(before, PetSpecies.DOG, 4f, 0f, STRANGER, PetCommand.SIT, 5000L);
        assertThat(out.feedback()).isEqualTo(Feedback.WILL_NOT_LISTEN);
        assertThat(out.state()).isEqualTo(before);
    }

    @Test
    void aCatThatRefusesTheSitLeavesItsStateAlone() {
        // TFCCat.willListenTo: roll 0.05 < 0.1 refuses the SIT for the packet, so tfc.pet.will_not_listen
        final PetState before = state(PetCommand.FOLLOW, false, false, PetState.NO_SIT_TICK, null);
        final PetRules.Outcome out = PetRules.handle(before, PetSpecies.CAT, 20f, 0.05f, OWNER, PetCommand.SIT, 5000L);
        assertThat(out.feedback()).isEqualTo(Feedback.WILL_NOT_LISTEN);
        assertThat(out.state()).isEqualTo(before);
    }

    @Test
    void sitTimesOutOnlyAfterMoreThanTwoThousandTicks() {
        // TamableAi.wantsToStopSitting: calendar ticks > SIT_TIME + 2000, strictly
        final PetState sat = state(PetCommand.SIT, true, false, 1000L, null);
        assertThat(PetRules.shouldStopSitting(sat, false, 3000L)).isFalse();
        assertThat(PetRules.shouldStopSitting(sat, false, 3001L)).isTrue();
        assertThat(PetRules.SIT_TIMEOUT_TICKS).isEqualTo(2000L);
    }

    @Test
    void aHurtPetStopsSittingAtOnce() {
        // TamableAi.wantsToStopSitting: a HURT_BY_ENTITY memory returns true before the time check
        final PetState sat = state(PetCommand.SIT, true, false, 1000L, null);
        assertThat(PetRules.shouldStopSitting(sat, true, 1001L)).isTrue();
    }

    @Test
    void aSitWithNoSitTickNeverTimesOut() {
        // no SIT_TIME memory: the time filter gives empty, so only the hurt check can stop it
        final PetState sat = state(PetCommand.SIT, true, false, PetState.NO_SIT_TICK, null);
        assertThat(PetRules.shouldStopSitting(sat, false, Long.MAX_VALUE)).isFalse();
    }

    @Test
    void stoppingNearHomeIdlesAtHomeAndAwayIdles() {
        // TamableAi.beginIdle(home): setSitting(false), setSleeping(false), RELAX, IDLE_AT_HOME if home else IDLE
        final PetState sat = state(PetCommand.SIT, true, true, 1000L, HOME_POS);
        final PetRules.Outcome near = PetRules.stopSitting(sat, true);
        assertThat(near.state()).isEqualTo(state(PetCommand.RELAX, false, false, 1000L, HOME_POS));
        assertThat(near.activity()).isEqualTo(PetActivity.IDLE_AT_HOME);
        assertThat(near.feedback()).isEqualTo(Feedback.NONE);

        final PetRules.Outcome away = PetRules.stopSitting(sat, false);
        assertThat(away.state().command()).isEqualTo(PetCommand.RELAX);
        assertThat(away.activity()).isEqualTo(PetActivity.IDLE);
    }

    @Test
    void tamingNeedsFamiliarityAboveFifteenPercentAfterTheBonus() {
        // mobInteract: familiarity + 0.06f > 0.15f. 0.10 gives 0.16 (taken). 0.05 gives 0.11 (refused).
        assertThat(PetRules.TAME_FAMILIARITY_BONUS).isEqualTo(0.06f);
        assertThat(PetRules.TAME_FAMILIARITY_THRESHOLD).isEqualTo(0.15f);
        assertThat(PetRules.isTameable(0.10f, false, true, true)).isTrue();
        assertThat(PetRules.isTameable(0.05f, false, true, true)).isFalse();
    }

    @Test
    void tamingAlsoNeedsNoOwnerFoodAndHunger() {
        // mobInteract: getOwnerUUID() == null && isFood(held) && isHungry()
        assertThat(PetRules.isTameable(0.5f, true, true, true)).isFalse();
        assertThat(PetRules.isTameable(0.5f, false, false, true)).isFalse();
        assertThat(PetRules.isTameable(0.5f, false, true, false)).isFalse();
    }

    @Test
    void sneakingWithAnEmptyHandOpensTheMenuForTheOwnerOnly() {
        // mobInteract: held.isEmpty() && isShiftKeyDown() && getOwner() != null && isOwnedBy(player) && !isOnFire()
        assertThat(PetRules.canOpenMenu(true, true, true, false)).isTrue();
        assertThat(PetRules.canOpenMenu(false, true, true, false)).isFalse();
        assertThat(PetRules.canOpenMenu(true, false, true, false)).isFalse();
        assertThat(PetRules.canOpenMenu(true, true, false, false)).isFalse();
        assertThat(PetRules.canOpenMenu(true, true, true, true)).isFalse();
    }

    @Test
    void petFlagsUseTfcBitsAndIgnoreInterested() {
        // addAdditionalSaveData/readAdditionalSaveData: SLEEPING_FLAG = 1, SITTING_FLAG = 4, INTERESTED_FLAG = 8 (not stored)
        assertThat(PetRules.FLAG_SLEEPING).isEqualTo(1);
        assertThat(PetRules.FLAG_SITTING).isEqualTo(4);
        assertThat(PetRules.packFlags(state(PetCommand.SIT, true, true, 0L, null))).isEqualTo(5);
        assertThat(PetRules.packFlags(state(PetCommand.RELAX, false, false, PetState.NO_SIT_TICK, null))).isZero();

        final PetState restored = PetRules.unpackFlags(state(PetCommand.SIT, false, false, 42L, null), 4 | 8);
        assertThat(restored.sitting()).isTrue();
        assertThat(restored.sleeping()).isFalse();
        assertThat(restored.sitTick()).isEqualTo(42L);
        assertThat(restored.command()).isEqualTo(PetCommand.SIT);
    }

    @Test
    void aStoredHomeCommandIsRejected() {
        // HOME is never stored: the stored command is RELAX after the HOME step
        assertThatThrownBy(() -> state(PetCommand.HOME, false, false, 0L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
