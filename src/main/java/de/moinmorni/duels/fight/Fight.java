package de.moinmorni.duels.fight;

import de.moinmorni.duels.data.PlayerData;
import de.moinmorni.duels.kit.Ability;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.match.QueueKey;
import de.moinmorni.duels.world.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.scheduler.BukkitTask;

/**
 * Ein laufender Kampf: zwei (oder bei „Jeder gegen jeden“ mehr) Teams in einer Arena, eine oder mehrere Runden.
 */
public final class Fight {

    /**
     * Countdown (eingefroren) → Kampf → Pause zwischen zwei Runden → … → Nachspiel (alle unverwundbar, dann zurück
     * in die Halle).
     */
    public enum Phase { COUNTDOWN, RUNNING, BREAK, ENDED }

    /**
     * Wie jemand ausgeschieden ist (LEFT = in eine andere Welt gewechselt, GONE = Verbindung weg und nicht rechtzeitig
     * zurückgekommen).
     */
    public enum Cause { KILLED, DIED, VOID, QUIT, LEFT, FORFEIT, GONE }

    /** Verbindung weg: Stand beim Verlassen und bis wann (Millisekunden) der Platz frei bleibt. */
    record Away(long until, PlayerState state) {
    }

    /** Wie der Kampf zustande kam. */
    public enum Kind {
        /** Aus der Warteschlange (locker oder gewertet). */
        QUEUE,
        /** Herausforderung mit /duel (1 gegen 1). */
        DUEL,
        /** Party gegen Party (Herausforderung zwischen zwei Party-Leitungen). */
        PARTY,
        /** Party: Jeder gegen jeden. */
        FFA
    }

    /** So lange zählt ein Treffer noch als „wer war's“ (z. B. bei einem Sturz in die Tiefe). */
    static final long CREDIT_MS = 15_000L;

    final int id;
    final Kit kit;
    final boolean ranked;
    final int size;
    final boolean challenge;
    final Kind kind;
    final Arena arena;
    final List<List<UUID>> teams;
    final Tally tally;
    final Set<UUID> alive = new HashSet<>();
    final Set<UUID> gaveUp = new HashSet<>();
    final Map<UUID, Integer> kills = new HashMap<>();
    final Map<UUID, Integer> deaths = new HashMap<>();
    final Map<UUID, Integer> hits = new HashMap<>();
    final Map<UUID, Integer> goals = new HashMap<>();
    /** Labyrinth: wie oft jemand als Erstes am Ziel war. */
    final Map<UUID, Integer> finishes = new HashMap<>();
    final Map<UUID, UUID> lastDamager = new HashMap<>();
    final Map<UUID, Long> lastDamageAt = new HashMap<>();
    final Map<UUID, Integer> eloBefore = new HashMap<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Map<UUID, PlayerData> data = new HashMap<>();
    final Map<UUID, Ability> abilities = new HashMap<>();
    final Set<UUID> spectators = new HashSet<>();
    final Map<String, BossBar> bars = new HashMap<>();
    /** Von Spielern gesetzte Blöcke (nur die dürfen wieder abgebaut werden), als Schlüssel x/y/z. */
    final Set<Long> placed = new HashSet<>();
    /** Schon angesagte Restzeiten (60, 30, 10 Sekunden). */
    final Set<Integer> warned = new HashSet<>();
    /** Wer gerade die Verbindung verloren hat und noch zurückkommen darf. */
    final Map<UUID, Away> away = new LinkedHashMap<>();
    /** Kommt beim Wiederkommen frisch an den Start (inzwischen neue Runde oder ein Tor), statt genau dorthin. */
    final Set<UUID> fresh = new HashSet<>();
    /** Angehalten, weil ein Team gerade ganz fehlt: alle eingefroren, die Zeit steht. */
    boolean paused;
    long pausedAt;
    /** Alle wieder da: noch so viele Sekunden Countdown, dann geht es weiter (0 = kein Countdown). */
    int resumeIn;
    Phase phase = Phase.COUNTDOWN;
    int countdown;
    long startedAt;
    int afterLeft;
    int breakLeft;
    int round = 1;
    int winner = -2;
    BukkitTask task;

    Fight(int id, Kit kit, boolean ranked, Kind kind, int bestOf, Arena arena, List<List<UUID>> teams) {
        this.id = id;
        this.kit = kit;
        this.ranked = ranked;
        this.kind = kind;
        this.challenge = kind != Kind.QUEUE;
        this.arena = arena;
        this.teams = teams.stream().map(List::copyOf).toList();
        this.size = this.teams.stream().mapToInt(List::size).max().orElse(1);
        this.tally = new Tally(this.teams.size(), kit.rounds() ? bestOf : 1);
    }

    public Kit kit() {
        return kit;
    }

    public boolean ranked() {
        return ranked;
    }

    public Kind kind() {
        return kind;
    }

    public Arena arena() {
        return arena;
    }

    public Phase phase() {
        return phase;
    }

    public Tally tally() {
        return tally;
    }

    public QueueKey key() {
        return new QueueKey(kit, ranked, size);
    }

    public boolean isAlive(UUID u) {
        return alive.contains(u);
    }

    /** Wird gerade gekämpft (läuft und ist nicht angehalten)? */
    public boolean live() {
        return phase == Phase.RUNNING && !paused;
    }

    /** Steht {@code u} gerade eingefroren (Countdown oder angehalten)? */
    public boolean frozen(UUID u) {
        return alive.contains(u) && phase != Phase.ENDED && (phase == Phase.COUNTDOWN || paused);
    }

    public boolean paused() {
        return paused;
    }

    /** Index des Teams (0, 1 …), -1 = gehört nicht dazu. */
    public int team(UUID u) {
        for (int t = 0; t < teams.size(); t++) {
            if (teams.get(t).contains(u)) {
                return t;
            }
        }
        return -1;
    }

    public int teamCount() {
        return teams.size();
    }

    public boolean sameTeam(UUID a, UUID b) {
        int t = team(a);
        return t >= 0 && t == team(b);
    }

    public List<UUID> participants() {
        List<UUID> all = new ArrayList<>();
        teams.forEach(all::addAll);
        return all;
    }

    /** Alle aus den anderen Teams. */
    public List<UUID> opponents(UUID u) {
        int t = team(u);
        if (t < 0) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            if (i != t) {
                out.addAll(teams.get(i));
            }
        }
        return out;
    }

    /** Gewählte Fähigkeit (Kit „Fähigkeiten“), festgelegt beim Start. */
    public Ability ability(UUID u) {
        return abilities.get(u);
    }

    public Set<UUID> spectators() {
        return spectators;
    }

    /** Wer hat zuletzt getroffen (innerhalb der letzten Sekunden)? */
    public UUID recentDamager(UUID victim, long now) {
        Long at = lastDamageAt.get(victim);
        return at != null && now - at <= CREDIT_MS ? lastDamager.get(victim) : null;
    }

    public void hit(UUID victim, UUID attacker, long now) {
        lastDamager.put(victim, attacker);
        lastDamageAt.put(victim, now);
    }

    /** Hat ein Spieler diesen Block in diesem Kampf gesetzt? */
    public boolean placed(int x, int y, int z) {
        return placed.contains(blockKey(x, y, z));
    }

    public void placedAdd(int x, int y, int z) {
        placed.add(blockKey(x, y, z));
    }

    public void placedRemove(int x, int y, int z) {
        placed.remove(blockKey(x, y, z));
    }

    static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    String name(UUID u) {
        return names.getOrDefault(u, "?");
    }

    /** Namen eines Teams, z. B. „Anna & Ben“. */
    String teamNames(int team) {
        return String.join(" & ", teams.get(team).stream().map(this::name).toList());
    }
}
