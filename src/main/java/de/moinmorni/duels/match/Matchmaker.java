package de.moinmorni.duels.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Sucht Gegner in einer Warteschlange. Ohne Bukkit, damit es sich gut prüfen lässt.
 *
 * <ul>
 *   <li><b>Locker:</b> wer zuerst kommt, spielt zuerst (die beiden Ältesten gegeneinander).</li>
 *   <li><b>Gewertet:</b> nach Elo. Am Anfang nur gegen ähnlich Starke ({@code rangeStart}), je länger man wartet,
 *   desto größer wird die erlaubte Spanne ({@code rangeGrowth} alle {@code stepSeconds}, höchstens {@code rangeMax}).
 *   Zwei passen, wenn der Abstand in die größere der beiden Spannen passt – so wartet niemand ewig.</li>
 *   <li><b>2 gegen 2:</b> Parties (zwei Leute) sind schon ein Team; Einzelne werden der Reihe nach zu Teams.</li>
 * </ul>
 */
public final class Matchmaker {

    /** Ein Eintrag in der Warteschlange: ein Spieler oder eine Party (alle ziehen gemeinsam). */
    public record Entry(UUID id, List<UUID> members, double elo, long since) {
        public Entry {
            members = List.copyOf(members);
        }
    }

    /** Ein gefundener Kampf: zwei Seiten, jede aus einem oder mehreren Einträgen. */
    public record Match(List<Entry> sideA, List<Entry> sideB) {
        public List<UUID> teamA() {
            return sideA.stream().flatMap(e -> e.members().stream()).toList();
        }

        public List<UUID> teamB() {
            return sideB.stream().flatMap(e -> e.members().stream()).toList();
        }

        public List<Entry> entries() {
            List<Entry> all = new ArrayList<>(sideA);
            all.addAll(sideB);
            return all;
        }
    }

    /** Ein fertiges Team (eine Party oder zusammengestellte Einzelne). */
    private record Unit(List<Entry> entries, double elo, long since) {
        static Unit of(List<Entry> entries) {
            int size = entries.stream().mapToInt(e -> e.members().size()).sum();
            double sum = entries.stream().mapToDouble(e -> e.elo() * e.members().size()).sum();
            long since = entries.stream().mapToLong(Entry::since).min().orElse(0);
            return new Unit(List.copyOf(entries), size == 0 ? 0 : sum / size, since);
        }
    }

    private static final long SECOND_MS = 1000L;

    private final int rangeStart;
    private final int rangeGrowth;
    private final int stepSeconds;
    private final int rangeMax;

    public Matchmaker(int rangeStart, int rangeGrowth, int stepSeconds, int rangeMax) {
        this.rangeStart = rangeStart;
        this.rangeGrowth = rangeGrowth;
        this.stepSeconds = Math.max(1, stepSeconds);
        this.rangeMax = Math.max(rangeStart, rangeMax);
    }

    /** Erlaubter Elo-Abstand nach so langer Wartezeit. */
    public int range(long since, long now) {
        long steps = Math.max(0, (now - since) / (stepSeconds * SECOND_MS));
        long range = rangeStart + steps * (long) rangeGrowth;
        return (int) Math.min(rangeMax, range);
    }

    /**
     * Findet Kämpfe. Einträge in der Reihenfolge, in der sie dazukamen.
     *
     * @param teamSize 1 = 1 gegen 1, 2 = 2 gegen 2
     */
    public List<Match> find(List<Entry> waiting, int teamSize, boolean ranked, long now) {
        List<Unit> units = units(waiting, teamSize);
        List<Match> matches = new ArrayList<>();
        if (!ranked) {
            for (int i = 0; i + 1 < units.size(); i += 2) {
                matches.add(new Match(units.get(i).entries(), units.get(i + 1).entries()));
            }
            return matches;
        }
        List<Unit> open = new ArrayList<>(units);
        open.sort(Comparator.comparingLong(Unit::since));
        while (open.size() >= 2) {
            Unit first = open.removeFirst();
            Unit best = null;
            double bestDiff = Double.MAX_VALUE;
            for (Unit other : open) {
                double diff = Math.abs(first.elo() - other.elo());
                int allowed = Math.max(range(first.since(), now), range(other.since(), now));
                if (diff <= allowed && diff < bestDiff) {
                    best = other;
                    bestDiff = diff;
                }
            }
            if (best != null) {
                open.remove(best);
                matches.add(new Match(first.entries(), best.entries()));
            }
        }
        return matches;
    }

    /** Teams bilden: Einträge mit genau {@code teamSize} Leuten sind fertig, Einzelne werden zusammengelegt. */
    private static List<Unit> units(List<Entry> waiting, int teamSize) {
        List<Unit> units = new ArrayList<>();
        List<Entry> loose = new ArrayList<>();
        for (Entry e : waiting) {
            int n = e.members().size();
            if (n == teamSize) {
                units.add(Unit.of(List.of(e)));
            } else if (n < teamSize) {
                loose.add(e);
                int sum = loose.stream().mapToInt(x -> x.members().size()).sum();
                if (sum == teamSize) {
                    units.add(Unit.of(loose));
                    loose = new ArrayList<>();
                } else if (sum > teamSize) {
                    // Passt nicht zusammen (kommt bei 1 und 2 nicht vor): neu anfangen mit dem letzten.
                    loose = new ArrayList<>(List.of(e));
                }
            }
            // Zu große Einträge (z. B. eine Dreier-Party im 2 gegen 2) lässt die Warteschlange gar nicht erst zu.
        }
        units.sort(Comparator.comparingLong(Unit::since));
        return units;
    }
}
