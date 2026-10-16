package dev.tfp.spike.bm01;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import kr.toxicity.model.api.util.function.BonePredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** /bm01 ... (run from RCON). Every subcommand logs to bm01-report.txt with the thread it ran on. */
final class Bm01Command implements BasicCommand {
    private final Bm01Plugin plugin;

    Bm01Command(Bm01Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        String sub = args.length > 0 ? args[0] : "";
        String thread = Thread.currentThread().getName();
        plugin.count("command:" + sub + ":" + thread);
        Report.line("command /bm01 " + String.join(" ", args) + " on thread " + thread);
        try {
            switch (sub) {
                case "spawn" -> spawn(args);
                case "walk" -> walk();
                case "eat" -> eat();
                case "baby" -> baby(args.length > 1 ? args[1] : "entity");
                case "adult" -> adult();
                case "tint" -> tint(args.length > 1 ? args[1] : "A0522D");
                case "part" -> part(args);
                case "mount" -> mount();
                case "attach" -> plugin.forEachCarrier("attach", null,
                        e -> plugin.attach(e, plugin.kindOf(e)));
                case "status" -> status();
                case "report" -> report();
                case "reapply" -> {
                    plugin.reapply = args.length > 1 && "on".equals(args[1]);
                    Report.line("reapply=" + plugin.reapply);
                }
                case "remove" -> remove();
                default -> stack.getSender().sendMessage("usage: /bm01 spawn|walk|eat|baby|adult|tint|part|mount|attach|status|report|reapply|remove");
            }
            stack.getSender().sendMessage("bm01 " + sub + ": dispatched (see bm01-report.txt)");
        } catch (RuntimeException e) {
            Report.line("ERROR in command " + sub + ": " + e);
            stack.getSender().sendMessage("bm01 " + sub + " failed: " + e);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            return List.of("spawn", "walk", "eat", "baby", "adult", "tint", "part", "mount", "attach", "status", "report", "reapply", "remove");
        }
        return List.of();
    }

    private void spawn(String[] args) {
        String kind = args[1];
        if (!Bm01Plugin.COW.equals(kind) && !Bm01Plugin.HORSE.equals(kind)) {
            throw new IllegalArgumentException("kind must be cow or horse");
        }
        World world = Bukkit.getWorlds().get(0);
        Location loc = new Location(world, Double.parseDouble(args[2]), Double.parseDouble(args[3]),
                Double.parseDouble(args[4]));
        plugin.spawnCarrier(kind, loc);
    }

    private void walk() {
        plugin.forEachCarrier("walk", null, e -> {
            if (e instanceof Mob mob) {
                Location target = e.getLocation().clone().add(6, 0, 0);
                boolean ok = mob.getPathfinder().moveTo(target);
                Report.line("walk " + plugin.kindOf(e) + " " + e.getUniqueId() + " moveTo " + target + " ok=" + ok);
            }
        });
    }

    private void eat() {
        plugin.forEachCarrier("eat", Bm01Plugin.COW, e -> {
            EntityTracker t = Bm01Plugin.trackerOf(e);
            boolean ok = t != null && t.animate("eat", AnimationModifier.builder()
                    .override(true).priority(10).type(AnimationIterator.Type.PLAY_ONCE).build());
            Report.line("eat " + e.getUniqueId() + " animate ok=" + ok);
        });
    }

    /**
     * baby entity: setBaby, SCALE attribute untouched, scaler = ModelScaler.entity().
     * baby attr: setBaby, SCALE attribute = 0.5, scaler = entity().
     * baby mult: setBaby, scaler = ModelScaler.entity().multiply(0.5).
     * adult: resets everything.
     */
    private void baby(String mode) {
        plugin.forEachCarrier("baby", Bm01Plugin.COW, e -> {
            Ageable ageable = (Ageable) e;
            ageable.setBaby();
            double attr = "attr".equals(mode) ? 0.5 : 1.0;
            LivingEntity living = (LivingEntity) e;
            living.getAttribute(Attribute.SCALE).setBaseValue(attr);
            e.getPersistentDataContainer().set(plugin.babyKey, PersistentDataType.STRING, mode);
            EntityTracker t = Bm01Plugin.trackerOf(e);
            if (t != null) {
                t.scaler("mult".equals(mode) ? ModelScaler.entity().multiply(0.5f) : ModelScaler.entity());
            }
            Report.line("baby mode=" + mode + " " + e.getUniqueId() + " isBaby=" + !ageable.isAdult() + " attrScale="
                    + Bm01Plugin.attributeScale(e) + " bmScale=" + Bm01Plugin.bmScale(e)
                    + " trackerScaler=" + (t == null ? "none" : t.scaler().name()));
        });
    }

    private void adult() {
        plugin.forEachCarrier("adult", Bm01Plugin.COW, e -> {
            ((Ageable) e).setAdult();
            ((LivingEntity) e).getAttribute(Attribute.SCALE).setBaseValue(1.0);
            e.getPersistentDataContainer().remove(plugin.babyKey);
            EntityTracker t = Bm01Plugin.trackerOf(e);
            if (t != null) {
                t.scaler(ModelScaler.entity());
            }
            Report.line("adult " + e.getUniqueId() + " attrScale=" + Bm01Plugin.attributeScale(e)
                    + " bmScale=" + Bm01Plugin.bmScale(e));
        });
    }

    private void tint(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        plugin.forEachCarrier("tint", null, e -> {
            e.getPersistentDataContainer().set(plugin.tintKey, PersistentDataType.INTEGER, rgb);
            EntityTracker t = Bm01Plugin.trackerOf(e);
            if (t != null) {
                t.update(TrackerUpdateAction.tint(rgb), BonePredicate.name(Bm01Plugin.bodyName(plugin.kindOf(e))).withChildren());
            }
            Report.line("tint 0x" + Integer.toHexString(rgb) + " " + plugin.kindOf(e) + " " + e.getUniqueId()
                    + " tracker=" + (t != null));
        });
    }

    private void part(String[] args) {
        String name = args[1];
        boolean visible = "on".equals(args[2]);
        plugin.forEachCarrier("part", null, e -> {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            Set<String> hidden = new TreeSet<>();
            String raw = pdc.get(plugin.hiddenKey, PersistentDataType.STRING);
            if (raw != null && !raw.isEmpty()) {
                hidden.addAll(Arrays.asList(raw.split(",")));
            }
            if (visible) {
                hidden.remove(name);
            } else {
                hidden.add(name);
            }
            pdc.set(plugin.hiddenKey, PersistentDataType.STRING, String.join(",", hidden));
            EntityTracker t = Bm01Plugin.trackerOf(e);
            if (t != null) {
                t.update(TrackerUpdateAction.togglePart(visible), BonePredicate.name(name).withChildren());
            }
            Report.line("part " + name + " visible=" + visible + " " + plugin.kindOf(e) + " tracker=" + (t != null)
                    + " hiddenPdc=" + hidden);
        });
    }

    private void mount() {
        Player bot = Bukkit.getPlayerExact("Bot");
        if (bot == null) {
            Report.line("mount: no player named Bot online");
            return;
        }
        for (Bm01Plugin.Carrier c : carriersOf(Bm01Plugin.HORSE)) {
            Location near = c.last.clone().add(-2, 0, 0);
            bot.teleportAsync(near).thenAccept(ok -> plugin.onCarrier(c, "mount", horse -> {
                boolean added = horse.addPassenger(bot);
                Report.line("mount: addPassenger(Bot) returned " + added + " vehicle=" + (bot.getVehicle() == null
                        ? "none" : bot.getVehicle().getUniqueId()) + " botY=" + bot.getLocation().getY()
                        + " horseY=" + horse.getLocation().getY() + " botYminusHorseY="
                        + (bot.getLocation().getY() - horse.getLocation().getY()));
            }));
        }
    }

    private void status() {
        plugin.forEachCarrier("status", null, e -> Report.line("status " + plugin.kindOf(e) + " " + e.getUniqueId()
                + " at " + e.getLocation().getBlockX() + " " + e.getLocation().getBlockY() + " " + e.getLocation().getBlockZ()
                + " trackers=" + BetterModel.registry(BukkitAdapter.adapt(e)).map(r -> r.trackers().size()).orElse(0)
                + " attrScale=" + Bm01Plugin.attributeScale(e) + " bmScale=" + Bm01Plugin.bmScale(e)
                + " hiddenPdc=" + e.getPersistentDataContainer().get(plugin.hiddenKey, PersistentDataType.STRING)
                + " tintPdc=" + e.getPersistentDataContainer().get(plugin.tintKey, PersistentDataType.INTEGER)));
    }

    private void report() {
        Report.line("REPORT folia=" + Bm01Plugin.isFolia() + " server=" + Bukkit.getVersion()
                + " modelKeys=" + BetterModel.modelKeys() + " reapply=" + plugin.reapply);
        for (String key : new TreeSet<>(plugin.counts().keySet())) {
            Report.line("REPORT count " + key + " = " + plugin.counts().get(key).get());
        }
        status();
    }

    private void remove() {
        plugin.forEachCarrier("remove", null, e -> {
            BetterModel.registry(BukkitAdapter.adapt(e)).ifPresent(r -> r.close());
            plugin.carriers.remove(e.getUniqueId());
            e.remove();
            Report.line("removed carrier " + e.getUniqueId());
        });
    }

    private List<Bm01Plugin.Carrier> carriersOf(String kind) {
        List<Bm01Plugin.Carrier> out = new ArrayList<>();
        for (Bm01Plugin.Carrier c : plugin.carriers.values()) {
            if (kind.equals(c.kind)) {
                out.add(c);
            }
        }
        return out;
    }
}
