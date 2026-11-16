package de.moinmorni.duels.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.duels.fight.Zones;
import de.moinmorni.duels.kit.Kit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Startplätze und Grenzen aller Arenen – ohne Server. */
class ArenaShapeTest {

    @Test
    void normalArenasKeepTheOldStartingSpots() {
        ArenaShape s = ArenaPlans.shape(ArenaType.BURGHOF);
        assertArrayEquals(new int[] {0, -16}, s.starts().side(0, 1).get(0));
        assertArrayEquals(new int[] {0, 16}, s.starts().side(1, 1).get(0));
        List<int[]> two = s.starts().side(0, 2);
        assertArrayEquals(new int[] {-2, -16}, two.get(0));
        assertArrayEquals(new int[] {2, -16}, two.get(1));
    }

    @Test
    void everyArenaHasRoomForBigPartiesInsideItsBox() {
        for (ArenaType t : ArenaType.values()) {
            ArenaShape s = ArenaPlans.shape(t);
            for (int side = 0; side < 2; side++) {
                for (int n = 1; n <= Arena.MAX_TEAM; n++) {
                    List<int[]> spots = s.starts().side(side, n);
                    assertEquals(n, spots.size(), t + " Seite " + side + ", " + n + " Leute");
                    for (int[] xz : spots) {
                        assertTrue(Math.abs(xz[0]) <= s.halfX() && Math.abs(xz[1]) <= s.halfZ(), t + " " + xz[0] + "/" + xz[1]);
                    }
                }
            }
            assertEquals(ArenaShape.RING, s.starts().ring(ArenaShape.RING).size());
        }
    }

    @Test
    void sumoStartsOnTheDanceFloorFacingEachOther() {
        ArenaShape s = ArenaPlans.shape(ArenaType.TANZBODEN);
        for (int n = 1; n <= 4; n++) {
            for (int side = 0; side < 2; side++) {
                for (int[] xz : s.starts().side(side, n)) {
                    assertTrue(Math.hypot(xz[0], xz[1]) <= SpecialArenas.DANCE_RADIUS - 1, "auf dem Tanzboden");
                }
            }
        }
        for (int[] xz : s.starts().ring(ArenaShape.RING)) {
            assertTrue(Math.hypot(xz[0], xz[1]) <= SpecialArenas.DANCE_RADIUS - 1);
        }
    }

    @Test
    void spleefStartsOnTheSnow() {
        ArenaShape s = ArenaPlans.shape(ArenaType.SCHNEEFELD);
        for (int side = 0; side < 2; side++) {
            for (int[] xz : s.starts().side(side, 4)) {
                assertTrue(Zones.spleefBreakable(xz[0], 0, xz[1]), "steht auf Schnee: " + xz[0] + "/" + xz[1]);
            }
        }
    }

    @Test
    void mazeStartsInOppositeCornerCells() {
        ArenaShape s = ArenaPlans.shape(ArenaType.IRRGARTEN);
        int[] a = s.starts().side(0, 1).get(0);
        int[] b = s.starts().side(1, 1).get(0);
        assertTrue(a[0] < 0 && a[1] < 0 && b[0] > 0 && b[1] > 0, "Nordwest gegen Südost");
        assertTrue(Math.hypot(b[0] - a[0], b[1] - a[1]) > 40, "weit auseinander");
        for (int[] xz : s.starts().side(0, 4)) {
            // in der Eckzelle (zwei Blöcke Weg), nie in einer Hecke
            assertTrue(Math.floorMod(xz[0] - SpecialArenas.MAZE_ORIGIN, SpecialArenas.MAZE_CELL) != 0);
            assertTrue(Math.floorMod(xz[1] - SpecialArenas.MAZE_ORIGIN, SpecialArenas.MAZE_CELL) != 0);
        }
    }

    @Test
    void mazeRingStartsInTheFourCornerCellsFirst() {
        ArenaShape s = ArenaPlans.shape(ArenaType.IRRGARTEN);
        java.util.List<int[]> ring = s.starts().ring(ArenaShape.RING);
        java.util.Set<String> corners = new java.util.HashSet<>();
        for (int i = 0; i < SpecialArenas.MAZE_CORNERS; i++) {
            int cx = Math.floorDiv(ring.get(i)[0] - SpecialArenas.MAZE_ORIGIN, SpecialArenas.MAZE_CELL);
            int cz = Math.floorDiv(ring.get(i)[1] - SpecialArenas.MAZE_ORIGIN, SpecialArenas.MAZE_CELL);
            assertTrue((cx == 0 || cx == SpecialArenas.MAZE_N - 1) && (cz == 0 || cz == SpecialArenas.MAZE_N - 1),
                    "Ecke: " + cx + "/" + cz);
            corners.add(cx + "/" + cz);
        }
        assertEquals(4, corners.size(), "vier verschiedene Ecken");
    }

    @Test
    void bridgeStartsOnTheIslandsInFrontOfTheirOwnGoal() {
        ArenaShape s = ArenaPlans.shape(ArenaType.KLAMMBRUECKE);
        for (int side = 0; side < 2; side++) {
            for (int[] xz : s.starts().side(side, 4)) {
                assertEquals(-1, Zones.bridgeGoal(xz[0], 1, xz[1]), "nicht im Tor");
                int own = side == 0 ? -SpecialArenas.BRIDGE_GOAL : SpecialArenas.BRIDGE_GOAL;
                assertTrue(Math.abs(xz[1] - own) < Math.abs(xz[1] + own), "näher am eigenen Tor");
            }
        }
    }

    @Test
    void specialKitsHaveTheirOwnArena() {
        assertEquals(Optional.of(ArenaType.TANZBODEN), ArenaType.of(Kit.SUMO));
        assertEquals(Optional.of(ArenaType.SCHNEEFELD), ArenaType.of(Kit.SPLEEF));
        assertEquals(Optional.of(ArenaType.IRRGARTEN), ArenaType.of(Kit.LABYRINTH));
        assertEquals(Optional.of(ArenaType.KLAMMBRUECKE), ArenaType.of(Kit.BRUECKE));
        assertTrue(ArenaType.of(Kit.KLASSISCH).isEmpty());
        assertEquals(4, ArenaType.normals().size());
        assertTrue(ArenaType.BURGHOF.fits(Kit.UHC));
        assertFalse(ArenaType.BURGHOF.fits(Kit.SUMO), "Sumo nur auf dem Tanzboden");
        assertFalse(ArenaType.TANZBODEN.fits(Kit.KLASSISCH));
        assertEquals(Optional.of(ArenaType.WIRTSHAUSGARTEN), ArenaType.parseNormal("Wirtshausgarten"));
        assertTrue(ArenaType.parseNormal("tanzboden").isEmpty(), "eigene Arenen wählt man nicht");
    }

    @Test
    void ringFacesTheCentre() {
        assertEquals(0f, ArenaShape.yawToCentre(0, -10), 0.01f);
        assertEquals(180f, Math.abs(ArenaShape.yawToCentre(0, 10)), 0.01f);
        assertEquals(90f, ArenaShape.yawToCentre(10, 0), 0.01f);
    }
}
