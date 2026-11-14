/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.calendar;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.bukkit.event.Event;

/**
 * PAR-CLIM-22: reads a time-skip event's reason and amount on every supported API family, without linking against a
 * reason enum that only one family has.
 *
 * <p>The plugin compiles against Paper 1.21.11, where {@code org.bukkit.event.world.TimeSkipEvent} (a
 * {@code WorldEvent}) declares {@code getSkipReason()} returning {@code TimeSkipEvent$SkipReason}. In the 26.x API
 * ({@code javap} of paper-api 26.3.build.244) {@code TimeSkipEvent} extends the new world-less
 * {@code org.bukkit.event.world.ClockTimeSkipEvent}; {@code getSkipReason()} is declared there and returns
 * {@code ClockTimeSkipEvent$SkipReason} (COMMAND, CUSTOM, NIGHT_SKIP), and {@code TimeSkipEvent$SkipReason} is gone,
 * so a direct call links to a method that no longer exists ({@code NoSuchMethodError}). The other members keep their
 * descriptors on both families ({@code javap} of both jars): {@code TimeSkipEvent.getWorld()Lorg/bukkit/World;}
 * (declared on TimeSkipEvent itself on 26.x, inherited from WorldEvent on 1.21.x), {@code getSkipAmount()J} and
 * {@code setSkipAmount(J)V} (inherited from ClockTimeSkipEvent on 26.x), and {@code Cancellable}. Only the reason is
 * read here by reflection; the amount accessors serve the world-less {@code ClockTimeSkipEvent}, which this API does
 * not have as a type.
 *
 * <p>Handles are resolved once per runtime event class and cached. Thread-safe.
 */
public final class TimeSkipReasons {
    /** Our view of a skip reason; the constant names match both APIs' enums. */
    public enum Reason {
        COMMAND, CUSTOM, NIGHT_SKIP,
        /** No reason method, a reason we do not know, or a failed read. Callers leave such a skip alone. */
        UNKNOWN
    }

    private static final MethodType READ_REASON = MethodType.methodType(Object.class, Event.class);
    private static final MethodType READ_AMOUNT = MethodType.methodType(long.class, Event.class);
    private static final MethodType WRITE_AMOUNT = MethodType.methodType(void.class, Event.class, long.class);

    private record Handles(MethodHandle reason, MethodHandle amount, MethodHandle setAmount) { }

    private static final ClassValue<Handles> HANDLES = new ClassValue<>() {
        @Override
        protected Handles computeValue(Class<?> type) {
            return new Handles(find(type, "getSkipReason", READ_REASON),
                    find(type, "getSkipAmount", READ_AMOUNT),
                    find(type, "setSkipAmount", WRITE_AMOUNT, long.class));
        }
    };

    private TimeSkipReasons() {
    }

    /** The reason of {@code event}, {@link Reason#UNKNOWN} where it has none we know. Never throws. */
    public static Reason of(Event event) {
        if (event == null) {
            return Reason.UNKNOWN;
        }
        MethodHandle h = HANDLES.get(event.getClass()).reason();
        if (h == null) {
            return Reason.UNKNOWN;
        }
        Object value;
        try {
            value = h.invokeExact(event);
        } catch (Throwable t) {
            return Reason.UNKNOWN;
        }
        if (!(value instanceof Enum<?> e)) {
            return Reason.UNKNOWN;
        }
        return switch (e.name()) {
            case "COMMAND" -> Reason.COMMAND;
            case "CUSTOM" -> Reason.CUSTOM;
            case "NIGHT_SKIP" -> Reason.NIGHT_SKIP;
            default -> Reason.UNKNOWN;
        };
    }

    /** Whether {@code event} has a readable and writable skip amount. */
    public static boolean hasAmount(Event event) {
        Handles h = HANDLES.get(event.getClass());
        return h.amount() != null && h.setAmount() != null;
    }

    /** {@code getSkipAmount()} of {@code event}; check {@link #hasAmount} first. */
    public static long amount(Event event) {
        MethodHandle h = HANDLES.get(event.getClass()).amount();
        if (h == null) {
            throw new IllegalArgumentException("no getSkipAmount on " + event.getClass().getName());
        }
        try {
            return (long) h.invokeExact(event);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** {@code setSkipAmount(amount)} of {@code event}; check {@link #hasAmount} first. */
    public static void setAmount(Event event, long amount) {
        MethodHandle h = HANDLES.get(event.getClass()).setAmount();
        if (h == null) {
            throw new IllegalArgumentException("no setSkipAmount on " + event.getClass().getName());
        }
        try {
            h.invokeExact(event, amount);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** A public no-argument-or-given-argument instance method as a handle of {@code type}, or null. */
    private static MethodHandle find(Class<?> owner, String name, MethodType type, Class<?>... params) {
        try {
            Method m = owner.getMethod(name, params);
            if (Modifier.isStatic(m.getModifiers())) {
                return null;
            }
            if (type.returnType() != Object.class && m.getReturnType() != type.returnType()) {
                return null;
            }
            MethodHandle h;
            try {
                h = MethodHandles.publicLookup().unreflect(m);
            } catch (IllegalAccessException e) {
                m.setAccessible(true); // a public method of a non-public class
                h = MethodHandles.lookup().unreflect(m);
            }
            return h.asType(type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
