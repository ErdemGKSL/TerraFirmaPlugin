/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java @ c3b4d9536 (getModelFromBlockState: the climate
 *       at the block, the viewer's hemisphere, the calendar)
 */
package dev.tfp.paper.plant.lifecycle;

import dev.tfp.core.plant.lifecycle.PlantLifecycle;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * PAR-WORLD-61: maps a plant's server block state to its dormant look's state at packet time, per viewer, the way TFC's
 * {@code tfc:plant} model loader picks a stage on the client. A base state with a dormant look shows it when
 * {@link PlantLifecycle#stage} gives {@code DORMANT} or {@code DYING} at the block (the other four stages draw the blooming
 * look, ARCHITECTURE.md section 9). Never touches the world: the packet layer rewrites the ids it sends.
 *
 * <p>Ids are the server's block state ids as the packet layer sees them, before CraftEngine maps them to client states;
 * the dormant look is a state of the never-placed {@code tfp:plant/dormant_look} block, so CraftEngine then sends its look.
 * Pure and immutable; any thread (Netty threads).
 */
public final class PlantStageRemap {
    /** The viewer's side of the decision: the calendar and the viewer's hemisphere ({@code ClientHelpers}). */
    public record View(float fractionOfYear, long dayTime, boolean northernHemisphere) {
    }

    /** The climate at a block ({@code Climate.getRainfallVariance}, {@code Climate.getAverageTemperature}). */
    public interface ColumnClimate {
        float rainfallVariance(int x, int z);

        float averageTemperature(int x, int y, int z);
    }

    /** One base id: its plant's bloom fields and the dormant look's id. */
    record Target(PlantLifecycle.Params params, int dormantId) {
    }

    public static final PlantStageRemap EMPTY = new PlantStageRemap(new Target[0], 0);

    private final Target[] byId;
    private final int count;

    private PlantStageRemap(Target[] byId, int count) {
        this.byId = byId;
        this.count = count;
    }

    /**
     * Resolves the table's CE states to server ids. {@code serverId} gives a CE state's server id, or a negative value when
     * it is unknown (the block is not loaded); such rows are left out.
     */
    public static PlantStageRemap resolve(PlantStageTable table, ToIntFunction<String> serverId) {
        int max = -1;
        int[][] pairs = new int[table.entries().size()][];
        PlantLifecycle.Params[] params = new PlantLifecycle.Params[pairs.length];
        int n = 0;
        for (Map.Entry<String, PlantStageTable.Entry> e : table.entries().entrySet()) {
            int base = serverId.applyAsInt(e.getKey());
            int dormant = serverId.applyAsInt(e.getValue().dormantState());
            if (base < 0 || dormant < 0 || base == dormant) {
                continue;
            }
            pairs[n] = new int[] {base, dormant};
            params[n] = e.getValue().params();
            max = Math.max(max, base);
            n++;
        }
        Target[] byId = new Target[max + 1];
        for (int i = 0; i < n; i++) {
            byId[pairs[i][0]] = new Target(params[i], pairs[i][1]);
        }
        return new PlantStageRemap(byId, n);
    }

    /** The base ids this remap rewrites. */
    public int size() {
        return count;
    }

    /** Whether {@code id} is a base state with a dormant look (a palette check before a section is decoded). */
    public boolean rewrites(int id) {
        return id >= 0 && id < byId.length && byId[id] != null;
    }

    /** {@code DORMANT} and {@code DYING} draw the dormant look; the other stages the blooming one. */
    public static boolean dormantLook(PlantLifecycle.Stage stage) {
        return stage == PlantLifecycle.Stage.DORMANT || stage == PlantLifecycle.Stage.DYING;
    }

    /** The id to send for server state {@code id} at block {@code (x, y, z)}. */
    public int block(int id, int x, int y, int z, ColumnClimate climate, View view) {
        if (!rewrites(id)) {
            return id;
        }
        Target t = byId[id];
        PlantLifecycle.Stage stage = PlantLifecycle.stage(t.params(), x, z,
            t.params().wetSeasonBlooming() ? climate.rainfallVariance(x, z) : 0f,
            t.params().wetSeasonBlooming() ? 0f : climate.averageTemperature(x, y, z),
            view.northernHemisphere(), view.fractionOfYear(), view.dayTime());
        return dormantLook(stage) ? t.dormantId() : id;
    }

    /**
     * Rewrites one chunk section's 4096 state ids in place (vanilla order: index {@code (y * 16 + z) * 16 + x}).
     *
     * @return whether any id changed
     */
    public boolean section(int[] states, int originX, int originY, int originZ, ColumnClimate climate, View view) {
        if (states.length != 4096) {
            throw new IllegalArgumentException("a section holds 4096 states, not " + states.length);
        }
        boolean changed = false;
        for (int i = 0; i < 4096; i++) {
            int id = states[i];
            if (!rewrites(id)) {
                continue;
            }
            int x = originX + (i & 15);
            int z = originZ + (i >> 4 & 15);
            int y = originY + (i >> 8);
            int to = block(id, x, y, z, climate, view);
            if (to != id) {
                states[i] = to;
                changed = true;
            }
        }
        return changed;
    }
}
