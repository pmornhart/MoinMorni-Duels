package de.moinmorni.duels.kit;

import de.moinmorni.duels.Items;
import de.moinmorni.duels.Texts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Was man in einem Kampf bekommt. Eine Ausrüstung („Loadout“) gibt es je Kampfart und je Fähigkeit; jede hat ihre
 * eigene Anordnung im Kit-Editor. Alles ist unzerbrechlich – in zehn Minuten soll nichts kaputtgehen.
 */
public final class Loadouts {

    /**
     * Verzauberung mit Stufe – als Name („sharpness“), aufgelöst erst beim Bauen des Gegenstands (so lassen sich die
     * Ausrüstungen ohne Server prüfen).
     */
    public record Ench(String key, int level) {
        Enchantment type() {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        }
    }

    /** Wirkung, die man den ganzen Kampf über hat (z. B. Schnelligkeit beim Boxen), als Name wie {@code "speed"}. */
    public record Effect(String key, int amplifier) {
        PotionEffectType type() {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key));
        }
    }

    /**
     * Ein einzelner Gegenstand mit Standardplatz und Menge. {@code team} = Blöcke in der Farbe des eigenen Teams
     * (Brücke): {@code material} gilt für Team 1 (Blau), Team 2 bekommt {@link #RED_BLOCK}.
     */
    public record Piece(String id, Material material, int amount, int defaultSlot, List<Ench> enchants,
                        PotionType potion, boolean team) {
        public Piece {
            enchants = List.copyOf(enchants);
        }

        Piece ench(String key, int level) {
            List<Ench> list = new ArrayList<>(enchants);
            list.add(new Ench(key, level));
            return new Piece(id, material, amount, defaultSlot, list, potion, team);
        }

        Piece potion(PotionType type) {
            return new Piece(id, material, amount, defaultSlot, enchants, type, team);
        }

        Piece teamColoured() {
            return new Piece(id, material, amount, defaultSlot, enchants, potion, true);
        }
    }

    /**
     * Eine Ausrüstung: Rüstung (4 Teile oder keine), Verzauberung der Rüstung, Farbe von Lederrüstung ({@code null} =
     * Teamfarbe), Zweithand, einzelne Gegenstände, Füllware für alle übrigen Plätze, Wirkungen.
     */
    public record Loadout(String id, Kit kit, Ability ability, Material[] armor, List<Ench> armorEnchants,
                          Color leather, Material offhand, List<Piece> pieces, Piece filler, int fillerCount,
                          List<Effect> effects) {

        public Set<String> pieceIds() {
            return pieces.stream().map(Piece::id).collect(Collectors.toSet());
        }

        public List<Layout.Item> layoutItems() {
            return pieces.stream().map(p -> new Layout.Item(p.id(), p.defaultSlot())).toList();
        }

        /** Gibt es etwas anzuordnen? */
        public boolean editable() {
            return kit.editable() && !pieces.isEmpty();
        }
    }

    /** Füllware (Suppen, Heiltränke) hat diese ID. */
    public static final String FILLER = "filler";
    /** Fähigkeits-Gegenstand hat diese ID. */
    public static final String ABILITY = "ability";
    /** Kompass im Labyrinth. */
    public static final String COMPASS = "compass";
    /** Blöcke der Brücke in Team 2 (Rot); Team 1 (Blau) bekommt das Material des Gegenstands. */
    public static final Material RED_BLOCK = Material.RED_CONCRETE;
    public static final Color BLUE_TEAM = Color.fromRGB(0x3B5EC8);
    public static final Color RED_TEAM = Color.fromRGB(0xC83B3B);
    private static final Color HEDGE_GREEN = Color.fromRGB(0x3E7A35);

    private static final Material[] NONE = {};
    private static final Material[] LEATHER = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
    private static final Material[] IRON = {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS,
            Material.IRON_BOOTS};
    private static final Material[] DIAMOND = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
    /** UHC: Diamant-Helm und -Hose, Eisen-Brust und -Stiefel. */
    private static final Material[] MIXED = {Material.DIAMOND_HELMET, Material.IRON_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.IRON_BOOTS};
    /** Ganzes Inventar außer dem Schwert: Suppen über Suppen, wie früher. */
    private static final int SOUPS = Layout.SLOTS - 1;

    private static final Map<String, Loadout> ALL = new LinkedHashMap<>();

    static {
        // ---------- Klassisch ----------
        add(Kit.KLASSISCH, IRON, List.of(), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 1)),
                p(FILLER, Material.MUSHROOM_STEW, 1, -1), SOUPS, List.of());
        add(Kit.SCHWERT, NONE, List.of(), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0)), null, 0, List.of());
        add(Kit.RITTER, IRON, List.of(), null, null, List.of(
                p("sword", Material.IRON_SWORD, 1, 0).ench("sharpness", 1),
                p("bow", Material.BOW, 1, 1),
                p("gapple", Material.GOLDEN_APPLE, 2, 2),
                p("arrows", Material.ARROW, 12, 8)), null, 0, List.of());
        add(Kit.DIAMANT, DIAMOND, List.of(new Ench("protection", 1)), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 1),
                p("bow", Material.BOW, 1, 1).ench("power", 1),
                p("gapple", Material.GOLDEN_APPLE, 3, 2),
                p("arrows", Material.ARROW, 12, 8)), null, 0, List.of());
        add(Kit.BOGEN, LEATHER, List.of(new Ench("projectile_protection", 1)), null, null, List.of(
                p("bow", Material.BOW, 1, 0).ench("power", 1).ench("infinity", 1),
                p("arrows", Material.ARROW, 1, 8)), null, 0, List.of());
        add(Kit.ANGEL, IRON, List.of(), null, null, List.of(
                p("sword", Material.IRON_SWORD, 1, 0).ench("sharpness", 1),
                p("rod", Material.FISHING_ROD, 1, 1)), null, 0, List.of());
        add(Kit.KOMBO, DIAMOND, List.of(new Ench("protection", 2)), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 2),
                p("gapple", Material.GOLDEN_APPLE, 8, 1)), null, 0, List.of());

        // ---------- Modern ----------
        add(Kit.MODERN, DIAMOND, List.of(), null, Material.SHIELD, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 1),
                p("axe", Material.DIAMOND_AXE, 1, 1),
                p("bow", Material.BOW, 1, 2).ench("power", 1),
                p("gapple", Material.GOLDEN_APPLE, 3, 3),
                p("pearl", Material.ENDER_PEARL, 2, 4),
                p("arrows", Material.ARROW, 16, 8)), null, 0, List.of());
        add(Kit.AXT, DIAMOND, List.of(), null, Material.SHIELD, List.of(
                p("axe", Material.DIAMOND_AXE, 1, 0).ench("sharpness", 1),
                p("gapple", Material.GOLDEN_APPLE, 2, 1)), null, 0, List.of());
        add(Kit.GOLDAPFEL, DIAMOND, List.of(new Ench("protection", 4)), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 5),
                p("godapple", Material.ENCHANTED_GOLDEN_APPLE, 12, 1),
                p("strength", Material.POTION, 1, 2).potion(PotionType.STRONG_STRENGTH),
                p("strength2", Material.POTION, 1, 3).potion(PotionType.STRONG_STRENGTH),
                p("speed", Material.POTION, 1, 4).potion(PotionType.STRONG_SWIFTNESS),
                p("speed2", Material.POTION, 1, 5).potion(PotionType.STRONG_SWIFTNESS)), null, 0, List.of());
        add(Kit.TRAENKE, DIAMOND, List.of(new Ench("protection", 2)), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 2),
                p("pearl", Material.ENDER_PEARL, 8, 1),
                p("speed", Material.POTION, 1, 2).potion(PotionType.STRONG_SWIFTNESS),
                p("speed2", Material.POTION, 1, 17).potion(PotionType.STRONG_SWIFTNESS)),
                p(FILLER, Material.SPLASH_POTION, 1, -1).potion(PotionType.STRONG_HEALING), Layout.SLOTS - 4,
                List.of());
        add(Kit.PERLEN, IRON, List.of(), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 1),
                p("pearl", Material.ENDER_PEARL, 16, 1),
                p("gapple", Material.GOLDEN_APPLE, 2, 2)), null, 0, List.of());
        add(Kit.UHC, MIXED, List.of(new Ench("protection", 2)), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0).ench("sharpness", 2),
                p("bow", Material.BOW, 1, 1).ench("power", 2),
                p("water", Material.WATER_BUCKET, 1, 2),
                p("lava", Material.LAVA_BUCKET, 1, 3),
                p("gapple", Material.GOLDEN_APPLE, 6, 4),
                p("cobble", Material.COBBLESTONE, 64, 5),
                p("planks", Material.SPRUCE_PLANKS, 64, 6),
                p("pickaxe", Material.DIAMOND_PICKAXE, 1, 7).ench("efficiency", 2),
                p("axe", Material.DIAMOND_AXE, 1, 8),
                p("arrows", Material.ARROW, 16, 9),
                p("water2", Material.WATER_BUCKET, 1, 10)), null, 0, List.of());
        add(Kit.BAUKAMPF, IRON, List.of(), null, null, List.of(
                p("sword", Material.IRON_SWORD, 1, 0).ench("sharpness", 1),
                p("planks", Material.SPRUCE_PLANKS, 64, 1),
                p("planks2", Material.SPRUCE_PLANKS, 64, 2),
                p("cobble", Material.COBBLESTONE, 64, 3),
                p("pickaxe", Material.DIAMOND_PICKAXE, 1, 4).ench("efficiency", 2),
                p("axe", Material.DIAMOND_AXE, 1, 5).ench("efficiency", 2),
                p("gapple", Material.GOLDEN_APPLE, 3, 6),
                p("bow", Material.BOW, 1, 7),
                p("arrows", Material.ARROW, 12, 8)), null, 0, List.of());

        // ---------- Spaß ----------
        for (Ability a : Ability.values()) {
            List<Piece> pieces = new ArrayList<>();
            pieces.add(p("sword", Material.IRON_SWORD, 1, 0).ench("sharpness", 1));
            pieces.add(p(ABILITY, a.item(), 1, 1));
            if (a == Ability.JAEGER) {
                pieces.add(p("bow", Material.BOW, 1, 2));
                pieces.add(p("gapple", Material.GOLDEN_APPLE, 1, 3));
                pieces.add(p("arrows", Material.ARROW, 24, 8));
            } else {
                pieces.add(p("gapple", Material.GOLDEN_APPLE, 1, 2));
            }
            put(new Loadout(a.loadoutId(), Kit.FAEHIGKEITEN, a, IRON, List.of(), null,
                    a == Ability.SCHMIED ? Material.SHIELD : null, List.copyOf(pieces), null, 0, List.of()));
        }
        add(Kit.SUMO, NONE, List.of(), null, null, List.of(), null, 0, List.of());
        add(Kit.BOXEN, NONE, List.of(), null, null, List.of(
                p("sword", Material.DIAMOND_SWORD, 1, 0)), null, 0,
                List.of(new Effect("speed", 1)));
        add(Kit.SPLEEF, NONE, List.of(), null, null, List.of(
                p("shovel", Material.DIAMOND_SHOVEL, 1, 0).ench("efficiency", 5)), null, 0, List.of());
        add(Kit.LABYRINTH, LEATHER, List.of(new Ench("protection", 1)), HEDGE_GREEN, null, List.of(
                p("sword", Material.IRON_SWORD, 1, 0).ench("sharpness", 1),
                p("bow", Material.BOW, 1, 1),
                p(COMPASS, Material.COMPASS, 1, 2),
                p("gapple", Material.GOLDEN_APPLE, 1, 3),
                p("arrows", Material.ARROW, 8, 8)), null, 0, List.of());
        add(Kit.BRUECKE, LEATHER, List.of(), null, null, List.of(
                p("sword", Material.IRON_SWORD, 1, 0),
                p("bow", Material.BOW, 1, 1),
                p("pickaxe", Material.DIAMOND_PICKAXE, 1, 2).ench("efficiency", 3),
                p("blocks", Material.BLUE_CONCRETE, 64, 3).teamColoured(),
                p("blocks2", Material.BLUE_CONCRETE, 64, 4).teamColoured(),
                p("gapple", Material.GOLDEN_APPLE, 4, 5),
                p("arrows", Material.ARROW, 8, 8)), null, 0, List.of());
    }

    private Loadouts() {
    }

    private static Piece p(String id, Material material, int amount, int slot) {
        return new Piece(id, material, amount, slot, List.of(), null, false);
    }

    private static void add(Kit kit, Material[] armor, List<Ench> armorEnchants, Color leather, Material offhand,
                            List<Piece> pieces, Piece filler, int fillerCount, List<Effect> effects) {
        put(new Loadout(kit.id(), kit, null, armor, List.copyOf(armorEnchants), leather, offhand, List.copyOf(pieces),
                filler, fillerCount, List.copyOf(effects)));
    }

    private static void put(Loadout l) {
        if (ALL.put(l.id(), l) != null) {
            throw new IllegalStateException("Ausrüstung doppelt: " + l.id());
        }
    }

    public static List<Loadout> all() {
        return List.copyOf(ALL.values());
    }

    /** Ausrüstungen, die man im Kit-Editor anordnen kann (nach Gruppe und Kampfart sortiert). */
    public static List<Loadout> editable(Kit.Group group) {
        return ALL.values().stream().filter(l -> l.kit().group() == group && l.editable()).toList();
    }

    public static Optional<Loadout> byId(String id) {
        return Optional.ofNullable(ALL.get(id));
    }

    /** Ausrüstung für eine Kampfart (bei Fähigkeiten die gewählte Fähigkeit). */
    public static Loadout of(Kit kit, Ability ability) {
        if (kit == Kit.FAEHIGKEITEN) {
            return ALL.get((ability == null ? Ability.BERGFUEHRER : ability).loadoutId());
        }
        return ALL.get(kit.id());
    }

    /** Anzeigename einer Ausrüstung („Klassisch“, „Fähigkeiten · Jäger“). */
    public static String displayName(Loadout l, String lang) {
        String kit = Texts.raw(lang, "kit." + l.kit().id());
        return l.ability() == null ? kit : kit + " · " + Texts.raw(lang, "ability." + l.ability().id());
    }

    /** Den Gegenstand zu einer ID bauen (auch für den Kit-Editor); Team 0 = Blau, 1 = Rot. */
    public static ItemStack item(Loadout l, String id, String lang, int team) {
        if (FILLER.equals(id) && l.filler() != null) {
            return build(l, l.filler(), lang, team);
        }
        for (Piece p : l.pieces()) {
            if (p.id().equals(id)) {
                return build(l, p, lang, team);
            }
        }
        return null;
    }

    private static ItemStack build(Loadout l, Piece p, String lang, int team) {
        ItemStack item;
        if (ABILITY.equals(p.id()) && l.ability() != null) {
            String key = "ability." + l.ability().id();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(Texts.lines(lang, key + ".desc"));
            lore.addAll(Texts.lines(lang, "ability.cooldown",
                    Placeholder.unparsed("secs", String.valueOf(l.ability().cooldownSeconds()))));
            item = Items.glow(Items.named(p.material(), 1, Texts.text(lang, "item.ability.name",
                    Placeholder.unparsed("name", Texts.raw(lang, key + ".item"))), lore), true);
        } else if (COMPASS.equals(p.id())) {
            item = Items.named(p.material(), 1, Texts.text(lang, "item.compass"), Texts.lines(lang, "item.compass.lore"));
        } else {
            Material m = p.team() && team == 1 ? RED_BLOCK : p.material();
            item = new ItemStack(m, Math.max(1, p.amount()));
        }
        boolean breakable = p.material().getMaxDurability() > 0;
        item.editMeta(meta -> {
            for (Ench e : p.enchants()) {
                Enchantment type = e.type();
                if (type != null) {
                    meta.addEnchant(type, e.level(), true);
                }
            }
            if (breakable) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            if (p.potion() != null && meta instanceof PotionMeta potion) {
                potion.setBasePotionType(p.potion());
            }
        });
        return Items.kitTag(item, p.id());
    }

    private static ItemStack armorPiece(Loadout l, Material m, int team) {
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
            for (Ench e : l.armorEnchants()) {
                Enchantment type = e.type();
                if (type != null) {
                    meta.addEnchant(type, e.level(), true);
                }
            }
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(l.leather() != null ? l.leather() : team == 1 ? RED_TEAM : BLUE_TEAM);
            }
        });
        return Items.kitTag(item, "armor");
    }

    /** Alle 36 Plätze nach der Anordnung des Spielers (für Kampf und Editor). */
    public static ItemStack[] contents(Loadout l, Layout layout, String lang, int team) {
        String[] ids = Layout.arrange(l.layoutItems(), l.filler() == null ? null : FILLER, l.fillerCount(), layout);
        ItemStack[] out = new ItemStack[Layout.SLOTS];
        for (int i = 0; i < ids.length; i++) {
            out[i] = ids[i] == null ? null : item(l, ids[i], lang, team);
        }
        return out;
    }

    /** Ausrüstung anlegen: Inventar leeren, Rüstung, Zweithand, Gegenstände nach Anordnung, Wirkungen. */
    public static void apply(Player player, Loadout l, Layout layout, String lang, int team) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (l.armor().length == 4) {
            inv.setHelmet(armorPiece(l, l.armor()[0], team));
            inv.setChestplate(armorPiece(l, l.armor()[1], team));
            inv.setLeggings(armorPiece(l, l.armor()[2], team));
            inv.setBoots(armorPiece(l, l.armor()[3], team));
        }
        if (l.offhand() != null) {
            ItemStack off = new ItemStack(l.offhand());
            off.editMeta(meta -> {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            });
            inv.setItemInOffHand(Items.kitTag(off, "offhand"));
        }
        ItemStack[] contents = contents(l, layout, lang, team);
        for (int i = 0; i < contents.length; i++) {
            inv.setItem(i, contents[i]);
        }
        inv.setHeldItemSlot(0);
        for (Effect e : l.effects()) {
            PotionEffectType type = e.type();
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, e.amplifier(), false,
                        false, true));
            }
        }
    }

    /** Für Tests: alle Materialien einer Ausrüstung. */
    static List<Material> materials(Loadout l) {
        List<Material> out = new ArrayList<>(Arrays.asList(l.armor()));
        l.pieces().forEach(p -> out.add(p.material()));
        if (l.filler() != null) {
            out.add(l.filler().material());
        }
        return out;
    }
}
