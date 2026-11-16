package de.moinmorni.duels.listen;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.Items;
import de.moinmorni.duels.Ui;
import de.moinmorni.duels.fight.Fight;
import de.moinmorni.duels.kit.Ability;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.kit.Loadouts;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Kampf: Schaden nur im laufenden Kampf und nur zwischen Gegnern, kein echter Tod (wer fallen würde, scheidet aus und
 * schaut zu), Suppe, Fähigkeiten, Rückstoß wie früher im Kit „Klassisch“, Hunger.
 */
public final class CombatListener implements Listener {

    private static final double ABILITY_ITEM_HIT = 1.0;
    private static final double CLASSIC_LIFT_MAX = 0.4;
    private static final double SPRINT_LIFT = 0.1;
    private static final long BOWL_TICKS = 40L;
    private static final int NO_PICKUP = 32767;

    private final DuelsPlugin plugin;

    public CombatListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || victim.getWorld() != plugin.world()) {
            return;
        }
        UUID v = victim.getUniqueId();
        Optional<Fight> of = plugin.fights().of(v);
        if (of.isEmpty() || !of.get().live() || !of.get().isAlive(v)) {
            e.setCancelled(true);
            return;
        }
        Fight f = of.get();
        Player attacker = attacker(e);
        long now = System.currentTimeMillis();
        if (attacker != null && attacker != victim) {
            UUID a = attacker.getUniqueId();
            if (!f.isAlive(a) || f.sameTeam(a, v) || f.team(a) < 0) {
                e.setCancelled(true);
                return;
            }
            f.hit(v, a, now);
            if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && !plugin.abilities().abilityHit()
                    && Loadouts.ABILITY.equals(Items.kitTag(attacker.getInventory().getItemInMainHand()))) {
                e.setDamage(ABILITY_ITEM_HIT);
            }
        }
        if (f.kit() == Kit.KLASSISCH && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && plugin.abilities().consumeFallSafe(v)) {
            e.setCancelled(true);
            return;
        }
        if (e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            UUID killer = attacker != null && attacker != victim ? attacker.getUniqueId() : f.recentDamager(v, now);
            plugin.fights().eliminate(f, victim, killer, attacker != null ? Fight.Cause.KILLED : Fight.Cause.DIED);
        }
    }

    /** Wer hat getroffen – auch mit Pfeil, Trank oder Perle. */
    private static Player attacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) {
            return null;
        }
        Entity d = by.getDamager();
        if (d instanceof Player p) {
            return p;
        }
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        if (d instanceof AreaEffectCloud c && c.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** Klassisch: Rückstoß hebt auch in der Luft an – wie früher (sonst nur am Boden). */
    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getHitBy() instanceof Player)) {
            return;
        }
        Optional<Fight> f = plugin.fights().of(victim.getUniqueId());
        if (f.isEmpty() || f.get().kit() != Kit.KLASSISCH) {
            return;
        }
        Vector add = e.getKnockback();
        double current = victim.getVelocity().getY();
        double after = current + add.getY();
        double wanted;
        if (e.getCause() == EntityKnockbackEvent.Cause.DAMAGE) {
            wanted = Math.min(CLASSIC_LIFT_MAX, current / 2.0 + e.getKnockbackStrength());
        } else if (e.getCause() == EntityKnockbackEvent.Cause.ENTITY_ATTACK) {
            wanted = Math.min(CLASSIC_LIFT_MAX, Math.max(current, after) + SPRINT_LIFT);
        } else {
            return;
        }
        if (wanted > after) {
            add.setY(wanted - current);
            e.setKnockback(add);
        }
    }

    /** Suppe (Klassisch) und Fähigkeiten (Rechtsklick; Bedrock auch Tippen in die Luft). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null) {
            return;
        }
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        Optional<Fight> of = plugin.fights().of(u);
        if (of.isEmpty()) {
            return;
        }
        Fight f = of.get();
        ItemStack item = e.getItem();
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (item.getType() == Material.MUSHROOM_STEW && right) {
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);
            if (f.live() && f.isAlive(u)) {
                soup(p);
            }
            return;
        }
        if (Loadouts.ABILITY.equals(Items.kitTag(item))
                && (right || a == Action.LEFT_CLICK_AIR && Ui.bedrock(u))) {
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);
            Ability ability = f.ability(u);
            if (f.live() && f.isAlive(u) && ability != null) {
                plugin.abilities().use(p, f, ability);
            }
        }
    }

    /** Pilzsuppe: heilt sofort, die leere Schüssel fällt herunter (und verschwindet). */
    private void soup(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double full = max == null ? 20.0 : max.getValue();
        if (p.getHealth() >= full) {
            return;
        }
        p.setHealth(Math.min(full, p.getHealth() + plugin.settings().soupHeal()));
        p.getInventory().setItemInMainHand(null);
        Item bowl = p.getWorld().dropItem(p.getLocation().add(0, 0.6, 0), new ItemStack(Material.BOWL));
        bowl.setPickupDelay(NO_PICKUP);
        bowl.setVelocity(p.getLocation().getDirection().multiply(0.15).setY(0.15));
        plugin.getServer().getScheduler().runTaskLater(plugin, bowl::remove, BOWL_TICKS);
        Ui.sound(p, "entity.generic.drink", 1.3f);
    }

    /** Angehalten (jemand fehlt): auch das Leben bleibt, wie es ist – niemand heilt sich in der Pause. */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && plugin.fights().of(p.getUniqueId()).filter(Fight::paused).isPresent()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.getWorld() != plugin.world()) {
            return;
        }
        if (plugin.fights().of(p.getUniqueId()).isEmpty()) {
            e.setCancelled(true);
            p.setFoodLevel(20);
            return;
        }
        if (e.getFoodLevel() < p.getFoodLevel()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent e) {
        Optional<Fight> f = plugin.fights().of(e.getPlayer().getUniqueId());
        if (f.isEmpty() || !f.get().live() || !f.get().isAlive(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && p.getWorld() == plugin.world() && !plugin.builder(p)) {
            e.setCancelled(true);
        }
    }

    /** Geschosse nur im laufenden Kampf. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || p.getWorld() != plugin.world()) {
            return;
        }
        Optional<Fight> f = plugin.fights().of(p.getUniqueId());
        if (f.isEmpty() || !f.get().live() || !f.get().isAlive(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Falls doch jemand stirbt (z. B. /kill): nichts fallen lassen; im Kampf zählt es als Ausscheiden. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld() != plugin.world()) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Optional<Fight> f = plugin.fights().of(p.getUniqueId());
        if (f.isPresent() && f.get().isAlive(p.getUniqueId())) {
            plugin.fights().eliminate(f.get(), p, f.get().recentDamager(p.getUniqueId(), System.currentTimeMillis()),
                    Fight.Cause.DIED);
        }
    }
}
