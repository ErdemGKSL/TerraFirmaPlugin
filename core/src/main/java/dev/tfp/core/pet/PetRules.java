/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/TamableMammal.java (willListenTo, receiveCommand,
 * mobInteract, addAdditionalSaveData flags), src/main/java/net/dries007/tfc/common/entities/livestock/pet/TFCCat.java
 * (willListenTo), src/main/java/net/dries007/tfc/common/entities/ai/pet/TamableAi.java (wantsToStopSitting, beginIdle),
 * src/main/java/net/dries007/tfc/network/PetCommandPacket.java (handle), src/main/java/net/dries007/tfc/client/screen/
 * PetCommandScreen.java (the menu) @ c3b4d9536
 */
package dev.tfp.core.pet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TFC's pet command rules as pure functions of the stored state, the pet's health, and caller-drawn random rolls. Nothing
 * here ticks, and nothing reads the world. The caller supplies the owner check, the home distance, the hurt flag and the
 * server calendar tick.
 *
 * <p>Out of scope here: the goal AI and navigation, the flee and owner-lost transitions, the taming side effects
 * (particles, advancement), the collar dye, and the packet and screen glue.
 */
public final class PetRules {

    /** TFC {@code TamableMammal.willListenTo}: a SIT is only accepted at or above this health ({@code getHealth() >= 5f}). */
    public static final float SIT_MIN_HEALTH = 5f;

    /** TFC {@code TamableAi.wantsToStopSitting}: a SIT ends once more than this many server ticks have passed. */
    public static final long SIT_TIMEOUT_TICKS = 2000L;

    /** TFC {@code TamableMammal.mobInteract}: the familiarity added before the taming comparison ({@code + 0.06f}). */
    public static final float TAME_FAMILIARITY_BONUS = 0.06f;

    /** TFC {@code TamableMammal.mobInteract}: an unowned animal is tamed above this, after the bonus ({@code > 0.15f}). */
    public static final float TAME_FAMILIARITY_THRESHOLD = 0.15f;

    /** TFC {@code TamableMammal.addAdditionalSaveData}: the sleeping bit of the {@code petFlags} byte. */
    public static final int FLAG_SLEEPING = 1;

    /** TFC {@code TamableMammal.addAdditionalSaveData}: the sitting bit of the {@code petFlags} byte. */
    public static final int FLAG_SITTING = 4;

    /** Why a command was not applied. {@link #NONE} means it was applied (or no command was given). */
    public enum Feedback {
        /** The command was applied. */
        NONE,
        /** The sender is not the owner (TFC {@code tfc.pet.not_owner}). */
        NOT_OWNER,
        /** The pet refuses the command in its state (TFC {@code tfc.pet.will_not_listen}). */
        WILL_NOT_LISTEN
    }

    /**
     * The result of a command or a state change.
     *
     * @param state    the state after the step (unchanged when refused)
     * @param activity the activity to set, or {@code null} when the step did not apply a command
     * @param feedback why the command was refused, or {@link Feedback#NONE}
     */
    public record Outcome(PetState state, PetActivity activity, Feedback feedback) {
    }

    /**
     * Who sent a command: whether they own the pet, and where they stand (the position {@link PetCommand#HOME} stores).
     *
     * @param owner    {@code true} if the sender is the pet's owner
     * @param position the sender's block position
     */
    public record Sender(boolean owner, PetState.Home position) {
        public Sender {
            Objects.requireNonNull(position, "position");
        }
    }

    private PetRules() {
    }

    /**
     * TFC {@code willListenTo(command, false)}, the server gate that {@code PetCommandPacket.handle} checks first. A SIT
     * needs health of at least {@link #SIT_MIN_HEALTH}, and a cat also refuses a SIT with its roll. Other commands are
     * accepted whatever the health and the roll.
     *
     * @param sitRoll a value in [0, 1) drawn by the caller; only read for a SIT
     */
    public static boolean listensOnServer(PetSpecies species, PetCommand command, float health, float sitRoll) {
        if (command == PetCommand.SIT && species.refusesSit(sitRoll)) {
            return false;
        }
        return listensInMenu(command, health);
    }

    /**
     * TFC {@code willListenTo(command, true)}: the menu check, with no roll. A client shows a command when this is true.
     * The base rule for both species: a SIT needs health of at least {@link #SIT_MIN_HEALTH}.
     */
    public static boolean listensInMenu(PetCommand command, float health) {
        return command != PetCommand.SIT || health >= SIT_MIN_HEALTH;
    }

    /**
     * The commands the menu shows, in TFC's enum order (TFC {@code PetCommandScreen}: every command the menu check accepts).
     */
    public static List<PetCommand> menu(float health) {
        final List<PetCommand> shown = new ArrayList<>();
        for (PetCommand command : PetCommand.values()) {
            if (listensInMenu(command, health)) {
                shown.add(command);
            }
        }
        return List.copyOf(shown);
    }

    /**
     * TFC {@code PetCommandPacket.handle} with {@code receiveCommand}: the server gate first, then the owner check, then
     * the command. A refused command leaves the state unchanged.
     *
     * @param before  the state before the command
     * @param species the pet's species, for the cat's SIT refusal
     * @param health  the pet's health
     * @param sitRoll a value in [0, 1) drawn by the caller; only read for a SIT
     * @param sender  who sent the command
     * @param command the command
     * @param now     the server calendar tick, which a SIT records
     */
    public static Outcome handle(PetState before, PetSpecies species, float health, float sitRoll, Sender sender, PetCommand command, long now) {
        if (!listensOnServer(species, command, health, sitRoll)) {
            return new Outcome(before, null, Feedback.WILL_NOT_LISTEN);
        }
        if (!sender.owner()) {
            return new Outcome(before, null, Feedback.NOT_OWNER);
        }
        return receive(before, command, now, sender.position());
    }

    /**
     * TFC {@code receiveCommand} once the owner check has passed. Each command's effect on the flags and memories, then the
     * stored command, then the activity. {@link PetCommand#HOME} stores {@link PetCommand#RELAX} and sets the home. Its
     * activity is then the one RELAX selects, {@link PetActivity#IDLE_AT_HOME}, because TFC reassigns the parameter before
     * it looks the activity up.
     */
    private static Outcome receive(PetState before, PetCommand command, long now, PetState.Home position) {
        boolean sitting = before.sitting();
        boolean sleeping = before.sleeping();
        long sitTick = before.sitTick();
        PetState.Home home = before.home();
        PetCommand stored = command;
        switch (command) {
            case RELAX -> {
                sitting = false;
                sleeping = false;
            }
            case SIT -> {
                sitting = true;
                sleeping = false;
                sitTick = now;
            }
            case HOME -> {
                home = position;
                stored = PetCommand.RELAX;
            }
            case FOLLOW, HUNT -> {
                sitting = false;
                sleeping = false;
                sitTick = PetState.NO_SIT_TICK;
            }
        }
        final PetState after = new PetState(stored, sitting, sleeping, sitTick, home);
        return new Outcome(after, stored.activity(), Feedback.NONE);
    }

    /**
     * TFC {@code TamableAi.wantsToStopSitting}: a hurt pet stops sitting at once, and any pet stops after more than
     * {@link #SIT_TIMEOUT_TICKS} since its SIT began. The caller only asks while the pet is in the sit activity.
     *
     * @param hurtRecently {@code true} if the pet has a hurt-by memory
     * @param now          the server calendar tick
     */
    public static boolean shouldStopSitting(PetState state, boolean hurtRecently, long now) {
        if (hurtRecently) {
            return true;
        }
        return state.sitTick() != PetState.NO_SIT_TICK && now > state.sitTick() + SIT_TIMEOUT_TICKS;
    }

    /**
     * TFC {@code TamableAi.beginIdle}: stop sitting and sleeping, store {@link PetCommand#RELAX}, and idle at home when near
     * it, otherwise idle in the vanilla idle activity. The SIT tick is not cleared (TFC does not erase it here).
     *
     * @param nearHome {@code true} if the pet is within the home distance (TFC {@code !farFromHome})
     */
    public static Outcome stopSitting(PetState state, boolean nearHome) {
        final PetActivity activity = nearHome ? PetActivity.IDLE_AT_HOME : PetActivity.IDLE;
        final PetState after = new PetState(PetCommand.RELAX, false, false, state.sitTick(), state.home());
        return new Outcome(after, activity, Feedback.NONE);
    }

    /**
     * TFC {@code TamableMammal.mobInteract}: an unowned animal that is hungry and holds food is tamed when
     * {@code familiarity + 0.06f > 0.15f}. The familiarity is the caller's current, decay-caught-up value.
     *
     * <p>The float arithmetic is the same as TFC's, so the comparison is done in {@code float}.
     */
    public static boolean isTameable(float familiarity, boolean hasOwner, boolean holdingFood, boolean hungry) {
        return familiarity + TAME_FAMILIARITY_BONUS > TAME_FAMILIARITY_THRESHOLD && !hasOwner && holdingFood && hungry;
    }

    /**
     * TFC {@code TamableMammal.mobInteract}: sneaking with an empty hand opens the command menu for the owner, unless the pet
     * is on fire. The owner check is {@code isOwnedBy(player)}, which already implies an owner is online.
     */
    public static boolean canOpenMenu(boolean handEmpty, boolean sneaking, boolean senderIsOwner, boolean onFire) {
        return handEmpty && sneaking && senderIsOwner && !onFire;
    }

    /**
     * TFC {@code addAdditionalSaveData}: the {@code petFlags} byte, with the sleeping bit (1) and the sitting bit (4). The
     * interested bit (8) is recomputed by TFC's AI each pass, so it is not part of this state and is written as 0.
     */
    public static int packFlags(PetState state) {
        int flags = 0;
        if (state.sleeping()) {
            flags |= FLAG_SLEEPING;
        }
        if (state.sitting()) {
            flags |= FLAG_SITTING;
        }
        return flags;
    }

    /**
     * TFC {@code readAdditionalSaveData}: sets sleeping and sitting from the {@code petFlags} byte, keeping the rest of the
     * state. The interested bit (8) is ignored, as {@link #packFlags} does not write it.
     */
    public static PetState unpackFlags(PetState state, int flags) {
        return new PetState(state.command(), (flags & FLAG_SITTING) != 0, (flags & FLAG_SLEEPING) != 0, state.sitTick(), state.home());
    }
}
