package net.morni.duels.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KitStatsTest {

    @Test
    void winsBuildAStreakAndALossEndsIt() {
        KitStats s = new KitStats(1000);
        s.record(KitStats.Result.WIN, 1, 0, 16, true);
        s.record(KitStats.Result.WIN, 1, 0, 15, true);
        s.record(KitStats.Result.DRAW, 0, 0, 0, true);
        assertEquals(2, s.streak, "Unentschieden lässt die Serie stehen");
        s.record(KitStats.Result.LOSS, 0, 1, -17, true);
        assertEquals(0, s.streak);
        assertEquals(2, s.bestStreak);
        assertEquals(2, s.wins);
        assertEquals(1, s.losses);
        assertEquals(1, s.draws);
        assertEquals(4, s.games);
        assertEquals(4, s.rankedGames);
        assertEquals(2, s.kills);
        assertEquals(1, s.deaths);
        assertEquals(1014, s.elo);
    }

    @Test
    void casualFightsDoNotChangeTheRating() {
        KitStats s = new KitStats(1000);
        s.record(KitStats.Result.WIN, 1, 0, 16, false);
        assertEquals(1000, s.elo);
        assertEquals(0, s.rankedGames);
        assertEquals(1, s.games);
    }

    @Test
    void ratingNeverGoesBelowZero() {
        KitStats s = new KitStats(10);
        s.record(KitStats.Result.LOSS, 0, 1, -32, true);
        assertEquals(0, s.elo);
    }

    @Test
    void scoresForElo() {
        assertEquals(1.0, KitStats.Result.WIN.score());
        assertEquals(0.5, KitStats.Result.DRAW.score());
        assertEquals(0.0, KitStats.Result.LOSS.score());
    }
}
