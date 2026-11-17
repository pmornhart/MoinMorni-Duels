package de.moinmorni.duels.fight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.duels.world.SpecialArenas;
import org.junit.jupiter.api.Test;

/**
 * Wo in den besonderen Arenen was gilt: Tore der Brücke, Bauen, Schnee des Spleef, Herausfallen beim Sumo, Ziel im
 * Irrgarten.
 */
class ZonesTest {

    private static final int GOAL = SpecialArenas.BRIDGE_GOAL;

    @Test
    void bridgeGoalsAreTheShaftsBelowTheFloor() {
        assertEquals(0, Zones.bridgeGoal(0, 0, -GOAL), "Tor im Norden (Team Blau)");
        assertEquals(1, Zones.bridgeGoal(1, -3, GOAL + 1), "Tor im Süden (Team Rot)");
        assertEquals(-1, Zones.bridgeGoal(0, 1, GOAL), "auf dem Boden stehen ist noch kein Tor");
        assertEquals(-1, Zones.bridgeGoal(2, 0, GOAL), "neben dem Schacht");
        assertEquals(-1, Zones.bridgeGoal(0, 0, GOAL + 2), "hinter dem Schacht");
        assertEquals(-1, Zones.bridgeGoal(0, -10, GOAL), "tief unten ist die Klamm, kein Tor");
        assertEquals(-1, Zones.bridgeGoal(0, 0, 0), "Mitte der Brücke");
    }

    @Test
    void bridgeBuildingKeepsGoalsOpenAndStaysInside() {
        assertTrue(Zones.bridgeBuildable(4, 1, 0), "neben der Brücke bauen");
        assertTrue(Zones.bridgeBuildable(0, -2, 5), "unter der Brücke");
        assertFalse(Zones.bridgeBuildable(0, 1, GOAL), "auf dem Tor");
        assertFalse(Zones.bridgeBuildable(2, 3, -GOAL - 2), "auf dem Rand des Tors");
        assertTrue(Zones.bridgeBuildable(0, 4, GOAL), "hoch über dem Tor geht");
        assertFalse(Zones.bridgeBuildable(SpecialArenas.BRIDGE_HALF_X + 1, 1, 0), "durch die Wand");
        assertFalse(Zones.bridgeBuildable(0, SpecialArenas.BRIDGE_BUILD_TOP + 1, 0), "zu hoch");
        assertFalse(Zones.bridgeBuildable(0, SpecialArenas.BRIDGE_BUILD_BOTTOM - 1, 0), "zu tief");
    }

    @Test
    void spleefOnlyTheSnowOfTheField() {
        assertTrue(Zones.spleefBreakable(0, 0, 0));
        assertTrue(Zones.spleefBreakable(SpecialArenas.SNOW_HALF, 0, -SpecialArenas.SNOW_HALF));
        assertFalse(Zones.spleefBreakable(SpecialArenas.SNOW_HALF + 1, 0, 0), "die Eiswand");
        assertFalse(Zones.spleefBreakable(0, -1, 0), "unter dem Feld");
        assertFalse(Zones.spleefBreakable(0, 1, 0), "über dem Feld");
    }

    @Test
    void normalArenasBuildInsideTheWalls() {
        assertTrue(Zones.arenaBuildable(0, 1, 0));
        assertTrue(Zones.arenaBuildable(24, 20, -24));
        assertFalse(Zones.arenaBuildable(25, 1, 0));
        assertFalse(Zones.arenaBuildable(0, 0, 0), "nicht im Boden");
        assertFalse(Zones.arenaBuildable(0, 21, 0), "nicht über der Decke");
    }

    @Test
    void sumoOutWhenFallenDeeperThanTheSetting() {
        int floor = 100;
        double depth = 2.0;
        double voidY = Zones.sumoVoid(floor, depth);
        assertFalse(Zones.fallen(floor + 1, voidY), "steht auf dem Tanzboden");
        assertFalse(Zones.fallen(floor - 0.5, voidY), "rutscht gerade über die Kante");
        assertTrue(Zones.fallen(floor - 1.1, voidY), "zwei Blöcke tiefer: draußen");
        assertTrue(Zones.fallen(floor - 1.1, Zones.sumoVoid(floor, 2.0)));
        assertFalse(Zones.fallen(floor - 1.1, Zones.sumoVoid(floor, 5.0)), "mit mehr Tiefe noch drin");
    }

    @Test
    void mazeGoalIsTheSquareInTheMiddleAndFairForBothCorners() {
        assertTrue(Zones.mazeGoal(0, 1, 0), "mitten auf dem Ziel");
        assertTrue(Zones.mazeGoal(-2, 1, 1), "am Rand des Ziels");
        assertTrue(Zones.mazeGoal(-1, 2, -1), "im Sprung über dem Ziel");
        assertFalse(Zones.mazeGoal(-1, 0, -1), "im Boden ist niemand");
        assertFalse(Zones.mazeGoal(-1, 3, -1), "zu hoch");
        assertFalse(Zones.mazeGoal(2, 1, 0), "daneben");
        assertFalse(Zones.mazeGoal(-3, 1, 0), "daneben");
        // Mitte des Irrgartens liegt zwischen zwei Blöcken: das Ziel liegt genau darum herum (beide Ecken gleich weit)
        int first = SpecialArenas.mazeCell(0);
        int lastCell = SpecialArenas.mazeCell(SpecialArenas.MAZE_N - 1) + 1;
        assertEquals(first + lastCell, SpecialArenas.MAZE_GOAL_LO + SpecialArenas.MAZE_GOAL_HI,
                "Ziel liegt symmetrisch zwischen den Ecken");
        assertFalse(Zones.mazeGoal(first, 1, first), "Startecke ist nicht das Ziel");
        // Das Ziel liegt ganz im offenen Platz (mittlere 3 × 3 Zellen)
        int plazaLo = SpecialArenas.mazeCell(SpecialArenas.MAZE_N / 2 - 1);
        int plazaHi = SpecialArenas.mazeCell(SpecialArenas.MAZE_N / 2 + 1) + 1;
        assertTrue(SpecialArenas.MAZE_GOAL_LO > plazaLo && SpecialArenas.MAZE_GOAL_HI < plazaHi);
    }
}
