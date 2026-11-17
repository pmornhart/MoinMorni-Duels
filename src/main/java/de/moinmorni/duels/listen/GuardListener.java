package de.moinmorni.duels.listen;

import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.world.Arena;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Schutz der eigenen Welt: niemand baut, nichts brennt, wächst oder verfällt, keine Tiere oder Monster, kein Regen.
 * Ausnahmen: Admins im Kreativmodus (zum Ausbessern) und Kämpfe, in denen gebaut oder geschaufelt wird (UHC,
 * Bau-Kampf, Brücke, Spleef – Regeln in {@code fight.Building}). Was sich in einer belegten Arena ändert, wird nach dem
 * Kampf zurückgesetzt.
 */
public final class GuardListener implements Listener {

    private final DuelsPlugin plugin;

    public GuardListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ours(World w) {
        return w == plugin.world();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (ours(e.getBlock().getWorld()) && !plugin.builder(e.getPlayer())
                && !plugin.building().mayBreak(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (ours(e.getBlock().getWorld()) && !plugin.builder(e.getPlayer())
                && !plugin.building().mayPlace(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
        }
    }

    /** Was verändert wird (im Kampf gebaut, Admin im Kreativmodus), merkt sich die Arena für danach. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void logBreak(BlockBreakEvent e) {
        remember(e.getBlock());
        plugin.building().removed(e.getPlayer(), e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void logPlace(BlockPlaceEvent e) {
        busyArena(e.getBlock()).ifPresent(a -> a.log().remember(e.getBlock(), e.getBlockReplacedState().getBlockData()));
        plugin.building().placed(e.getPlayer(), e.getBlock());
    }

    private void remember(Block b) {
        busyArena(b).ifPresent(a -> a.log().remember(b));
    }

    private Optional<Arena> busyArena(Block b) {
        if (!ours(b.getWorld())) {
            return Optional.empty();
        }
        return plugin.arenas().at(b.getLocation()).filter(Arena::busy);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!ours(p.getWorld()) || plugin.builder(p)) {
            return;
        }
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Türen, Truhen, Hebel … bleiben zu. Sonst nicht sperren: wer im Kampf bauen darf, setzt seine Blöcke über
            // genau diesen Klick (ein gesperrter Block ließe auch das Setzen nicht zu; ob gebaut werden darf, prüft
            // onPlace).
            Block b = e.getClickedBlock();
            if (b == null || b.getType().isInteractable() || !plugin.building().mayPlace(p, b.getRelative(e.getBlockFace()))) {
                e.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (ours(e.getBlock().getWorld()) && !plugin.builder(e.getPlayer())
                && !plugin.building().mayEmpty(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (ours(e.getBlock().getWorld()) && !plugin.builder(e.getPlayer())
                && !plugin.building().mayFill(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void logBucket(PlayerBucketEmptyEvent e) {
        remember(e.getBlock());
        plugin.building().placed(e.getPlayer(), e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void logBucket(PlayerBucketFillEvent e) {
        remember(e.getBlock());
        plugin.building().removed(e.getPlayer(), e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    /** Lava trifft Wasser: in einer UHC-Arena darf Stein entstehen (wird gemerkt), sonst nichts. */
    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (!ours(e.getBlock().getWorld())) {
            return;
        }
        if (plugin.building().liquids(e.getBlock()).isPresent()) {
            remember(e.getBlock());
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    /** Wasser und Lava fließen nur in einer UHC-Arena (jeder erreichte Block wird gemerkt), sonst nie. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!ours(e.getBlock().getWorld())) {
            return;
        }
        Block to = e.getToBlock();
        if (plugin.building().liquids(e.getBlock()).filter(f -> f.arena().contains(to.getLocation())).isPresent()) {
            remember(to);
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (ours(e.getLocation().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        if (ours(e.getBlock().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        if (ours(e.getEntity().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (ours(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (ours(e.getWorld()) && e.toWeatherState()) {
            e.setCancelled(true);
        }
    }
}
