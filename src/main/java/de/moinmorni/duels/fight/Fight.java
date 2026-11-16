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

/** Ein laufender Kampf: zwei Teams in einer Arena. */
public final class Fight {

    /** Countdown (eingefroren) → Kampf → Nachspiel (alle unverwundbar, dann zurück in die Halle). */
    public enum Phase { COUNTDOWN, RUNNING, ENDED }

    /** Wie jemand ausgeschieden ist (LEFT = in eine andere Welt gewechselt). */
    public enum Cause { KILLED, DIED, VOID, QUIT, LEFT, FORFEIT }

    /** So lange zählt ein Treffer noch als „wer war's“ (z. B. bei einem Sturz in die Tiefe). */
    static final long CREDIT_MS = 15_000L;

    final int id;
    final Kit kit;
    final boolean ranked;
    final int size;
    final boolean challenge;
    final Arena arena;
    final List<List<UUID>> teams;
    final Set<UUID> alive = new HashSet<>();
    final Set<UUID> gaveUp = new HashSet<>();
    final Map<UUID, Integer> kills = new HashMap<>();
    final Map<UUID, Integer> deaths = new HashMap<>();
    final Map<UUID, UUID> lastDamager = new HashMap<>();
    final Map<UUID, Long> lastDamageAt = new HashMap<>();
    final Map<UUID, Integer> eloBefore = new HashMap<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Map<UUID, PlayerData> data = new HashMap<>();
    final Map<UUID, Ability> abilities = new HashMap<>();
    final Set<UUID> spectators = new HashSet<>();
    final Map<String, BossBar> bars = new HashMap<>();
    /** Schon angesagte Restzeiten (60, 30, 10 Sekunden). */
    final Set<Integer> warned = new HashSet<>();
    Phase phase = Phase.COUNTDOWN;
    int countdown;
    long startedAt;
    int afterLeft;
    int winner = -2;
    BukkitTask task;

    Fight(int id, Kit kit, boolean ranked, boolean challenge, Arena arena, List<UUID> a, List<UUID> b) {
        this.id = id;
        this.kit = kit;
        this.ranked = ranked;
        this.challenge = challenge;
        this.arena = arena;
        this.size = Math.max(a.size(), b.size());
        this.teams = List.of(List.copyOf(a), List.copyOf(b));
    }

    public Kit kit() {
        return kit;
    }

    public boolean ranked() {
        return ranked;
    }

    public Arena arena() {
        return arena;
    }

    public Phase phase() {
        return phase;
    }

    public QueueKey key() {
        return new QueueKey(kit, ranked, size);
    }

    public boolean isAlive(UUID u) {
        return alive.contains(u);
    }

    /** 0 oder 1, -1 = gehört nicht dazu. */
    public int team(UUID u) {
        if (teams.get(0).contains(u)) {
            return 0;
        }
        return teams.get(1).contains(u) ? 1 : -1;
    }

    public boolean sameTeam(UUID a, UUID b) {
        int t = team(a);
        return t >= 0 && t == team(b);
    }

    public List<UUID> participants() {
        List<UUID> all = new ArrayList<>(teams.get(0));
        all.addAll(teams.get(1));
        return all;
    }

    public List<UUID> opponents(UUID u) {
        int t = team(u);
        return t < 0 ? List.of() : teams.get(1 - t);
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

    String name(UUID u) {
        return names.getOrDefault(u, "?");
    }

    /** Namen eines Teams, z. B. „Anna & Ben“. */
    String teamNames(int team) {
        return String.join(" & ", teams.get(team).stream().map(this::name).toList());
    }
}
