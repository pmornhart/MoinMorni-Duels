package de.moinmorni.duels.world;

import de.moinmorni.duels.kit.Kit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Die Arenen – alle im Alpen-Stil, eigene Entwürfe. Die vier ersten sind für alle Kampfarten mit Schwert und Bogen,
 * die übrigen gehören je einer besonderen Kampfart.
 */
public enum ArenaType {
    ALMWIESE("almwiese", null),
    BURGHOF("burghof", null),
    BERGWERK("bergwerk", null),
    WIRTSHAUSGARTEN("wirtshausgarten", null),
    /** Runder Tanzboden hoch über einem Bergsee (Sumo). */
    TANZBODEN("tanzboden", Kit.SUMO),
    /** Schneefeld auf einem Gletscher (Spleef). */
    SCHNEEFELD("schneefeld", Kit.SPLEEF),
    /** Irrgarten aus Hecken (Labyrinth). */
    IRRGARTEN("irrgarten", Kit.LABYRINTH),
    /** Zwei Inseln mit Toren und eine Brücke über die Klamm (Brücke). */
    KLAMMBRUECKE("klammbruecke", Kit.BRUECKE);

    private final String id;
    private final Kit kit;

    ArenaType(String id, Kit kit) {
        this.id = id;
        this.kit = kit;
    }

    public String id() {
        return id;
    }

    /** Text-Schlüssel des Namens. */
    public String textKey() {
        return "arena." + id;
    }

    /** Für alle normalen Kampfarten (nicht einer besonderen vorbehalten)? */
    public boolean normal() {
        return kit == null;
    }

    /** Die eigene Arena einer Kampfart (Sumo, Spleef, Labyrinth, Brücke), sonst leer. */
    public static Optional<ArenaType> of(Kit kit) {
        for (ArenaType t : values()) {
            if (t.kit == kit && kit != null) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** Die vier Arenen für alle. */
    public static List<ArenaType> normals() {
        List<ArenaType> list = new ArrayList<>();
        for (ArenaType t : values()) {
            if (t.normal()) {
                list.add(t);
            }
        }
        return list;
    }

    /** Darf eine Kampfart hier kämpfen? */
    public boolean fits(Kit k) {
        return kit == null ? ArenaType.of(k).isEmpty() : kit == k;
    }

    /** Liest „burghof“, „Wirtshausgarten“ … (nur Arenen für alle – die anderen gehören fest zu ihrer Kampfart). */
    public static Optional<ArenaType> parseNormal(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.trim().toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae");
        for (ArenaType t : values()) {
            if (t.normal() && t.id.equals(s)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
