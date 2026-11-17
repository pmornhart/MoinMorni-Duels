package de.moinmorni.duels.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Der Irrgarten des Labyrinths: jede Zelle erreichbar, Ecken verbunden, Platz in der Mitte, gleicher Startwert = gleicher
 * Irrgarten, drehsymmetrisch (alle vier Startecken gleich weit vom Ziel).
 */
class MazeTest {

    private static final int N = SpecialArenas.MAZE_N;

    @Test
    void everyCellCanBeReachedFromEveryCorner() {
        Maze m = Maze.generate(N, SpecialArenas.MAZE_SEED, 0.55);
        assertTrue(m.connected());
        int d = m.distance(0, 0, N - 1, N - 1);
        assertTrue(d >= N - 1, "von Ecke zu Ecke mindestens quer durch: " + d);
        assertTrue(m.distance(N - 1, 0, 0, N - 1) > 0);
    }

    @Test
    void sameSeedSameMazeOtherSeedOtherMaze() {
        Maze a = Maze.generate(N, 7, 0.5);
        Maze b = Maze.generate(N, 7, 0.5);
        Maze c = Maze.generate(N, 8, 0.5);
        boolean same = true;
        boolean other = false;
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                same &= a.openEast(x, z) == b.openEast(x, z) && a.openSouth(x, z) == b.openSouth(x, z);
                other |= a.openEast(x, z) != c.openEast(x, z) || a.openSouth(x, z) != c.openSouth(x, z);
            }
        }
        assertTrue(same);
        assertTrue(other);
    }

    @Test
    void braidingRemovesDeadEnds() {
        for (long seed = 1; seed <= 20; seed++) {
            Maze perfect = Maze.generate(N, seed, 0.0);
            Maze braided = Maze.generate(N, seed, 0.6);
            assertTrue(braided.deadEnds() < perfect.deadEnds(), "Startwert " + seed);
            assertTrue(braided.connected());
        }
    }

    @Test
    void centreIsAnOpenSquare() {
        Maze m = Maze.generate(N, SpecialArenas.MAZE_SEED, 0.55);
        int c = N / 2;
        for (int x = c - 1; x < c + 1; x++) {
            for (int z = c - 1; z <= c + 1; z++) {
                assertTrue(m.openEast(x, z), "Platz offen bei " + x + "/" + z);
            }
        }
        assertTrue(m.degree(c, c) == 4, "Mitte nach allen Seiten offen");
    }

    @Test
    void tooSmallIsRefusedAndOutsideIsClosed() {
        assertThrows(IllegalArgumentException.class, () -> Maze.generate(3, 1, 0));
        Maze m = Maze.generate(N, 1, 0);
        assertTrue(!m.openEast(N - 1, 0) && !m.openSouth(0, N - 1), "am Rand geht es nicht hinaus");
        assertEquals(N, m.size());
    }

    @Test
    void everyMazeIsFairAllFourCornersEquallyFarFromTheGoal() {
        int c = N / 2;
        for (long seed = 1; seed <= 300; seed++) {
            Maze m = Maze.generate(N, seed, 0.55);
            assertTrue(m.symmetric(), "drehsymmetrisch, Startwert " + seed);
            assertTrue(m.connected(), "alles erreichbar, Startwert " + seed);
            int a = m.distance(0, 0, c, c);
            assertEquals(a, m.distance(N - 1, N - 1, c, c), "gegenüber gleich weit, Startwert " + seed);
            assertEquals(a, m.distance(N - 1, 0, c, c), "Ecke rechts gleich weit, Startwert " + seed);
            assertEquals(a, m.distance(0, N - 1, c, c), "Ecke links gleich weit, Startwert " + seed);
            assertTrue(a >= c, "nicht kürzer als quer durch: " + a);
        }
    }

    @Test
    void turningFourTimesIsTheSameCell() {
        Maze m = Maze.generate(N, 3, 0.5);
        int[] p = {2, 7};
        for (int k = 0; k < 4; k++) {
            p = m.turn(p[0], p[1]);
        }
        assertEquals(2, p[0]);
        assertEquals(7, p[1]);
        assertTrue(m.linked(N / 2, N / 2, N / 2 + 1, N / 2), "Mitte offen");
        assertTrue(!m.linked(0, 0, -1, 0), "nach außen zu");
    }

    @Test
    void perfectMazeIsATreeBeforeBraiding() {
        // Ohne geöffnete Sackgassen: genau n² - 1 Durchgänge (jede Zelle auf genau einem Weg) – bis auf den Platz.
        Maze m = Maze.generate(N, 42, 0.0);
        int open = 0;
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                open += (m.openEast(x, z) ? 1 : 0) + (m.openSouth(x, z) ? 1 : 0);
            }
        }
        assertTrue(open >= N * N - 1, "mindestens ein Baum: " + open);
        assertTrue(m.symmetric());
    }

    @Test
    void evenSizeHasNoMiddle() {
        assertThrows(IllegalArgumentException.class, () -> Maze.generate(10, 1, 0));
    }
}
