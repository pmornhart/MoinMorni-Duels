package net.morni.duels.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.morni.duels.DuelsPlugin;
import net.morni.duels.Items;
import net.morni.duels.Texts;
import net.morni.duels.Ui;
import net.morni.duels.data.KitStats;
import net.morni.duels.data.Leaderboard;
import net.morni.duels.data.PlayerData;
import net.morni.duels.kit.Ability;
import net.morni.duels.kit.Kit;
import net.morni.duels.kit.Loadouts;
import net.morni.duels.match.QueueKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Alle Menüs: Kampf wählen, Fähigkeit, Kit bearbeiten, Rangliste, Herausforderung. */
public final class Menus implements Listener {

    private static final Material[] FRAME = {Material.LIME_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE};
    private static final int TOP_LIST = 10;

    private final DuelsPlugin plugin;

    public Menus(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Ereignisse ----------

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (holder instanceof Menu menu) {
            menu.click(e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (holder instanceof Menu menu) {
            menu.drag(e);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (holder instanceof Menu menu && e.getPlayer() instanceof Player p) {
            menu.closed(p);
        }
    }

    /** Offene Menüs mit Live-Zahlen neu zeichnen (jede Sekunde). */
    public void refreshOpen() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder(false);
            if (holder instanceof Menu menu) {
                menu.refresh();
            }
        }
    }

    // ---------- Kampf wählen ----------

    public void openFight(Player p) {
        String lang = plugin.lang(p);
        Menu menu = new Menu(6, Texts.text(lang, "menu.fight.title"));
        Runnable draw = () -> drawFight(menu, p, lang);
        draw.run();
        menu.refresher(draw);
        menu.open(p);
        Ui.sound(p, "block.chest.open", 1.4f);
    }

    private void drawFight(Menu menu, Player p, String lang) {
        menu.clear();
        for (int i = 0; i < 9; i++) {
            menu.set(i, Items.pane(FRAME[i % FRAME.length]), null);
            menu.set(36 + i, Items.pane(FRAME[(i + 1) % FRAME.length]), null);
        }
        menu.set(4, Items.named(Material.GOLDEN_SWORD, 1, Texts.text(lang, "menu.fight.info"),
                Texts.lines(lang, "menu.fight.info.lore",
                        Placeholder.unparsed("players", String.valueOf(plugin.getServer().getOnlinePlayers().size())),
                        Placeholder.unparsed("fights", String.valueOf(plugin.fights().running())))), null);
        PlayerData data = plugin.data(p);
        Optional<QueueKey> mine = plugin.queues().of(p.getUniqueId());
        Kit[] kits = Kit.values();
        for (int row = 0; row < kits.length; row++) {
            Kit kit = kits[row];
            int base = 9 * (row + 1);
            Material icon = kit == Kit.FAEHIGKEITEN ? data.ability().item() : kit.icon();
            List<Component> lore = new ArrayList<>(Texts.lines(lang, "kit." + kit.id() + ".desc"));
            if (kit == Kit.FAEHIGKEITEN) {
                lore.add(Texts.text(lang, "menu.kit.ability",
                        Placeholder.unparsed("name", Texts.raw(lang, "ability." + data.ability().id()))));
            }
            ItemStack kitItem = Items.named(icon, 1, Texts.text(lang, "kit." + kit.id()).color(
                    net.kyori.adventure.text.format.TextColor.color(Texts.GOLD)), lore);
            menu.set(base + 1, kitItem, kit == Kit.FAEHIGKEITEN ? this::openAbility : null);
            int[] cols = {3, 4, 6, 7};
            int c = 0;
            for (boolean ranked : new boolean[] {false, true}) {
                for (int size = 1; size <= 2; size++) {
                    QueueKey key = new QueueKey(kit, ranked, size);
                    boolean here = mine.isPresent() && mine.get().equals(key);
                    menu.set(base + cols[c++], queueButton(lang, key, data, here), who -> {
                        if (plugin.queues().of(who.getUniqueId()).map(key::equals).orElse(false)) {
                            plugin.queues().leave(who.getUniqueId(), true);
                            menu.refresh();
                        } else {
                            who.closeInventory();
                            plugin.queues().request(who, key);
                        }
                    });
                }
            }
        }
        menu.set(45, Items.named(Material.ANVIL, 1, Texts.text(lang, "item.kit"), Texts.lines(lang, "item.kit.lore")),
                who -> openEditorChooser(who));
        if (mine.isPresent()) {
            menu.set(49, Items.named(Material.BARRIER, 1, Texts.text(lang, "item.leave"), null), who -> {
                plugin.queues().leave(who.getUniqueId(), true);
                menu.refresh();
            });
        } else {
            menu.set(49, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
        }
        menu.set(53, Items.named(Material.GOLD_INGOT, 1, Texts.text(lang, "item.stats"),
                Texts.lines(lang, "item.stats.lore")), this::openStats);
    }

    private ItemStack queueButton(String lang, QueueKey key, PlayerData data, boolean here) {
        Material m = key.ranked() ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
        List<Component> lore = new ArrayList<>();
        lore.add(Texts.text(lang, "menu.queue.waiting",
                Placeholder.unparsed("n", String.valueOf(plugin.queues().waitingPlayers(key)))));
        lore.add(Texts.text(lang, "menu.queue.fighting",
                Placeholder.unparsed("n", String.valueOf(plugin.fights().fighting(key)))));
        if (key.ranked()) {
            lore.add(Texts.text(lang, "menu.queue.elo",
                    Placeholder.unparsed("elo", String.valueOf(data.kit(key.kit()).elo))));
        }
        if (key.size() == 2) {
            lore.add(Texts.text(lang, "menu.queue.party"));
        }
        lore.add(Component.empty());
        lore.add(Texts.text(lang, here ? "menu.queue.here" : "menu.queue.click"));
        ItemStack item = Items.named(m, key.size(), Texts.text(lang, "menu.queue.name",
                Placeholder.unparsed("mode", Texts.raw(lang, key.modeKey())),
                Placeholder.unparsed("size", Texts.raw(lang, key.sizeKey()))), lore);
        return Items.glow(item, here);
    }

    // ---------- Fähigkeit wählen ----------

    public void openAbility(Player p) {
        String lang = plugin.lang(p);
        Menu menu = new Menu(3, Texts.text(lang, "menu.ability.title"));
        PlayerData data = plugin.data(p);
        int[] slots = {10, 12, 14, 16};
        Ability[] all = Ability.values();
        for (int i = 0; i < all.length; i++) {
            Ability a = all[i];
            boolean chosen = data.ability() == a;
            List<Component> lore = new ArrayList<>(Texts.lines(lang, "ability." + a.id() + ".desc"));
            lore.addAll(Texts.lines(lang, "ability.cooldown", Placeholder.unparsed("secs", String.valueOf(a.cooldownSeconds()))));
            lore.add(Component.empty());
            lore.add(Texts.text(lang, chosen ? "menu.ability.selected" : "menu.ability.click"));
            ItemStack item = Items.glow(Items.named(a.item(), 1,
                    Texts.text(lang, "ability." + a.id()).color(net.kyori.adventure.text.format.TextColor.color(Texts.GOLD)),
                    lore), chosen);
            menu.set(slots[i], item, who -> {
                PlayerData d = plugin.data(who);
                d.ability(a);
                plugin.store().save(d);
                who.sendMessage(Texts.msg(lang, "ability.chosen",
                        Placeholder.unparsed("name", Texts.raw(lang, "ability." + a.id()))));
                Ui.sound(who, "entity.experience_orb.pickup", 1.2f);
                openFight(who);
            });
        }
        menu.set(22, Items.named(Material.ARROW, 1, Texts.text(lang, "menu.back"), null), this::openFight);
        menu.open(p);
    }

    // ---------- Kit bearbeiten ----------

    public void openEditorChooser(Player p) {
        String lang = plugin.lang(p);
        if (!plugin.hall().inHall(p)) {
            p.sendMessage(Texts.msg(lang, "editor.busy"));
            return;
        }
        Menu menu = new Menu(3, Texts.text(lang, "menu.editor.choose.title"));
        int[] slots = {10, 11, 13, 14, 15, 16};
        List<Loadouts.Loadout> all = Loadouts.all();
        for (int i = 0; i < all.size() && i < slots.length; i++) {
            Loadouts.Loadout l = all.get(i);
            Material icon = l.ability() != null ? l.ability().item() : l.kit().icon();
            List<Component> lore = new ArrayList<>(l.ability() == null
                    ? Texts.lines(lang, "kit." + l.kit().id() + ".desc")
                    : Texts.lines(lang, "ability." + l.ability().id() + ".desc"));
            lore.add(Component.empty());
            lore.add(Texts.text(lang, "menu.editor.choose.lore"));
            menu.set(slots[i], Items.named(icon, 1, Component.text(Loadouts.displayName(l, lang))
                    .color(net.kyori.adventure.text.format.TextColor.color(Texts.GOLD))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), lore),
                    who -> openEditor(who, l));
        }
        menu.set(22, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
        menu.open(p);
    }

    public void openEditor(Player p, Loadouts.Loadout loadout) {
        if (!plugin.hall().inHall(p)) {
            p.sendMessage(Texts.msg(plugin.lang(p), "editor.busy"));
            return;
        }
        new KitEditor(plugin, p, loadout).open(p);
        Ui.sound(p, "block.anvil.use", 1.6f);
    }

    // ---------- Rangliste & Statistik ----------

    public void openStats(Player p) {
        String lang = plugin.lang(p);
        Menu menu = new Menu(4, Texts.text(lang, "menu.stats.title"));
        PlayerData d = plugin.data(p);
        int games = 0;
        int wins = 0;
        for (Kit k : Kit.values()) {
            games += d.kit(k).games;
            wins += d.kit(k).wins;
        }
        ItemStack head = head(p);
        head.editMeta(m -> {
            m.displayName(Texts.text(lang, "menu.stats.me", Placeholder.unparsed("name", p.getName())));
        });
        int totalGames = games;
        int totalWins = wins;
        head.editMeta(m -> m.lore(Texts.lines(lang, "menu.stats.me.lore",
                Placeholder.unparsed("games", String.valueOf(totalGames)),
                Placeholder.unparsed("wins", String.valueOf(totalWins)),
                Placeholder.unparsed("streak", String.valueOf(d.streak())),
                Placeholder.unparsed("best", String.valueOf(d.bestStreak())))));
        menu.set(4, head, null);
        int[] own = {11, 13, 15};
        int[] tops = {20, 22, 24};
        Kit[] kits = Kit.values();
        for (int i = 0; i < kits.length; i++) {
            Kit kit = kits[i];
            String kitName = Texts.raw(lang, "kit." + kit.id());
            menu.set(own[i], Items.named(kit == Kit.FAEHIGKEITEN ? d.ability().item() : kit.icon(), 1,
                    Texts.text(lang, "menu.stats.own", Placeholder.unparsed("kit", kitName)),
                    statLines(lang, d, kit)), null);
            List<Component> lore = new ArrayList<>();
            List<Leaderboard.Row> top = plugin.board().top(kit, TOP_LIST);
            if (top.isEmpty()) {
                lore.add(Texts.text(lang, "stats.top.empty"));
            }
            for (int r = 0; r < top.size(); r++) {
                Leaderboard.Row row = top.get(r);
                lore.add(Texts.text(lang, "stats.top.line", Placeholder.unparsed("pos", String.valueOf(r + 1)),
                        Placeholder.unparsed("name", row.name()), Placeholder.unparsed("elo", String.valueOf(row.elo())),
                        Placeholder.unparsed("wins", String.valueOf(row.wins()))));
            }
            menu.set(tops[i], Items.named(Material.GOLD_INGOT, Math.max(1, Math.min(64, top.size())),
                    Texts.text(lang, "menu.stats.top", Placeholder.unparsed("kit", kitName)), lore), null);
        }
        menu.set(31, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
        menu.open(p);
    }

    /**
     * Kopf mit dem Skin des Spielers – aber nur, wenn sein Profil die Textur schon mitbringt (im Netzwerk über den
     * Proxy). Sonst ein einfacher Kopf, damit der Server nicht bei Mojang nachfragen muss.
     */
    private static ItemStack head(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (p.getPlayerProfile().hasTextures()) {
            head.editMeta(SkullMeta.class, m -> m.setPlayerProfile(p.getPlayerProfile()));
        }
        return head;
    }

    /** Zeilen mit den Werten einer Kampfart (Menü und /stats). */
    public List<Component> statLines(String lang, PlayerData d, Kit kit) {
        KitStats s = d.kit(kit);
        int pos = plugin.board().position(kit, d.uuid());
        Component posText = pos > 0 ? Texts.MM.deserialize(Texts.raw(lang, "stats.line.pos"),
                Placeholder.unparsed("pos", String.valueOf(pos))) : Component.empty();
        List<Component> lines = new ArrayList<>();
        lines.add(Texts.text(lang, "stats.line.elo", Placeholder.unparsed("elo", String.valueOf(s.elo)),
                Placeholder.component("pos", posText)));
        lines.add(Texts.text(lang, "stats.line.record", Placeholder.unparsed("wins", String.valueOf(s.wins)),
                Placeholder.unparsed("losses", String.valueOf(s.losses)),
                Placeholder.unparsed("draws", String.valueOf(s.draws))));
        lines.add(Texts.text(lang, "stats.line.kd", Placeholder.unparsed("kills", String.valueOf(s.kills)),
                Placeholder.unparsed("deaths", String.valueOf(s.deaths))));
        lines.add(Texts.text(lang, "stats.line.streak", Placeholder.unparsed("streak", String.valueOf(s.streak)),
                Placeholder.unparsed("best", String.valueOf(s.bestStreak))));
        return lines;
    }

    // ---------- Herausforderung ----------

    /** Kampfart für eine Herausforderung wählen. */
    public void openDuel(Player p, Player target) {
        String lang = plugin.lang(p);
        Menu menu = new Menu(3, Texts.text(lang, "menu.duel.title", Placeholder.unparsed("name", target.getName())));
        int[] slots = {11, 13, 15};
        Kit[] kits = Kit.values();
        for (int i = 0; i < kits.length; i++) {
            Kit kit = kits[i];
            List<Component> lore = new ArrayList<>(Texts.lines(lang, "kit." + kit.id() + ".desc"));
            lore.add(Component.empty());
            lore.add(Texts.text(lang, "menu.duel.click"));
            menu.set(slots[i], Items.named(kit.icon(), 1, Texts.text(lang, "kit." + kit.id())
                    .color(net.kyori.adventure.text.format.TextColor.color(Texts.GOLD)), lore), who -> {
                        who.closeInventory();
                        Player t = plugin.getServer().getPlayer(target.getUniqueId());
                        if (t != null) {
                            plugin.challenges().challenge(who, t, kit);
                        }
                    });
        }
        menu.set(22, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
        menu.open(p);
    }

    /** Für Bedrock-Spieler: Herausforderung als Menü (im Chat kann man dort nichts anklicken). */
    public void openChallenge(Player to, Player from, Kit kit) {
        String lang = plugin.lang(to);
        Menu menu = new Menu(3, Texts.text(lang, "menu.challenge.title", Placeholder.unparsed("name", from.getName())));
        menu.set(11, Items.named(Material.LIME_WOOL, 1, Texts.text(lang, "menu.challenge.accept"), null), who -> {
            who.closeInventory();
            plugin.challenges().accept(who, from.getName());
        });
        ItemStack head = head(from);
        head.editMeta(m -> {
            m.displayName(Texts.text(lang, "menu.challenge.info", Placeholder.unparsed("name", from.getName())));
            m.lore(List.of(Texts.text(lang, "kit." + kit.id())));
        });
        menu.set(13, head, null);
        menu.set(15, Items.named(Material.RED_WOOL, 1, Texts.text(lang, "menu.challenge.deny"), null), who -> {
            who.closeInventory();
            plugin.challenges().deny(who, from.getName());
        });
        menu.open(to);
    }
}
