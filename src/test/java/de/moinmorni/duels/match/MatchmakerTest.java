package de.moinmorni.duels.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MatchmakerTest {

    private static final long SECOND = 1000L;
    private final Matchmaker mm = new Matchmaker(100, 50, 5, 1000);

    private static Matchmaker.Entry solo(String name, double elo, long since) {
        return new Matchmaker.Entry(UUID.nameUUIDFromBytes(("e" + name).getBytes()),
                List.of(UUID.nameUUIDFromBytes(name.getBytes())), elo, since);
    }

    private static Matchmaker.Entry party(String a, String b, double elo, long since) {
        return new Matchmaker.Entry(UUID.nameUUIDFromBytes(("p" + a + b).getBytes()),
                List.of(UUID.nameUUIDFromBytes(a.getBytes()), UUID.nameUUIDFromBytes(b.getBytes())), elo, since);
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    @Test
    void casualIsFirstComeFirstServed() {
        var matches = mm.find(List.of(solo("A", 1000, 0), solo("B", 2000, SECOND), solo("C", 1000, 2 * SECOND)),
                1, false, 3 * SECOND);
        assertEquals(1, matches.size());
        assertEquals(List.of(id("A")), matches.get(0).teamA());
        assertEquals(List.of(id("B")), matches.get(0).teamB());
    }

    @Test
    void casualOddPlayerWaits() {
        assertEquals(0, mm.find(List.of(solo("A", 1000, 0)), 1, false, 0).size());
    }

    @Test
    void rankedMatchesSimilarRatingsAtOnce() {
        var matches = mm.find(List.of(solo("A", 1000, 0), solo("B", 1080, 0)), 1, true, 0);
        assertEquals(1, matches.size());
    }

    @Test
    void rankedWaitsUntilRangeHasGrown() {
        var waiting = List.of(solo("A", 1000, 0), solo("B", 1300, 0));
        assertEquals(0, mm.find(waiting, 1, true, 0).size(), "300 apart: not at once");
        assertEquals(0, mm.find(waiting, 1, true, 19 * SECOND).size(), "after 19 s range is 250");
        assertEquals(1, mm.find(waiting, 1, true, 20 * SECOND).size(), "after 20 s range is 300");
    }

    @Test
    void rangeGrowsAndIsCapped() {
        assertEquals(100, mm.range(0, 4 * SECOND));
        assertEquals(150, mm.range(0, 5 * SECOND));
        assertEquals(600, mm.range(0, 50 * SECOND));
        assertEquals(1000, mm.range(0, 3600 * SECOND));
    }

    @Test
    void theLongerWaitingPlayerWidensTheSearch() {
        // A wartet schon lange (Spanne 600), B kommt gerade (100): Abstand 400 passt in A's Spanne.
        var matches = mm.find(List.of(solo("A", 1000, 0), solo("B", 1400, 50 * SECOND)), 1, true, 50 * SECOND);
        assertEquals(1, matches.size());
    }

    @Test
    void rankedPicksTheClosestOpponent() {
        var matches = mm.find(List.of(solo("A", 1000, 0), solo("B", 1090, SECOND), solo("C", 1010, 2 * SECOND)),
                1, true, 2 * SECOND);
        assertEquals(1, matches.size());
        assertEquals(List.of(id("A")), matches.get(0).teamA());
        assertEquals(List.of(id("C")), matches.get(0).teamB());
    }

    @Test
    void soloPlayersFormTeamsInTwoVsTwo() {
        var matches = mm.find(List.of(solo("A", 1000, 0), solo("B", 1000, 1), solo("C", 1000, 2),
                solo("D", 1000, 3)), 2, false, 10);
        assertEquals(1, matches.size());
        assertEquals(Set.of(id("A"), id("B")), Set.copyOf(matches.get(0).teamA()));
        assertEquals(Set.of(id("C"), id("D")), Set.copyOf(matches.get(0).teamB()));
    }

    @Test
    void partyStaysTogether() {
        var matches = mm.find(List.of(solo("C", 1000, 0), party("A", "B", 1000, 1), solo("D", 1000, 2)),
                2, false, 10);
        assertEquals(1, matches.size());
        Set<Set<UUID>> teams = Set.of(Set.copyOf(matches.get(0).teamA()), Set.copyOf(matches.get(0).teamB()));
        assertTrue(teams.contains(Set.of(id("A"), id("B"))));
        assertTrue(teams.contains(Set.of(id("C"), id("D"))));
    }

    @Test
    void threeSolosAreNotEnoughForTwoVsTwo() {
        assertEquals(0, mm.find(List.of(solo("A", 1000, 0), solo("B", 1000, 1), solo("C", 1000, 2)), 2, false, 10)
                .size());
    }

    @Test
    void nobodyIsMatchedTwice() {
        var waiting = List.of(solo("A", 1000, 0), solo("B", 1000, 1), solo("C", 1000, 2), solo("D", 1000, 3),
                solo("E", 1000, 4));
        var matches = mm.find(waiting, 1, true, 10);
        assertEquals(2, matches.size());
        var all = matches.stream().flatMap(m -> m.entries().stream()).map(Matchmaker.Entry::id)
                .collect(Collectors.toList());
        assertEquals(all.size(), Set.copyOf(all).size());
    }
}
