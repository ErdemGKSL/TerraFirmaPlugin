/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import dev.tfp.core.json.Codec;

/**
 * How one directory of data files becomes one registry.
 *
 * @param key        the registry; its name is also the reference name for its entries
 * @param directory  directory below the namespace: {@code recipe}, or for TFC definitions the path
 *                   below {@code data/<ns>/<tfc|tfp>/} (e.g. {@code item_heat}, {@code worldgen/rock_settings})
 * @param definition whether this lives under {@code data/<ns>/<tfc|tfp>/} (TFC's {@code DataManager}s)
 * @param codec      decoder for one file
 */
public record DataKind<T>(RegistryKey<T> key, String directory, boolean definition, Codec<T> codec) {
}
