package de.moinmorni.duels.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EloTest {

    @Test
    void equalRatingsExpectHalf() {
        assertEquals(0.5, Elo.expected(1000, 1000), 1e-9);
    }

    @Test
    void equalRatingsWinAndLoseSixteenWithK32() {
        assertEquals(16, Elo.delta(1000, 1000, 1.0, 32));
        assertEquals(-16, Elo.delta(1000, 1000, 0.0, 32));
        assertEquals(0, Elo.delta(1000, 1000, 0.5, 32));
    }

    @Test
    void beatingAStrongerPlayerGivesMore() {
        int upset = Elo.delta(1000, 1400, 1.0, 32);
        int expectedWin = Elo.delta(1400, 1000, 1.0, 32);
        assertEquals(29, upset);
        assertEquals(3, expectedWin);
        assertTrue(upset > expectedWin);
    }

    @Test
    void gainAndLossBalanceOut() {
        for (int a = 600; a <= 1800; a += 150) {
            for (int b = 600; b <= 1800; b += 150) {
                double win = 32 * (1 - Elo.expected(a, b));
                double loss = 32 * (0 - Elo.expected(b, a));
                assertEquals(0.0, win + loss, 1e-9);
            }
        }
    }

    @Test
    void drawAgainstStrongerGainsALittle() {
        assertTrue(Elo.delta(1000, 1200, 0.5, 32) > 0);
        assertTrue(Elo.delta(1200, 1000, 0.5, 32) < 0);
    }
}
