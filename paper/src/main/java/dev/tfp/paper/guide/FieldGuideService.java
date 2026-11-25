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

    /** The default (English) book locale. */
    public static final String ENGLISH = "en_us";

    private record Cached(Book book, long builtAt) {
    }

    /** One locale's laid-out books and the renderer of their pages. */
    public record Variant(GuideBooks books, GuideBookRenderer renderer) {
    }

    private final Map<String, Variant> variants;
    private final BiConsumer<Player, Runnable> onPlayerThread;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public FieldGuideService(GuideBooks books, GuideBookRenderer renderer, BiConsumer<Player, Runnable> onPlayerThread,
                             LongSupplier clock) {
        this(new Variant(books, renderer), Map.of(), onPlayerThread, clock);
    }

    /** {@code english} is the default; {@code translations} are the books by locale key ({@code zh_cn}). */
    public FieldGuideService(Variant english, Map<String, Variant> translations, BiConsumer<Player, Runnable> onPlayerThread,
                             LongSupplier clock) {
        Map<String, Variant> all = new java.util.HashMap<>(translations);
        all.put(ENGLISH, english);
        this.variants = Map.copyOf(all);
        this.onPlayerThread = onPlayerThread;
        this.clock = clock;
    }

    public GuideBooks books() {
        return variants.get(ENGLISH).books();
    }

    /**
     * The book locale for a client locale: its {@code language_country} in lower case when there is such a book
     * ({@code zh_CN} is {@code zh_cn}), else the first book of the same language ({@code pt_PT} gets {@code pt_br}), else
     * English.
     */
    public String localeKey(java.util.Locale locale) {
        if (locale == null) {
            return ENGLISH;
        }
        String exact = locale.toString().toLowerCase(java.util.Locale.ROOT);
        if (variants.containsKey(exact)) {
            return exact;
        }
        String language = locale.getLanguage().toLowerCase(java.util.Locale.ROOT);
        return variants.keySet().stream().filter(k -> k.startsWith(language + "_")).sorted().findFirst().orElse(ENGLISH);
    }

    /** The English book of a target ({@link GuideLink#INDEX} or {@code entry:<id>[@volume]}), or null for an unknown one. */
    public Book book(String target) {
        return book(ENGLISH, target);
    }

    /** The book of a target in a locale ({@link #localeKey}); English for a locale without books. */
    public Book book(String locale, String target) {
        Variant variant = variants.getOrDefault(locale, variants.get(ENGLISH));
        String key = variants.containsKey(locale) ? locale : ENGLISH;
        String cacheKey = key + "|" + target;
        long now = clock.getAsLong();
        Cached c = cache.get(cacheKey);
        if (c != null && now - c.builtAt() < REBUILD_NANOS) {
            return c.book();
        }
        List<BookPaginator.Page> pages = variant.books().book(target);
        if (pages == null) {
            return null;
        }
        Book book = variant.renderer().book(pages);
        cache.put(cacheKey, new Cached(book, now));
        return book;
    }

    /** Opens the target's book in the player's client language on the player's thread. Returns false for an unknown target. */
    public boolean open(Player player, String target) {
        Book book = book(localeKey(player.locale()), target);
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
