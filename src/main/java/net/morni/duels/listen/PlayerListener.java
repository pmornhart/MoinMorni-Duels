package net.morni.duels.listen;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.morni.duels.DuelsPlugin;
import net.morni.duels.Items;
import net.morni.duels.Texts;
import net.morni.duels.Ui;
import net.morni.duels.fight.Fight;
import net.morni.duels.hall.Hall;
import net.morni.duels.menu.Menu;
import net.morni.duels.world.Arenas;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Ankommen, Gehen, Knöpfe der Wartehalle, Bewegung (Einfrieren im Countdown, Sturz in die Tiefe). */
public final class PlayerListener implements Listener {

    /** Doppelklicks (Rechtsklick auf Block + Luft) nur einmal zählen. */
    private static final long CLICK_GAP_MS = 250L;
    private static final double FALL_LIMIT = 25.0;
    private static final long WELCOME_DELAY_TICKS = 20L;

    private final DuelsPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public PlayerListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Kommen und Gehen ----------

    /** Gleich in der Wartehalle ankommen (nicht erst in der Hauptwelt). */
    @EventHandler
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent e) {
        e.setSpawnLocation(plugin.hall().spawn());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.joinMessage(null);
        Player p = e.getPlayer();
        plugin.data(p);
        plugin.board().remember(p.getUniqueId(), p.getName());
        plugin.hall().enter(p);
        plugin.stateChanged();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            String lang = plugin.lang(p);
            plugin.hall().checkLanguage(p);
            Ui.title(p, Texts.text(lang, "join.title"), Texts.text(lang, "join.subtitle"), 2000);
            p.sendMessage(Texts.msg(lang, "join.welcome"));
            if (!plugin.ready()) {
                p.sendMessage(Texts.msg(lang, "not-ready"));
            }
        }, WELCOME_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(null);
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        plugin.queues().leave(u, false);
        plugin.challenges().clear(u);
        plugin.fights().quit(p);
        plugin.abilities().forget(u);
        plugin.hall().forget(u);
        plugin.store().unload(u);
        lastClick.remove(u);
        plugin.stateChanged();
    }

    // ---------- Knöpfe in der Wartehalle ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) {
            return;
        }
        Player p = e.getPlayer();
        String action = Items.hallTag(e.getItem());
        if (action == null || !plugin.hall().inHall(p)) {
            return;
        }
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean bedrockTap = e.getAction() == Action.LEFT_CLICK_AIR && Ui.bedrock(p.getUniqueId());
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        if ((right || bedrockTap) && debounce(p)) {
            plugin.hall().use(p, action);
        }
    }

    /** Rechtsklick mit dem Schwert auf einen Spieler in der Halle: herausfordern. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractPlayer(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player target)) {
            return;
        }
        Player p = e.getPlayer();
        if (!Hall.FIGHT.equals(Items.hallTag(p.getInventory().getItemInMainHand())) || !plugin.hall().inHall(p)
                || !plugin.hall().inHall(target) || !debounce(p)) {
            return;
        }
        e.setCancelled(true);
        plugin.menus().openDuel(p, target);
    }

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        return last == null || now - last > CLICK_GAP_MS;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getWorld() == plugin.world() && !plugin.builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (plugin.hall().inHall(e.getPlayer()) && !plugin.builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** In der Halle bleibt alles, wo es ist; ein Klick auf einen Knopf im Inventar zählt wie Rechtsklick. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (holder instanceof Menu || !(e.getWhoClicked() instanceof Player p) || plugin.builder(p)
                || !plugin.hall().inHall(p)) {
            return;
        }
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        String action = Items.hallTag(clicked);
        if (action != null && e.getClick() == ClickType.LEFT && debounce(p)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                plugin.hall().use(p, action);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (plugin.hall().inHall(p) && !plugin.builder(p)) {
            e.setCancelled(true);
        }
    }

    // ---------- Bewegung ----------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) {
            return;
        }
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        Optional<Fight> of = plugin.fights().of(u);
        if (of.isPresent()) {
            Fight f = of.get();
            Location from = e.getFrom();
            Location to = e.getTo();
            if (f.phase() == Fight.Phase.COUNTDOWN && f.isAlive(u)) {
                if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
                    Location back = from.clone();
                    back.setYaw(to.getYaw());
                    back.setPitch(to.getPitch());
                    e.setTo(back);
                }
                return;
            }
            if (f.phase() == Fight.Phase.RUNNING && f.isAlive(u) && to.getWorld() == plugin.world()
                    && to.getY() < f.arena().voidY()) {
                plugin.fights().eliminate(f, p, f.recentDamager(u, System.currentTimeMillis()), Fight.Cause.VOID);
            }
            return;
        }
        if (plugin.fights().watching(u).isPresent()) {
            return;
        }
        if (p.getWorld() == plugin.world() && e.getTo().getY() < Arenas.FLOOR_Y - FALL_LIMIT
                && p.getGameMode() != GameMode.SPECTATOR) {
            plugin.hall().enter(p);
        }
    }

    /** Wer die Duels-Welt verlässt (Admin-Befehl, anderes Plugin), ist nicht mehr im Kampf und nicht mehr in der Schlange. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (e.getFrom() != plugin.world()) {
            return;
        }
        Player p = e.getPlayer();
        plugin.fights().leftWorld(p);
        plugin.queues().leave(p.getUniqueId(), false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            Optional<Fight> f = plugin.fights().of(u);
            if (f.isEmpty() || !f.get().isAlive(u) || f.get().phase() != Fight.Phase.RUNNING
                    || !f.get().arena().contains(e.getTo())) {
                e.setCancelled(true);
            }
        } else if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && p.getWorld() == plugin.world()) {
            Optional<Fight> f = plugin.fights().watching(u).or(() -> plugin.fights().of(u));
            if (f.isEmpty() || !f.get().arena().contains(e.getTo())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Optional<Fight> f = plugin.fights().of(p.getUniqueId());
        if (f.isPresent()) {
            e.setRespawnLocation(f.get().arena().viewPoint());
            return;
        }
        e.setRespawnLocation(plugin.hall().spawn());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && plugin.hall().inHall(p)) {
                plugin.hall().enter(p);
            }
        });
    }
}
