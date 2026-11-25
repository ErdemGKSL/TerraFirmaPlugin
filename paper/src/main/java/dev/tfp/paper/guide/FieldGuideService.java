/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import dev.tfp.core.guide.BookPaginator;
import dev.tfp.core.guide.GuideBooks;
import dev.tfp.core.guide.GuideLink;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import net.kyori.adventure.inventory.Book;
import org.bukkit.entity.Player;

/**
 * Opens the field guide's books for a player (PAR-DATA-04). The books are laid out once ({@link GuideBooks}); the
 * Adventure books (with their click callbacks) are kept per target for {@link #REBUILD_NANOS} and then rebuilt, so the
 * callbacks, which live {@link #CALLBACK_LIFETIME_NANOS}, never expire on a cached book. {@code open} hands the book to
 * the player's own thread ({@code onPlayerThread}); thread-safe.
 */
public final class FieldGuideService {
    /** A cached book is rebuilt after 30 minutes. */
    public static final long REBUILD_NANOS = 30L * 60 * 1_000_000_000L;
    /** Its click callbacks live an hour. */
    public static final long CALLBACK_LIFETIME_NANOS = 2 * REBUILD_NANOS;

    private record Cached(Book book, long builtAt) {
    }

    private final GuideBooks books;
    private final GuideBookRenderer renderer;
    private final BiConsumer<Player, Runnable> onPlayerThread;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public FieldGuideService(GuideBooks books, GuideBookRenderer renderer, BiConsumer<Player, Runnable> onPlayerThread,
                             LongSupplier clock) {
        this.books = books;
        this.renderer = renderer;
        this.onPlayerThread = onPlayerThread;
        this.clock = clock;
    }

    public GuideBooks books() {
        return books;
    }

    /** The book of a target ({@link GuideLink#INDEX} or {@code entry:<id>[@volume]}), or null for an unknown one. */
    public Book book(String target) {
        long now = clock.getAsLong();
        Cached c = cache.get(target);
        if (c != null && now - c.builtAt() < REBUILD_NANOS) {
            return c.book();
        }
        List<BookPaginator.Page> pages = books.book(target);
        if (pages == null) {
            return null;
        }
        Book book = renderer.book(pages);
        cache.put(target, new Cached(book, now));
        return book;
    }

    /** Opens the target's book on the player's thread. Returns false for an unknown target. */
    public boolean open(Player player, String target) {
        Book book = book(target);
        if (book == null) {
            return false;
        }
        onPlayerThread.accept(player, () -> player.openBook(book));
        return true;
    }

    /** Opens the contents book. */
    public void openIndex(Player player) {
        open(player, GuideLink.INDEX);
    }
}
