package de.moinmorni.duels;

import de.moinmorni.duels.fight.Fight;
import de.moinmorni.duels.fight.Fights;
import de.moinmorni.duels.net.Network;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Party-Kämpfe in der eigenen Party: „Jeder gegen jeden“ (bis einer übrig ist) oder „Zwei Teams“ (die Party wird
 * zufällig in zwei gleich große Teams geteilt – geht auch mit der Brücke). Die Party-Leitung startet; alle aus der
 * Party (mindestens zwei, höchstens {@code party.max-spieler}) müssen im Turnierhof sein. Immer locker. Party gegen Party
 * läuft über {@link Challenges} (zwei Party-Leitungen fordern sich heraus).
 */
public final class PartyFights {

    private final DuelsPlugin plugin;

    public PartyFights(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Jeder gegen jeden in der eigenen Party starten (die Party kommt vom Netzwerk, darum erst später). */
    public void ffa(Player leader, Challenges.Options o) {
        start(leader, o, false);
    }

    /**
     * Party-Kampf starten: {@code split} = zwei Teams (zufällig geteilt), sonst Jeder gegen jeden. Die Party kommt vom
     * Netzwerk, darum erst später.
     */
    public void start(Player leader, Challenges.Options o, boolean split) {
        String lang = plugin.lang(leader);
        if (!plugin.ready()) {
            leader.sendMessage(Texts.msg(lang, "not-ready"));
            return;
        }
        if (!plugin.kitSettings().on(o.kit())) {
            leader.sendMessage(Texts.msg(lang, "kit.off"));
            return;
        }
        if (!split && !o.kit().ffa()) {
            leader.sendMessage(Texts.msg(lang, "party.no-ffa"));
            return;
        }
        if (!plugin.hall().inHall(leader)) {
            leader.sendMessage(Texts.msg(lang, "duel.you-busy"));
            return;
        }
        UUID u = leader.getUniqueId();
        plugin.net().party(u).thenAccept(party -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = plugin.getServer().getPlayer(u);
            if (p != null) {
                start(p, party.orElse(null), o, split);
            }
        }));
    }

    private void start(Player leader, Network.Party party, Challenges.Options o, boolean split) {
        String lang = plugin.lang(leader);
        if (!plugin.hall().inHall(leader)) {
            return;
        }
        if (party == null || party.members().size() < 2) {
            leader.sendMessage(Texts.msg(lang, "party.none"));
            return;
        }
        if (!party.leader().equals(leader.getUniqueId())) {
            leader.sendMessage(Texts.msg(lang, "party.not-leader"));
            return;
        }
        int max = plugin.kitSettings().partyMax();
        if (party.members().size() > max) {
            leader.sendMessage(Texts.msg(lang, "party.too-big", Placeholder.unparsed("n", String.valueOf(max))));
            return;
        }
        List<List<UUID>> teams = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Network.Party.Member m : party.members()) {
            Player p = plugin.getServer().getPlayer(m.uuid());
            if (p == null) {
                leader.sendMessage(Texts.msg(lang, "party.away", Placeholder.unparsed("name", m.name())));
                return;
            }
            if (!plugin.hall().inHall(p)) {
                leader.sendMessage(Texts.msg(lang, "party.busy", Placeholder.unparsed("name", p.getName())));
                return;
            }
            teams.add(List.of(p.getUniqueId()));
            names.add(p.getName());
        }
        Fights.Request r;
        if (split) {
            List<List<UUID>> two = splitTeams(teams.stream().map(t -> t.get(0)).toList(), new Random());
            String a = names(two.get(0));
            String b = names(two.get(1));
            for (List<UUID> team : teams) {
                Player p = plugin.getServer().getPlayer(team.get(0));
                if (p != null) {
                    String l = plugin.lang(p);
                    p.sendMessage(Texts.msg(l, "party.split.start",
                            Placeholder.unparsed("kit", Challenges.describe(l, o, false)),
                            Placeholder.unparsed("first", a), Placeholder.unparsed("second", b)));
                }
            }
            r = new Fights.Request(o.kit(), false, Fight.Kind.PARTY, two, o.arena(), o.bestOf());
        } else {
            String who = String.join(", ", names);
            for (List<UUID> team : teams) {
                Player p = plugin.getServer().getPlayer(team.get(0));
                if (p != null) {
                    String l = plugin.lang(p);
                    p.sendMessage(Texts.msg(l, "party.ffa.start",
                            Placeholder.unparsed("kit", Challenges.describe(l, o, false)),
                            Placeholder.unparsed("names", who)));
                }
            }
            r = new Fights.Request(o.kit(), false, Fight.Kind.FFA, teams, o.arena(), o.bestOf());
        }
        if (!plugin.fights().start(r)) {
            leader.sendMessage(Texts.msg(lang, "fight.no-arena"));
        }
    }

    private String names(List<UUID> team) {
        List<String> out = new ArrayList<>();
        for (UUID u : team) {
            Player p = plugin.getServer().getPlayer(u);
            out.add(p == null ? "?" : p.getName());
        }
        return String.join(" & ", out);
    }

    /** Party zufällig in zwei Teams teilen – bei ungerader Zahl hat das erste eine Person mehr. */
    static List<List<UUID>> splitTeams(List<UUID> members, Random r) {
        List<UUID> mixed = new ArrayList<>(members);
        Collections.shuffle(mixed, r);
        List<UUID> a = new ArrayList<>();
        List<UUID> b = new ArrayList<>();
        for (int i = 0; i < mixed.size(); i++) {
            (i % 2 == 0 ? a : b).add(mixed.get(i));
        }
        return List.of(a, b);
    }
}
