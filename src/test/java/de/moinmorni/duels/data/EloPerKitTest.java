package de.moinmorni.duels.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.match.Elo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Jede Kampfart hat ihre eigene Wertung und Rangliste – Sumo-Siege ändern die Klassisch-Wertung nicht. */
class EloPerKitTest {

    private static final UUID ANNA = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID BERT = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    void everyKitKeepsItsOwnRating() {
        PlayerData d = new PlayerData(ANNA, "Anna", 1000);
        d.kit(Kit.SUMO).record(KitStats.Result.WIN, 0, 0, Elo.delta(1000, 1000, 1, 32), true);
        d.kit(Kit.BRUECKE).record(KitStats.Result.LOSS, 2, 1, Elo.delta(1000, 1000, 0, 32), true);
        d.kit(Kit.UHC).record(KitStats.Result.WIN, 1, 0, 0, false);
        assertEquals(1016, d.kit(Kit.SUMO).elo);
        assertEquals(984, d.kit(Kit.BRUECKE).elo);
        assertEquals(1000, d.kit(Kit.UHC).elo, "locker: Wertung bleibt");
        assertEquals(1000, d.kit(Kit.KLASSISCH).elo, "andere Kampfarten bleiben unberührt");
        PlayerData back = PlayerData.fromYaml(ANNA, d.toYaml(), 1000);
        assertEquals(1016, back.kit(Kit.SUMO).elo);
        assertEquals(984, back.kit(Kit.BRUECKE).elo);
        assertEquals(1, back.kit(Kit.UHC).wins);
        assertTrue(back.toYaml().contains("sumo:") && back.toYaml().contains("bruecke:"));
    }

    @Test
    void leaderboardIsPerKitAndSurvivesSaving() throws Exception {
        Path dir = Files.createTempDirectory("duels-rangliste");
        Leaderboard board = new Leaderboard(Logger.getAnonymousLogger(), dir);
        PlayerData anna = new PlayerData(ANNA, "Anna", 1000);
        PlayerData bert = new PlayerData(BERT, "Bert", 1000);
        anna.kit(Kit.SUMO).record(KitStats.Result.WIN, 0, 0, 16, true);
        bert.kit(Kit.SUMO).record(KitStats.Result.LOSS, 0, 0, -16, true);
        bert.kit(Kit.BOXEN).record(KitStats.Result.WIN, 0, 0, 16, true);
        board.update(anna);
        board.update(bert);
        assertEquals("Anna", board.top(Kit.SUMO, 5).get(0).name());
        assertEquals("Bert", board.top(Kit.BOXEN, 5).get(0).name());
        assertEquals(1, board.top(Kit.BOXEN, 5).size(), "Anna hat nicht gewertet geboxt");
        assertTrue(board.top(Kit.KLASSISCH, 5).isEmpty());
        Files.writeString(board.file(), board.toYaml());
        Leaderboard again = new Leaderboard(Logger.getAnonymousLogger(), dir);
        again.load();
        assertEquals(1016, again.top(Kit.SUMO, 5).get(0).elo());
        assertEquals(2, again.position(Kit.SUMO, BERT));
        assertEquals(1, again.position(Kit.BOXEN, BERT));
    }
}
