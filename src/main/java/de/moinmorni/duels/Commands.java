package de.moinmorni.duels;

import de.moinmorni.duels.data.Leaderboard;
import de.moinmorni.duels.data.PlayerData;
import de.moinmorni.duels.kit.Ability;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.kit.Loadouts;
import de.moinmorni.duels.match.QueueKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Alle Befehle: /duel, /spectate, /verlassen, /stats, /top, /kit, /warteschlange, /duels (Verwaltung). */
public final class Commands implements CommandExecutor, TabCompleter {

    private static final Set<String> ACCEPT = Set.of("annehmen", "accept", "ja", "yes", "ok");
    private static final Set<String> DENY = Set.of("ablehnen", "deny", "decline", "nein", "no");
    private static final Set<String> LEAVE = Set.of("verlassen", "leave", "raus");
    private static final int TOP = 10;

    private final DuelsPlugin plugin;

    public Commands(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("duels")) {
            admin(sender, args);
            return true;
        }
        if (name.equals("top")) {
            top(sender, args);
            return true;
        }
        if (name.equals("stats")) {
            stats(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Texts.msg("de", "only-players"));
            return true;
        }
        switch (name) {
            case "duel" -> duel(p, args);
            case "spectate" -> spectate(p, args);
            case "verlassen" -> leave(p);
            case "kit" -> kit(p, args);
            case "warteschlange" -> queue(p, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void duel(Player p, String[] args) {
        String lang = plugin.lang(p);
        if (args.length == 0) {
            p.sendMessage(Texts.msg(lang, "duel.usage"));
            return;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        String second = args.length > 1 ? args[1] : null;
        if (ACCEPT.contains(first)) {
            plugin.challenges().accept(p, second);
            return;
        }
        if (DENY.contains(first)) {
            plugin.challenges().deny(p, second);
            return;
        }
        Player target = find(args[0]);
        if (target == null || target.getWorld() != plugin.world()) {
            p.sendMessage(Texts.msg(lang, "duel.not-found", Placeholder.unparsed("name", args[0])));
            return;
        }
        if (second == null) {
            if (target.equals(p)) {
                p.sendMessage(Texts.msg(lang, "duel.self"));
            } else if (!plugin.hall().inHall(p)) {
                p.sendMessage(Texts.msg(lang, "duel.you-busy"));
            } else {
                plugin.menus().openDuel(p, target);
            }
            return;
        }
        Optional<Kit> kit = Kit.parse(second);
        if (kit.isEmpty()) {
            p.sendMessage(Texts.msg(lang, "duel.unknown-kit"));
            return;
        }
        plugin.challenges().challenge(p, target, kit.get());
    }

    private void spectate(Player p, String[] args) {
        String lang = plugin.lang(p);
        if (args.length == 0) {
            p.sendMessage(Texts.msg(lang, "spectate.usage"));
            return;
        }
        Player target = find(args[0]);
        if (target == null) {
            p.sendMessage(Texts.msg(lang, "spectate.not-fighting", Placeholder.unparsed("name", args[0])));
            return;
        }
        plugin.fights().spectate(p, target);
    }

    private void leave(Player p) {
        if (plugin.queues().leave(p.getUniqueId(), true)) {
            return;
        }
        if (!plugin.fights().leave(p)) {
            p.sendMessage(Texts.msg(plugin.lang(p), "fight.nothing"));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        String lang = sender instanceof Player p ? plugin.lang(p) : "de";
        PlayerData d;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Texts.msg(lang, "only-players"));
                return;
            }
            d = plugin.data(p);
        } else {
            Player online = find(args[0]);
            if (online != null) {
                d = plugin.data(online);
            } else {
                Optional<UUID> uuid = plugin.board().byName(args[0]);
                Optional<PlayerData> read = uuid.flatMap(u -> plugin.store().read(u));
                if (read.isEmpty()) {
                    sender.sendMessage(Texts.msg(lang, "stats.unknown", Placeholder.unparsed("name", args[0])));
                    return;
                }
                d = read.get();
            }
        }
        sender.sendMessage(Texts.msg(lang, "stats.header", Placeholder.unparsed("name", d.name())));
        boolean any = false;
        for (Kit kit : Kit.values()) {
            if (!d.played(kit)) {
                continue;
            }
            any = true;
            sender.sendMessage(Component.text(" ").append(Texts.text(lang, "stats.kit",
                    Placeholder.unparsed("kit", Texts.raw(lang, "kit." + kit.id())))));
            for (Component line : plugin.menus().statLines(lang, d, kit)) {
                sender.sendMessage(Component.text("   ").append(line));
            }
        }
        if (!any) {
            sender.sendMessage(Texts.msg(lang, "stats.none", Placeholder.unparsed("name", d.name())));
        }
    }

    private void top(CommandSender sender, String[] args) {
        String lang = sender instanceof Player p ? plugin.lang(p) : "de";
        Kit kit = args.length == 0 ? Kit.KLASSISCH : Kit.parse(args[0]).orElse(null);
        if (kit == null) {
            sender.sendMessage(Texts.msg(lang, "duel.unknown-kit"));
            return;
        }
        sender.sendMessage(Texts.msg(lang, "top.header", Placeholder.unparsed("kit", Texts.raw(lang, "kit." + kit.id()))));
        List<Leaderboard.Row> rows = plugin.board().top(kit, TOP);
        if (rows.isEmpty()) {
            sender.sendMessage(Component.text(" ").append(Texts.text(lang, "stats.top.empty")));
        }
        for (int i = 0; i < rows.size(); i++) {
            Leaderboard.Row r = rows.get(i);
            sender.sendMessage(Component.text(" ").append(Texts.text(lang, "stats.top.line",
                    Placeholder.unparsed("pos", String.valueOf(i + 1)), Placeholder.unparsed("name", r.name()),
                    Placeholder.unparsed("elo", String.valueOf(r.elo())),
                    Placeholder.unparsed("wins", String.valueOf(r.wins())))));
        }
    }

    private void kit(Player p, String[] args) {
        if (args.length == 0) {
            plugin.menus().openEditorChooser(p);
            return;
        }
        Optional<Loadouts.Loadout> l = Kit.parse(args[0])
                .filter(k -> k != Kit.FAEHIGKEITEN)
                .map(k -> Loadouts.of(k, null))
                .or(() -> Ability.parse(args[0]).map(a -> Loadouts.of(Kit.FAEHIGKEITEN, a)));
        if (l.isEmpty()) {
            plugin.menus().openEditorChooser(p);
            return;
        }
        plugin.menus().openEditor(p, l.get());
    }

    private void queue(Player p, String[] args) {
        String lang = plugin.lang(p);
        if (args.length >= 1 && LEAVE.contains(args[0].toLowerCase(Locale.ROOT))) {
            if (!plugin.queues().leave(p.getUniqueId(), true)) {
                p.sendMessage(Texts.msg(lang, "fight.nothing"));
            }
            return;
        }
        if (args.length < 3) {
            p.sendMessage(Texts.msg(lang, "queue.usage"));
            return;
        }
        Optional<Kit> kit = Kit.parse(args[0]);
        Optional<Boolean> ranked = QueueKey.parseRanked(args[1]);
        Optional<Integer> size = QueueKey.parseSize(args[2]);
        if (kit.isEmpty() || ranked.isEmpty() || size.isEmpty()) {
            p.sendMessage(Texts.msg(lang, "queue.usage"));
            return;
        }
        plugin.queues().request(p, new QueueKey(kit.get(), ranked.get(), size.get()));
    }

    private void admin(CommandSender sender, String[] args) {
        String lang = sender instanceof Player p ? plugin.lang(p) : "de";
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> sender.sendMessage(Texts.msg(lang, "admin.info",
                    Placeholder.unparsed("busy", String.valueOf(plugin.arenas().busyCount())),
                    Placeholder.unparsed("total", String.valueOf(plugin.arenas().all().size())),
                    Placeholder.unparsed("fights", String.valueOf(plugin.fights().running())),
                    Placeholder.unparsed("queued", String.valueOf(plugin.queues().totalWaiting())),
                    Placeholder.unparsed("net", plugin.net().connected() ? "ja/yes" : "nein/no")));
            case "neubauen", "rebuild" -> {
                sender.sendMessage(Texts.msg(lang, "admin.rebuild"));
                plugin.rebuild(() -> sender.sendMessage(Texts.msg(lang, "admin.rebuilt")));
            }
            case "neuladen", "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(Texts.msg(lang, "admin.reloaded"));
            }
            default -> sender.sendMessage(Texts.msg(lang, "admin.usage"));
        }
    }

    /** Spieler nach Namen (ohne Groß/Klein). */
    private Player find(String name) {
        Player exact = plugin.getServer().getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    // ---------- Tab-Vorschläge ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = switch (name) {
            case "duel" -> duelOptions(sender, args);
            case "spectate" -> args.length == 1 ? fighters() : List.of();
            case "stats" -> args.length == 1 ? names() : List.of();
            case "top" -> args.length == 1 ? kitIds() : List.of();
            case "kit" -> args.length == 1 ? Loadouts.all().stream()
                    .map(l -> l.ability() == null ? l.kit().id() : l.ability().id()).toList() : List.of();
            case "warteschlange" -> switch (args.length) {
                case 1 -> Stream.concat(kitIds().stream(), Stream.of("verlassen")).toList();
                case 2 -> List.of("locker", "gewertet");
                case 3 -> List.of("1v1", "2v2");
                default -> List.of();
            };
            case "duels" -> args.length == 1 ? List.of("info", "neubauen", "neuladen") : List.of();
            default -> List.of();
        };
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    private List<String> duelOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("annehmen", "ablehnen"));
            for (String n : names()) {
                if (!n.equals(sender.getName())) {
                    list.add(n);
                }
            }
            return list;
        }
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if ((ACCEPT.contains(first) || DENY.contains(first)) && sender instanceof Player p) {
                return plugin.challenges().challengers(p.getUniqueId());
            }
            return kitIds();
        }
        return List.of();
    }

    private List<String> names() {
        return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> fighters() {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> plugin.fights().of(p.getUniqueId()).isPresent())
                .map(Player::getName).toList();
    }

    private static List<String> kitIds() {
        return Stream.of(Kit.values()).map(Kit::id).toList();
    }
}
