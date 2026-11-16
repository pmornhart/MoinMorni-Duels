package de.moinmorni.duels;

import de.moinmorni.duels.fight.Fight;
import de.moinmorni.duels.fight.Fights;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.net.Network;
import de.moinmorni.duels.world.ArenaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Herausforderungen mit /duel: Kampfart, Arena und Runden (Bester aus 1/3/5) wählbar, zum Anklicken annehmen oder
 * ablehnen, nach einer Minute verfallen sie. Sind beide Leitung einer Party (mit mindestens zwei Leuten), kämpfen die
 * ganzen Partys gegeneinander. Bedrock-Spieler bekommen ein Menü, weil sie im Chat nichts anklicken können.
 * Herausforderungen sind immer locker (ungewertet).
 */
public final class Challenges {

    /** Was herausgefordert wird: Kampfart, Arena ({@code null} = Zufall) und Runden. */
    public record Options(Kit kit, ArenaType arena, int bestOf) {
        public Options {
            bestOf = kit.rounds() ? KitSettings.bestOf(bestOf) : 1;
            arena = arena != null && arena.normal() && ArenaType.of(kit).isEmpty() ? arena : null;
        }

        public static Options of(Kit kit) {
            return new Options(kit, null, 1);
        }
    }

    private record Challenge(UUID from, String fromName, UUID to, String toName, Options options, boolean party,
                             long expires) {
    }

    private final DuelsPlugin plugin;
    /** Herausgeforderter → (Herausforderer → Herausforderung). */
    private final Map<UUID, Map<UUID, Challenge>> byTarget = new HashMap<>();

    public Challenges(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Kurz beschrieben: „Klassisch“, „Sumo · Bester aus 3“, „Ritter · Burghof · Party gegen Party“. */
    public static String describe(String lang, Options o, boolean party) {
        List<String> parts = new ArrayList<>();
        parts.add(Texts.raw(lang, "kit." + o.kit().id()));
        if (o.bestOf() > 1) {
            parts.add(Texts.plain(lang, "duel.desc.bestof", Placeholder.unparsed("n", String.valueOf(o.bestOf()))));
        }
        if (o.arena() != null) {
            parts.add(Texts.raw(lang, o.arena().textKey()));
        }
        if (party) {
            parts.add(Texts.raw(lang, "duel.desc.party"));
        }
        return String.join(" · ", parts);
    }

    public void challenge(Player from, Player to, Kit kit) {
        challenge(from, to, Options.of(kit));
    }

    public void challenge(Player from, Player to, Options o) {
        String lang = plugin.lang(from);
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(Texts.msg(lang, "duel.self"));
            return;
        }
        if (!plugin.ready()) {
            from.sendMessage(Texts.msg(lang, "not-ready"));
            return;
        }
        if (!plugin.kitSettings().on(o.kit())) {
            from.sendMessage(Texts.msg(lang, "kit.off"));
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
        UUID a = from.getUniqueId();
        UUID b = to.getUniqueId();
        CompletableFuture<Optional<Network.Party>> pa = plugin.net().party(a);
        CompletableFuture<Optional<Network.Party>> pb = plugin.net().party(b);
        pa.thenCombine(pb, (x, y) -> partyDuel(x.orElse(null), y.orElse(null), a, b))
                .exceptionally(e -> false)
                .thenAccept(party -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player f = plugin.getServer().getPlayer(a);
                    Player t = plugin.getServer().getPlayer(b);
                    if (f != null && t != null) {
                        send(f, t, o, party);
                    }
                }));
    }

    /** Party gegen Party: beide sind Leitung zweier verschiedener Partys mit mindestens zwei Leuten. */
    static boolean partyDuel(Network.Party a, Network.Party b, UUID from, UUID to) {
        return a != null && b != null && a.members().size() > 1 && b.members().size() > 1
                && from.equals(a.leader()) && to.equals(b.leader()) && !a.leader().equals(b.leader())
                && a.members().stream().noneMatch(m -> m.uuid().equals(to));
    }

    private void send(Player from, Player to, Options o, boolean party) {
        String lang = plugin.lang(from);
        if (!plugin.hall().inHall(from) || !plugin.hall().inHall(to)) {
            return;
        }
        Map<UUID, Challenge> open = byTarget.computeIfAbsent(to.getUniqueId(), k -> new HashMap<>());
        Challenge old = open.get(from.getUniqueId());
        if (old != null && old.options().equals(o) && old.party() == party) {
            from.sendMessage(Texts.msg(lang, "duel.already", Placeholder.unparsed("name", to.getName())));
            return;
        }
        int secs = plugin.settings().challengeSeconds();
        open.put(from.getUniqueId(), new Challenge(from.getUniqueId(), from.getName(), to.getUniqueId(), to.getName(),
                o, party, System.currentTimeMillis() + secs * 1000L));
        from.sendMessage(Texts.msg(lang, "duel.sent", Placeholder.unparsed("name", to.getName()),
                Placeholder.unparsed("kit", describe(lang, o, party)),
                Placeholder.unparsed("secs", String.valueOf(secs))));
        String tl = plugin.lang(to);
        to.sendMessage(Texts.msg(tl, "duel.received", Placeholder.unparsed("name", from.getName()),
                Placeholder.unparsed("kit", describe(tl, o, party))));
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
            plugin.menus().openChallenge(to, from, describe(tl, o, party));
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
        if (!plugin.kitSettings().on(c.options().kit())) {
            to.sendMessage(Texts.msg(lang, "kit.off"));
            return;
        }
        if (!c.party()) {
            begin(to, from, c, List.of(c.from()), List.of(c.to()), Fight.Kind.DUEL);
            return;
        }
        // Party gegen Party: beide Partys frisch nachsehen – es kämpft, wer gerade im Turnierhof ist
        plugin.net().party(c.from()).thenCombine(plugin.net().party(c.to()), (a, b) -> new Object[] {a, b})
                .thenAccept(both -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    @SuppressWarnings("unchecked")
                    Optional<Network.Party> a = (Optional<Network.Party>) both[0];
                    @SuppressWarnings("unchecked")
                    Optional<Network.Party> b = (Optional<Network.Party>) both[1];
                    Player f = plugin.getServer().getPlayer(c.from());
                    Player t = plugin.getServer().getPlayer(c.to());
                    if (f == null || t == null) {
                        return;
                    }
                    List<UUID> teamA = inHall(a.orElse(null), c.from());
                    List<UUID> teamB = inHall(b.orElse(null), c.to());
                    begin(t, f, c, teamA, teamB, Fight.Kind.PARTY);
                }));
    }

    /** Mitglieder einer Party, die gerade im Turnierhof sind (mindestens die Leitung selbst), höchstens so viele wie erlaubt. */
    private List<UUID> inHall(Network.Party party, UUID leader) {
        List<UUID> out = new ArrayList<>();
        out.add(leader);
        if (party != null) {
            for (Network.Party.Member m : party.members()) {
                Player p = plugin.getServer().getPlayer(m.uuid());
                if (!m.uuid().equals(leader) && p != null && plugin.hall().inHall(p)
                        && out.size() < plugin.kitSettings().partyMax()) {
                    out.add(m.uuid());
                }
            }
        }
        return out;
    }

    private void begin(Player to, Player from, Challenge c, List<UUID> teamA, List<UUID> teamB, Fight.Kind kind) {
        Options o = c.options();
        Fights.Request r = new Fights.Request(o.kit(), false, kind, List.of(teamA, teamB), o.arena(), o.bestOf());
        if (!plugin.fights().start(r)) {
            to.sendMessage(Texts.msg(plugin.lang(to), "fight.no-arena"));
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
