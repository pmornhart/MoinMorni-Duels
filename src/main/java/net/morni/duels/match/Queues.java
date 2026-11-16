package net.morni.duels.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.morni.duels.DuelsPlugin;
import net.morni.duels.Settings;
import net.morni.duels.Texts;
import net.morni.duels.Ui;
import net.morni.duels.net.Network;
import org.bukkit.entity.Player;

/**
 * Warteschlangen je Kampfart × gewertet/locker × Teamgröße. Jede Sekunde sucht der {@link Matchmaker} Gegner;
 * passt etwas und ist eine Arena frei, beginnt der Kampf. Im 2 gegen 2 meldet der Party-Leiter seine Party an.
 */
public final class Queues {

    private static final long SECOND_MS = 1000L;
    /** So oft höchstens „alle Arenen belegt“ melden. */
    private static final long BUSY_NOTICE_MS = 15_000L;

    private final DuelsPlugin plugin;
    private final Map<QueueKey, List<Matchmaker.Entry>> waiting = new LinkedHashMap<>();
    private final Map<UUID, QueueKey> keyOf = new HashMap<>();
    private final Map<UUID, Matchmaker.Entry> entryOf = new HashMap<>();
    private Matchmaker matchmaker;
    private long lastBusyNotice;

    public Queues(DuelsPlugin plugin) {
        this.plugin = plugin;
        QueueKey.all().forEach(k -> waiting.put(k, new ArrayList<>()));
        reload(plugin.settings());
    }

    public void reload(Settings s) {
        matchmaker = new Matchmaker(s.rangeStart(), s.rangeGrowth(), s.rangeStepSeconds(), s.rangeMax());
    }

    public Optional<QueueKey> of(UUID u) {
        return Optional.ofNullable(keyOf.get(u));
    }

    public int waitingPlayers(QueueKey key) {
        return waiting.get(key).stream().mapToInt(e -> e.members().size()).sum();
    }

    public int totalWaiting() {
        return keyOf.size();
    }

    /** Anmelden (aus Menü oder Befehl). Im 2 gegen 2 wird die Party gefragt. */
    public void request(Player p, QueueKey key) {
        String lang = plugin.lang(p);
        if (!plugin.ready()) {
            p.sendMessage(Texts.msg(lang, "not-ready"));
            return;
        }
        if (!plugin.hall().inHall(p)) {
            p.sendMessage(Texts.msg(lang, "queue.not-in-hall"));
            return;
        }
        if (key.size() == 1) {
            join(List.of(p), key, false);
            return;
        }
        plugin.net().party(p.getUniqueId()).thenAccept(party -> plugin.getServer().getScheduler().runTask(plugin,
                () -> withParty(p, key, party.orElse(null))));
    }

    private void withParty(Player p, QueueKey key, Network.Party party) {
        if (!p.isOnline() || !plugin.hall().inHall(p)) {
            return;
        }
        String lang = plugin.lang(p);
        if (party == null || party.members().size() <= 1) {
            join(List.of(p), key, false);
            return;
        }
        if (party.members().size() > key.size()) {
            p.sendMessage(Texts.msg(lang, "queue.party.too-big"));
            return;
        }
        if (!party.leader().equals(p.getUniqueId())) {
            p.sendMessage(Texts.msg(lang, "queue.party.not-leader"));
            return;
        }
        List<Player> team = new ArrayList<>();
        team.add(p);
        for (Network.Party.Member m : party.members()) {
            if (m.uuid().equals(p.getUniqueId())) {
                continue;
            }
            Player mate = plugin.getServer().getPlayer(m.uuid());
            if (mate == null) {
                p.sendMessage(Texts.msg(lang, "queue.party.away", Placeholder.unparsed("name", m.name())));
                return;
            }
            if (!plugin.hall().inHall(mate)) {
                p.sendMessage(Texts.msg(lang, "queue.party.busy", Placeholder.unparsed("name", mate.getName())));
                return;
            }
            team.add(mate);
        }
        join(team, key, true);
    }

    private void join(List<Player> team, QueueKey key, boolean party) {
        for (Player p : team) {
            remove(p.getUniqueId());
        }
        double elo = team.stream().mapToInt(p -> plugin.data(p).kit(key.kit()).elo).average().orElse(0);
        Matchmaker.Entry entry = new Matchmaker.Entry(UUID.randomUUID(),
                team.stream().map(Player::getUniqueId).toList(), elo, System.currentTimeMillis());
        waiting.get(key).add(entry);
        for (Player p : team) {
            keyOf.put(p.getUniqueId(), key);
            entryOf.put(p.getUniqueId(), entry);
            String lang = plugin.lang(p);
            p.sendMessage(Texts.msg(lang, party ? "queue.joined.party" : "queue.joined",
                    Placeholder.unparsed("kit", Texts.raw(lang, "kit." + key.kit().id())),
                    Placeholder.unparsed("mode", Texts.raw(lang, key.modeKey())),
                    Placeholder.unparsed("size", Texts.raw(lang, key.sizeKey()))));
            Ui.sound(p, "block.note_block.chime", 1.2f);
            plugin.hall().items(p);
        }
    }

    /** Verlassen: die ganze Gruppe (Party) wartet nicht mehr. */
    public boolean leave(UUID u, boolean tell) {
        Matchmaker.Entry entry = entryOf.get(u);
        if (entry == null) {
            return false;
        }
        QueueKey key = keyOf.get(u);
        drop(entry, key);
        Player leaver = plugin.getServer().getPlayer(u);
        String leaverName = leaver == null ? "?" : leaver.getName();
        for (UUID m : entry.members()) {
            Player p = plugin.getServer().getPlayer(m);
            if (p == null) {
                continue;
            }
            String lang = plugin.lang(p);
            if (m.equals(u)) {
                if (tell) {
                    p.sendMessage(Texts.msg(lang, "queue.left"));
                }
            } else {
                p.sendMessage(Texts.msg(lang, "queue.left.team", Placeholder.unparsed("name", leaverName)));
            }
            p.sendActionBar(Component.empty());
            if (plugin.hall().inHall(p)) {
                plugin.hall().items(p);
            }
        }
        return true;
    }

    /** Still entfernen (z. B. weil der Kampf beginnt). */
    public void remove(UUID u) {
        Matchmaker.Entry entry = entryOf.get(u);
        if (entry != null) {
            drop(entry, keyOf.get(u));
        }
    }

    private void drop(Matchmaker.Entry entry, QueueKey key) {
        if (key != null) {
            waiting.get(key).remove(entry);
        }
        for (UUID m : entry.members()) {
            keyOf.remove(m);
            entryOf.remove(m);
        }
    }

    /** Jede Sekunde: Gegner suchen, Wartenden die Suche anzeigen. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<QueueKey, List<Matchmaker.Entry>> e : waiting.entrySet()) {
            QueueKey key = e.getKey();
            List<Matchmaker.Entry> list = e.getValue();
            // Wer nicht mehr in der Halle ist (oder weg), fliegt raus.
            for (Iterator<Matchmaker.Entry> it = list.iterator(); it.hasNext();) {
                Matchmaker.Entry entry = it.next();
                boolean ok = entry.members().stream().allMatch(m -> {
                    Player p = plugin.getServer().getPlayer(m);
                    return p != null && plugin.hall().inHall(p);
                });
                if (!ok) {
                    it.remove();
                    entry.members().forEach(m -> {
                        keyOf.remove(m);
                        entryOf.remove(m);
                    });
                }
            }
            if (!plugin.ready() || list.size() < 2 && key.size() == 1) {
                continue;
            }
            for (Matchmaker.Match match : matchmaker.find(List.copyOf(list), key.size(), key.ranked(), now)) {
                if (!plugin.arenas().anyFree()) {
                    if (now - lastBusyNotice > BUSY_NOTICE_MS) {
                        lastBusyNotice = now;
                        list.forEach(entry -> entry.members().forEach(m -> {
                            Player p = plugin.getServer().getPlayer(m);
                            if (p != null) {
                                p.sendMessage(Texts.msg(plugin.lang(p), "queue.busy"));
                            }
                        }));
                    }
                    break;
                }
                match.entries().forEach(entry -> drop(entry, key));
                if (!plugin.fights().start(key.kit(), key.ranked(), match.teamA(), match.teamB(), false)) {
                    // Hat nicht geklappt (jemand fehlt): wer noch da ist, wartet weiter.
                    match.entries().forEach(entry -> {
                        if (entry.members().stream().allMatch(m -> plugin.getServer().getPlayer(m) != null)) {
                            list.add(entry);
                            entry.members().forEach(m -> {
                                keyOf.put(m, key);
                                entryOf.put(m, entry);
                            });
                        }
                    });
                }
            }
        }
        showSearch(now);
    }

    private void showSearch(long now) {
        entryOf.forEach((u, entry) -> {
            Player p = plugin.getServer().getPlayer(u);
            QueueKey key = keyOf.get(u);
            if (p == null || key == null) {
                return;
            }
            String lang = plugin.lang(p);
            Component bar = Texts.text(lang, "queue.searching",
                    Placeholder.unparsed("kit", Texts.raw(lang, "kit." + key.kit().id())),
                    Placeholder.unparsed("mode", Texts.raw(lang, key.modeKey())),
                    Placeholder.unparsed("size", Texts.raw(lang, key.sizeKey())),
                    Placeholder.unparsed("time", Ui.clock((now - entry.since()) / SECOND_MS)));
            if (key.ranked()) {
                bar = bar.append(Texts.text(lang, "queue.range",
                        Placeholder.unparsed("range", String.valueOf(matchmaker.range(entry.since(), now)))));
            }
            p.sendActionBar(bar);
        });
    }
}
