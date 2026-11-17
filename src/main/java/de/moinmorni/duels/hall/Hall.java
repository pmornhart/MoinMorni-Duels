package de.moinmorni.duels.hall;

import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.Items;
import de.moinmorni.duels.Texts;
import de.moinmorni.duels.world.Arenas;
import de.moinmorni.duels.world.HallPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Die Wartehalle (Turnierhof): hier kommt man an, hier wartet man auf Gegner. Niemand nimmt Schaden, niemand hat
 * Hunger, in der Schnellleiste liegen die Knöpfe für Kampf, Kit-Editor, Rangliste und Lobby.
 */
public final class Hall {

    /** Aktionen der Gegenstände in der Schnellleiste. */
    public static final String FIGHT = "fight";
    public static final String KIT = "kit";
    public static final String STATS = "stats";
    public static final String LOBBY = "lobby";
    public static final String LEAVE = "leave";

    private static final int SLOT_FIGHT = 0;
    private static final int SLOT_KIT = 1;
    private static final int SLOT_LEAVE = 4;
    private static final int SLOT_STATS = 7;
    private static final int SLOT_LOBBY = 8;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    /** Wie lange man nach einem Treffer unverwundbar ist (Minecraft-Standard; die Kampfart Kombo setzt es kürzer). */
    private static final int DEFAULT_NO_DAMAGE_TICKS = 20;

    private final DuelsPlugin plugin;
    private final World world;
    /** Sprache, in der die Gegenstände eines Spielers gerade beschriftet sind. */
    private final Map<UUID, String> itemLang = new HashMap<>();

    public Hall(DuelsPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public Location spawn() {
        return new Location(world, HallPlan.SPAWN_X, Arenas.FLOOR_Y + HallPlan.SPAWN_Y, HallPlan.SPAWN_Z,
                HallPlan.SPAWN_YAW, 0f);
    }

    /** In der Wartehalle = in unserer Welt, nicht im Kampf und nicht beim Zuschauen. */
    public boolean inHall(Player p) {
        return p.getWorld() == world && plugin.fights().of(p.getUniqueId()).isEmpty()
                && plugin.fights().watching(p.getUniqueId()).isEmpty();
    }

    public boolean near(Location l) {
        return l.getWorld() == world && Math.abs(l.getX()) <= HallPlan.REACH && Math.abs(l.getZ()) <= HallPlan.REACH;
    }

    /** In die Wartehalle: alles zurücksetzen, hinstellen, Knöpfe geben. */
    public void enter(Player p) {
        reset(p);
        p.teleport(spawn());
        items(p);
        plugin.boards().show(p);
    }

    /** Zustand wie neu: Abenteuer-Modus, volle Gesundheit, satt, keine Effekte, normale Angriffsgeschwindigkeit. */
    public void reset(Player p) {
        p.closeInventory();
        if (p.getGameMode() != GameMode.ADVENTURE) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        p.setAllowFlight(false);
        p.setFlying(false);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        attackSpeed(p, DEFAULT_ATTACK_SPEED);
        p.setMaximumNoDamageTicks(DEFAULT_NO_DAMAGE_TICKS);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20.0 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setArrowsInBody(0);
        p.setLevel(0);
        p.setExp(0f);
        p.getInventory().clear();
        p.setItemOnCursor(null);
    }

    public static void attackSpeed(Player p, double value) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
        if (a != null) {
            a.setBaseValue(value);
        }
    }

    /** Knöpfe in die Schnellleiste (in der Sprache des Spielers). */
    public void items(Player p) {
        String lang = plugin.lang(p);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(SLOT_FIGHT, Items.hallTag(Items.named(Material.IRON_SWORD, 1, Texts.text(lang, "item.fight"),
                Texts.lines(lang, "item.fight.lore")), FIGHT));
        inv.setItem(SLOT_KIT, Items.hallTag(Items.named(Material.ANVIL, 1, Texts.text(lang, "item.kit"),
                Texts.lines(lang, "item.kit.lore")), KIT));
        if (plugin.queues().of(p.getUniqueId()).isPresent()) {
            inv.setItem(SLOT_LEAVE, Items.hallTag(Items.named(Material.BARRIER, 1, Texts.text(lang, "item.leave"),
                    Texts.lines(lang, "item.leave.lore")), LEAVE));
        }
        inv.setItem(SLOT_STATS, Items.hallTag(Items.named(Material.GOLD_INGOT, 1, Texts.text(lang, "item.stats"),
                Texts.lines(lang, "item.stats.lore")), STATS));
        inv.setItem(SLOT_LOBBY, Items.hallTag(Items.named(Material.RED_BED, 1, Texts.text(lang, "item.lobby"),
                Texts.lines(lang, "item.lobby.lore")), LOBBY));
        itemLang.put(p.getUniqueId(), lang);
    }

    /** Sprache gewechselt (z. B. per /sprache im Netzwerk)? Dann Knöpfe und Schilder neu beschriften. */
    public void checkLanguage(Player p) {
        String lang = plugin.lang(p);
        if (!lang.equals(itemLang.get(p.getUniqueId())) && inHall(p)) {
            items(p);
            plugin.boards().show(p);
        }
    }

    public void forget(UUID uuid) {
        itemLang.remove(uuid);
    }

    /** Knopf in der Schnellleiste gedrückt. */
    public void use(Player p, String action) {
        switch (action) {
            case FIGHT -> plugin.menus().openFight(p);
            case KIT -> plugin.menus().openEditorChooser(p);
            case STATS -> plugin.menus().openStats(p);
            case LEAVE -> plugin.queues().leave(p.getUniqueId(), true);
            case LOBBY -> {
                // Eigener Wunsch: das Netzwerk fragt nach, wenn jemand gerade spielt (in der Halle spielt niemand).
                p.sendMessage(Texts.msg(plugin.lang(p), "lobby.going"));
                plugin.queues().leave(p.getUniqueId(), false);
                plugin.net().askToLobby(p, Texts.text(plugin.lang(p), "lobby.kick"));
            }
            default -> {
            }
        }
    }
}
