package de.moinmorni.duels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.duels.kit.Kit;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KitSettingsTest {

    private static KitSettings of(String yaml) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return KitSettings.load(c);
    }

    @Test
    void shippedConfigListsEveryKitSwitchedOn() throws Exception {
        try (var in = new InputStreamReader(KitSettingsTest.class.getResourceAsStream("/config.yml"),
                StandardCharsets.UTF_8)) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(in);
            for (Kit k : Kit.values()) {
                assertTrue(c.isConfigurationSection("kits." + k.id()), "config.yml erklärt " + k.id());
                assertTrue(c.getBoolean("kits." + k.id() + ".an"), k.id() + " ist an");
            }
            KitSettings s = KitSettings.load(c);
            assertEquals(2.0, s.sumoDepth());
            assertEquals(100, s.boxingHits());
            assertEquals(3, s.bridgeGoals());
            assertEquals(2, s.comboPauseTicks());
            assertEquals(1.0, s.rodPull());
            assertTrue(s.mazeCompass());
            assertTrue(s.mazeGoal());
            assertTrue(s.mazeReshuffle());
            assertEquals(8, s.partyMax());
            for (Kit k : Kit.values()) {
                assertEquals(1, s.rounds(k), k.id());
            }
        }
    }

    @Test
    void missingValuesUseTheDefaultsAndLimitsHold() throws Exception {
        KitSettings s = of("welt: duels");
        assertTrue(s.on(Kit.SUMO));
        assertEquals(100, s.boxingHits());
        KitSettings t = of("""
                kits:
                  sumo:
                    an: false
                    runden: 4
                  klassisch:
                    runden: 2
                  bruecke:
                    runden: 5
                boxen:
                  treffer: 0
                bruecke:
                  tore: 99
                party:
                  max-spieler: 50
                kombo:
                  treffer-pause-ticks: -3
                labyrinth:
                  ziel: false
                  jede-runde-neu: false
                """);
        assertTrue(s.mazeGoal() && s.mazeReshuffle(), "ohne Angabe: Ziel an, jede Runde neu");
        assertFalse(t.mazeGoal());
        assertFalse(t.mazeReshuffle());
        assertFalse(t.on(Kit.SUMO));
        assertTrue(t.on(Kit.KLASSISCH));
        assertEquals(5, t.rounds(Kit.SUMO), "4 → Bester aus 5");
        assertEquals(3, t.rounds(Kit.KLASSISCH), "2 → Bester aus 3");
        assertEquals(1, t.rounds(Kit.BRUECKE), "die Brücke zählt Tore, keine Runden");
        assertEquals(1, t.boxingHits());
        assertEquals(20, t.bridgeGoals());
        assertEquals(KitSettings.MAX_TEAM, t.partyMax());
        assertEquals(0, t.comboPauseTicks());
    }

    @Test
    void bestOfRoundsToOneThreeOrFive() {
        assertEquals(1, KitSettings.bestOf(0));
        assertEquals(1, KitSettings.bestOf(1));
        assertEquals(3, KitSettings.bestOf(2));
        assertEquals(3, KitSettings.bestOf(3));
        assertEquals(5, KitSettings.bestOf(4));
        assertEquals(5, KitSettings.bestOf(9));
    }
}
