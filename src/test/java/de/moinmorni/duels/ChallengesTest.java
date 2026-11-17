package de.moinmorni.duels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.net.Network;
import de.moinmorni.duels.world.ArenaType;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Herausforderungen: Optionen, Party gegen Party, Runden aus dem Befehl. */
class ChallengesTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BERT = UUID.randomUUID();
    private static final UUID CARL = UUID.randomUUID();
    private static final UUID DORA = UUID.randomUUID();

    private static Network.Party party(UUID leader, UUID... members) {
        return new Network.Party(leader, java.util.Arrays.stream(members)
                .map(u -> new Network.Party.Member(u, "x", "duels")).toList());
    }

    @Test
    void partyVersusPartyOnlyBetweenTwoLeadersOfRealParties() {
        Network.Party ab = party(ANNA, ANNA, BERT);
        Network.Party cd = party(CARL, CARL, DORA);
        assertTrue(Challenges.partyDuel(ab, cd, ANNA, CARL));
        assertFalse(Challenges.partyDuel(ab, cd, BERT, CARL), "Bert leitet nicht");
        assertFalse(Challenges.partyDuel(ab, cd, ANNA, DORA), "Dora leitet nicht");
        assertFalse(Challenges.partyDuel(ab, null, ANNA, CARL), "Carl ohne Party: 1 gegen 1");
        assertFalse(Challenges.partyDuel(ab, ab, ANNA, BERT), "in derselben Party: 1 gegen 1");
        assertFalse(Challenges.partyDuel(party(ANNA, ANNA), cd, ANNA, CARL), "Party aus einer Person zählt nicht");
    }

    @Test
    void optionsKeepOnlyWhatFitsTheKit() {
        Challenges.Options o = new Challenges.Options(Kit.KLASSISCH, ArenaType.BURGHOF, 3);
        assertEquals(ArenaType.BURGHOF, o.arena());
        assertEquals(3, o.bestOf());
        assertNull(new Challenges.Options(Kit.SUMO, ArenaType.BURGHOF, 3).arena(), "Sumo hat seine eigene Arena");
        assertEquals(1, new Challenges.Options(Kit.BRUECKE, null, 5).bestOf(), "die Brücke zählt Tore");
        assertEquals(5, new Challenges.Options(Kit.SPLEEF, null, 4).bestOf());
        assertNull(new Challenges.Options(Kit.KLASSISCH, ArenaType.TANZBODEN, 1).arena());
    }

    @Test
    void descriptionInBothLanguages() {
        assertEquals("Klassisch", Challenges.describe("de", Challenges.Options.of(Kit.KLASSISCH), false));
        assertEquals("Sumo · Bester aus 3", Challenges.describe("de", new Challenges.Options(Kit.SUMO, null, 3), false));
        assertEquals("Knight · best of 5 · Castle Courtyard · party vs party",
                Challenges.describe("en", new Challenges.Options(Kit.RITTER, ArenaType.BURGHOF, 5), true));
    }

    @Test
    void roundsFromTheCommand() {
        for (String s : List.of("3", "bo3", "bester3", "best3", "Bester-aus-3")) {
            assertEquals(OptionalInt.of(3), Commands.rounds(s), s);
        }
        assertEquals(OptionalInt.of(1), Commands.rounds("1"));
        assertEquals(OptionalInt.of(5), Commands.rounds("bo5"));
        assertTrue(Commands.rounds("4").isEmpty());
        assertTrue(Commands.rounds("boxen").isEmpty());
    }
}
