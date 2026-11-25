/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.guide.FieldGuide;
import dev.tfp.core.guide.GuideBooks;
import dev.tfp.core.guide.GuideLink;
import dev.tfp.paper.testing.MockServerTest;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-04: the field guide item opens the contents book; data parsing; rendering; the book cache. */
class FieldGuideMockBukkitTest extends MockServerTest {
    static final String JSON = """
        {"book": {"name": "Field Guide", "landing_text": "Welcome $(thing)traveller$()!"},
         "categories": [{"id": "tfp:getting_started", "name": "Getting Started", "description": "Start here."}],
         "entries": [
          {"id": "tfp:getting_started/firepit", "category": "tfp:getting_started", "title": "Pits of Fire",
           "content": [{"text": "Hold $(k:key.use) and see $(l:getting_started/firepit)this$()."},
                       {"title": "Firepit", "text": "$(l)Bold$() $(l:https://example.org)web$()"}]}]}
        """;

    static FieldGuide guide() throws Exception {
        return GuideData.read(new StringReader(JSON));
    }

    /** Item ids: a paper stack stands in for tfp:field_guide. */
    static Optional<String> idOf(ItemStack stack) {
        return stack.getType() == Material.PAPER ? Optional.of(FieldGuideListener.ITEM) : Optional.empty();
    }

    @Test
    void dataReadsBookCategoriesAndPages() throws Exception {
        FieldGuide g = guide();
        assertThat(g.name()).isEqualTo("Field Guide");
        assertThat(g.categories().getFirst().entries()).hasSize(1);
        FieldGuide.Entry e = g.entries().getFirst();
        assertThat(e.pages()).extracting(FieldGuide.Page::title).containsExactly(null, "Firepit");
    }

    @Test
    void rendererMapsStylesKeysAndLinks() throws Exception {
        FieldGuide g = guide();
        GuideBooks books = new GuideBooks(g, "tfp");
        List<String> internal = new ArrayList<>();
        GuideBookRenderer r = new GuideBookRenderer(g, t -> {
            internal.add(t);
            return ClickEvent.runCommand("/guide " + t);
        });
        Book book = r.book(books.entry("tfp:getting_started/firepit", 1));
        String flat = flatten(book.pages().getFirst());
        assertThat(flat).contains("Pits of Fire").contains("[key.use]");
        assertThat(internal).containsExactly(GuideLink.INDEX, "entry:tfp:getting_started/firepit");
        List<Component> parts = new ArrayList<>();
        collect(book.pages().getFirst(), parts);
        assertThat(parts).anySatisfy(c -> {
            assertThat(c.clickEvent()).isEqualTo(ClickEvent.openUrl("https://example.org"));
        });
        assertThat(parts).anySatisfy(c -> {
            assertThat(c).isInstanceOf(TextComponent.class);
            assertThat(((TextComponent) c).content()).isEqualTo("Bold");
            assertThat(c.decoration(TextDecoration.BOLD)).isEqualTo(TextDecoration.State.TRUE);
        });
        Book index = r.book(books.index());
        List<Component> idx = new ArrayList<>();
        collect(index.pages().getFirst(), idx);
        assertThat(idx).anySatisfy(c -> assertThat(c.clickEvent()).isEqualTo(ClickEvent.changePage(2)));
        assertThat(idx).noneSatisfy(c -> assertThat(c.color()).isEqualTo(NamedTextColor.WHITE));
    }

    @Test
    void usingTheGuideOpensTheContentsInEveryGameMode() throws Exception {
        List<Player> opened = new ArrayList<>();
        FieldGuideListener listener = new FieldGuideListener(FieldGuideMockBukkitTest::idOf, opened::add);
        PlayerMock player = server.addPlayer();
        for (GameMode mode : new GameMode[] {GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.CREATIVE}) {
            player.setGameMode(mode);
            PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.PAPER),
                null, BlockFace.UP, EquipmentSlot.HAND);
            listener.onUse(e);
            assertThat(e.useItemInHand()).isEqualTo(Event.Result.DENY);
            assertThat(e.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        }
        assertThat(opened).hasSize(3);
    }

    @Test
    void otherItemsLeftClicksAndDeniedUsesAreIgnored() {
        List<Player> opened = new ArrayList<>();
        FieldGuideListener listener = new FieldGuideListener(FieldGuideMockBukkitTest::idOf, opened::add);
        PlayerMock player = server.addPlayer();
        listener.onUse(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.BOOK), null,
            BlockFace.UP, EquipmentSlot.HAND));
        listener.onUse(new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, new ItemStack(Material.PAPER), null,
            BlockFace.UP, EquipmentSlot.HAND));
        listener.onUse(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.UP,
            EquipmentSlot.HAND));
        PlayerInteractEvent denied = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.PAPER),
            null, BlockFace.UP, EquipmentSlot.HAND);
        denied.setUseItemInHand(Event.Result.DENY);
        listener.onUse(denied);
        assertThat(opened).isEmpty();
    }

    @Test
    void serviceCachesBooksAndOpensOnThePlayersThread() throws Exception {
        FieldGuide g = guide();
        AtomicLong now = new AtomicLong();
        List<Runnable> queued = new ArrayList<>();
        int[] renders = {0};
        GuideBookRenderer renderer = new GuideBookRenderer(g, t -> {
            renders[0]++;
            return ClickEvent.runCommand("/x");
        });
        FieldGuideService service = new FieldGuideService(new GuideBooks(g, "tfp"), renderer,
            (p, task) -> queued.add(task), now::get);
        Book first = service.book(GuideLink.INDEX);
        assertThat(service.book(GuideLink.INDEX)).isSameAs(first);
        now.addAndGet(FieldGuideService.REBUILD_NANOS);
        assertThat(service.book(GuideLink.INDEX)).isNotSameAs(first);
        assertThat(service.book("entry:tfp:nope")).isNull();

        PlayerMock player = server.addPlayer();
        assertThat(service.open(player, "entry:tfp:getting_started/firepit")).isTrue();
        assertThat(service.open(player, "entry:tfp:nope")).isFalse();
        assertThat(queued).hasSize(1); // nothing runs until the player's thread takes it
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.children().forEach(child -> collect(child, out));
    }

    private static String flatten(Component c) {
        StringBuilder sb = new StringBuilder();
        List<Component> all = new ArrayList<>();
        collect(c, all);
        for (Component x : all) {
            if (x instanceof TextComponent t) {
                sb.append(t.content());
            } else if (x instanceof net.kyori.adventure.text.KeybindComponent k) {
                sb.append('[').append(k.keybind()).append(']');
            }
        }
        return sb.toString();
    }
}
