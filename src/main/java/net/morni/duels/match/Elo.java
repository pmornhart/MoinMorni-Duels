package net.morni.duels.match;

/**
 * Elo-Wertung: Wer gegen Stärkere gewinnt, bekommt viel dazu, wer gegen Schwächere verliert, verliert viel.
 * Start 1000, K-Faktor 32 (beides in der config.yml).
 */
public final class Elo {

    private static final double SCALE = 400.0;
    private static final double BASE = 10.0;

    private Elo() {
    }

    /** Erwartetes Ergebnis (0 … 1) für jemanden mit {@code own} gegen {@code other}. */
    public static double expected(double own, double other) {
        return 1.0 / (1.0 + Math.pow(BASE, (other - own) / SCALE));
    }

    /**
     * Änderung der Wertung.
     *
     * @param own   eigene Wertung (im 2 gegen 2 der Schnitt des eigenen Teams)
     * @param other Wertung der Gegner (Schnitt)
     * @param score 1 = Sieg, 0,5 = Unentschieden, 0 = Niederlage
     * @param k     K-Faktor
     */
    public static int delta(double own, double other, double score, int k) {
        return (int) Math.round(k * (score - expected(own, other)));
    }
}
