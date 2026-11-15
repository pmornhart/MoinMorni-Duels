package de.moinmorni.duels.kit;

import de.moinmorni.duels.Items;
import de.moinmorni.duels.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Was man in einem Kampf bekommt. Eine Ausrüstung („Loadout“) gibt es für Klassisch, Modern und je Fähigkeit; jede
 * hat ihre eigene Anordnung im Kit-Editor. Alles ist unzerbrechlich – in zehn Minuten soll nichts kaputtgehen.
 */
public final class Loadouts {

    /** Ein einzelner Gegenstand mit Standardplatz und Menge. */
    public record Piece(String id, Material material, int amount, int defaultSlot, Enchantment enchant, int level) {
        Piece(String id, Material material, int amount, int defaultSlot) {
            this(id, material, amount, defaultSlot, null, 0);
        }
    }

    /** Eine Ausrüstung: Rüstung, Zweithand, einzelne Gegenstände, Füllware. */
    public record Loadout(String id, Kit kit, Ability ability, Material[] armor, Material offhand, List<Piece> pieces,
                          Material filler, int fillerCount) {

        public Set<String> pieceIds() {
            return pieces.stream().map(Piece::id).collect(Collectors.toSet());
        }

        public List<Layout.Item> layoutItems() {
            return pieces.stream().map(p -> new Layout.Item(p.id(), p.defaultSlot())).toList();
        }
    }

    /** Füllware (Suppen) hat diese ID. */
    public static final String FILLER = "soup";
    /** Fähigkeits-Gegenstand hat diese ID. */
    public static final String ABILITY = "ability";

    private static final Material[] IRON = {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS,
            Material.IRON_BOOTS};
    private static final Material[] DIAMOND = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
    /** Ganzes Inventar außer dem Schwert: Suppen über Suppen, wie früher. */
    private static final int SOUPS = Layout.SLOTS - 1;

    private static final Map<String, Loadout> ALL = new LinkedHashMap<>();

    static {
        add(new Loadout(Kit.KLASSISCH.id(), Kit.KLASSISCH, null, IRON, null, List.of(
                new Piece("sword", Material.DIAMOND_SWORD, 1, 0, Enchantment.SHARPNESS, 1)),
                Material.MUSHROOM_STEW, SOUPS));
        add(new Loadout(Kit.MODERN.id(), Kit.MODERN, null, DIAMOND, Material.SHIELD, List.of(
                new Piece("sword", Material.DIAMOND_SWORD, 1, 0, Enchantment.SHARPNESS, 1),
                new Piece("axe", Material.DIAMOND_AXE, 1, 1),
                new Piece("bow", Material.BOW, 1, 2, Enchantment.POWER, 1),
                new Piece("gapple", Material.GOLDEN_APPLE, 3, 3),
                new Piece("pearl", Material.ENDER_PEARL, 2, 4),
                new Piece("arrows", Material.ARROW, 16, 8)),
                null, 0));
        for (Ability a : Ability.values()) {
            List<Piece> pieces = new ArrayList<>();
            pieces.add(new Piece("sword", Material.IRON_SWORD, 1, 0, Enchantment.SHARPNESS, 1));
            pieces.add(new Piece(ABILITY, a.item(), 1, 1));
            if (a == Ability.JAEGER) {
                pieces.add(new Piece("bow", Material.BOW, 1, 2));
                pieces.add(new Piece("gapple", Material.GOLDEN_APPLE, 1, 3));
                pieces.add(new Piece("arrows", Material.ARROW, 24, 8));
            } else {
                pieces.add(new Piece("gapple", Material.GOLDEN_APPLE, 1, 2));
            }
            add(new Loadout(a.loadoutId(), Kit.FAEHIGKEITEN, a, IRON, a == Ability.SCHMIED ? Material.SHIELD : null,
                    List.copyOf(pieces), null, 0));
        }
    }

    private Loadouts() {
    }

    private static void add(Loadout l) {
        ALL.put(l.id(), l);
    }

    public static List<Loadout> all() {
        return List.copyOf(ALL.values());
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

    /** Den Gegenstand zu einer ID bauen (auch für den Kit-Editor). */
    public static ItemStack item(Loadout l, String id, String lang) {
        if (FILLER.equals(id) && l.filler() != null) {
            return Items.kitTag(new ItemStack(l.filler()), FILLER);
        }
        for (Piece p : l.pieces()) {
            if (p.id().equals(id)) {
                return build(l, p, lang);
            }
        }
        return null;
    }

    private static ItemStack build(Loadout l, Piece p, String lang) {
        ItemStack item;
        if (ABILITY.equals(p.id()) && l.ability() != null) {
            String key = "ability." + l.ability().id();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(Texts.lines(lang, key + ".desc"));
            lore.addAll(Texts.lines(lang, "ability.cooldown",
                    Placeholder.unparsed("secs", String.valueOf(l.ability().cooldownSeconds()))));
            item = Items.glow(Items.named(p.material(), 1, Texts.text(lang, "item.ability.name",
                    Placeholder.unparsed("name", Texts.raw(lang, key + ".item"))), lore), true);
        } else {
            item = new ItemStack(p.material(), p.amount());
        }
        boolean breakable = p.material().getMaxDurability() > 0;
        item.editMeta(meta -> {
            if (p.enchant() != null) {
                meta.addEnchant(p.enchant(), p.level(), true);
            }
            if (breakable) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
        });
        return Items.kitTag(item, p.id());
    }

    private static ItemStack armorPiece(Material m) {
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        });
        return Items.kitTag(item, "armor");
    }

    /** Alle 36 Plätze nach der Anordnung des Spielers (für Kampf und Editor). */
    public static ItemStack[] contents(Loadout l, Layout layout, String lang) {
        String[] ids = Layout.arrange(l.layoutItems(), l.filler() == null ? null : FILLER, l.fillerCount(), layout);
        ItemStack[] out = new ItemStack[Layout.SLOTS];
        for (int i = 0; i < ids.length; i++) {
            out[i] = ids[i] == null ? null : item(l, ids[i], lang);
        }
        return out;
    }

    /** Ausrüstung anlegen: Inventar leeren, Rüstung, Zweithand, Gegenstände nach Anordnung. */
    public static void apply(Player player, Loadout l, Layout layout, String lang) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setHelmet(armorPiece(l.armor()[0]));
        inv.setChestplate(armorPiece(l.armor()[1]));
        inv.setLeggings(armorPiece(l.armor()[2]));
        inv.setBoots(armorPiece(l.armor()[3]));
        if (l.offhand() != null) {
            ItemStack off = armorPiece(l.offhand());
            Items.kitTag(off, "offhand");
            inv.setItemInOffHand(off);
        }
        ItemStack[] contents = contents(l, layout, lang);
        for (int i = 0; i < contents.length; i++) {
            inv.setItem(i, contents[i]);
        }
        inv.setHeldItemSlot(0);
    }
}
