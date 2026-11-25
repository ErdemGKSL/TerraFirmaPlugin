/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import dev.tfp.core.guide.FieldGuide;
import dev.tfp.core.guide.GuideBooks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

/**
 * PAR-DATA-04: the field guide as vanilla written books. Loads datagen's {@code content/guide/field_guide.json}, lays the
 * books out once, provides {@link FieldGuideService} and opens the contents book when a player uses
 * {@code tfp:field_guide}. Links between books are click callbacks that open the target book on the player's thread.
 */
public final class FieldGuideModule implements FeatureModule {
    public static final String ID = "field_guide";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        FieldGuide guide = GuideData.load(context.plugin().getClass().getClassLoader());
        GuideBooks books = new GuideBooks(guide, "tfp");
        FieldGuideService[] self = new FieldGuideService[1];
        ClickCallback.Options options = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofNanos(FieldGuideService.CALLBACK_LIFETIME_NANOS))
            .build();
        Function<String, ClickEvent> links = target -> ClickEvent.callback(audience -> {
            if (audience instanceof Player p && self[0] != null) {
                self[0].open(p, target);
            }
        }, options);
        FieldGuideService.Variant english = new FieldGuideService.Variant(books, new GuideBookRenderer(guide, links));
        Map<String, FieldGuideService.Variant> translations = new HashMap<>();
        for (String locale : GuideData.LOCALES) {
            try {
                FieldGuide t = GuideData.load(context.plugin().getClass().getClassLoader(), locale).withFallback(guide);
                translations.put(locale, new FieldGuideService.Variant(new GuideBooks(t, "tfp"), new GuideBookRenderer(t, links)));
            } catch (IOException e) {
                context.logger().warning("field guide: no " + locale + " book (" + e.getMessage() + "), English is used");
            }
        }
        FieldGuideService service = new FieldGuideService(english, translations,
            (player, task) -> context.scheduler().entity(player, task, () -> { }), System::nanoTime);
        self[0] = service;
        context.services().provide(FieldGuideService.class, service);
        context.listen(new FieldGuideListener(context.integrations().customItems()::idOf, service::openIndex));
        context.logger().info("field guide: " + guide.categories().size() + " categories, " + guide.entries().size()
            + " entries, " + books.index().size() + " contents pages, " + translations.size() + " translations");
    }
}
