/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil.tooltip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-METAL-21: the forging bonus line with the smith and the "Worked" line, over the real lang file and PDC writers. */
class ForgingTooltipTest extends MockServerTest {
    private final ForgingTooltipContributor contributor = new ForgingTooltipContributor(Messages.fromJar()::get);

    private List<Component> lines(Map<String, Object> values) {
        List<Component> out = new ArrayList<>();
        contributor.contribute(new TooltipItem(null, "minecraft:paper", 1, TooltipData.of(values)),
            new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        return out;
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void everyTierHasTfcsName() {
        Map<String, String> names = Map.of("MODEST", "Modestly Forged", "WELL", "Well Forged", "EXPERT", "Expertly Forged",
            "PERFECT", "Perfectly Forged!");
        names.forEach((tier, name) -> {
            List<Component> l = lines(Map.of("tfp:forging_bonus", tier));
            assertThat(l).hasSize(1);
            assertThat(plain(l.get(0))).isEqualTo(name);
            assertThat(l.get(0).color()).isNull(); // TFC: the bare display name has no colour
        });
    }

    @Test
    void noneAndUnknownShowNothing() {
        assertThat(lines(Map.of())).isEmpty();
        assertThat(lines(Map.of("tfp:forging_bonus", "NONE"))).isEmpty();
        assertThat(lines(Map.of("tfp:forging_bonus", "bogus"))).isEmpty();
        assertThat(lines(Map.of("tfp:forging_author", "Bot"))).isEmpty();
    }

    @Test
    void authorFollowsTheBonusInGrey() {
        List<Component> l = lines(Map.of("tfp:forging_bonus", "PERFECT", "tfp:forging_author", "Bot"));
        assertThat(l).hasSize(1);
        assertThat(plain(l.get(0))).isEqualTo("Perfectly Forged! by Bot");
        TextComponent line = (TextComponent) l.get(0);
        assertThat(line.children()).hasSize(2);
        assertThat(line.children().get(0).color()).isEqualTo(NamedTextColor.GREEN);
        assertThat(line.children().get(1).color()).isEqualTo(NamedTextColor.GRAY);
    }

    @Test
    void workedOnlyWhenAStepWasTaken() {
        assertThat(lines(Map.of("tfp:forging", "tfc:anvil/x;60;0;0;"))).isEmpty();
        List<Component> l = lines(Map.of("tfp:forging", "tfc:anvil/x;60;7;1;HIT_LIGHT"));
        assertThat(l.stream().map(ForgingTooltipTest::plain)).containsExactly("Worked");
        assertThat(lines(Map.of("tfp:forging", "garbage"))).isEmpty();
    }

    @Test
    void bonusComesBeforeWorkedAndSitsAtTfcsOrder() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tfp:forging", "tfc:anvil/x;60;7;2;HIT_LIGHT,DRAW");
        values.put("tfp:forging_bonus", "WELL");
        assertThat(lines(values).stream().map(ForgingTooltipTest::plain)).containsExactly("Well Forged", "Worked");
        assertThat(contributor.order()).isEqualTo(TooltipContributor.ORDER_FORGING)
            .isGreaterThan(TooltipContributor.ORDER_SIZE).isLessThan(TooltipContributor.ORDER_FOOD);
    }

    @Test
    void pdcKeysMatchTheWriters() {
        ItemStack stack = new ItemStack(Material.PAPER);
        dev.tfp.paper.tool.ForgingBonusPdc.write(stack, ForgingQuality.EXPERT, ForgingConfig.DEFAULTS, "Bot");
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.KEY.toString()).isEqualTo(ForgingTooltipContributor.BONUS_KEY);
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.AUTHOR.toString()).isEqualTo(ForgingTooltipContributor.AUTHOR_KEY);
        assertThat(dev.tfp.paper.anvil.plan.ForgingPdc.KEY.toString()).isEqualTo(ForgingTooltipContributor.FORGING_KEY);
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.author(stack)).isEqualTo("Bot");
        ItemStack copy = dev.tfp.paper.tool.ForgingBonusPdc.copy(new ItemStack(Material.PAPER), stack, ForgingConfig.DEFAULTS);
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.read(copy)).isEqualTo(ForgingQuality.EXPERT);
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.author(copy)).isEqualTo("Bot");
        // a rewrite without a smith (welding) drops the old name; NONE removes both
        dev.tfp.paper.tool.ForgingBonusPdc.write(copy, ForgingQuality.WELL, ForgingConfig.DEFAULTS);
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.author(copy)).isNull();
        dev.tfp.paper.tool.ForgingBonusPdc.write(stack, ForgingQuality.NONE, ForgingConfig.DEFAULTS, "Bot");
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.author(stack)).isNull();
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.read(stack)).isEqualTo(ForgingQuality.NONE);
    }

    @Test
    void longNameIsCutToTfcsLimit() {
        ItemStack stack = new ItemStack(Material.PAPER);
        dev.tfp.paper.tool.ForgingBonusPdc.write(stack, ForgingQuality.WELL, ForgingConfig.DEFAULTS, "x".repeat(40));
        assertThat(dev.tfp.paper.tool.ForgingBonusPdc.author(stack)).hasSize(32);
    }
}
