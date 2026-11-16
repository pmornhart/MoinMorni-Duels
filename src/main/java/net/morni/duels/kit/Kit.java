package net.morni.duels.kit;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;

/** Die drei Kampfarten. Jede hat ihre eigene Wertung (Elo) und eigene Warteschlangen. */
public enum Kit {
    /** Eisen + Diamantschwert, Pilzsuppe heilt sofort, keine Abklingzeit beim Schlagen. */
    KLASSISCH("klassisch", Material.MUSHROOM_STEW, "classic", "suppe", "soup"),
    /** Heutiger Kampf: Schild, Axt, Bogen, Goldäpfel, Enderperlen. */
    MODERN("modern", Material.SHIELD, "neu"),
    /** Eigene Fähigkeiten mit Abklingzeit (Bergführer, Schmied, Kräuterfrau, Jäger). */
    FAEHIGKEITEN("faehigkeiten", Material.NETHER_STAR, "abilities", "ability", "faehigkeit", "skills");

    private final String id;
    private final Material icon;
    private final String[] aliases;

    Kit(String id, Material icon, String... aliases) {
        this.id = id;
        this.icon = icon;
        this.aliases = aliases;
    }

    /** Name in Dateien, Befehlen und Statistiken (klein, ohne Umlaute). */
    public String id() {
        return id;
    }

    public Material icon() {
        return icon;
    }

    /** Liest „klassisch“, „Classic“, „fähigkeiten“, „abilities“ … */
    public static Optional<Kit> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        for (Kit kit : values()) {
            if (kit.id.equals(s)) {
                return Optional.of(kit);
            }
            for (String alias : kit.aliases) {
                if (alias.equals(s)) {
                    return Optional.of(kit);
                }
            }
        }
        return Optional.empty();
    }
}
