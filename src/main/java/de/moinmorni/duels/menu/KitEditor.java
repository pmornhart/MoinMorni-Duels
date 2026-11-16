package de.moinmorni.duels.menu;

import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.Items;
import de.moinmorni.duels.Texts;
import de.moinmorni.duels.Ui;
import de.moinmorni.duels.data.PlayerData;
import de.moinmorni.duels.kit.Layout;
import de.moinmorni.duels.kit.Loadouts;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Kit-Editor: oben das Inventar (drei Reihen), darunter die Schnellleiste, dann ein Strich und die Knöpfe. Man klickt
 * einen Gegenstand an und dann den Platz, wo er hin soll. Gespeichert wird die Anordnung (nicht die Gegenstände).
 */
public final class KitEditor extends Menu {

    /** Plätze 0–26 = Inventar (Spieler-Plätze 9–35), 27–35 = Schnellleiste (Spieler-Plätze 0–8). */
    private static final int EDIT_SLOTS = 36;
    private static final int HOTBAR_START = 27;
    private static final int SEPARATOR_START = 36;
    private static final int BUTTONS_START = 45;
    private static final int BACK = 45;
    private static final int RESET = 47;
    private static final int INFO = 49;
    private static final int SAVE = 51;
    private static final int SIZE = 54;

    private final DuelsPlugin plugin;
    private final Loadouts.Loadout loadout;
    private final String lang;
    private boolean saved;

    public KitEditor(DuelsPlugin plugin, Player player, Loadouts.Loadout loadout) {
        super(6, Texts.text(plugin.lang(player), "menu.editor.title",
                Placeholder.unparsed("kit", Loadouts.displayName(loadout, plugin.lang(player)))));
        this.plugin = plugin;
        this.loadout = loadout;
        this.lang = plugin.lang(player);
        fill(plugin.data(player).layout(loadout.id()));
        for (int i = SEPARATOR_START; i < BUTTONS_START; i++) {
            set(i, Items.named(Material.LIME_STAINED_GLASS_PANE, 1, Texts.text(lang, "menu.editor.hotbar"), null), null);
        }
        set(BACK, Items.named(Material.ARROW, 1, Texts.text(lang, "menu.back"), null), p -> {
            p.closeInventory();
            plugin.menus().openEditorChooser(p);
        });
        set(RESET, Items.named(Material.WATER_BUCKET, 1, Texts.text(lang, "menu.editor.reset"),
                Texts.lines(lang, "menu.editor.reset.lore")), p -> {
                    fill(Layout.EMPTY);
                    p.sendMessage(Texts.msg(lang, "editor.reset"));
                    Ui.sound(p, "block.note_block.hat", 1.2f);
                });
        set(INFO, Items.named(Material.BOOK, 1, Texts.text(lang, "menu.editor.info"),
                Texts.lines(lang, "menu.editor.info.lore")), null);
        set(SAVE, Items.named(Material.EMERALD, 1, Texts.text(lang, "menu.editor.save"),
                Texts.lines(lang, "menu.editor.save.lore")), this::save);
    }

    public static int editorSlot(int playerSlot) {
        return playerSlot < 9 ? HOTBAR_START + playerSlot : playerSlot - 9;
    }

    public static int playerSlot(int editorSlot) {
        return editorSlot >= HOTBAR_START ? editorSlot - HOTBAR_START : editorSlot + 9;
    }

    private void fill(Layout layout) {
        ItemStack[] contents = Loadouts.contents(loadout, layout, lang, 0);
        for (int i = 0; i < EDIT_SLOTS; i++) {
            getInventory().setItem(editorSlot(i), contents[i]);
        }
    }

    private void save(Player p) {
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            for (int i = 0; i < EDIT_SLOTS; i++) {
                ItemStack at = getInventory().getItem(i);
                if (at == null || at.getType().isAir()) {
                    getInventory().setItem(i, cursor);
                    break;
                }
            }
            p.setItemOnCursor(null);
        }
        String[] ids = new String[Layout.SLOTS];
        for (int e = 0; e < EDIT_SLOTS; e++) {
            ids[playerSlot(e)] = Items.kitTag(getInventory().getItem(e));
        }
        Layout layout = Layout.read(ids, loadout.pieceIds());
        PlayerData d = plugin.data(p);
        d.layout(loadout.id(), layout);
        plugin.store().save(d);
        saved = true;
        p.sendMessage(Texts.msg(lang, "editor.saved", Placeholder.unparsed("kit", Loadouts.displayName(loadout, lang))));
        Ui.sound(p, "entity.player.levelup", 1.6f);
        p.closeInventory();
    }

    /** Nur einfache Linksklicks im oberen Teil (Gegenstand nehmen/ablegen/tauschen). */
    @Override
    public void click(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= SIZE) {
            e.setCancelled(true);
            return;
        }
        if (raw >= SEPARATOR_START) {
            super.click(e);
            return;
        }
        InventoryAction action = e.getAction();
        boolean simple = e.getClick() == ClickType.LEFT && action != InventoryAction.COLLECT_TO_CURSOR
                && action != InventoryAction.MOVE_TO_OTHER_INVENTORY && action != InventoryAction.HOTBAR_SWAP;
        if (!simple) {
            e.setCancelled(true);
        }
    }

    @Override
    public void drag(InventoryDragEvent e) {
        for (int raw : e.getRawSlots()) {
            if (raw >= SEPARATOR_START) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void closed(Player p) {
        p.setItemOnCursor(null);
        // Sicherheitsnetz: Kit-Gegenstände haben im eigenen Inventar nichts verloren.
        ItemStack[] own = p.getInventory().getStorageContents();
        for (int i = 0; i < own.length; i++) {
            String tag = Items.kitTag(own[i]);
            if (tag != null) {
                p.getInventory().setItem(i, null);
            }
        }
        if (!saved) {
            p.sendMessage(Texts.msg(lang, "editor.not-saved"));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && plugin.hall().inHall(p)) {
                plugin.hall().items(p);
            }
        });
    }
}
