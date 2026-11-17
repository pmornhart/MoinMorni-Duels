package de.moinmorni.duels.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;

/**
 * Die Kampfarten („Kits“). Jede hat ihre eigene Wertung (Elo), eigene Warteschlangen und eigene Ranglisten. Die
 * Mechaniken kennt man von großen PvP-Servern – Namen, Ausrüstungen und Arenen sind eigene.
 *
 * <p>Die IDs stehen in Dateien, Befehlen und Statistiken (klein, ohne Umlaute) und ändern sich nie.
 */
public enum Kit {
    // ---------- Klassisch: Kampf wie früher (ohne Abklingzeit beim Schlagen) ----------
    /** Eisen + Diamantschwert, Pilzsuppe heilt sofort, keine Abklingzeit beim Schlagen. */
    KLASSISCH("klassisch", Group.KLASSISCH, Material.MUSHROOM_STEW, "classic", "suppe", "soup"),
    /** Nur ein Schwert: keine Rüstung, nichts zu essen. */
    SCHWERT("schwert", Group.KLASSISCH, Material.DIAMOND_SWORD, "nurschwert", "nur_schwert", "sword", "swordonly",
            "onlysword", "sword_only"),
    /** Ritter: komplett aus Eisen. */
    RITTER("ritter", Group.KLASSISCH, Material.IRON_CHESTPLATE, "eisen", "iron", "knight"),
    /** Komplett aus Diamant (leicht verzaubert). */
    DIAMANT("diamant", Group.KLASSISCH, Material.DIAMOND_CHESTPLATE, "diamond", "diamanten"),
    /** Bogen und Pfeile, leichte Rüstung. */
    BOGEN("bogen", Group.KLASSISCH, Material.BOW, "bow", "archer", "archery", "pfeil"),
    /** Schwert und Angel: die Angel holt den Gegner heran. */
    ANGEL("angel", Group.KLASSISCH, Material.FISHING_ROD, "rod", "fishingrod", "fishing_rod", "angelrute"),
    /** Kurze Pause zwischen Treffern: schnelle Kombos. */
    KOMBO("kombo", Group.KLASSISCH, Material.PUFFERFISH, "combo"),

    // ---------- Modern: Kampf wie heute (mit Abklingzeit) ----------
    /** Heutiger Kampf: Schild, Axt, Bogen, Goldäpfel, Enderperlen. */
    MODERN("modern", Group.MODERN, Material.SHIELD, "neu"),
    /** Axt und Schild: Axt-Treffer legen den Schild lahm. */
    AXT("axt", Group.MODERN, Material.DIAMOND_AXE, "axtundschild", "axt_schild", "axe", "axeshield", "axe_shield"),
    /** Verzauberte Goldäpfel, starke Rüstung, Tränke. */
    GOLDAPFEL("goldapfel", Group.MODERN, Material.ENCHANTED_GOLDEN_APPLE, "goldaepfel", "gapple", "goldenapple",
            "golden_apple"),
    /** Heiltränke zum Werfen und Schnelligkeit. */
    TRAENKE("traenke", Group.MODERN, Material.SPLASH_POTION, "trank", "potions", "potion", "traenk"),
    /** Kampf mit Enderperlen (Landen tut nicht weh). */
    PERLEN("perlen", Group.MODERN, Material.ENDER_PEARL, "enderperlen", "enderperle", "pearl", "pearls",
            "enderpearl", "enderpearls"),
    /** Keine natürliche Heilung, Goldäpfel, Lava und Wasser, Blöcke, Bogen. */
    UHC("uhc", Group.MODERN, Material.LAVA_BUCKET, "ultrahardcore"),
    /** Blöcke bauen erlaubt – die Arena wird danach zurückgesetzt. */
    BAUKAMPF("baukampf", Group.MODERN, Material.BRICKS, "bau", "bauen", "build", "buildfight", "build_fight"),

    // ---------- Spaß: besondere Regeln und eigene Arenen ----------
    /** Eisen und eine besondere Fähigkeit mit Abklingzeit (Bergführer, Schmied, Kräuterfrau, Jäger). */
    FAEHIGKEITEN("faehigkeiten", Group.SPASS, Material.NETHER_STAR, "abilities", "ability", "faehigkeit", "skills"),
    /** Tanzboden: kein Schaden, wer herunterfällt, verliert. */
    SUMO("sumo", Group.SPASS, Material.SLIME_BALL),
    /** Wer zuerst genug Treffer landet, gewinnt – kein Schaden. */
    BOXEN("boxen", Group.SPASS, Material.LEATHER, "boxing", "box"),
    /** Schaufel und Schneefeld: den Boden unter dem Gegner wegschaufeln. */
    SPLEEF("spleef", Group.SPASS, Material.DIAMOND_SHOVEL, "schnee", "snow", "schaufel"),
    /** Irrgarten aus Hecken: suchen, finden, kämpfen. */
    LABYRINTH("labyrinth", Group.SPASS, Material.AZALEA_LEAVES, "maze", "irrgarten"),
    /** Klammbrücke: ins gegnerische Tor springen, Tore zählen. */
    BRUECKE("bruecke", Group.SPASS, Material.SPRUCE_PLANKS, "bridge", "klammbruecke");

    /** Gruppen im Menü – je Gruppe eine Reihe. */
    public enum Group {
        KLASSISCH("klassisch", Material.IRON_SWORD),
        MODERN("modern", Material.NETHERITE_SWORD),
        SPASS("spass", Material.FIREWORK_ROCKET);

        private final String id;
        private final Material icon;

        Group(String id, Material icon) {
            this.id = id;
            this.icon = icon;
        }

        public String id() {
            return id;
        }

        public Material icon() {
            return icon;
        }

        /** Kampfarten dieser Gruppe in fester Reihenfolge. */
        public List<Kit> kits() {
            List<Kit> list = new ArrayList<>();
            for (Kit k : Kit.values()) {
                if (k.group == this) {
                    list.add(k);
                }
            }
            return list;
        }
    }

    private final String id;
    private final Group group;
    private final Material icon;
    private final String[] aliases;

    Kit(String id, Group group, Material icon, String... aliases) {
        this.id = id;
        this.group = group;
        this.icon = icon;
        this.aliases = aliases;
    }

    /** Name in Dateien, Befehlen und Statistiken (klein, ohne Umlaute). */
    public String id() {
        return id;
    }

    public Group group() {
        return group;
    }

    public Material icon() {
        return icon;
    }

    // ---------- Regeln ----------

    /**
     * Kampf wie früher: keine Abklingzeit beim Schlagen, kein Schwung-Treffer, Rückstoß hebt auch in der Luft an.
     * Alles aus der Gruppe „Klassisch“ und die Spaß-Arten, in denen geschlagen wird.
     */
    public boolean classicCombat() {
        return group == Group.KLASSISCH || this == SUMO || this == BOXEN || this == LABYRINTH || this == BRUECKE;
    }

    /** Dürfen Blöcke gebaut werden (nur eigene dürfen wieder abgebaut werden)? */
    public boolean building() {
        return this == UHC || this == BAUKAMPF || this == BRUECKE;
    }

    /** Wasser und Lava aus Eimern (fließen dürfen sie dann auch). */
    public boolean liquids() {
        return this == UHC;
    }

    /** Heilt man von selbst (satt)? Bei UHC nur mit Goldäpfeln. */
    public boolean naturalRegen() {
        return this != UHC;
    }

    /** Schaden durch Treffer? Bei Sumo, Boxen und Spleef nicht. */
    public boolean hitsHurt() {
        return this != SUMO && this != BOXEN && this != SPLEEF;
    }

    /** Stoßen Treffer weg? Bei Spleef nicht – dort zählt nur die Schaufel. */
    public boolean hitsPush() {
        return this != SPLEEF;
    }

    /** Wer besiegt wird oder fällt, kommt wieder (bei der Brücke) – sonst scheidet man aus. */
    public boolean respawns() {
        return this == BRUECKE;
    }

    /** Überlebensmodus im Kampf (bauen oder Schnee schaufeln), sonst Abenteuermodus. */
    public boolean survival() {
        return building() || this == SPLEEF;
    }

    /** Gibt es etwas anzuordnen? Beim Sumo hat man nichts in der Hand. */
    public boolean editable() {
        return this != SUMO;
    }

    /** Geht „Jeder gegen jeden“? Die Brücke braucht genau zwei Seiten. */
    public boolean ffa() {
        return this != BRUECKE;
    }

    /** Geht „Bester aus 3/5“? Die Brücke zählt Tore statt Runden. */
    public boolean rounds() {
        return this != BRUECKE;
    }

    /** Liest „klassisch“, „Classic“, „fähigkeiten“, „abilities“, „Brücke“ … */
    public static Optional<Kit> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("-", "_").replace(" ", "_");
        String compact = s.replace("_", "");
        for (Kit kit : values()) {
            if (kit.id.equals(s) || kit.id.equals(compact)) {
                return Optional.of(kit);
            }
            for (String alias : kit.aliases) {
                if (alias.equals(s) || alias.equals(compact)) {
                    return Optional.of(kit);
                }
            }
        }
        return Optional.empty();
    }
}
