package net.morni.duels.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KitTest {

    @Test
    void parsesGermanEnglishAndUmlauts() {
        assertEquals(Optional.of(Kit.KLASSISCH), Kit.parse("Klassisch"));
        assertEquals(Optional.of(Kit.KLASSISCH), Kit.parse("classic"));
        assertEquals(Optional.of(Kit.MODERN), Kit.parse("MODERN"));
        assertEquals(Optional.of(Kit.FAEHIGKEITEN), Kit.parse("fähigkeiten"));
        assertEquals(Optional.of(Kit.FAEHIGKEITEN), Kit.parse("abilities"));
        assertTrue(Kit.parse("sumo").isEmpty());
        assertTrue(Kit.parse(null).isEmpty());
    }

    @Test
    void abilitiesHaveOwnLoadoutIds() {
        assertEquals("faehigkeiten_jaeger", Ability.JAEGER.loadoutId());
        assertEquals(Optional.of(Ability.KRAEUTERFRAU), Ability.parse("Kräuterfrau"));
        assertEquals(Optional.of(Ability.SCHMIED), Ability.parse("faehigkeiten_schmied"));
        assertTrue(Ability.parse("zauberer").isEmpty());
    }
}
