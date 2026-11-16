package net.morni.duels;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.morni.duels.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Herausforderungen mit /duel: zum Anklicken annehmen oder ablehnen, nach einer Minute verfallen sie. Bedrock-Spieler
 * bekommen ein Menü, weil sie im Chat nichts anklicken können. Herausforderungen sind immer locker (ungewertet).
 */
public final class Challenges {

    private record Challenge(UUID from, String fromName, UUID to, String toName, Kit kit, long expires) {
    }

    private final DuelsPlugin plugin;
    /** Herausgeforderter → (Herausforderer → Herausforderung). */
    private final Map<UUID, Map<UUID, Challenge>> byTarget = new HashMap<>();

    public Challenges(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void challenge(Player from, Player to, Kit kit) {
        String lang = plugin.lang(from);
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(Texts.msg(lang, "duel.self"));
            return;
        }
        if (!plugin.ready()) {
            from.sendMessage(Texts.msg(lang, "not-ready"));
            return;
        }
        if (!plugin.hall().inHall(from)) {
            from.sendMessage(Texts.msg(lang, "duel.you-busy"));
            return;
        }
        if (!plugin.hall().inHall(to)) {
            from.sendMessage(Texts.msg(lang, "duel.busy", Placeholder.unparsed("name", to.getName())));
            return;
        }
        Map<UUID, Challenge> open = byTarget.computeIfAbsent(to.getUniqueId(), k -> new HashMap<>());
        Challenge old = open.get(from.getUniqueId());
        if (old != null && old.kit() == kit) {
            from.sendMessage(Texts.msg(lang, "duel.already", Placeholder.unparsed("name", to.getName())));
            return;
        }
        int secs = plugin.settings().challengeSeconds();
        open.put(from.getUniqueId(), new Challenge(from.getUniqueId(), from.getName(), to.getUniqueId(), to.getName(),
                kit, System.currentTimeMillis() + secs * 1000L));
        from.sendMessage(Texts.msg(lang, "duel.sent", Placeholder.unparsed("name", to.getName()),
                Placeholder.unparsed("kit", Texts.raw(lang, "kit." + kit.id())),
                Placeholder.unparsed("secs", String.valueOf(secs))));
        String tl = plugin.lang(to);
        to.sendMessage(Texts.msg(tl, "duel.received", Placeholder.unparsed("name", from.getName()),
                Placeholder.unparsed("kit", Texts.raw(tl, "kit." + kit.id()))));
        Component accept = Texts.text(tl, "duel.accept.button")
                .clickEvent(ClickEvent.runCommand("/duel annehmen " + from.getName()))
                .hoverEvent(HoverEvent.showText(Texts.text(tl, "duel.accept.hover")));
        Component deny = Texts.text(tl, "duel.deny.button")
                .clickEvent(ClickEvent.runCommand("/duel ablehnen " + from.getName()))
                .hoverEvent(HoverEvent.showText(Texts.text(tl, "duel.deny.hover")));
        to.sendMessage(Texts.msg(tl, "duel.buttons", Placeholder.component("accept", accept),
                Placeholder.component("deny", deny), Placeholder.unparsed("name", from.getName())));
        Ui.sound(to, "block.note_block.chime", 1.0f);
        if (Ui.bedrock(to.getUniqueId()) && plugin.hall().inHall(to)) {
            plugin.menus().openChallenge(to, from, kit);
        }
    }

    public void accept(Player to, String fromName) {
        String lang = plugin.lang(to);
        Challenge c = pick(to, fromName);
        if (c == null) {
            return;
        }
        byTarget.get(to.getUniqueId()).remove(c.from());
        Player from = plugin.getServer().getPlayer(c.from());
        if (from == null) {
            to.sendMessage(Texts.msg(lang, "duel.not-found", Placeholder.unparsed("name", c.fromName())));
            return;
        }
        if (!plugin.hall().inHall(to)) {
            to.sendMessage(Texts.msg(lang, "duel.you-busy"));
            return;
        }
        if (!plugin.hall().inHall(from)) {
            to.sendMessage(Texts.msg(lang, "duel.busy", Placeholder.unparsed("name", from.getName())));
            return;
        }
        if (!plugin.fights().start(c.kit(), false, List.of(c.from()), List.of(c.to()), true)) {
            to.sendMessage(Texts.msg(lang, "fight.no-arena"));
            from.sendMessage(Texts.msg(plugin.lang(from), "fight.no-arena"));
        }
    }

    public void deny(Player to, String fromName) {
        Challenge c = pick(to, fromName);
        if (c == null) {
            return;
        }
        byTarget.get(to.getUniqueId()).remove(c.from());
        to.sendMessage(Texts.msg(plugin.lang(to), "duel.denied.to"));
        Player from = plugin.getServer().getPlayer(c.from());
        if (from != null) {
            from.sendMessage(Texts.msg(plugin.lang(from), "duel.denied.from", Placeholder.unparsed("name", to.getName())));
        }
    }

    /** Die gemeinte Herausforderung: nach Namen, oder die einzige offene. */
    private Challenge pick(Player to, String fromName) {
        String lang = plugin.lang(to);
        Map<UUID, Challenge> open = byTarget.getOrDefault(to.getUniqueId(), Map.of());
        if (open.isEmpty()) {
            to.sendMessage(Texts.msg(lang, "duel.none"));
            return null;
        }
        if (fromName == null || fromName.isBlank()) {
            if (open.size() > 1) {
                to.sendMessage(Texts.msg(lang, "duel.which"));
                return null;
            }
            return open.values().iterator().next();
        }
        String wanted = fromName.toLowerCase(Locale.ROOT);
        for (Challenge c : open.values()) {
            if (c.fromName().toLowerCase(Locale.ROOT).equals(wanted)) {
                return c;
            }
        }
        to.sendMessage(Texts.msg(lang, "duel.none"));
        return null;
    }

    /** Namen derer, die einen herausgefordert haben (für Tab-Vorschläge). */
    public List<String> challengers(UUID to) {
        return byTarget.getOrDefault(to, Map.of()).values().stream().map(Challenge::fromName).toList();
    }

    public void clear(UUID u) {
        byTarget.remove(u);
        byTarget.values().forEach(m -> m.remove(u));
    }

    /** Abgelaufene Herausforderungen entfernen (jede Sekunde). */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Map<UUID, Challenge> open : byTarget.values()) {
            for (Iterator<Challenge> it = open.values().iterator(); it.hasNext();) {
                Challenge c = it.next();
                if (c.expires() > now) {
                    continue;
                }
                it.remove();
                Player from = plugin.getServer().getPlayer(c.from());
                if (from != null) {
                    from.sendMessage(Texts.msg(plugin.lang(from), "duel.expired.from",
                            Placeholder.unparsed("name", c.toName())));
                }
                Player to = plugin.getServer().getPlayer(c.to());
                if (to != null) {
                    to.sendMessage(Texts.msg(plugin.lang(to), "duel.expired.to",
                            Placeholder.unparsed("name", c.fromName())));
                }
            }
        }
        byTarget.values().removeIf(Map::isEmpty);
    }
}
