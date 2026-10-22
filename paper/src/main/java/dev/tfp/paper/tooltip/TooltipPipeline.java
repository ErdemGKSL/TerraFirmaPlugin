/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.clock.ClockSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The ordered list of {@link TooltipContributor}s and the one rule for adding and removing their lines (PAR-INFRA-05).
 *
 * <p>{@link #decorate} appends every contributor's lines, in {@link TooltipContributor#order() order}, to the lore of
 * the packet's copy of a stack and records how many it added in one {@code custom_data} marker ({@value #MARKER}).
 * {@link #strip} removes exactly that many lines from the end and the marker, so a creative-mode client that sends the
 * decorated stack back never turns the lines into real item data, however many contributors added lines. A stack that
 * already carries the marker is left alone (a copy built from a decorated copy).
 *
 * <p>Lines get the base style of TFC's own tooltip lines: white and not italic (a vanilla client draws lore dark purple
 * and italic by default); a contributor's own colours win. Contributors are held in an immutable array behind a
 * volatile field: {@link #add} is rare, {@link #decorate} runs on Netty threads, lock-free. A contributor that throws is
 * counted and logged (rate-limited) and the others still contribute.
 */
public final class TooltipPipeline {
    /** The {@code custom_data} marker: an int, the number of lore lines the pipeline added. */
    public static final String MARKER = "tfp:lore";

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final long ERROR_LOG_EVERY = 1000;
    private static final Comparator<TooltipContributor> ORDER =
            Comparator.comparingInt(TooltipContributor::order).thenComparing(TooltipContributor::id);

    private final Supplier<ClockSnapshot> clock;
    private final Logger log;
    private final LongAdder errors = new LongAdder();
    private volatile TooltipContributor[] contributors = new TooltipContributor[0];

    /**
     * @param clock the global clock (read once per {@link #context})
     * @param log   where failing contributors are reported; may be null (tests)
     */
    public TooltipPipeline(Supplier<ClockSnapshot> clock, Logger log) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = log;
    }

    /**
     * Adds a contributor; one with the same id is replaced (a feature module taking over a default line). Any thread;
     * takes effect for the next stack.
     */
    public synchronized void add(TooltipContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (!ID.matcher(contributor.id()).matches()) {
            throw new IllegalArgumentException("invalid tooltip contributor id: " + contributor.id());
        }
        List<TooltipContributor> list = new ArrayList<>(List.of(contributors));
        list.removeIf(c -> c.id().equals(contributor.id()));
        list.add(contributor);
        list.sort(ORDER);
        contributors = list.toArray(TooltipContributor[]::new);
    }

    /** Removes a contributor by id. @return whether it was there */
    public synchronized boolean remove(String id) {
        List<TooltipContributor> list = new ArrayList<>(List.of(contributors));
        boolean removed = list.removeIf(c -> c.id().equals(id));
        contributors = list.toArray(TooltipContributor[]::new);
        return removed;
    }

    /** The contributor ids in the order their lines appear. */
    public List<String> ids() {
        return List.of(contributors).stream().map(TooltipContributor::id).toList();
    }

    /** True when no contributor is registered (the packet handler then skips all work). */
    public boolean isEmpty() {
        return contributors.length == 0;
    }

    /** Contributor failures so far. */
    public long errors() {
        return errors.sum();
    }

    /** The context for one packet: the viewer's language and one clock reading. */
    public TooltipContext context(String locale) {
        return new TooltipContext(locale, clock.get());
    }

    /** Every contributor's lines for {@code item}, in order, with the base style. */
    public List<Component> lines(TooltipItem item, TooltipContext context) {
        TooltipContributor[] cs = contributors;
        if (cs.length == 0) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(2);
        for (TooltipContributor c : cs) {
            try {
                c.contribute(item, context, line -> out.add(baseStyle(line)));
            } catch (RuntimeException e) {
                errors.increment();
                long n = errors.sum();
                if (log != null && (n == 1 || n % ERROR_LOG_EVERY == 0)) {
                    log.log(Level.SEVERE, "Tooltip contributor " + c.id() + " failed on " + item.id() + " (" + n
                            + " error(s) so far); its lines are left out", e);
                }
            }
        }
        return out;
    }

    /** TFC's tooltip text style on a vanilla client: white and upright unless the line says otherwise. */
    public static Component baseStyle(Component line) {
        return line.colorIfAbsent(NamedTextColor.WHITE)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Appends the contributors' lines to the lore of {@code stack} and sets the marker.
     *
     * @return true if the stack was changed
     */
    public boolean decorate(TooltipStack stack, TooltipContext context) {
        if (contributors.length == 0 || stack.marker() != null) {
            return false;
        }
        List<Component> added = lines(stack.item(), context);
        if (added.isEmpty()) {
            return false;
        }
        List<Component> lore = new ArrayList<>(stack.lore());
        lore.addAll(added);
        stack.setLore(lore);
        stack.setMarker(added.size());
        return true;
    }

    /**
     * Removes what {@link #decorate} added: the marker and that many lines from the end of the lore.
     *
     * @return true if the stack carried the marker
     */
    public static boolean strip(TooltipStack stack) {
        Number added = stack.marker();
        if (added == null) {
            return false;
        }
        stack.clearMarker();
        List<Component> lore = new ArrayList<>(stack.lore());
        int n = Math.max(0, Math.min(added.intValue(), lore.size()));
        lore.subList(lore.size() - n, lore.size()).clear();
        stack.setLore(lore);
        return true;
    }
}
