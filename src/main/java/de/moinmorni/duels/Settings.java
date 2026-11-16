package de.moinmorni.duels;

import org.bukkit.configuration.file.FileConfiguration;

/** Werte aus der config.yml (Erklärungen stehen dort). */
public record Settings(
        String world,
        int arenaCopies,
        int timeLimitSeconds,
        int countdownSeconds,
        int afterSeconds,
        int challengeSeconds,
        int eloStart,
        int kFactor,
        int rangeStart,
        int rangeGrowth,
        int rangeStepSeconds,
        int rangeMax,
        double soupHeal,
        int returnSeconds) {

    private static final int MAX_COPIES = 20;
    /** Höchstens so lange bleibt ein Platz frei (länger ist für den Gegner zu viel Warten). */
    private static final int MAX_RETURN_SECONDS = 600;

    static Settings load(FileConfiguration c) {
        return new Settings(
                c.getString("welt", "duels"),
                clamp(c.getInt("arena-kopien", 3), 1, MAX_COPIES),
                Math.max(10, c.getInt("kampf.zeitlimit-sekunden", 600)),
                clamp(c.getInt("kampf.countdown-sekunden", 3), 1, 10),
                clamp(c.getInt("kampf.nachspiel-sekunden", 10), 1, 60),
                Math.max(10, c.getInt("herausforderung.ablauf-sekunden", 60)),
                Math.max(0, c.getInt("wertung.start", 1000)),
                Math.max(1, c.getInt("wertung.k-faktor", 32)),
                Math.max(0, c.getInt("wertung.spanne-start", 100)),
                Math.max(0, c.getInt("wertung.spanne-plus", 50)),
                Math.max(1, c.getInt("wertung.spanne-alle-sekunden", 5)),
                Math.max(0, c.getInt("wertung.spanne-max", 1000)),
                Math.max(0.5, c.getDouble("suppe.heilung", 7.0)),
                clamp(c.getInt("rueckkehr-sekunden", 30), 0, MAX_RETURN_SECONDS));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
