package de.moinmorni.duels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Party-Kampf „Zwei Teams“: die Party wird zufällig und gleich groß geteilt, niemand fehlt, niemand doppelt. */
class PartyFightsTest {

    private static List<UUID> party(int n) {
        List<UUID> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new UUID(0, i));
        }
        return list;
    }

    @Test
    void splitIsEvenAndComplete() {
        for (int n = 2; n <= 8; n++) {
            List<UUID> members = party(n);
            List<List<UUID>> teams = PartyFights.splitTeams(members, new Random(n));
            assertEquals(2, teams.size());
            assertEquals((n + 1) / 2, teams.get(0).size(), "erstes Team bei " + n);
            assertEquals(n / 2, teams.get(1).size(), "zweites Team bei " + n);
            Set<UUID> all = new HashSet<>(teams.get(0));
            all.addAll(teams.get(1));
            assertEquals(new HashSet<>(members), all, "alle dabei, keiner doppelt");
        }
    }

    @Test
    void splitIsRandom() {
        List<UUID> members = party(4);
        Set<Set<UUID>> seen = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            seen.add(new HashSet<>(PartyFights.splitTeams(members, new Random(seed)).get(0)));
        }
        assertTrue(seen.size() > 2, "verschiedene Aufteilungen: " + seen.size());
    }
}
