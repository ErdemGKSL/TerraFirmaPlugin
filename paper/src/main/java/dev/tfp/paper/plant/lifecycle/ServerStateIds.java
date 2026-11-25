/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.lifecycle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.block.data.BlockData;

/**
 * PAR-WORLD-63: the server's global block state id of a {@link BlockData}, the id chunk and block-change packets carry
 * before CraftEngine's encoder maps it to a client state (docs/spikes/M0-NET-01.md: {@code pe-encoder-packetevents} runs
 * before {@code craftengine_encoder}). Paper has no API for it, so this reads {@code CraftBlockData.getState()} and
 * {@code Block.getId(BlockState)} (Mojang-mapped at runtime since 1.20.5) once through method handles. The registry is
 * frozen after startup, so any thread.
 */
final class ServerStateIds {
    private static final String BLOCK = "net.minecraft.world.level.block.Block";
    private static final String STATE = "net.minecraft.world.level.block.state.BlockState";

    private final MethodHandle getState;
    private final MethodHandle getId;

    private ServerStateIds(MethodHandle getState, MethodHandle getId) {
        this.getState = getState;
        this.getId = getId;
    }

    /** The resolver for {@code sample}'s implementation class; throws when the server does not expose the methods. */
    static ServerStateIds forServer(BlockData sample) throws ReflectiveOperationException {
        ClassLoader loader = sample.getClass().getClassLoader();
        Class<?> state = Class.forName(STATE, false, loader);
        Class<?> block = Class.forName(BLOCK, false, loader);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> craft = sample.getClass();
        while (craft != null && !craft.getSimpleName().equals("CraftBlockData")) {
            craft = craft.getSuperclass();
        }
        if (craft == null) {
            throw new NoSuchMethodException(sample.getClass() + " is no CraftBlockData");
        }
        MethodHandle getState = lookup.findVirtual(craft, "getState", MethodType.methodType(state));
        MethodHandle getId = lookup.findStatic(block, "getId", MethodType.methodType(int.class, state));
        return new ServerStateIds(getState.asType(MethodType.methodType(Object.class, BlockData.class)),
            getId.asType(MethodType.methodType(int.class, Object.class)));
    }

    /** The global id of {@code data}, or -1 when it cannot be read. */
    int id(BlockData data) {
        try {
            return (int) getId.invokeExact(getState.invokeExact(data));
        } catch (Throwable e) {
            return -1;
        }
    }
}
