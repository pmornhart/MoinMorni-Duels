package de.moinmorni.duels.menu;

import de.moinmorni.duels.Challenges;
import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.Items;
import de.moinmorni.duels.Texts;
import de.moinmorni.duels.Ui;
import de.moinmorni.duels.data.KitStats;
import de.moinmorni.duels.data.Leaderboard;
import de.moinmorni.duels.data.PlayerData;
import de.moinmorni.duels.kit.Ability;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.kit.Loadouts;
import de.moinmorni.duels.match.QueueKey;
import de.moinmorni.duels.world.ArenaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionType;

/**
 * Alle Menüs: Kampf wählen (nach Gruppen Klassisch, Modern, Spaß), Kampfart mit ihren Warteschlangen, Fähigkeit, Kit
 * bearbeiten, Rangliste, Herausforderung (mit Arena und Runden), Party: Jeder gegen jeden.
 */
public final class Menus implements Listener {

    private static final Material[] FRAME = {Material.LIME_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE};
    private static final int TOP_LIST = 10;
    private static final int TOP_SHORT = 5;
    private static final int ROW = 9;
    /** Kampf-Menü: Plätze der Warteschlangen (wie früher: Spalten 3, 4, 6, 7 einer Reihe). */
    private static final int[] QUEUE_COLS = {3, 4, 6, 7};
    private static final int KIT_QUEUE_ROW = 18;
    private static final int[] ROUNDS = {1, 3, 5};

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

    // ---------- Bausteine ----------

    private static Component gold(Component c) {
        return c.color(TextColor.color(Texts.GOLD)).decoration(TextDecoration.ITALIC, false);
    }

    private void frame(Menu menu, int row) {
        for (int i = 0; i < ROW; i++) {
            menu.set(row * ROW + i, Items.pane(FRAME[(i + row) % FRAME.length]), null);
        }
    }

    /** Symbol einer Kampfart (Fähigkeiten: die gewählte Fähigkeit, Tränke: ein Heiltrank). */
    private static ItemStack kitIcon(Kit kit, PlayerData data) {
        Material m = kit == Kit.FAEHIGKEITEN && data != null ? data.ability().item() : kit.icon();
        ItemStack item = new ItemStack(m);
        if (kit == Kit.TRAENKE) {
            item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.STRONG_HEALING));
        }
        return item;
    }

    private static ItemStack named(ItemStack item, Component name, List<Component> lore) {
        item.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    /**
     * Drei Reihen nach Gruppen: links das Gruppen-Schild, rechts daneben die Kampfarten (nur eingeschaltete, die
     * {@code show} erfüllen). {@code look} baut den Knopf, {@code click} reagiert.
     */
    private void kitRows(Menu menu, String lang, Predicate<Kit> show, BiConsumer<Kit, Integer> place) {
        Kit.Group[] groups = Kit.Group.values();
        for (int g = 0; g < groups.length; g++) {
            Kit.Group group = groups[g];
            int base = (g + 1) * ROW;
            List<Component> lore = new ArrayList<>(Texts.lines(lang, "group." + group.id() + ".desc"));
            menu.set(base, Items.named(group.icon(), 1, Texts.text(lang, "group." + group.id())
                    .color(TextColor.color(Texts.EMERALD)).decoration(TextDecoration.BOLD, true), lore), null);
            int col = 1;
            for (Kit kit : group.kits()) {
                if (!show.test(kit) || col >= ROW) {
                    continue;
                }
                place.accept(kit, base + col);
                col++;
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
        frame(menu, 0);
        frame(menu, 4);
        menu.set(4, Items.named(Material.GOLDEN_SWORD, 1, Texts.text(lang, "menu.fight.info"),
                Texts.lines(lang, "menu.fight.info.lore",
                        Placeholder.unparsed("players", String.valueOf(plugin.getServer().getOnlinePlayers().size())),
                        Placeholder.unparsed("fights", String.valueOf(plugin.fights().running())))), null);
        PlayerData data = plugin.data(p);
        Optional<QueueKey> mine = plugin.queues().of(p.getUniqueId());
        kitRows(menu, lang, kit -> plugin.kitSettings().on(kit), (kit, slot) -> {
            boolean here = mine.isPresent() && mine.get().kit() == kit;
            List<Component> lore = new ArrayList<>(Texts.lines(lang, "kit." + kit.id() + ".desc"));
            lore.add(Component.empty());
            lore.add(Texts.text(lang, "menu.kit.counts", Placeholder.unparsed("waiting", String.valueOf(waiting(kit))),
                    Placeholder.unparsed("fighting", String.valueOf(fighting(kit)))));
            lore.add(Texts.text(lang, "menu.queue.elo", Placeholder.unparsed("elo", String.valueOf(data.kit(kit).elo))));
            if (kit == Kit.FAEHIGKEITEN) {
                lore.add(Texts.text(lang, "menu.kit.ability", Placeholder.unparsed("name",
                        Texts.raw(lang, "ability." + data.ability().id()))));
            }
            lore.add(Texts.text(lang, here ? "menu.kit.here" : "menu.kit.open"));
            ItemStack item = named(kitIcon(kit, data), gold(Texts.text(lang, "kit." + kit.id())), lore);
            menu.set(slot, Items.glow(item, here), who -> openKit(who, kit));
        });
        menu.set(38, Items.named(Material.FIREWORK_STAR, 1, Texts.text(lang, "menu.party.own"),
                Texts.lines(lang, "menu.party.own.lore")), this::openFfa);
        menu.set(42, Items.named(Material.RED_BANNER, 1, Texts.text(lang, "menu.party.vs"),
                Texts.lines(lang, "menu.party.vs.lore")), null);
        menu.set(45, Items.named(Material.ANVIL, 1, Texts.text(lang, "item.kit"), Texts.lines(lang, "item.kit.lore")),
                this::openEditorChooser);
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

    private int waiting(Kit kit) {
        int n = 0;
        for (QueueKey key : keys(kit)) {
            n += plugin.queues().waitingPlayers(key);
        }
        return n;
    }

    private int fighting(Kit kit) {
        int n = 0;
        for (QueueKey key : keys(kit)) {
            n += plugin.fights().fighting(key);
        }
        return n;
    }

    private static List<QueueKey> keys(Kit kit) {
        List<QueueKey> out = new ArrayList<>();
        for (boolean ranked : new boolean[] {false, true}) {
            for (int size = 1; size <= 2; size++) {
                out.add(new QueueKey(kit, ranked, size));
            }
        }
        return out;
    }

    // ---------- Eine Kampfart: Warteschlangen, Kit, Rangliste ----------

    public void openKit(Player p, Kit kit) {
        String lang = plugin.lang(p);
        if (!plugin.kitSettings().on(kit)) {
            p.sendMessage(Texts.msg(lang, "kit.off"));
            return;
        }
        Menu menu = new Menu(4, Texts.text(lang, "menu.kit.title",
                Placeholder.unparsed("kit", Texts.raw(lang, "kit." + kit.id()))));
        Runnable draw = () -> drawKit(menu, p, lang, kit);
        draw.run();
        menu.refresher(draw);
        menu.open(p);
        Ui.sound(p, "item.book.page_turn", 1.2f);
    }

    private void drawKit(Menu menu, Player p, String lang, Kit kit) {
        menu.clear();
        frame(menu, 0);
        PlayerData data = plugin.data(p);
        List<Component> lore = new ArrayList<>(Texts.lines(lang, "kit." + kit.id() + ".desc"));
        ArenaType.of(kit).ifPresent(t -> lore.add(Texts.text(lang, "menu.kit.arena",
                Placeholder.unparsed("arena", Texts.raw(lang, t.textKey())))));
        if (kit.rounds()) {
            lore.add(Texts.text(lang, "menu.kit.rounds", Placeholder.unparsed("rounds",
                    roundsName(lang, plugin.kitSettings().rounds(kit)))));
        }
        menu.set(4, named(kitIcon(kit, data), gold(Texts.text(lang, "kit." + kit.id())), lore), null);
        Optional<QueueKey> mine = plugin.queues().of(p.getUniqueId());
        int c = 0;
        for (boolean ranked : new boolean[] {false, true}) {
            for (int size = 1; size <= 2; size++) {
                QueueKey key = new QueueKey(kit, ranked, size);
                boolean here = mine.isPresent() && mine.get().equals(key);
                menu.set(KIT_QUEUE_ROW + QUEUE_COLS[c++], queueButton(lang, key, data, here), who -> {
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
        if (kit == Kit.FAEHIGKEITEN) {
            Ability a = data.ability();
            menu.set(KIT_QUEUE_ROW + 1, Items.glow(Items.named(a.item(), 1, Texts.text(lang, "menu.kit.ability",
                    Placeholder.unparsed("name", Texts.raw(lang, "ability." + a.id()))),
                    Texts.lines(lang, "menu.kit.ability.lore")), true), this::openAbility);
        }
        menu.set(27, Items.named(Material.ARROW, 1, Texts.text(lang, "menu.back"), null), this::openFight);
        Loadouts.Loadout loadout = Loadouts.of(kit, data.ability());
        if (loadout.editable()) {
            menu.set(31, Items.named(Material.ANVIL, 1, Texts.text(lang, "menu.kit.editor"),
                    Texts.lines(lang, "item.kit.lore")), who -> openEditor(who, Loadouts.of(kit, plugin.data(who).ability())));
        }
        menu.set(35, Items.named(Material.GOLD_INGOT, 1, Texts.text(lang, "menu.kit.top",
                Placeholder.unparsed("kit", Texts.raw(lang, "kit." + kit.id()))), topLines(lang, kit, TOP_LIST)), null);
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

    /** Die Besten einer Kampfart als Zeilen. */
    private List<Component> topLines(String lang, Kit kit, int limit) {
        List<Component> lore = new ArrayList<>();
        List<Leaderboard.Row> top = plugin.board().top(kit, limit);
        if (top.isEmpty()) {
            lore.add(Texts.text(lang, "stats.top.empty"));
        }
        for (int r = 0; r < top.size(); r++) {
            Leaderboard.Row row = top.get(r);
            lore.add(Texts.text(lang, "stats.top.line", Placeholder.unparsed("pos", String.valueOf(r + 1)),
                    Placeholder.unparsed("name", row.name()), Placeholder.unparsed("elo", String.valueOf(row.elo())),
                    Placeholder.unparsed("wins", String.valueOf(row.wins()))));
        }
        return lore;
    }

    private static String roundsName(String lang, int bestOf) {
        return bestOf <= 1 ? Texts.raw(lang, "rounds.1")
                : Texts.plain(lang, "rounds.n", Placeholder.unparsed("n", String.valueOf(bestOf)));
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
            ItemStack item = Items.glow(Items.named(a.item(), 1, gold(Texts.text(lang, "ability." + a.id())), lore),
                    chosen);
            menu.set(slots[i], item, who -> {
                PlayerData d = plugin.data(who);
                d.ability(a);
                plugin.store().save(d);
                who.sendMessage(Texts.msg(lang, "ability.chosen",
                        Placeholder.unparsed("name", Texts.raw(lang, "ability." + a.id()))));
                Ui.sound(who, "entity.experience_orb.pickup", 1.2f);
                openKit(who, Kit.FAEHIGKEITEN);
            });
        }
        menu.set(22, Items.named(Material.ARROW, 1, Texts.text(lang, "menu.back"), null),
                who -> openKit(who, Kit.FAEHIGKEITEN));
        menu.open(p);
    }

    // ---------- Kit bearbeiten ----------

    public void openEditorChooser(Player p) {
        String lang = plugin.lang(p);
        if (!plugin.hall().inHall(p)) {
            p.sendMessage(Texts.msg(lang, "editor.busy"));
            return;
        }
        Menu menu = new Menu(6, Texts.text(lang, "menu.editor.choose.title"));
        frame(menu, 0);
        Kit.Group[] groups = Kit.Group.values();
        for (int g = 0; g < groups.length; g++) {
            Kit.Group group = groups[g];
            int base = (g + 1) * ROW;
            menu.set(base, Items.named(group.icon(), 1, Texts.text(lang, "group." + group.id())
                    .color(TextColor.color(Texts.EMERALD)).decoration(TextDecoration.BOLD, true),
                    Texts.lines(lang, "group." + group.id() + ".desc")), null);
            List<Loadouts.Loadout> list = Loadouts.editable(group);
            for (int i = 0; i < list.size() && i + 1 < ROW; i++) {
                Loadouts.Loadout l = list.get(i);
                Material icon = l.ability() != null ? l.ability().item() : null;
                ItemStack item = icon != null ? new ItemStack(icon) : kitIcon(l.kit(), null);
                List<Component> lore = new ArrayList<>(l.ability() == null
                        ? Texts.lines(lang, "kit." + l.kit().id() + ".desc")
                        : Texts.lines(lang, "ability." + l.ability().id() + ".desc"));
                lore.add(Component.empty());
                lore.add(Texts.text(lang, "menu.editor.choose.lore"));
                menu.set(base + 1 + i, named(item, gold(Component.text(Loadouts.displayName(l, lang))), lore),
                        who -> openEditor(who, l));
            }
        }
        frame(menu, 4);
        menu.set(49, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
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
        Menu menu = new Menu(6, Texts.text(lang, "menu.stats.title"));
        frame(menu, 0);
        frame(menu, 4);
        PlayerData d = plugin.data(p);
        int games = 0;
        int wins = 0;
        for (Kit k : Kit.values()) {
            games += d.kit(k).games;
            wins += d.kit(k).wins;
        }
        ItemStack head = head(p);
        int totalGames = games;
        int totalWins = wins;
        head.editMeta(m -> {
            m.displayName(Texts.text(lang, "menu.stats.me", Placeholder.unparsed("name", p.getName())));
            m.lore(Texts.lines(lang, "menu.stats.me.lore",
                    Placeholder.unparsed("games", String.valueOf(totalGames)),
                    Placeholder.unparsed("wins", String.valueOf(totalWins)),
                    Placeholder.unparsed("streak", String.valueOf(d.streak())),
                    Placeholder.unparsed("best", String.valueOf(d.bestStreak()))));
        });
        menu.set(4, head, null);
        kitRows(menu, lang, kit -> true, (kit, slot) -> {
            String kitName = Texts.raw(lang, "kit." + kit.id());
            List<Component> lore = new ArrayList<>();
            lore.add(Texts.text(lang, "menu.stats.own", Placeholder.unparsed("kit", kitName)));
            lore.addAll(statLines(lang, d, kit));
            lore.add(Component.empty());
            lore.add(Texts.text(lang, "menu.stats.kit.top"));
            lore.addAll(topLines(lang, kit, TOP_SHORT));
            menu.set(slot, named(kitIcon(kit, d), gold(Texts.text(lang, "kit." + kit.id())), lore), null);
        });
        menu.set(49, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
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

    // ---------- Herausforderung und Party: Jeder gegen jeden ----------

    /** Kampfart für eine Herausforderung wählen – vorher (unten) Arena und Runden. */
    public void openDuel(Player p, Player target) {
        String lang = plugin.lang(p);
        Menu menu = new Menu(6, Texts.text(lang, "menu.duel.title", Placeholder.unparsed("name", target.getName())));
        int[] choice = {0, 0};
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            menu.clear();
            frame(menu, 0);
            ItemStack head = head(target);
            head.editMeta(m -> {
                m.displayName(Texts.text(lang, "menu.duel.info", Placeholder.unparsed("name", target.getName())));
                m.lore(Texts.lines(lang, "menu.duel.info.lore"));
            });
            menu.set(4, head, null);
            kitRows(menu, lang, kit -> plugin.kitSettings().on(kit), (kit, slot) -> {
                List<Component> lore = new ArrayList<>(Texts.lines(lang, "kit." + kit.id() + ".desc"));
                lore.add(Component.empty());
                lore.add(Texts.text(lang, "menu.duel.click"));
                menu.set(slot, named(kitIcon(kit, null), gold(Texts.text(lang, "kit." + kit.id())), lore), who -> {
                    who.closeInventory();
                    Player t = plugin.getServer().getPlayer(target.getUniqueId());
                    if (t != null) {
                        plugin.challenges().challenge(who, t, options(kit, choice));
                    }
                });
            });
            options(menu, lang, choice, draw[0]);
            menu.set(49, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
        };
        draw[0].run();
        menu.open(p);
    }

    /** Party-Kampf in der eigenen Party – Kampfart wählen (Spielweise, Arena und Runden unten). */
    public void openFfa(Player p) {
        openFfa(p, false);
    }

    /**
     * Party-Kampf in der eigenen Party: Jeder gegen jeden oder zwei Teams ({@code split}), Kampfart wählen; unten
     * Spielweise, Arena und Runden.
     */
    public void openFfa(Player p, boolean split) {
        String lang = plugin.lang(p);
        if (!plugin.hall().inHall(p)) {
            p.sendMessage(Texts.msg(lang, "duel.you-busy"));
            return;
        }
        Menu menu = new Menu(6, Texts.text(lang, "menu.ffa.title"));
        int[] choice = {0, 0};
        boolean[] teams = {split};
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            menu.clear();
            frame(menu, 0);
            String mode = teams[0] ? "menu.party.split" : "menu.party.ffa";
            menu.set(4, Items.named(teams[0] ? Material.RED_BANNER : Material.FIREWORK_STAR, 1, Texts.text(lang, mode),
                    Texts.lines(lang, mode + ".lore")), null);
            kitRows(menu, lang, kit -> plugin.kitSettings().on(kit) && (teams[0] || kit.ffa()), (kit, slot) -> {
                List<Component> lore = new ArrayList<>(Texts.lines(lang, "kit." + kit.id() + ".desc"));
                lore.add(Component.empty());
                lore.add(Texts.text(lang, "menu.ffa.click"));
                menu.set(slot, named(kitIcon(kit, null), gold(Texts.text(lang, "kit." + kit.id())), lore), who -> {
                    who.closeInventory();
                    plugin.partyFights().start(who, options(kit, choice), teams[0]);
                });
            });
            options(menu, lang, choice, draw[0]);
            menu.set(40, Items.named(teams[0] ? Material.RED_BANNER : Material.FIREWORK_STAR, 1,
                    Texts.text(lang, "menu.option.party", Placeholder.unparsed("mode",
                            Texts.raw(lang, teams[0] ? "party.mode.split" : "party.mode.ffa"))),
                    Texts.lines(lang, "menu.option.party.lore")), who -> {
                        teams[0] = !teams[0];
                        draw[0].run();
                        Ui.sound(who, "ui.button.click", 1.2f);
                    });
            menu.set(45, Items.named(Material.ARROW, 1, Texts.text(lang, "menu.back"), null), this::openFight);
            menu.set(49, Items.named(Material.BARRIER, 1, Texts.text(lang, "menu.close"), null), Player::closeInventory);
        };
        draw[0].run();
        menu.open(p);
    }

    /** Knöpfe für Arena (Zufall oder eine der vier) und Runden (1/3/5); {@code choice} = {Arena, Runden}. */
    private void options(Menu menu, String lang, int[] choice, Runnable redraw) {
        frame(menu, 4);
        List<ArenaType> arenas = ArenaType.normals();
        String arenaName = choice[0] == 0 ? Texts.raw(lang, "arena.random")
                : Texts.raw(lang, arenas.get(choice[0] - 1).textKey());
        menu.set(39, Items.named(Material.FILLED_MAP, 1, Texts.text(lang, "menu.option.arena",
                Placeholder.unparsed("arena", arenaName)), Texts.lines(lang, "menu.option.arena.lore")), who -> {
                    choice[0] = (choice[0] + 1) % (arenas.size() + 1);
                    redraw.run();
                    Ui.sound(who, "ui.button.click", 1.2f);
                });
        int bestOf = ROUNDS[choice[1]];
        menu.set(41, Items.named(Material.CLOCK, bestOf, Texts.text(lang, "menu.option.rounds",
                Placeholder.unparsed("rounds", roundsName(lang, bestOf))), Texts.lines(lang, "menu.option.rounds.lore")),
                who -> {
                    choice[1] = (choice[1] + 1) % ROUNDS.length;
                    redraw.run();
                    Ui.sound(who, "ui.button.click", 1.2f);
                });
    }

    private static Challenges.Options options(Kit kit, int[] choice) {
        List<ArenaType> arenas = ArenaType.normals();
        ArenaType arena = choice[0] == 0 ? null : arenas.get(choice[0] - 1);
        return new Challenges.Options(kit, arena, ROUNDS[choice[1]]);
    }

    /** Für Bedrock-Spieler: Herausforderung als Menü (im Chat kann man dort nichts anklicken). */
    public void openChallenge(Player to, Player from, String description) {
        String lang = plugin.lang(to);
        Menu menu = new Menu(3, Texts.text(lang, "menu.challenge.title", Placeholder.unparsed("name", from.getName())));
        menu.set(11, Items.named(Material.LIME_WOOL, 1, Texts.text(lang, "menu.challenge.accept"), null), who -> {
            who.closeInventory();
            plugin.challenges().accept(who, from.getName());
        });
        ItemStack head = head(from);
        head.editMeta(m -> {
            m.displayName(Texts.text(lang, "menu.challenge.info", Placeholder.unparsed("name", from.getName())));
            m.lore(List.of(Component.text(description).decoration(TextDecoration.ITALIC, false)));
        });
        menu.set(13, head, null);
        menu.set(15, Items.named(Material.RED_WOOL, 1, Texts.text(lang, "menu.challenge.deny"), null), who -> {
            who.closeInventory();
            plugin.challenges().deny(who, from.getName());
        });
        menu.open(to);
    }
}
