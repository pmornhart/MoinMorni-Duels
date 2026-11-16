package de.moinmorni.duels.fight;

import de.moinmorni.duels.world.ArenaPlans;
import de.moinmorni.duels.world.SpecialArenas;

/**
 * Wo in einer Arena was gilt – ohne Minecraft (gut zu prüfen). Alle Werte relativ zum Mittelpunkt der Arena, y = 0 ist
 * der Boden (man steht auf y = 1).
 */
public final class Zones {

    /** Wer mit den Füßen so tief im Torschacht steckt, hat getroffen (Boden des Tors liegt bei -5). */
    private static final int GOAL_DEPTH = -6;
    /** Über den Toren und auf ihrem Rand darf niemand bauen (sonst ließen sie sich zumauern). */
    private static final int GOAL_KEEP_FREE = 3;
    /** Über dem Ziel im Irrgarten zählt man bis zu dieser Höhe (Füße), also auch mitten im Sprung. */
    private static final int MAZE_GOAL_JUMP = 2;

    private Zones() {
    }

    /**
     * Brücke: Steckt ein Spieler (Füße auf Block x/y/z) in einem Tor? 0 = Tor im Norden (gehört Team 1, Blau),
     * 1 = Tor im Süden (Team 2, Rot), -1 = in keinem.
     */
    public static int bridgeGoal(int x, int y, int z) {
        if (y > 0 || y < GOAL_DEPTH || Math.abs(x) > 1) {
            return -1;
        }
        if (Math.abs(z + SpecialArenas.BRIDGE_GOAL) <= 1) {
            return 0;
        }
        if (Math.abs(z - SpecialArenas.BRIDGE_GOAL) <= 1) {
            return 1;
        }
        return -1;
    }

    /** Brücke: Darf hier ein Block hin? Innerhalb der Wände, nicht zu hoch, nicht auf oder über den Toren. */
    public static boolean bridgeBuildable(int x, int y, int z) {
        if (Math.abs(x) > SpecialArenas.BRIDGE_HALF_X || Math.abs(z) > SpecialArenas.BRIDGE_END) {
            return false;
        }
        if (y < SpecialArenas.BRIDGE_BUILD_BOTTOM || y > SpecialArenas.BRIDGE_BUILD_TOP) {
            return false;
        }
        boolean nearGoal = Math.abs(x) <= 2 && (Math.abs(z + SpecialArenas.BRIDGE_GOAL) <= 2
                || Math.abs(z - SpecialArenas.BRIDGE_GOAL) <= 2);
        return !(nearGoal && y <= GOAL_KEEP_FREE);
    }

    /** Bauen in den normalen Arenen: innerhalb der unsichtbaren Wand, über dem Boden, unter der Decke. */
    public static boolean arenaBuildable(int x, int y, int z) {
        return Math.abs(x) <= ArenaPlans.HALF && Math.abs(z) <= ArenaPlans.HALF && y >= 1 && y <= ArenaPlans.TOP;
    }

    /** Spleef: Nur der Schnee des Spielfelds lässt sich wegschaufeln (Wände und Grube nicht). */
    public static boolean spleefBreakable(int x, int y, int z) {
        return y == 0 && Math.abs(x) <= SpecialArenas.SNOW_HALF && Math.abs(z) <= SpecialArenas.SNOW_HALF;
    }

    /** Sumo: Ab dieser Höhe ist man vom Tanzboden gefallen (so tief unter der Standhöhe wie in der config.yml). */
    public static double sumoVoid(int floorY, double depth) {
        return floorY + 1 - depth;
    }

    /**
     * Labyrinth: Steht ein Spieler (Füße auf Block x/y/z) auf dem Ziel in der Mitte des Irrgartens? Das Ziel liegt auf
     * dem Boden; ein Sprung darüber zählt auch.
     */
    public static boolean mazeGoal(int x, int y, int z) {
        return y >= 1 && y <= MAZE_GOAL_JUMP && x >= SpecialArenas.MAZE_GOAL_LO && x <= SpecialArenas.MAZE_GOAL_HI
                && z >= SpecialArenas.MAZE_GOAL_LO && z <= SpecialArenas.MAZE_GOAL_HI;
    }

    /** Herausgefallen (Sumo, Spleef, Brücke …)? */
    public static boolean fallen(double y, double voidY) {
        return y < voidY;
    }
}
