package de.moinmorni.duels;

import de.moinmorni.duels.kit.Kit;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Einstellungen der Kampfarten aus der config.yml (seit 0.2.0, Erklärungen stehen dort): jede Kampfart an/aus und
 * ihre Runden in der Warteschlange, dazu die Werte der besonderen Kampfarten. Getrennt von {@link Settings}, damit
 * beides unabhängig wachsen kann.
 */
public record KitSettings(
        Map<Kit, Boolean> enabled,
        Map<Kit, Integer> rounds,
        double sumoDepth,
        int boxingHits,
        int bridgeGoals,
        int comboPauseTicks,
        double rodPull,
        boolean mazeCompass,
        boolean mazeGoal,
        boolean mazeReshuffle,
        int partyMax) {

    /** Größte Party (und größtes Team) überhaupt – mehr Startplätze haben die Arenen nicht. */
    public static final int MAX_TEAM = 8;

    public KitSettings {
        enabled = Map.copyOf(enabled);
        rounds = Map.copyOf(rounds);
    }

    static KitSettings load(ConfigurationSection c) {
        Map<Kit, Boolean> on = new EnumMap<>(Kit.class);
        Map<Kit, Integer> rounds = new EnumMap<>(Kit.class);
        for (Kit kit : Kit.values()) {
            String base = "kits." + kit.id() + ".";
            on.put(kit, c.getBoolean(base + "an", true));
            rounds.put(kit, kit.rounds() ? bestOf(c.getInt(base + "runden", 1)) : 1);
        }
        return new KitSettings(on, rounds,
                clamp(c.getDouble("sumo.aus-tiefe", 2.0), 0.5, 20.0),
                (int) clamp(c.getInt("boxen.treffer", 100), 1, 1000),
                (int) clamp(c.getInt("bruecke.tore", 3), 1, 20),
                (int) clamp(c.getInt("kombo.treffer-pause-ticks", 2), 0, 10),
                clamp(c.getDouble("angel.zug", 1.0), 0.0, 3.0),
                c.getBoolean("labyrinth.kompass", true),
                c.getBoolean("labyrinth.ziel", true),
                c.getBoolean("labyrinth.jede-runde-neu", true),
                (int) clamp(c.getInt("party.max-spieler", MAX_TEAM), 2, MAX_TEAM));
    }

    /** Ist die Kampfart eingeschaltet? */
    public boolean on(Kit kit) {
        return enabled.getOrDefault(kit, true);
    }

    /** Runden in der Warteschlange: 1, 3 oder 5. */
    public int rounds(Kit kit) {
        return rounds.getOrDefault(kit, 1);
    }

    /** 1, 3 oder 5 – andere Zahlen werden auf die nächste passende gerundet (2 → 3, 4 → 5). */
    public static int bestOf(int n) {
        if (n <= 1) {
            return 1;
        }
        return n <= 3 ? 3 : 5;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
