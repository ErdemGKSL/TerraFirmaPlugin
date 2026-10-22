/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.clock;
import static dev.tfp.paper.tooltip.NbtStacks.custom;
import static dev.tfp.paper.tooltip.NbtStacks.plain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import dev.tfp.paper.tooltip.NbtStacks.Access;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-05: ordering, the one marker, idempotent strip, creative return with two contributors, base style. */
class TooltipPipelineTest {
    /** A contributor that adds fixed lines (with the client language appended). */
    private record Fixed(String id, int order, List<String> texts) implements TooltipContributor {
        Fixed(String id, int order, String... texts) {
            this(id, order, List.of(texts));
        }

        @Override
        public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
            for (String t : texts) {
                lines.accept(Component.text(t + "@" + context.locale()));
            }
        }
    }

    private static TooltipPipeline pipeline(TooltipContributor... cs) {
        TooltipPipeline p = new TooltipPipeline(() -> clock(100L), null);
        for (TooltipContributor c : cs) {
            p.add(c);
        }
        return p;
    }

    private static List<String> texts(List<Component> lines) {
        return lines.stream().map(NbtStacks::plain).toList();
    }

    @Test
    void linesFollowTheOrderThenTheIdWhateverTheRegistrationOrder() {
        TooltipPipeline p = pipeline(new Fixed("zeta", 500, "heat"), new Fixed("food", 300, "food"),
                new Fixed("alpha", 500, "alpha"), new Fixed("size", 100, "size"));
        assertThat(p.ids()).containsExactly("size", "food", "alpha", "zeta");
        assertThat(texts(p.lines(custom("tfp:x").stack().item(), p.context("en_us"))))
                .containsExactly("size@en_us", "food@en_us", "alpha@en_us", "heat@en_us");
    }

    @Test
    void aContributorWithTheSameIdReplacesTheEarlierOneAndCanBeRemoved() {
        TooltipPipeline p = pipeline(new Fixed("heat", 500, "old"), new Fixed("food", 300, "food"));
        p.add(new Fixed("heat", 500, "new"));
        assertThat(texts(p.lines(custom("tfp:x").stack().item(), p.context("en_us"))))
                .containsExactly("food@en_us", "new@en_us");
        assertThat(p.remove("food")).isTrue();
        assertThat(p.remove("food")).isFalse();
        assertThat(p.ids()).containsExactly("heat");
        assertThatThrownBy(() -> p.add(new Fixed("Bad Id", 1, "x"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theClientLanguageOfThePacketReachesEveryContributor() {
        TooltipPipeline p = pipeline(new Fixed("a", 1, "x"), new Fixed("b", 2, "y"));
        Access a = custom("tfp:x");
        assertThat(p.decorate(a.stack(), p.context("tr_tr"))).isTrue();
        assertThat(texts(a.lore)).containsExactly("x@tr_tr", "y@tr_tr");
    }

    @Test
    void linesAreWhiteAndUprightUnlessTheContributorSaysOtherwise() {
        Component plain = TooltipPipeline.baseStyle(Component.text("a"));
        assertThat(plain.color()).isEqualTo(NamedTextColor.WHITE);
        assertThat(plain.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        Component red = TooltipPipeline.baseStyle(Component.text("b", NamedTextColor.RED));
        assertThat(red.color()).isEqualTo(NamedTextColor.RED);
        assertThat(red.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
        Component italic = TooltipPipeline.baseStyle(Component.text("c").decorate(TextDecoration.ITALIC));
        assertThat(italic.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.TRUE);
    }

    @Test
    void twoContributorsAddTheirLinesUnderOneMarkerAndTheCreativeReturnStripsExactlyThem() {
        TooltipPipeline p = pipeline(new Fixed("food", 300, "expires"), new Fixed("heat", 500, "hot"));
        Access a = custom("tfp:food/mutton");
        a.lore.add(Component.text("Owner lore"));
        NBTCompound before = a.data.copy();

        assertThat(p.decorate(a.stack(), p.context("en_us"))).isTrue();
        assertThat(texts(a.lore)).containsExactly("Owner lore", "expires@en_us", "hot@en_us");
        assertThat(a.data.getNumberTagValueOrNull(TooltipPipeline.MARKER)).isEqualTo(2);

        // the client sends the decorated stack back from the creative inventory
        assertThat(TooltipPipeline.strip(a.stack())).isTrue();
        assertThat(texts(a.lore)).containsExactly("Owner lore");
        assertThat(a.data).isEqualTo(before);
        assertThat(TooltipPipeline.strip(a.stack())).as("a second strip finds nothing").isFalse();
        assertThat(texts(a.lore)).containsExactly("Owner lore");
    }

    @Test
    void aCopyThatIsAlreadyDecoratedGetsNoSecondSetOfLines() {
        TooltipPipeline p = pipeline(new Fixed("food", 300, "expires"), new Fixed("heat", 500, "hot"));
        Access a = custom("tfp:x");
        assertThat(p.decorate(a.stack(), p.context("en_us"))).isTrue();
        assertThat(p.decorate(a.stack(), p.context("en_us"))).isFalse();
        assertThat(a.lore).hasSize(2);
    }

    @Test
    void aVanillaStackWithoutCustomDataIsLeftWithoutOneAfterTheStrip() {
        TooltipPipeline p = pipeline(new Fixed("size", 100, "Tiny"));
        Access a = new Access();
        assertThat(p.decorate(a.stack(), p.context("en_us"))).isTrue();
        assertThat(a.data).isNotNull();
        assertThat(TooltipPipeline.strip(a.stack())).isTrue();
        assertThat(a.data).as("custom_data removed again").isNull();
        assertThat(a.lore).isEmpty();
    }

    @Test
    void noLinesMeansNoChangeAndNoMarker() {
        TooltipPipeline p = pipeline(new TooltipContributor() {
            @Override
            public String id() {
                return "none";
            }

            @Override
            public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
            }
        });
        Access a = custom("tfp:x");
        assertThat(p.decorate(a.stack(), p.context("en_us"))).isFalse();
        assertThat(a.data.getNumberTagValueOrNull(TooltipPipeline.MARKER)).isNull();
        assertThat(pipeline().decorate(a.stack(), p.context("en_us"))).as("empty pipeline").isFalse();
    }

    @Test
    void aStripClampsToTheLinesThatAreStillThere() {
        Access a = custom("tfp:x");
        a.lore.add(Component.text("only"));
        a.stack().setMarker(5);
        assertThat(TooltipPipeline.strip(a.stack())).isTrue();
        assertThat(a.lore).isEmpty();
    }

    @Test
    void aFailingContributorIsLeftOutAndTheOthersStillContribute() {
        TooltipPipeline p = pipeline(new Fixed("a", 1, "x"), new TooltipContributor() {
            @Override
            public String id() {
                return "boom";
            }

            @Override
            public int order() {
                return 2;
            }

            @Override
            public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
                lines.accept(Component.text("half"));
                throw new IllegalStateException("broken");
            }
        }, new Fixed("c", 3, "z"));
        List<Component> lines = new ArrayList<>(p.lines(custom("tfp:x").stack().item(), p.context("en_us")));
        assertThat(texts(lines)).contains("x@en_us", "z@en_us");
        assertThat(p.errors()).isEqualTo(1);
    }

    @Test
    void theItemViewReadsTheCraftEngineIdTheMaterialAndThePdc() {
        Access a = NbtStacks.heated(custom("tfp:metal/ingot/copper"), 900f, 5L);
        a.material = "minecraft:copper_ingot";
        a.amount = 3;
        TooltipItem item = a.stack().item();
        assertThat(item.customId()).isEqualTo("tfp:metal/ingot/copper");
        assertThat(item.id()).isEqualTo("tfp:metal/ingot/copper");
        assertThat(item.material()).isEqualTo("minecraft:copper_ingot");
        assertThat(item.count()).isEqualTo(3);
        assertThat(item.values().compound("tfp:heat").number("tfp:tick")).isEqualTo(5L);
        assertThat(item.values().compound("tfp:heat").string("tfp:tick")).isNull();
        assertThat(item.values().compound("missing")).isNull();
        TooltipItem vanilla = new Access().stack().item();
        assertThat(vanilla.customId()).isNull();
        assertThat(vanilla.id()).isEqualTo("minecraft:paper");
        assertThat(vanilla.values()).isSameAs(TooltipData.EMPTY);
        assertThat(plain(Component.text("x"))).isEqualTo("x");
    }
}
