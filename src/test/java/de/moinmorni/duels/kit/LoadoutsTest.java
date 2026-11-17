package de.moinmorni.duels.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Die Ausrüstungen aller Kampfarten – ohne Server (Verzauberungen werden erst beim Bauen aufgelöst). */
class LoadoutsTest {

    @Test
    void everyKitHasItsLoadout() {
        for (Kit k : Kit.values()) {
            Loadouts.Loadout l = Loadouts.of(k, null);
            assertNotNull(l, k.id());
            assertEquals(k, l.kit());
        }
        for (Ability a : Ability.values()) {
            assertEquals(a, Loadouts.of(Kit.FAEHIGKEITEN, a).ability());
        }
        assertEquals(Kit.values().length - 1 + Ability.values().length, Loadouts.all().size());
    }

    @Test
    void piecesHaveUniqueIdsAndFitIntoTheInventory() {
        for (Loadouts.Loadout l : Loadouts.all()) {
            Set<String> ids = new HashSet<>();
            Set<Integer> slots = new HashSet<>();
            for (Loadouts.Piece p : l.pieces()) {
                assertTrue(ids.add(p.id()), l.id() + ": doppelt " + p.id());
                assertTrue(slots.add(p.defaultSlot()), l.id() + ": Platz doppelt " + p.defaultSlot());
                assertTrue(p.defaultSlot() >= 0 && p.defaultSlot() < Layout.SLOTS, l.id() + " " + p.id());
                assertTrue(p.amount() >= 1 && p.amount() <= 64, l.id() + " " + p.id());
                // Anordnungen werden als Text gespeichert – die IDs müssen dafür taugen
                assertEquals(p.id(), Layout.parse(p.id() + ":" + p.defaultSlot()).asMap().keySet().iterator().next());
            }
            assertTrue(l.pieces().size() + l.fillerCount() <= Layout.SLOTS, l.id() + " passt nicht ins Inventar");
            assertTrue(l.armor().length == 0 || l.armor().length == 4, l.id());
            String[] arranged = Layout.arrange(l.layoutItems(), l.filler() == null ? null : Loadouts.FILLER,
                    l.fillerCount(), Layout.EMPTY);
            long placed = java.util.Arrays.stream(arranged).filter(java.util.Objects::nonNull).count();
            assertEquals(l.pieces().size() + l.fillerCount(), placed, l.id());
        }
    }

    @Test
    void kitsLookLikeTheirName() {
        assertEquals(List.of(Material.DIAMOND_SWORD), Loadouts.materials(Loadouts.of(Kit.SCHWERT, null)),
                "Nur Schwert: nur ein Schwert, keine Rüstung, kein Essen");
        assertTrue(Loadouts.materials(Loadouts.of(Kit.SUMO, null)).isEmpty(), "Sumo: nichts in der Hand");
        assertTrue(Loadouts.materials(Loadouts.of(Kit.BOGEN, null)).containsAll(
                List.of(Material.BOW, Material.ARROW, Material.LEATHER_CHESTPLATE)));
        assertTrue(Loadouts.materials(Loadouts.of(Kit.ANGEL, null)).contains(Material.FISHING_ROD));
        assertTrue(Loadouts.materials(Loadouts.of(Kit.UHC, null)).containsAll(List.of(Material.LAVA_BUCKET,
                Material.WATER_BUCKET, Material.GOLDEN_APPLE, Material.COBBLESTONE, Material.BOW)));
        assertTrue(Loadouts.materials(Loadouts.of(Kit.GOLDAPFEL, null)).containsAll(
                List.of(Material.ENCHANTED_GOLDEN_APPLE, Material.POTION, Material.DIAMOND_CHESTPLATE)));
        assertEquals(Material.SPLASH_POTION, Loadouts.of(Kit.TRAENKE, null).filler().material());
        assertTrue(Loadouts.of(Kit.TRAENKE, null).fillerCount() >= 30, "Tränke: das Inventar voller Heiltränke");
        assertTrue(Loadouts.materials(Loadouts.of(Kit.AXT, null)).contains(Material.DIAMOND_AXE));
        assertEquals(Material.SHIELD, Loadouts.of(Kit.AXT, null).offhand());
        assertTrue(Loadouts.materials(Loadouts.of(Kit.RITTER, null)).containsAll(
                List.of(Material.IRON_SWORD, Material.IRON_CHESTPLATE)));
        assertTrue(Loadouts.materials(Loadouts.of(Kit.DIAMANT, null)).containsAll(
                List.of(Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE)));
        assertTrue(Loadouts.materials(Loadouts.of(Kit.PERLEN, null)).contains(Material.ENDER_PEARL));
        assertEquals(List.of(Material.DIAMOND_SHOVEL), Loadouts.materials(Loadouts.of(Kit.SPLEEF, null)));
        assertTrue(Loadouts.materials(Loadouts.of(Kit.LABYRINTH, null)).containsAll(
                List.of(Material.IRON_SWORD, Material.BOW, Material.COMPASS)));
        assertEquals(List.of(new Loadouts.Effect("speed", 1)), Loadouts.of(Kit.BOXEN, null).effects());
        assertTrue(Loadouts.materials(Loadouts.of(Kit.BAUKAMPF, null)).contains(Material.SPRUCE_PLANKS));
    }

    @Test
    void onlyTheBridgeHasTeamColouredBlocks() {
        for (Loadouts.Loadout l : Loadouts.all()) {
            boolean team = l.pieces().stream().anyMatch(Loadouts.Piece::team);
            assertEquals(l.kit() == Kit.BRUECKE, team, l.id());
        }
        assertEquals(Material.RED_CONCRETE, Loadouts.RED_BLOCK);
        assertTrue(Loadouts.of(Kit.BRUECKE, null).pieces().stream().filter(Loadouts.Piece::team)
                .allMatch(p -> p.material() == Material.BLUE_CONCRETE));
    }

    @Test
    void enchantmentsUseKnownNames() {
        Set<String> known = Set.of("sharpness", "protection", "power", "infinity", "efficiency",
                "projectile_protection");
        for (Loadouts.Loadout l : Loadouts.all()) {
            l.armorEnchants().forEach(e -> assertTrue(known.contains(e.key()), l.id() + ": " + e.key()));
            l.pieces().forEach(p -> p.enchants().forEach(e -> assertTrue(known.contains(e.key()),
                    l.id() + " " + p.id() + ": " + e.key())));
        }
    }

    @Test
    void editorListsEverythingExceptSumoGroupedInRows() {
        int total = 0;
        for (Kit.Group g : Kit.Group.values()) {
            List<Loadouts.Loadout> list = Loadouts.editable(g);
            assertTrue(list.size() <= 8, g + ": " + list.size() + " passen nicht in eine Reihe");
            assertTrue(list.stream().noneMatch(l -> l.kit() == Kit.SUMO));
            total += list.size();
        }
        assertEquals(Loadouts.all().size() - 1, total, "alle außer Sumo");
        assertEquals(Kit.KLASSISCH, Loadouts.editable(Kit.Group.KLASSISCH).get(0).kit(),
                "Klassisch steht vorn (wie im Kit-Editor gewohnt)");
        assertFalse(Loadouts.of(Kit.SUMO, null).editable());
    }
}
