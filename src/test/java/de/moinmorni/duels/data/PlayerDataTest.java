package de.moinmorni.duels.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.duels.kit.Ability;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.kit.Layout;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerDataTest {

    private static final UUID ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @Test
    void yamlRoundTripKeepsEverything() {
        PlayerData d = new PlayerData(ID, "Anna", 1000);
        d.ability(Ability.JAEGER);
        d.kit(Kit.KLASSISCH).record(KitStats.Result.WIN, 2, 0, 16, true);
        d.kit(Kit.MODERN).record(KitStats.Result.LOSS, 0, 1, 0, false);
        d.recordOverall(KitStats.Result.WIN);
        d.layout("klassisch", Layout.parse("sword:4"));
        d.layout("faehigkeiten_jaeger", Layout.parse("bow:0,ability:1"));

        PlayerData back = PlayerData.fromYaml(ID, d.toYaml(), 1000);
        assertEquals("Anna", back.name());
        assertEquals(Ability.JAEGER, back.ability());
        assertEquals(1016, back.kit(Kit.KLASSISCH).elo);
        assertEquals(1, back.kit(Kit.KLASSISCH).wins);
        assertEquals(2, back.kit(Kit.KLASSISCH).kills);
        assertEquals(1, back.kit(Kit.KLASSISCH).rankedGames);
        assertEquals(1, back.kit(Kit.MODERN).losses);
        assertEquals(1000, back.kit(Kit.MODERN).elo);
        assertEquals(1, back.bestStreak());
        assertEquals("sword:4", back.layout("klassisch").format());
        assertEquals("bow:0,ability:1", back.layout("faehigkeiten_jaeger").format());
        assertTrue(back.played(Kit.KLASSISCH));
        assertFalse(back.played(Kit.FAEHIGKEITEN));
    }

    @Test
    void newPlayersStartAtTheConfiguredRating() {
        PlayerData d = PlayerData.fromYaml(ID, "name: Ben\n", 1200);
        assertEquals(1200, d.kit(Kit.MODERN).elo);
        assertEquals(Ability.BERGFUEHRER, d.ability());
        assertTrue(d.layout("modern").isEmpty());
    }

    @Test
    void emptyLayoutRemovesTheEntry() {
        PlayerData d = new PlayerData(ID, "Anna", 1000);
        d.layout("modern", Layout.parse("axe:0"));
        d.layout("modern", Layout.EMPTY);
        assertFalse(d.toYaml().contains("anordnung"));
    }
}
