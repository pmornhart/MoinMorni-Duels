package de.moinmorni.duels.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class KitTest {

    /** IDs stehen in Dateien und Statistiken: klein, ohne Umlaute (wie es das Netzwerk für Werte erlaubt). */
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]*$");

    @Test
    void parsesGermanEnglishAndUmlauts() {
        assertEquals(Optional.of(Kit.KLASSISCH), Kit.parse("Klassisch"));
        assertEquals(Optional.of(Kit.KLASSISCH), Kit.parse("classic"));
        assertEquals(Optional.of(Kit.MODERN), Kit.parse("MODERN"));
        assertEquals(Optional.of(Kit.FAEHIGKEITEN), Kit.parse("fähigkeiten"));
        assertEquals(Optional.of(Kit.FAEHIGKEITEN), Kit.parse("abilities"));
        assertTrue(Kit.parse("zaubern").isEmpty());
        assertTrue(Kit.parse(null).isEmpty());
    }

    @Test
    void parsesTheNewKitsByNameAndAlias() {
        assertEquals(Optional.of(Kit.SUMO), Kit.parse("sumo"));
        assertEquals(Optional.of(Kit.SCHWERT), Kit.parse("Nur Schwert"));
        assertEquals(Optional.of(Kit.SCHWERT), Kit.parse("sword"));
        assertEquals(Optional.of(Kit.BRUECKE), Kit.parse("Brücke"));
        assertEquals(Optional.of(Kit.BRUECKE), Kit.parse("bridge"));
        assertEquals(Optional.of(Kit.LABYRINTH), Kit.parse("maze"));
        assertEquals(Optional.of(Kit.TRAENKE), Kit.parse("Tränke"));
        assertEquals(Optional.of(Kit.AXT), Kit.parse("Axt und Schild"));
        assertEquals(Optional.of(Kit.BAUKAMPF), Kit.parse("bau-kampf"));
        assertEquals(Optional.of(Kit.PERLEN), Kit.parse("Enderperlen"));
        assertEquals(Optional.of(Kit.GOLDAPFEL), Kit.parse("gapple"));
        assertEquals(Optional.of(Kit.BOXEN), Kit.parse("boxing"));
        assertEquals(Optional.of(Kit.SPLEEF), Kit.parse("Spleef"));
        assertEquals(Optional.of(Kit.UHC), Kit.parse("UHC"));
        assertEquals(Optional.of(Kit.RITTER), Kit.parse("iron"));
        assertEquals(Optional.of(Kit.DIAMANT), Kit.parse("diamond"));
        assertEquals(Optional.of(Kit.BOGEN), Kit.parse("bow"));
        assertEquals(Optional.of(Kit.ANGEL), Kit.parse("rod"));
        assertEquals(Optional.of(Kit.KOMBO), Kit.parse("combo"));
    }

    @Test
    void everyKitHasAUniqueSafeIdAndAnAliasesNeverClash() {
        Set<String> ids = new HashSet<>();
        for (Kit k : Kit.values()) {
            assertTrue(ID.matcher(k.id()).matches(), k.id());
            assertTrue(ids.add(k.id()), "doppelt: " + k.id());
            assertEquals(Optional.of(k), Kit.parse(k.id()), "ID liest sich zurück: " + k.id());
        }
        assertTrue(Kit.values().length >= 20, "mindestens 20 Kampfarten");
    }

    @Test
    void groupsFitIntoOneMenuRowEach() {
        int total = 0;
        for (Kit.Group g : Kit.Group.values()) {
            int n = g.kits().size();
            assertTrue(n >= 1 && n <= 8, g + ": " + n + " Kampfarten passen nicht in eine Reihe");
            total += n;
        }
        assertEquals(Kit.values().length, total);
        assertTrue(Kit.Group.KLASSISCH.kits().contains(Kit.KLASSISCH));
        assertTrue(Kit.Group.MODERN.kits().contains(Kit.UHC));
        assertTrue(Kit.Group.SPASS.kits().contains(Kit.BRUECKE));
    }

    @Test
    void rulesOfTheSpecialKits() {
        assertFalse(Kit.SUMO.hitsHurt(), "Sumo: Treffer stoßen nur");
        assertTrue(Kit.SUMO.hitsPush());
        assertFalse(Kit.BOXEN.hitsHurt());
        assertFalse(Kit.SPLEEF.hitsPush(), "Spleef: Treffer zählen gar nicht");
        assertTrue(Kit.SPLEEF.survival(), "Schaufeln geht nur im Überlebensmodus");
        assertTrue(Kit.UHC.building() && Kit.UHC.liquids() && !Kit.UHC.naturalRegen());
        assertTrue(Kit.BAUKAMPF.building() && !Kit.BAUKAMPF.liquids() && Kit.BAUKAMPF.naturalRegen());
        assertTrue(Kit.BRUECKE.building() && Kit.BRUECKE.respawns());
        assertFalse(Kit.BRUECKE.ffa(), "die Brücke braucht genau zwei Seiten");
        assertFalse(Kit.BRUECKE.rounds(), "die Brücke zählt Tore");
        assertFalse(Kit.SUMO.editable(), "beim Sumo gibt es nichts anzuordnen");
        assertTrue(Kit.KLASSISCH.classicCombat() && Kit.SCHWERT.classicCombat() && Kit.KOMBO.classicCombat());
        assertFalse(Kit.MODERN.classicCombat() || Kit.AXT.classicCombat() || Kit.UHC.classicCombat());
        for (Kit k : Kit.values()) {
            if (k != Kit.UHC) {
                assertTrue(k.naturalRegen(), k + " heilt normal");
            }
            if (!k.building()) {
                assertFalse(k.liquids(), k + ": Wasser/Lava nur mit Bauen");
            }
        }
    }

    @Test
    void abilitiesHaveOwnLoadoutIds() {
        assertEquals("faehigkeiten_jaeger", Ability.JAEGER.loadoutId());
        assertEquals(Optional.of(Ability.KRAEUTERFRAU), Ability.parse("Kräuterfrau"));
        assertEquals(Optional.of(Ability.SCHMIED), Ability.parse("faehigkeiten_schmied"));
        assertTrue(Ability.parse("zauberer").isEmpty());
    }
}
