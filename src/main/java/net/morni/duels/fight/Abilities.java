package net.morni.duels.fight;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.morni.duels.DuelsPlugin;
import net.morni.duels.Texts;
import net.morni.duels.Ui;
import net.morni.duels.kit.Ability;
import net.morni.duels.kit.Kit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SplashPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

/**
 * Fähigkeiten im Kit „Fähigkeiten“: Auslösen per Rechtsklick auf den Fähigkeits-Gegenstand, danach Abklingzeit.
 * Die Restzeit steht in der Aktionsleiste (und als Abklingzeit am Gegenstand).
 */
public final class Abilities {

    private static final long SECOND_MS = 1000L;
    private static final int TICKS_PER_SECOND = 20;
    /** Bergführer: so lange nach dem Sprung tut die Landung nicht weh. */
    private static final long FALL_SAFE_MS = 6000L;
    private static final double LEAP_FORWARD = 1.25;
    private static final double LEAP_UP = 0.8;
    /** Schmied: Reichweite und Winkel des Schild-Stoßes. */
    private static final double BASH_RANGE = 5.0;
    private static final double BASH_ANGLE = Math.toRadians(70);
    private static final double BASH_DAMAGE = 2.0;
    private static final double BASH_PUSH = 1.4;
    private static final double BASH_LIFT = 0.45;
    private static final int BASH_SLOW_TICKS = 40;
    /** Kräuterfrau: Regeneration im Trank. */
    private static final int HERB_REGEN_TICKS = 80;
    /** Jäger: Pfeile im Fächer. */
    private static final int ARROWS_EACH_SIDE = 2;
    private static final double ARROW_SPREAD_DEG = 7.0;
    private static final double ARROW_SPEED = 2.4;
    private static final double ARROW_DAMAGE = 1.5;
    private static final long HAIL_LIFETIME_TICKS = 60L;

    private final DuelsPlugin plugin;
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private final Map<UUID, Long> fallSafeUntil = new HashMap<>();
    /** Gerade läuft ein Treffer durch eine Fähigkeit (nicht durch den Gegenstand selbst). */
    private boolean abilityHit;

    public Abilities(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset(Player p) {
        readyAt.remove(p.getUniqueId());
        fallSafeUntil.remove(p.getUniqueId());
        for (Ability a : Ability.values()) {
            p.setCooldown(a.item(), 0);
        }
    }

    public void forget(UUID u) {
        readyAt.remove(u);
        fallSafeUntil.remove(u);
    }

    public boolean abilityHit() {
        return abilityHit;
    }

    /** Fällt die nächste Landung weich? (wird dabei verbraucht) */
    public boolean consumeFallSafe(UUID u) {
        Long until = fallSafeUntil.remove(u);
        return until != null && until >= System.currentTimeMillis();
    }

    public void use(Player p, Fight f, Ability a) {
        long now = System.currentTimeMillis();
        long ready = readyAt.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            Ui.sound(p, "block.note_block.bass", 0.7f);
            return;
        }
        readyAt.put(p.getUniqueId(), now + a.cooldownSeconds() * SECOND_MS);
        p.setCooldown(a.item(), a.cooldownSeconds() * TICKS_PER_SECOND);
        switch (a) {
            case BERGFUEHRER -> leap(p, now);
            case SCHMIED -> bash(p, f);
            case KRAEUTERFRAU -> herbs(p);
            case JAEGER -> hail(p);
        }
    }

    /** Kletterseil: kurzer Sprung nach vorn und oben. */
    private void leap(Player p, long now) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        p.setVelocity(dir.normalize().multiply(LEAP_FORWARD).setY(LEAP_UP));
        fallSafeUntil.put(p.getUniqueId(), now + FALL_SAFE_MS);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 14, 0.3, 0.05, 0.3, 0.02);
        Ui.sound(p, "entity.goat.long_jump", 1.0f);
    }

    /** Schmiedehammer: Schild-Stoß nach vorn. */
    private void bash(Player p, Fight f) {
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        for (UUID o : f.opponents(p.getUniqueId())) {
            Player t = plugin.getServer().getPlayer(o);
            if (t == null || !f.isAlive(o)) {
                continue;
            }
            Vector to = t.getLocation().toVector().subtract(p.getLocation().toVector());
            Vector flat = to.clone().setY(0);
            if (to.length() > BASH_RANGE || flat.lengthSquared() < 1.0E-4 || look.angle(flat) > BASH_ANGLE) {
                continue;
            }
            abilityHit = true;
            try {
                t.damage(BASH_DAMAGE, p);
            } finally {
                abilityHit = false;
            }
            if (f.isAlive(o)) {
                t.setVelocity(flat.normalize().multiply(BASH_PUSH).setY(BASH_LIFT));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BASH_SLOW_TICKS, 1));
            }
        }
        Location front = p.getEyeLocation().add(look.clone().multiply(1.5));
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, front, 3, 0.5, 0.2, 0.5, 0);
        Ui.sound(p, "item.shield.block", 0.8f);
        Ui.sound(p, "block.anvil.land", 1.6f);
    }

    /** Kräuterfläschchen: Heiltrank werfen. */
    private void herbs(Player p) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        potion.editMeta(PotionMeta.class, m -> {
            m.setBasePotionType(PotionType.HEALING);
            m.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, HERB_REGEN_TICKS, 0), true);
        });
        SplashPotion thrown = p.launchProjectile(SplashPotion.class);
        thrown.setItem(potion);
        Ui.sound(p, "entity.splash_potion.throw", 1.0f);
    }

    /** Pfeilbündel: fünf Pfeile im Fächer. */
    private void hail(Player p) {
        Vector base = p.getEyeLocation().getDirection();
        for (int i = -ARROWS_EACH_SIDE; i <= ARROWS_EACH_SIDE; i++) {
            Vector v = base.clone().rotateAroundY(Math.toRadians(i * ARROW_SPREAD_DEG)).multiply(ARROW_SPEED);
            Arrow arrow = p.launchProjectile(Arrow.class, v);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(ARROW_DAMAGE);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (arrow.isValid()) {
                    arrow.remove();
                }
            }, HAIL_LIFETIME_TICKS);
        }
        Ui.sound(p, "item.crossbow.shoot", 1.0f);
        Ui.sound(p, "entity.arrow.shoot", 0.8f);
    }

    /** Aktionsleiste mit der Restzeit (alle paar Ticks). */
    public void showCooldowns() {
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Optional<Fight> of = plugin.fights().of(p.getUniqueId());
            if (of.isEmpty()) {
                continue;
            }
            Fight f = of.get();
            Ability a = f.ability(p.getUniqueId());
            if (f.kit() != Kit.FAEHIGKEITEN || f.phase() != Fight.Phase.RUNNING || !f.isAlive(p.getUniqueId())
                    || a == null) {
                continue;
            }
            String lang = plugin.lang(p);
            String name = Texts.raw(lang, "ability." + a.id() + ".item");
            long left = readyAt.getOrDefault(p.getUniqueId(), 0L) - now;
            p.sendActionBar(left <= 0
                    ? Texts.text(lang, "ability.ready", Placeholder.unparsed("name", name))
                    : Texts.text(lang, "ability.wait", Placeholder.unparsed("name", name),
                            Placeholder.unparsed("secs", Ui.seconds(left, lang))));
        }
    }
}
