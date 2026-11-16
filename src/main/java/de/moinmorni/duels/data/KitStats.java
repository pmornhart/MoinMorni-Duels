package de.moinmorni.duels.data;

/** Werte eines Spielers in einer Kampfart: Wertung, Siege, Serie … */
public final class KitStats {

    /** Ausgang eines Kampfes aus Sicht eines Spielers. */
    public enum Result {
        WIN, LOSS, DRAW;

        /** Für die Elo-Rechnung: 1, 0 oder 0,5. */
        public double score() {
            return switch (this) {
                case WIN -> 1.0;
                case LOSS -> 0.0;
                case DRAW -> 0.5;
            };
        }
    }

    public int elo;
    public int wins;
    public int losses;
    public int draws;
    public int kills;
    public int deaths;
    public int games;
    /** Gewertete Kämpfe – erst danach erscheint man in der Rangliste. */
    public int rankedGames;
    /** Siege in Folge (eine Niederlage setzt sie zurück, ein Unentschieden nicht). */
    public int streak;
    public int bestStreak;

    public KitStats(int elo) {
        this.elo = elo;
    }

    /** Ein Kampf ist vorbei: alles hochzählen. */
    public void record(Result result, int kills, int deaths, int eloDelta, boolean ranked) {
        games++;
        this.kills += kills;
        this.deaths += deaths;
        switch (result) {
            case WIN -> {
                wins++;
                streak++;
                bestStreak = Math.max(bestStreak, streak);
            }
            case LOSS -> {
                losses++;
                streak = 0;
            }
            case DRAW -> draws++;
        }
        if (ranked) {
            rankedGames++;
            elo = Math.max(0, elo + eloDelta);
        }
    }
}
