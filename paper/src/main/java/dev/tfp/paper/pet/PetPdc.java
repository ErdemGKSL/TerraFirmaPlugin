/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/TamableMammal.java @ c3b4d9536 (addAdditionalSaveData,
 * readAdditionalSaveData: Owner, command, petFlags, CollarColor)
 */
package dev.tfp.paper.pet;

import dev.tfp.core.pet.PetCommand;
import dev.tfp.core.pet.PetRules;
import dev.tfp.core.pet.PetState;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The saved pet data in the carrier's PDC, the plugin's form of TFC's NBT: the owner, the last command, the {@code petFlags}
 * byte (sleeping 1, sitting 4), the SIT tick, the home, the last sleep tick and the collar colour. A pet with no owner has
 * none of these keys (a wild or untamed animal).
 */
final class PetPdc {
    static final NamespacedKey OWNER = new NamespacedKey("tfp", "pet_owner");
    static final NamespacedKey COMMAND = new NamespacedKey("tfp", "pet_command");
    /** Also read by the ambient sound hook: bit 1 is sleeping (TFC {@code TamableMammal.getAmbientSound}). */
    static final NamespacedKey FLAGS = new NamespacedKey("tfp", "pet_flags");
    static final NamespacedKey SIT_TICK = new NamespacedKey("tfp", "pet_sit_tick");
    static final NamespacedKey HOME = new NamespacedKey("tfp", "pet_home");
    static final NamespacedKey LAST_SLEPT = new NamespacedKey("tfp", "pet_last_slept");
    static final NamespacedKey COLLAR = new NamespacedKey("tfp", "pet_collar");

    private PetPdc() {
    }

    static UUID owner(PersistentDataContainer pdc) {
        String s = pdc.get(OWNER, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void setOwner(PersistentDataContainer pdc, UUID owner) {
        if (owner == null) {
            pdc.remove(OWNER);
        } else {
            pdc.set(OWNER, PersistentDataType.STRING, owner.toString());
        }
    }

    /** The saved {@link PetState}; a pet never commanded is {@link PetState#fresh()}. */
    static PetState read(PersistentDataContainer pdc) {
        Integer command = pdc.get(COMMAND, PersistentDataType.INTEGER);
        Byte flags = pdc.get(FLAGS, PersistentDataType.BYTE);
        Long sit = pdc.get(SIT_TICK, PersistentDataType.LONG);
        PetCommand c = PetCommand.RELAX;
        if (command != null) {
            try {
                c = PetCommand.fromOrdinal(command);
            } catch (IllegalArgumentException e) {
                c = PetCommand.RELAX;
            }
            if (c == PetCommand.HOME) {
                c = PetCommand.RELAX; // HOME is never a stored state
            }
        }
        PetState state = new PetState(c, false, false, sit == null ? PetState.NO_SIT_TICK : sit, home(pdc));
        return PetRules.unpackFlags(state, flags == null ? 0 : flags);
    }

    static void write(PersistentDataContainer pdc, PetState state) {
        pdc.set(COMMAND, PersistentDataType.INTEGER, state.command().ordinal());
        pdc.set(FLAGS, PersistentDataType.BYTE, (byte) PetRules.packFlags(state));
        if (state.sitTick() == PetState.NO_SIT_TICK) {
            pdc.remove(SIT_TICK);
        } else {
            pdc.set(SIT_TICK, PersistentDataType.LONG, state.sitTick());
        }
        if (state.home() == null) {
            pdc.remove(HOME);
        } else {
            PetState.Home h = state.home();
            pdc.set(HOME, PersistentDataType.STRING, h.dimension() + "|" + h.x() + "|" + h.y() + "|" + h.z());
        }
    }

    static PetState.Home home(PersistentDataContainer pdc) {
        String s = pdc.get(HOME, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        String[] p = s.split("\\|");
        try {
            return p.length == 4 ? new PetState.Home(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static long lastSlept(PersistentDataContainer pdc) {
        Long l = pdc.get(LAST_SLEPT, PersistentDataType.LONG);
        return l == null ? PetState.NO_SIT_TICK : l;
    }

    static void setLastSlept(PersistentDataContainer pdc, long tick) {
        if (tick == PetState.NO_SIT_TICK) {
            pdc.remove(LAST_SLEPT);
        } else {
            pdc.set(LAST_SLEPT, PersistentDataType.LONG, tick);
        }
    }

    /** The collar colour id (TFC {@code DyeColor.WHITE.getId()} = 0 when never dyed). */
    static int collar(PersistentDataContainer pdc) {
        Integer c = pdc.get(COLLAR, PersistentDataType.INTEGER);
        return c == null ? 0 : c;
    }

    static void setCollar(PersistentDataContainer pdc, int id) {
        pdc.set(COLLAR, PersistentDataType.INTEGER, id);
    }
}
