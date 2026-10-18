/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/loot_table/entities/*.json @ c3b4d9536
 */
package dev.tfp.core.hunt;

/**
 * One item stack an animal kill yields.
 *
 * @param item  the item id
 * @param count the number of items, above 0
 */
public record HuntYield(String item, int count) {}
