/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PatchouliMarkupTest {
    private static List<GuideRun> parse(String s) {
        return PatchouliMarkup.parse(s, GuideStyle.PLAIN, "tfp");
    }

    @Test
    void thingAndResetColourASpan() {
        // TFC firepit.json, first page
        List<GuideRun> runs = parse("$(thing)Fire$() is an important technological advancement.");
        assertThat(runs).containsExactly(
            new GuideRun.Text("Fire", GuideStyle.PLAIN.withColor(PatchouliMarkup.THING_COLOR)),
            new GuideRun.Text(" is an important technological advancement.", GuideStyle.PLAIN));
    }

    @Test
    void keybindIsTheClientsKeyName() {
        List<GuideRun> runs = parse("hold $(item)$(k:key.use)$() down");
        assertThat(runs).containsExactly(
            new GuideRun.Text("hold ", GuideStyle.PLAIN),
            new GuideRun.Keybind("key.use", GuideStyle.PLAIN.withColor(PatchouliMarkup.ITEM_COLOR)),
            new GuideRun.Text(" down", GuideStyle.PLAIN));
    }

    @Test
    void linksTargetEntriesAndUrlsAndDropAnchors() {
        List<GuideRun> runs = parse("$(l:core_mechanics/heating#temp)heated$() and $(l:https://en.wikipedia.org/wiki/Mafic)mafic$(/l).");
        GuideRun.Text heated = (GuideRun.Text) runs.get(0);
        assertThat(heated.style().link()).isEqualTo("entry:tfp:core_mechanics/heating");
        assertThat(heated.style().color()).isEqualTo(PatchouliMarkup.LINK_COLOR);
        GuideRun.Text mafic = (GuideRun.Text) runs.get(2);
        assertThat(mafic.style().link()).isEqualTo("url:https://en.wikipedia.org/wiki/Mafic");
        assertThat(((GuideRun.Text) runs.get(3)).style()).isEqualTo(GuideStyle.PLAIN);
    }

    @Test
    void breaksBulletsAndBoldMacros() {
        List<GuideRun> runs = parse("A$(br2)$(bold)B$()$(li)one$(li)two$(br)C");
        assertThat(PatchouliMarkup.plain(runs)).isEqualTo("A\n\nB\n• one\n• two\nC");
        assertThat(((GuideRun.Text) runs.get(3)).style().bold()).isTrue();
    }

    @Test
    void bareLIsBoldNotALink() {
        GuideRun.Text t = (GuideRun.Text) parse("$(l)Bold").getFirst();
        assertThat(t.style().bold()).isTrue();
        assertThat(t.style().link()).isNull();
    }

    @Test
    void chatAndHexColoursAndTooltips() {
        List<GuideRun> runs = parse("$(c)red$(#FFDD00)gold$(#490)short$(t:Yellow)tip$(/t)x");
        assertThat(((GuideRun.Text) runs.get(0)).style().color()).isEqualTo(0xFF5555);
        assertThat(((GuideRun.Text) runs.get(1)).style().color()).isEqualTo(0xFFDD00);
        assertThat(((GuideRun.Text) runs.get(2)).style().color()).isEqualTo(0x449900);
        assertThat(((GuideRun.Text) runs.get(3)).style().tooltip()).isEqualTo("Yellow");
        assertThat(((GuideRun.Text) runs.get(4)).style().tooltip()).isNull();
    }

    @Test
    void unknownCodesAreDroppedAndUnclosedIsText() {
        assertThat(PatchouliMarkup.plain(parse("a$(playername)b $(oops"))).isEqualTo("ab $(oops");
    }
}
