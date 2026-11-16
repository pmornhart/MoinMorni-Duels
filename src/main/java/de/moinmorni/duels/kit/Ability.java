package de.moinmorni.duels.kit;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;

/**
 * Fähigkeiten im Kit „Fähigkeiten“: jede mit eigenem Gegenstand (Rechtsklick) und eigener Abklingzeit.
 * Eigene Namen und Ideen – nichts von anderen Servern.
 */
public enum Ability {
    /** Kletterseil: kurzer Sprung-Schub nach vorn und oben, die nächste Landung tut nicht weh. */
    BERGFUEHRER("bergfuehrer", Material.LEAD, 8),
    /** Schmiedehammer: Schild-Stoß – stößt Gegner vor einem weg und bremst sie kurz. */
    SCHMIED("schmied", Material.MACE, 10),
    /** Kräuterfläschchen: wirft einen Heiltrank (heilt alle in der Nähe, auch Gegner – gut zielen!). */
    KRAEUTERFRAU("kraeuterfrau", Material.GLASS_BOTTLE, 15),
    /** Pfeilbündel: Pfeilhagel – fünf Pfeile im Fächer. */
    JAEGER("jaeger", Material.FEATHER, 12);

    private final String id;
    private final Material item;
    private final int cooldownSeconds;

    Ability(String id, Material item, int cooldownSeconds) {
        this.id = id;
        this.item = item;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String id() {
        return id;
    }

    /** Gegenstand, mit dem man die Fähigkeit auslöst. */
    public Material item() {
        return item;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Ausrüstung dieser Fähigkeit (eigene Anordnung im Kit-Editor). */
    public String loadoutId() {
        return Kit.FAEHIGKEITEN.id() + "_" + id;
    }

    public static Optional<Ability> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue");
        for (Ability a : values()) {
            if (a.id.equals(s) || a.loadoutId().equals(s) || a.name().toLowerCase(Locale.ROOT).equals(s)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
