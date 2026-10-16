/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import java.lang.reflect.InvocationTargetException;

/** Picks the {@link PacketLayer}: PacketEvents when its plugin is installed, otherwise none. */
public final class PacketLayers {
    /** Plugin name of PacketEvents (its plugin.yml). */
    public static final String PACKETEVENTS = "packetevents";

    private PacketLayers() {
    }

    /**
     * @param packetEventsInstalled whether the PacketEvents plugin is installed (loaded; it enables after us)
     */
    public static PacketLayer create(boolean packetEventsInstalled) {
        if (!packetEventsInstalled) {
            return none("PacketEvents not installed: no seasonal grass colours or item tooltips");
        }
        // Reflective, so that this class (and the plugin) still load when PacketEvents is absent.
        try {
            return (PacketLayer) Class.forName(PacketLayers.class.getPackageName() + ".PacketEventsLayer")
                    .getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException | LinkageError e) {
            return none("PacketEvents could not be linked: " + e);
        }
    }

    /** A layer that installs nothing (degraded mode). */
    public static PacketLayer none(String reason) {
        return new PacketLayer() {
            @Override
            public void install(PacketHandlerRegistry registry) {
            }

            @Override
            public void uninstall() {
            }

            @Override
            public boolean installed() {
                return false;
            }

            @Override
            public String describe() {
                return "none (" + reason + ")";
            }
        };
    }
}
