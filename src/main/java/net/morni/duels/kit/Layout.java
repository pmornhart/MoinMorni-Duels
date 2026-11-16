package net.morni.duels.kit;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Eigene Anordnung eines Kits: welcher Gegenstand in welchem Platz liegt. Plätze wie im Spieler-Inventar:
 * 0–8 = Schnellleiste, 9–35 = Inventar. Gespeichert als Text, z. B. {@code "sword:3,rod:0"}.
 *
 * <p>Nur „einzelne“ Gegenstände (Schwert, Bogen, Fähigkeit …) haben einen festen Platz; Füllware (Suppen) kommt in
 * alle übrigen Plätze.
 */
public final class Layout {

    public static final int SLOTS = 36;
    public static final Layout EMPTY = new Layout(Map.of());
    // Gegenstands-Namen: klein, Ziffern, Unterstrich, z. B. "sword", "gapple", "arrows".
    // https://regex101.com/?flavor=java&regex=%5E%5Ba-z%5D%5Ba-z0-9_%5D%7B0%2C31%7D%24&testString=sword_iron
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");

    /** Ein einzelner Gegenstand eines Kits und sein Standardplatz. */
    public record Item(String id, int defaultSlot) {
    }

    private final Map<String, Integer> slots;

    private Layout(Map<String, Integer> slots) {
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }

    /** Aus Gegenstand → Platz. Ungültige Plätze und doppelt belegte Plätze fallen weg. */
    public static Layout of(Map<String, Integer> slots) {
        Map<String, Integer> clean = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        slots.forEach((id, slot) -> {
            if (id != null && ID.matcher(id).matches() && slot != null && slot >= 0 && slot < SLOTS
                    && used.add(slot)) {
                clean.put(id, slot);
            }
        });
        return new Layout(clean);
    }

    /** Liest {@code "sword:3,bow:2"}; Kaputtes wird übersprungen. */
    public static Layout parse(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String part : text.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2 || map.containsKey(kv[0].trim())) {
                continue;
            }
            try {
                map.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            } catch (NumberFormatException ignored) {
                // überspringen
            }
        }
        return of(map);
    }

    /** Als Text, nach Platz sortiert. */
    public String format() {
        return slots.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    public OptionalInt slotOf(String id) {
        Integer slot = slots.get(id);
        return slot == null ? OptionalInt.empty() : OptionalInt.of(slot);
    }

    public Map<String, Integer> asMap() {
        return slots;
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * Verteilt ein Kit auf die 36 Plätze: erst die einzelnen Gegenstände (eigener Platz aus der Anordnung, sonst ihr
     * Standardplatz, sonst der erste freie), dann die Füllware in die übrigen Plätze – Schnellleiste zuerst.
     *
     * @return 36 Einträge mit der Gegenstands-ID oder {@code null} für leer
     */
    public static String[] arrange(List<Item> items, String fillerId, int fillerCount, Layout layout) {
        String[] out = new String[SLOTS];
        Layout own = layout == null ? EMPTY : layout;
        // 1. Gegenstände mit eigenem Platz
        Set<String> placed = new HashSet<>();
        for (Item item : items) {
            OptionalInt slot = own.slotOf(item.id());
            if (slot.isPresent() && out[slot.getAsInt()] == null) {
                out[slot.getAsInt()] = item.id();
                placed.add(item.id());
            }
        }
        // 2. der Rest auf den Standardplatz oder den ersten freien
        for (Item item : items) {
            if (placed.contains(item.id())) {
                continue;
            }
            int slot = item.defaultSlot() >= 0 && item.defaultSlot() < SLOTS && out[item.defaultSlot()] == null
                    ? item.defaultSlot() : firstFree(out);
            if (slot >= 0) {
                out[slot] = item.id();
            }
        }
        // 3. Füllware
        int left = fillerId == null ? 0 : fillerCount;
        for (int i = 0; i < SLOTS && left > 0; i++) {
            if (out[i] == null) {
                out[i] = fillerId;
                left--;
            }
        }
        return out;
    }

    /** Anordnung aus belegten Plätzen ablesen (nur die einzelnen Gegenstände zählen, jeweils der erste Platz). */
    public static Layout read(String[] slotIds, Set<String> itemIds) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(SLOTS, slotIds.length); i++) {
            String id = slotIds[i];
            if (id != null && itemIds.contains(id) && !map.containsKey(id)) {
                map.put(id, i);
            }
        }
        return of(map);
    }

    private static int firstFree(String[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Layout other && other.slots.equals(slots);
    }

    @Override
    public int hashCode() {
        return slots.hashCode();
    }

    @Override
    public String toString() {
        return "Layout[" + format() + "]";
    }
}
