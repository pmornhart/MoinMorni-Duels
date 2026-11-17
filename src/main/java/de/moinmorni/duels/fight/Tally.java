package de.moinmorni.duels.fight;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Spielstand eines Kampfes, ohne Minecraft (gut zu prüfen): gewonnene Runden je Team („Bester aus 1/3/5“) und der
 * Punktestand der laufenden Runde – Treffer beim Boxen, Tore bei der Brücke.
 */
public final class Tally {

    private final int teams;
    private final int bestOf;
    private final int[] rounds;
    private final int[] score;

    public Tally(int teams, int bestOf) {
        this.teams = Math.max(2, teams);
        this.bestOf = Math.max(1, bestOf);
        this.rounds = new int[this.teams];
        this.score = new int[this.teams];
    }

    public int bestOf() {
        return bestOf;
    }

    /** So viele Runden braucht man zum Sieg: Bester aus 3 → 2, Bester aus 5 → 3. */
    public static int needed(int bestOf) {
        return Math.max(1, bestOf) / 2 + 1;
    }

    public int needed() {
        return needed(bestOf);
    }

    /** Team gewinnt eine Runde. {@code true} = damit ist der ganze Kampf entschieden. */
    public boolean roundWon(int team) {
        if (team < 0 || team >= teams) {
            return false;
        }
        rounds[team]++;
        return rounds[team] >= needed();
    }

    /** Neue Runde: der Punktestand (Treffer) beginnt wieder bei 0, gewonnene Runden bleiben. */
    public void newRound() {
        Arrays.fill(score, 0);
    }

    /**
     * Punkte für ein Team (Treffer, Tore). {@code true} = das Team hat {@code target} erreicht.
     */
    public boolean add(int team, int amount, int target) {
        if (team < 0 || team >= teams) {
            return false;
        }
        score[team] += amount;
        return score[team] >= target;
    }

    public int rounds(int team) {
        return team >= 0 && team < teams ? rounds[team] : 0;
    }

    public int score(int team) {
        return team >= 0 && team < teams ? score[team] : 0;
    }

    /** Wurde schon eine Runde gewonnen oder ein Punkt gemacht? */
    public boolean started() {
        return Arrays.stream(rounds).anyMatch(r -> r > 0) || Arrays.stream(score).anyMatch(s -> s > 0);
    }

    /**
     * Wer liegt vorn, wenn die Zeit abläuft? Erst zählen gewonnene Runden, dann der Punktestand. -1 = gleich
     * (Unentschieden).
     */
    public int leader() {
        int best = -1;
        boolean tie = false;
        for (int t = 0; t < teams; t++) {
            if (best < 0 || compare(t, best) > 0) {
                best = t;
                tie = false;
            } else if (compare(t, best) == 0) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }

    private int compare(int a, int b) {
        if (rounds[a] != rounds[b]) {
            return Integer.compare(rounds[a], rounds[b]);
        }
        return Integer.compare(score[a], score[b]);
    }

    /** Runden als „2:1“ (bei mehr als zwei Teams „2:1:0“). */
    public String roundsText() {
        return Arrays.stream(rounds).mapToObj(String::valueOf).collect(Collectors.joining(":"));
    }

    /** Punkte als „37:52“. */
    public String scoreText() {
        return Arrays.stream(score).mapToObj(String::valueOf).collect(Collectors.joining(":"));
    }
}
