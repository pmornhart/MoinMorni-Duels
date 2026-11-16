package de.moinmorni.duels.fight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Wertung von Runden („Bester aus 3/5“), Boxen (Treffer) und Brücke (Tore). */
class TallyTest {

    @Test
    void bestOfNeedsTheMajority() {
        assertEquals(1, Tally.needed(1));
        assertEquals(2, Tally.needed(3));
        assertEquals(3, Tally.needed(5));
    }

    @Test
    void bestOfThreeEndsAfterTwoWins() {
        Tally t = new Tally(2, 3);
        assertFalse(t.roundWon(0), "1:0 – noch nicht entschieden");
        assertFalse(t.roundWon(1), "1:1");
        assertEquals("1:1", t.roundsText());
        assertTrue(t.roundWon(1), "1:2 – entschieden");
        assertEquals(1, t.leader());
    }

    @Test
    void sumoBestOfFiveCanGoTheDistance() {
        Tally t = new Tally(2, 5);
        int[] winners = {0, 1, 0, 1};
        for (int w : winners) {
            assertFalse(t.roundWon(w));
        }
        assertEquals("2:2", t.roundsText());
        assertEquals(-1, t.leader(), "gleich – wer jetzt nicht mehr gewinnt, spielt unentschieden");
        assertTrue(t.roundWon(0));
    }

    @Test
    void boxingCountsHitsUntilTheTarget() {
        Tally t = new Tally(2, 1);
        for (int i = 0; i < 99; i++) {
            assertFalse(t.add(0, 1, 100));
        }
        assertFalse(t.add(1, 1, 100));
        assertTrue(t.add(0, 1, 100), "der hundertste Treffer gewinnt");
        assertEquals("100:1", t.scoreText());
        assertEquals(0, t.leader());
    }

    @Test
    void boxingHitsStartAgainInTheNextRoundButRoundsStay() {
        Tally t = new Tally(2, 3);
        t.add(0, 1, 10);
        t.roundWon(0);
        t.newRound();
        assertEquals(0, t.score(0));
        assertEquals(1, t.rounds(0));
    }

    @Test
    void timeoutLeaderComparesRoundsFirstThenScore() {
        Tally t = new Tally(2, 3);
        t.roundWon(1);
        t.add(0, 37, 100);
        t.add(1, 12, 100);
        assertEquals(1, t.leader(), "mehr Runden zählen vor mehr Treffern");
        Tally hits = new Tally(2, 1);
        hits.add(0, 37, 100);
        hits.add(1, 52, 100);
        assertEquals(1, hits.leader(), "ohne Runden: mehr Treffer");
        assertEquals(-1, new Tally(2, 1).leader(), "nichts passiert = unentschieden");
    }

    @Test
    void bridgeGoalsToThree() {
        Tally t = new Tally(2, 1);
        assertFalse(t.add(0, 1, 3));
        assertFalse(t.add(1, 1, 3));
        assertFalse(t.add(0, 1, 3));
        assertEquals("2:1", t.scoreText());
        assertTrue(t.add(0, 1, 3));
    }

    @Test
    void freeForAllHasMoreTeams() {
        Tally t = new Tally(4, 1);
        assertEquals("0:0:0:0", t.roundsText());
        assertFalse(t.started());
        t.add(2, 1, 10);
        assertTrue(t.started());
        assertEquals(2, t.leader());
        assertFalse(t.roundWon(7), "kein solches Team");
    }
}
