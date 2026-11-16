package net.morni.duels.kit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LayoutTest {

    private static final List<Layout.Item> MODERN = List.of(new Layout.Item("sword", 0), new Layout.Item("axe", 1),
            new Layout.Item("bow", 2), new Layout.Item("arrows", 8));

    @Test
    void parseAndFormatRoundTrip() {
        Layout l = Layout.parse("sword:3, bow:0 ,axe:35");
        assertEquals("bow:0,sword:3,axe:35", l.format());
        assertEquals(l, Layout.parse(l.format()));
        assertEquals(OptionalInt.of(3), l.slotOf("sword"));
        assertEquals(OptionalInt.empty(), l.slotOf("pearl"));
    }

    @Test
    void brokenPartsAreSkipped() {
        Layout l = Layout.parse("sword:99,bow:x,:3,axe:2,axe:4,Sword:5,gapple:-1,pearl");
        assertEquals(Map.of("axe", 2), l.asMap());
    }

    @Test
    void aSlotIsOnlyUsedOnce() {
        assertEquals("a:1", Layout.parse("a:1,b:1").format());
    }

    @Test
    void emptyTextIsEmptyLayout() {
        assertTrue(Layout.parse(null).isEmpty());
        assertTrue(Layout.parse("  ").isEmpty());
        assertEquals("", Layout.EMPTY.format());
    }

    @Test
    void arrangeUsesDefaultsWithoutLayout() {
        String[] slots = Layout.arrange(MODERN, null, 0, Layout.EMPTY);
        assertEquals("sword", slots[0]);
        assertEquals("axe", slots[1]);
        assertEquals("bow", slots[2]);
        assertEquals("arrows", slots[8]);
        assertEquals(4, Arrays.stream(slots).filter(s -> s != null).count());
    }

    @Test
    void arrangeFollowsThePlayersLayout() {
        String[] slots = Layout.arrange(MODERN, null, 0, Layout.parse("sword:4,arrows:20"));
        assertEquals("sword", slots[4]);
        assertEquals("arrows", slots[20]);
        assertEquals("axe", slots[1]);
        assertNull(slots[0]);
    }

    @Test
    void anItemWhoseDefaultIsTakenGoesToTheFirstFreeSlot() {
        // Der Bogen liegt laut Anordnung auf dem Platz der Axt; die Axt rutscht auf den ersten freien Platz.
        String[] slots = Layout.arrange(MODERN, null, 0, Layout.parse("bow:1"));
        assertEquals("bow", slots[1]);
        assertEquals("sword", slots[0]);
        assertEquals("axe", slots[2]);
    }

    @Test
    void fillersFillTheRestHotbarFirst() {
        List<Layout.Item> classic = List.of(new Layout.Item("sword", 0));
        String[] slots = Layout.arrange(classic, "soup", 35, Layout.parse("sword:5"));
        assertEquals("sword", slots[5]);
        for (int i = 0; i < Layout.SLOTS; i++) {
            if (i != 5) {
                assertEquals("soup", slots[i], "Platz " + i);
            }
        }
        String[] few = Layout.arrange(classic, "soup", 3, Layout.EMPTY);
        assertArrayEquals(new String[] {"sword", "soup", "soup", "soup", null}, Arrays.copyOf(few, 5));
    }

    @Test
    void readTakesOnlyKnownItemsAndTheirFirstSlot() {
        String[] ids = new String[Layout.SLOTS];
        ids[0] = "soup";
        ids[3] = "sword";
        ids[7] = "sword";
        ids[12] = "unknown";
        ids[30] = "bow";
        Layout l = Layout.read(ids, Set.of("sword", "bow"));
        assertEquals("sword:3,bow:30", l.format());
    }
}
