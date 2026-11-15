package de.moinmorni.duels.match;

import de.moinmorni.duels.kit.Kit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Eine Warteschlange: Kampfart × gewertet/locker × 1 gegen 1 / 2 gegen 2. */
public record QueueKey(Kit kit, boolean ranked, int size) {

    public static List<QueueKey> all() {
        List<QueueKey> list = new ArrayList<>();
        for (Kit kit : Kit.values()) {
            for (boolean ranked : new boolean[] {false, true}) {
                for (int size = 1; size <= 2; size++) {
                    list.add(new QueueKey(kit, ranked, size));
                }
            }
        }
        return list;
    }

    public String modeKey() {
        return ranked ? "mode.ranked" : "mode.casual";
    }

    public String sizeKey() {
        return "size." + size;
    }

    /** Liest „locker“/„casual“ bzw. „gewertet“/„ranked“. */
    public static Optional<Boolean> parseRanked(String s) {
        String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "locker", "casual", "unranked", "spass", "spaß" -> Optional.of(false);
            case "gewertet", "ranked", "wertung", "rangliste" -> Optional.of(true);
            default -> Optional.empty();
        };
    }

    /** Liest „1v1“, „1“, „2v2“, „2gegen2“ … */
    public static Optional<Integer> parseSize(String s) {
        String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return switch (v) {
            case "1", "1v1", "1gegen1", "1vs1", "solo" -> Optional.of(1);
            case "2", "2v2", "2gegen2", "2vs2", "duo", "team" -> Optional.of(2);
            default -> Optional.empty();
        };
    }
}
