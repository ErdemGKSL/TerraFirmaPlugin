/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onEntityJoinLevel, lines 932-960: enableLightning,
 *       enableLightningStrippingLogs, the 20 % roll, the 11 x 11 x 11 scan with a one in three roll and the radius 5 test),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (lines 319-320, both default true)
 */
package dev.tfp.core.lightning;

import java.util.ArrayList;
import java.util.List;

/**
 * TFC's rules for a lightning bolt that comes into the world: it can be cancelled, and with a 20 % chance it strips the bark
 * off logs around it. Pure: the random draws are passed in, in TFC's order.
 */
public final class LightningRules {
    private LightningRules() {}

    /** TFC {@code level.random.nextFloat() < 0.2f}. */
    public static final float STRIP_CHANCE = 0.2f;

    /** The scan runs from {@code -5} to {@code 5} on every axis. */
    public static final int RADIUS = 5;

    /** TFC {@code x * x + y * y + z * z <= 25}. */
    public static final int RADIUS_SQUARED = 25;

    /** The scan takes a cell when {@code nextInt(3) == 0}. */
    public static final int CELL_ODDS = 3;

    /** The cells of the 11 x 11 x 11 box that lie within the radius: 515. */
    public static final int CELLS_IN_RADIUS = 515;

    /** The server options (TFC {@code enableLightning}, {@code enableLightningStrippingLogs}); both default true. */
    public record Config(boolean enableLightning, boolean enableLightningStrippingLogs) {
        public static final Config DEFAULTS = new Config(true, true);
    }

    /** The two draws TFC makes from {@code level.random}. */
    public interface Draws {
        float nextFloat();

        int nextInt(int bound);
    }

    /** One offset from the bolt's block. */
    public record Offset(int x, int y, int z) {
    }

    /** False when the bolt is cancelled (TFC cancels the join). */
    public static boolean allowed(Config config) {
        return config.enableLightning();
    }

    /**
     * The offsets whose logs TFC strips. Draw order: one {@code nextFloat} for the 20 % roll; when it passes, one
     * {@code nextInt(3)} for every cell of the box in x, then y, then z order (the radius test comes after the draw, so every
     * cell draws), and an offset is returned when the draw is 0 and it lies within the radius. Empty when the option is off or
     * the roll fails (the box is then not drawn at all).
     */
    public static List<Offset> strippedOffsets(Config config, Draws draws) {
        if (!config.enableLightningStrippingLogs() || !(draws.nextFloat() < STRIP_CHANCE)) {
            return List.of();
        }
        List<Offset> out = new ArrayList<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (draws.nextInt(CELL_ODDS) == 0 && x * x + y * y + z * z <= RADIUS_SQUARED) {
                        out.add(new Offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /**
     * The stripped block id of a log-like block, or null. TFC's {@code LogBlock} strips {@code LOG} and {@code WOOD} to
     * {@code STRIPPED_LOG} and {@code STRIPPED_WOOD} of the same wood; vanilla's axe strips its own logs, woods, stems, hyphae
     * and bamboo blocks. A small own table until PAR-GATHER-08 provides the shared one (ledger divergence).
     */
    public static String stripped(String blockId) {
        if (blockId.startsWith("tfp:wood/log/")) {
            return "tfp:wood/stripped_log/" + blockId.substring("tfp:wood/log/".length());
        }
        if (blockId.startsWith("tfp:wood/wood/")) {
            return "tfp:wood/stripped_wood/" + blockId.substring("tfp:wood/wood/".length());
        }
        if (blockId.startsWith("minecraft:") && !blockId.startsWith("minecraft:stripped_")) {
            String name = blockId.substring("minecraft:".length());
            if (name.equals("bamboo_block") || name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")
                    || name.endsWith("_hyphae")) {
                return "minecraft:stripped_" + name;
            }
        }
        return null;
    }
}
