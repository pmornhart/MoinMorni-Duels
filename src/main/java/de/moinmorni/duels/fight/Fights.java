package de.moinmorni.duels.fight;

import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.Texts;
import de.moinmorni.duels.Ui;
import de.moinmorni.duels.data.KitStats;
import de.moinmorni.duels.data.PlayerData;
import de.moinmorni.duels.hall.Hall;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.kit.Loadouts;
import de.moinmorni.duels.match.Elo;
import de.moinmorni.duels.match.QueueKey;
import de.moinmorni.duels.world.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;

/**
 * Kämpfe: starten (Arena belegen, Ausrüstung, Countdown), Ausscheiden (keine echten Tode – wer fallen würde, schaut
 * zu), Ende (Sieg, Niederlage, Unentschieden nach Zeitlimit), Wertung und Statistik, zurück in die Halle.
 *
 * <p>Verbindung mitten im Kampf weg: Der Platz bleibt {@code rueckkehr-sekunden} frei. Fehlt dadurch ein ganzes Team,
 * hält der Kampf an (alle eingefroren, die Zeit steht) und zeigt „Warte auf …“; sonst (2 gegen 2) kämpft das Team
 * weiter. Wer rechtzeitig zurückkommt, steht genau wieder dort (Leben, Ausrüstung, Ort), nach 3-2-1 geht es weiter.
 * Wer nicht kommt, scheidet aus wie bisher (zählt als Niederlage).
 */
public final class Fights {

    /** Angriffsgeschwindigkeit „ohne Abklingzeit“ (Klassisch). */
    public static final double NO_COOLDOWN = 1024.0;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    private static final float CLASSIC_SATURATION = 0f;
    private static final float MODERN_SATURATION = 5f;
    private static final long TICKS_PER_SECOND = 20L;
    private static final double SPECTATOR_REACH = 60.0;
    private static final int[] WARN_AT = {60, 30, 10};
    /** Alle wieder da: so viele Sekunden 3-2-1, dann geht es weiter. */
    private static final int RESUME_SECONDS = 3;
    private static final long SECOND_MS = 1000L;
    /** So lange erfährt jemand beim nächsten Betreten noch, dass sein Kampf ohne ihn zu Ende ging. */
    private static final long MISSED_MS = 15 * 60 * SECOND_MS;

    private final DuelsPlugin plugin;
    private final Map<UUID, Fight> byPlayer = new HashMap<>();
    private final Map<UUID, Fight> bySpectator = new HashMap<>();
    private final List<Fight> fights = new ArrayList<>();
    /** Wo jemand stand, dessen Platz frei bleibt – für den Ort beim Betreten (wird außerhalb des Server-Takts gelesen). */
    private final Map<UUID, Location> returnSpots = new ConcurrentHashMap<>();
    /** Wer zu spät kam: sein Kampf ging ohne ihn zu Ende (Zeitpunkt, für einen Hinweis beim nächsten Betreten). */
    private final Map<UUID, Long> missed = new HashMap<>();
    private int nextId = 1;

    public Fights(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<Fight> of(UUID u) {
        return Optional.ofNullable(byPlayer.get(u));
    }

    public Optional<Fight> watching(UUID u) {
        return Optional.ofNullable(bySpectator.get(u));
    }

    public int running() {
        return fights.size();
    }

    /** Wie viele gerade in dieser Warteschlangen-Art kämpfen. */
    public int fighting(QueueKey key) {
        int n = 0;
        for (Fight f : fights) {
            if (!f.challenge && f.key().equals(key) && f.phase != Fight.Phase.ENDED) {
                n += f.participants().size();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Start

    /** Kampf starten; false, wenn jemand fehlt oder keine Arena frei ist. */
    public boolean start(Kit kit, boolean ranked, List<UUID> teamA, List<UUID> teamB, boolean challenge) {
        List<UUID> everyone = new ArrayList<>(teamA);
        everyone.addAll(teamB);
        for (UUID u : everyone) {
            Player p = plugin.getServer().getPlayer(u);
            if (p == null || !plugin.hall().inHall(p)) {
                return false;
            }
        }
        Optional<Arena> arena = plugin.arenas().reserve();
        if (arena.isEmpty()) {
            return false;
        }
        Fight f = new Fight(nextId++, kit, ranked, challenge, arena.get(), teamA, teamB);
        fights.add(f);
        for (int t = 0; t < 2; t++) {
            List<UUID> team = f.teams.get(t);
            List<Location> spawns = f.arena.spawns(t, f.size);
            for (int i = 0; i < team.size(); i++) {
                UUID u = team.get(i);
                Player p = plugin.getServer().getPlayer(u);
                plugin.queues().remove(u);
                PlayerData d = plugin.data(p);
                plugin.store().pin(u);
                byPlayer.put(u, f);
                f.alive.add(u);
                f.names.put(u, p.getName());
                f.data.put(u, d);
                f.eloBefore.put(u, d.kit(kit).elo);
                f.abilities.put(u, d.ability());
                prepare(p, f, d);
                p.teleportAsync(spawns.get(i % spawns.size()));
                plugin.net().playing(p, true);
            }
        }
        for (UUID u : everyone) {
            Player p = plugin.getServer().getPlayer(u);
            String lang = plugin.lang(p);
            p.sendMessage(Texts.msg(lang, "fight.found", Placeholder.unparsed("names", opponentNames(f, u)),
                    Placeholder.unparsed("arena", Texts.raw(lang, f.arena.type().textKey()))));
        }
        f.countdown = plugin.settings().countdownSeconds();
        f.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(f), 1L, TICKS_PER_SECOND);
        plugin.stateChanged();
        return true;
    }

    private String opponentNames(Fight f, UUID u) {
        return String.join(" & ", f.opponents(u).stream().map(f::name).toList());
    }

    /** Ausrüstung anlegen, alles zurücksetzen. */
    private void prepare(Player p, Fight f, PlayerData d) {
        plugin.hall().reset(p);
        p.setSaturation(f.kit == Kit.KLASSISCH ? CLASSIC_SATURATION : MODERN_SATURATION);
        Hall.attackSpeed(p, f.kit == Kit.KLASSISCH ? NO_COOLDOWN : DEFAULT_ATTACK_SPEED);
        Loadouts.Loadout l = Loadouts.of(f.kit, d.ability());
        Loadouts.apply(p, l, d.layout(l.id()), plugin.lang(p));
        plugin.abilities().reset(p);
    }

    // ------------------------------------------------------------------ Ablauf

    private void tick(Fight f) {
        if (f.phase != Fight.Phase.ENDED) {
            checkAway(f);
            if (f.phase == Fight.Phase.ENDED) {
                return;
            }
        }
        switch (f.phase) {
            case COUNTDOWN -> {
                if (f.paused) {
                    pausedTick(f);
                } else {
                    countdown(f);
                }
            }
            case RUNNING -> {
                if (f.paused) {
                    pausedTick(f);
                } else {
                    running(f);
                }
            }
            case ENDED -> {
                if (--f.afterLeft <= 0) {
                    cleanup(f);
                }
            }
        }
    }

    private void countdown(Fight f) {
        if (f.countdown > 0) {
            int n = f.countdown--;
            forEachParticipant(f, p -> {
                String lang = plugin.lang(p);
                Component num = Component.text(String.valueOf(n), TextColor.color(Texts.GOLD), TextDecoration.BOLD);
                Ui.title(p, num, Texts.text(lang, "fight.countdown.sub",
                        Placeholder.unparsed("kit", Texts.raw(lang, "kit." + f.kit.id())),
                        Placeholder.unparsed("names", opponentNames(f, p.getUniqueId()))), 900);
                Ui.sound(p, "block.note_block.pling", 0.8f + 0.2f * (4 - Math.min(3, n)));
            });
            return;
        }
        f.phase = Fight.Phase.RUNNING;
        f.startedAt = System.currentTimeMillis();
        forEachParticipant(f, p -> {
            String lang = plugin.lang(p);
            Ui.title(p, Texts.text(lang, "fight.go"), Texts.text(lang, "fight.go.sub"), 700);
            Ui.sound(p, "block.note_block.bell", 1.6f);
            p.showBossBar(bar(f, lang));
        });
        updateBars(f, plugin.settings().timeLimitSeconds());
    }

    private void running(Fight f) {
        int limit = plugin.settings().timeLimitSeconds();
        long elapsed = (System.currentTimeMillis() - f.startedAt) / 1000L;
        long left = limit - elapsed;
        updateBars(f, left);
        for (int warn : WARN_AT) {
            // Jede Warnung höchstens einmal – auch wenn der Server kurz hängt und eine Sekunde überspringt.
            if (left <= warn && left > 0 && limit > warn * 2 && f.warned.add(warn) && left > warn - 3) {
                broadcast(f, "fight.time-left", lang -> new TagResolver[] {Placeholder.unparsed("n", String.valueOf(warn))});
            }
        }
        if (left <= 0) {
            finish(f, -1);
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID u : List.copyOf(f.alive)) {
            Player p = plugin.getServer().getPlayer(u);
            if (p != null && p.getWorld() == f.arena.world() && p.getLocation().getY() < f.arena.voidY()) {
                eliminate(f, p, f.recentDamager(u, now), Fight.Cause.VOID);
            }
        }
        for (UUID u : List.copyOf(f.spectators)) {
            Player s = plugin.getServer().getPlayer(u);
            if (s != null && (s.getWorld() != f.arena.world()
                    || s.getLocation().distance(f.arena.center()) > SPECTATOR_REACH)) {
                s.teleport(f.arena.viewPoint());
            }
        }
    }

    // ------------------------------------------------------------------ Verbindung weg, wiederkommen

    /** Verbindung weg mitten im Kampf: Stand merken, Platz freihalten, ggf. anhalten. */
    private void hold(Fight f, Player p, int seconds) {
        UUID u = p.getUniqueId();
        f.away.put(u, new Fight.Away(System.currentTimeMillis() + seconds * SECOND_MS, PlayerState.of(p)));
        returnSpots.put(u, p.getLocation().clone());
        plugin.net().holdSlot(u, seconds);
        updatePause(f);
        String name = f.name(u);
        String key = f.paused ? "fight.away" : "fight.away.on";
        broadcast(f, key, lang -> new TagResolver[] {Placeholder.unparsed("victim", name),
                Placeholder.unparsed("n", String.valueOf(seconds))});
    }

    /** Fehlt von Team {@code t} gerade jeder (alle Lebenden haben die Verbindung verloren)? */
    private boolean teamGone(Fight f, int t) {
        boolean away = false;
        for (UUID u : f.teams.get(t)) {
            if (!f.alive.contains(u)) {
                continue;
            }
            if (f.away.containsKey(u)) {
                away = true;
            } else if (plugin.getServer().getPlayer(u) != null) {
                return false;
            }
        }
        return away;
    }

    /** Anhalten, solange ein Team ganz fehlt; sind wieder alle da: 3-2-1, dann weiter. */
    private void updatePause(Fight f) {
        if (f.phase == Fight.Phase.ENDED) {
            return;
        }
        if (teamGone(f, 0) || teamGone(f, 1)) {
            if (!f.paused) {
                f.paused = true;
                f.pausedAt = System.currentTimeMillis();
            }
            f.resumeIn = 0;
            showWaiting(f);
        } else if (f.paused && f.resumeIn == 0) {
            if (f.phase == Fight.Phase.COUNTDOWN) {
                // Noch vor dem Start: der Countdown beginnt einfach von vorn.
                f.paused = false;
                f.countdown = plugin.settings().countdownSeconds();
            } else {
                f.resumeIn = RESUME_SECONDS + 1;
            }
        }
    }

    /** Jede Sekunde, solange angehalten: „Warte auf …“ oder der Countdown bis es weitergeht. */
    private void pausedTick(Fight f) {
        if (f.resumeIn > 0) {
            int n = --f.resumeIn;
            if (n == 0) {
                resume(f);
                return;
            }
            forEachParticipant(f, p -> {
                String lang = plugin.lang(p);
                Component num = Component.text(String.valueOf(n), TextColor.color(Texts.GOLD), TextDecoration.BOLD);
                Ui.title(p, num, Texts.text(lang, "fight.resume.sub"), 900);
                Ui.sound(p, "block.note_block.pling", 0.8f + 0.2f * (4 - Math.min(3, n)));
            });
            return;
        }
        showWaiting(f);
    }

    /** Weiter geht's: die Uhr läuft dort weiter, wo sie stand. */
    private void resume(Fight f) {
        f.paused = false;
        f.resumeIn = 0;
        if (f.phase != Fight.Phase.RUNNING) {
            return;
        }
        f.startedAt += System.currentTimeMillis() - f.pausedAt;
        forEachParticipant(f, p -> {
            String lang = plugin.lang(p);
            Ui.title(p, Texts.text(lang, "fight.resume.go"), Component.empty(), 700);
            Ui.sound(p, "block.note_block.bell", 1.6f);
        });
        updateBars(f, timeLeft(f));
    }

    private long timeLeft(Fight f) {
        return plugin.settings().timeLimitSeconds() - (System.currentTimeMillis() - f.startedAt) / SECOND_MS;
    }

    /** „Warte auf Anna – 25 s“: in der Aktionsleiste und (im Kampf) in der Leiste oben. */
    private void showWaiting(Fight f) {
        long now = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        long until = now;
        for (int t = 0; t < 2; t++) {
            if (!teamGone(f, t)) {
                continue;
            }
            for (UUID u : f.teams.get(t)) {
                Fight.Away a = f.away.get(u);
                if (a != null && f.alive.contains(u)) {
                    names.add(f.name(u));
                    until = Math.max(until, a.until());
                }
            }
        }
        if (names.isEmpty()) {
            return;
        }
        String who = String.join(" & ", names);
        String secs = String.valueOf((until - now + SECOND_MS - 1) / SECOND_MS);
        TagResolver[] r = {Placeholder.unparsed("name", who), Placeholder.unparsed("n", secs)};
        if (f.phase == Fight.Phase.RUNNING) {
            for (String lang : new String[] {"de", "en"}) {
                BossBar bar = bar(f, lang);
                bar.name(Texts.text(lang, "fight.waiting.bar", r));
                bar.color(BossBar.Color.YELLOW);
            }
        }
        List<UUID> all = new ArrayList<>(f.participants());
        all.addAll(f.spectators);
        for (UUID u : all) {
            Player p = plugin.getServer().getPlayer(u);
            if (p != null && (byPlayer.get(u) == f || bySpectator.get(u) == f) && !f.away.containsKey(u)) {
                p.sendActionBar(Texts.text(plugin.lang(p), "fight.waiting", r));
            }
        }
    }

    /** Frist abgelaufen: wer bis jetzt nicht zurück ist, scheidet aus (zählt wie Verlassen). */
    private void checkAway(Fight f) {
        long now = System.currentTimeMillis();
        for (UUID u : List.copyOf(f.away.keySet())) {
            Fight.Away a = f.away.get(u);
            if (a == null || a.until() > now) {
                continue;
            }
            dropAway(f, u);
            eliminate(f, u, null, f.recentDamager(u, now), Fight.Cause.GONE);
            if (f.phase == Fight.Phase.ENDED) {
                return;
            }
        }
    }

    /** Platz nicht mehr frei halten (Frist um oder Kampf vorbei); beim nächsten Betreten gibt es einen Hinweis. */
    private void dropAway(Fight f, UUID u) {
        if (f.away.remove(u) == null) {
            return;
        }
        returnSpots.remove(u);
        long now = System.currentTimeMillis();
        missed.values().removeIf(at -> now - at > MISSED_MS);
        missed.put(u, now);
        plugin.net().holdSlot(u, 0);
    }

    /** Ort beim Betreten für jemanden, dessen Platz frei ist (null = normal in die Halle). Darf von überall gerufen werden. */
    public Location returnSpot(UUID u) {
        Location l = returnSpots.get(u);
        return l == null ? null : l.clone();
    }

    /**
     * Beim Betreten: Wird ein Platz frei gehalten, kommt der Spieler genau dorthin zurück (Ort, Leben, Ausrüstung) und
     * der Kampf geht nach 3-2-1 weiter. false = kein Platz frei (dann in die Halle).
     */
    public boolean rejoin(Player p) {
        UUID u = p.getUniqueId();
        returnSpots.remove(u);
        Fight f = byPlayer.get(u);
        Fight.Away a = f == null ? null : f.away.remove(u);
        if (a == null) {
            return false;
        }
        missed.remove(u);
        String lang = plugin.lang(p);
        a.state().restore(p);
        Hall.attackSpeed(p, f.kit == Kit.KLASSISCH ? NO_COOLDOWN : DEFAULT_ATTACK_SPEED);
        plugin.net().holdSlot(u, 0);
        plugin.net().playing(p, true);
        if (f.phase == Fight.Phase.RUNNING) {
            p.showBossBar(bar(f, lang));
        }
        Ui.title(p, Texts.text(lang, "fight.welcome-back.title"), Texts.text(lang, "fight.welcome-back.sub"), 2000);
        p.sendMessage(Texts.msg(lang, "fight.welcome-back"));
        String name = p.getName();
        List<UUID> others = new ArrayList<>(f.participants());
        others.addAll(f.spectators);
        for (UUID o : others) {
            Player q = plugin.getServer().getPlayer(o);
            if (q != null && !o.equals(u) && (byPlayer.get(o) == f || bySpectator.get(o) == f)) {
                q.sendMessage(Texts.msg(plugin.lang(q), "fight.returned", Placeholder.unparsed("name", name)));
            }
        }
        updatePause(f);
        return true;
    }

    /** Kam {@code u} zu spät zurück (Kampf ging ohne ihn zu Ende)? Gibt es nur einmal. */
    public boolean missed(UUID u) {
        Long at = missed.remove(u);
        return at != null && System.currentTimeMillis() - at <= MISSED_MS;
    }

    private BossBar bar(Fight f, String lang) {
        return f.bars.computeIfAbsent(lang, l -> BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN,
                BossBar.Overlay.NOTCHED_10));
    }

    private void updateBars(Fight f, long left) {
        int limit = plugin.settings().timeLimitSeconds();
        float progress = Math.max(0f, Math.min(1f, left / (float) limit));
        for (String lang : new String[] {"de", "en"}) {
            BossBar bar = bar(f, lang);
            bar.name(Texts.text(lang, "fight.bossbar",
                    Placeholder.unparsed("kit", Texts.raw(lang, "kit." + f.kit.id())),
                    Placeholder.unparsed("arena", Texts.raw(lang, f.arena.type().textKey())),
                    Placeholder.unparsed("time", Ui.clock(left))));
            bar.progress(progress);
            bar.color(left <= 30 ? BossBar.Color.RED : BossBar.Color.GREEN);
        }
    }

    private void hideBars(Fight f, Player p) {
        f.bars.values().forEach(p::hideBossBar);
    }

    // ------------------------------------------------------------------ Ausscheiden

    /** Jemand scheidet aus (besiegt, gestürzt, verlassen, aufgegeben). */
    public void eliminate(Fight f, Player victim, UUID killer, Fight.Cause cause) {
        eliminate(f, victim.getUniqueId(), victim, killer, cause);
    }

    /** Wie oben; {@code victim} ist null, wenn die Person nicht mehr da ist (Frist nach Verbindungsabbruch um). */
    private void eliminate(Fight f, UUID v, Player victim, UUID killer, Fight.Cause cause) {
        if (f.phase == Fight.Phase.ENDED || !f.alive.remove(v)) {
            return;
        }
        f.deaths.merge(v, 1, Integer::sum);
        UUID credited = killer != null && !killer.equals(v) && f.team(killer) >= 0 && !f.sameTeam(killer, v)
                ? killer : null;
        if (credited != null) {
            f.kills.merge(credited, 1, Integer::sum);
        }
        String key = switch (cause) {
            case KILLED, DIED -> credited != null ? "fight.killed" : "fight.died";
            case VOID -> "fight.void";
            case QUIT -> "fight.quit";
            case LEFT -> "fight.left";
            case FORFEIT -> "fight.forfeit";
            case GONE -> "fight.away.gone";
        };
        String victimName = f.name(v);
        String killerName = credited == null ? "" : f.name(credited);
        broadcast(f, key, lang -> new TagResolver[] {Placeholder.unparsed("victim", victimName),
                Placeholder.unparsed("killer", killerName)});
        if (cause == Fight.Cause.FORFEIT || cause == Fight.Cause.QUIT || cause == Fight.Cause.LEFT
                || cause == Fight.Cause.GONE) {
            f.gaveUp.add(v);
            byPlayer.remove(v);
            if (victim != null) {
                hideBars(f, victim);
                plugin.net().playing(victim, false);
                if (cause == Fight.Cause.FORFEIT) {
                    victim.sendMessage(Texts.msg(plugin.lang(victim), "fight.gave-up"));
                    plugin.hall().enter(victim);
                }
            }
        } else if (victim != null) {
            plugin.net().playing(victim, false);
            String lang = plugin.lang(victim);
            Location to = cause == Fight.Cause.VOID ? f.arena.viewPoint() : victim.getLocation().add(0, 1.5, 0);
            victim.getInventory().clear();
            victim.setItemOnCursor(null);
            for (PotionEffect e : victim.getActivePotionEffects()) {
                victim.removePotionEffect(e.getType());
            }
            victim.setFireTicks(0);
            if (!victim.isDead()) {
                AttributeInstance max = victim.getAttribute(Attribute.MAX_HEALTH);
                victim.setHealth(max == null ? 20.0 : max.getValue());
            }
            victim.setGameMode(GameMode.SPECTATOR);
            Ui.title(victim, Texts.text(lang, "fight.dead.title"), Texts.text(lang, "fight.dead.sub"), 1500);
            Ui.sound(victim, "entity.player.hurt", 0.8f);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (victim.isOnline() && byPlayer.get(v) == f) {
                    victim.teleport(to);
                }
            });
        }
        if (credited != null) {
            Player k = plugin.getServer().getPlayer(credited);
            if (k != null) {
                Ui.sound(k, "entity.experience_orb.pickup", 1.4f);
            }
        }
        checkEnd(f);
        updatePause(f);
    }

    private void checkEnd(Fight f) {
        boolean a = f.teams.get(0).stream().anyMatch(f.alive::contains);
        boolean b = f.teams.get(1).stream().anyMatch(f.alive::contains);
        if (a && b) {
            return;
        }
        finish(f, a ? 0 : b ? 1 : -1);
    }

    // ------------------------------------------------------------------ Ende

    private void finish(Fight f, int winner) {
        if (f.phase == Fight.Phase.ENDED) {
            return;
        }
        f.phase = Fight.Phase.ENDED;
        f.winner = winner;
        f.afterLeft = plugin.settings().afterSeconds();
        for (UUID u : List.copyOf(f.away.keySet())) {
            dropAway(f, u);
        }
        f.paused = false;
        f.resumeIn = 0;
        double[] avg = {average(f, 0), average(f, 1)};
        int k = plugin.settings().kFactor();
        for (UUID u : f.participants()) {
            int team = f.team(u);
            KitStats.Result r = f.gaveUp.contains(u) ? KitStats.Result.LOSS
                    : winner < 0 ? KitStats.Result.DRAW : team == winner ? KitStats.Result.WIN : KitStats.Result.LOSS;
            PlayerData d = f.data.get(u);
            KitStats s = d.kit(f.kit);
            int delta = f.ranked ? Elo.delta(avg[team], avg[1 - team], r.score(), k) : 0;
            int kills = f.kills.getOrDefault(u, 0);
            int deaths = f.deaths.getOrDefault(u, 0);
            s.record(r, kills, deaths, delta, f.ranked);
            d.recordOverall(r);
            plugin.store().save(d);
            plugin.board().update(d);
            report(u, d, s, r, kills, deaths, f);
            Player p = plugin.getServer().getPlayer(u);
            if (p == null) {
                continue;
            }
            plugin.net().playing(p, false);
            String lang = plugin.lang(p);
            String t = switch (r) {
                case WIN -> "fight.win";
                case LOSS -> "fight.lose";
                case DRAW -> "fight.draw";
            };
            Ui.title(p, Texts.text(lang, t + ".title"), Texts.text(lang, t + ".sub"), 2500);
            Ui.sound(p, r == KitStats.Result.WIN ? "ui.toast.challenge_complete" : "block.note_block.bass",
                    r == KitStats.Result.WIN ? 1f : 0.6f);
            if (f.ranked) {
                Component change = Component.text((delta >= 0 ? "+" : "") + delta,
                        delta > 0 ? NamedTextColor.GREEN : delta < 0 ? NamedTextColor.RED : NamedTextColor.GRAY);
                p.sendMessage(Texts.msg(lang, "fight.result.elo",
                        Placeholder.unparsed("kit", Texts.raw(lang, "kit." + f.kit.id())),
                        Placeholder.unparsed("elo", String.valueOf(s.elo)), Placeholder.component("delta", change)));
            }
        }
        plugin.store().write(plugin.board().file(), plugin.board().toYaml());
        if (winner >= 0) {
            String names = f.teamNames(winner);
            broadcast(f, "fight.result.win", lang -> new TagResolver[] {Placeholder.unparsed("names", names)});
            for (UUID u : f.teams.get(winner)) {
                Player p = plugin.getServer().getPlayer(u);
                if (p != null && f.alive.contains(u)) {
                    double hp = p.getHealth();
                    broadcast(f, "fight.result.health", lang -> new TagResolver[] {
                            Placeholder.unparsed("name", p.getName()), Placeholder.unparsed("hp", Ui.hearts(hp, lang))});
                    firework(p.getLocation());
                }
            }
        } else {
            broadcast(f, "fight.result.draw", lang -> new TagResolver[0]);
        }
        int back = plugin.settings().afterSeconds();
        broadcast(f, "fight.back", lang -> new TagResolver[] {Placeholder.unparsed("n", String.valueOf(back))});
        for (UUID u : f.participants()) {
            Player p = plugin.getServer().getPlayer(u);
            if (p != null) {
                hideBars(f, p);
            }
        }
        for (UUID u : f.spectators) {
            Player p = plugin.getServer().getPlayer(u);
            if (p != null) {
                hideBars(f, p);
            }
        }
        if (f.ranked) {
            plugin.boards().update();
        }
        plugin.stateChanged();
    }

    private static double average(Fight f, int team) {
        return f.teams.get(team).stream().mapToInt(u -> f.eloBefore.getOrDefault(u, 0)).average().orElse(0);
    }

    /** Statistik ans Netzwerk (Werte siehe README). */
    private void report(UUID u, PlayerData d, KitStats s, KitStats.Result r, int kills, int deaths, Fight f) {
        Map<String, Long> add = new LinkedHashMap<>();
        String kit = f.kit.id();
        String result = switch (r) {
            case WIN -> "wins";
            case LOSS -> "losses";
            case DRAW -> "draws";
        };
        add.put("games", 1L);
        add.put("games_" + kit, 1L);
        add.put(result, 1L);
        add.put(result + "_" + kit, 1L);
        if (kills > 0) {
            add.put("kills", (long) kills);
        }
        if (deaths > 0) {
            add.put("deaths", (long) deaths);
        }
        if (f.ranked) {
            add.put("ranked_games", 1L);
            if (r == KitStats.Result.WIN) {
                add.put("ranked_wins", 1L);
            }
        }
        Map<String, Long> best = new LinkedHashMap<>();
        best.put("streak_best", (long) d.bestStreak());
        if (f.ranked) {
            best.put("elo_" + kit, (long) s.elo);
        }
        plugin.net().stats(u, add, best);
    }

    private void firework(Location l) {
        World w = l.getWorld();
        w.spawn(l.clone().add(0, 1, 0), Firework.class, fw -> {
            FireworkMeta m = fw.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(Texts.EMERALD), Color.fromRGB(Texts.GOLD))
                    .withFade(Color.WHITE).flicker(true).build());
            m.setPower(1);
            fw.setFireworkMeta(m);
        });
    }

    /** Arena aufräumen, alle zurück in die Halle. */
    private void cleanup(Fight f) {
        if (f.task != null) {
            f.task.cancel();
        }
        fights.remove(f);
        for (UUID u : f.participants()) {
            if (byPlayer.get(u) == f) {
                byPlayer.remove(u);
                Player p = plugin.getServer().getPlayer(u);
                if (p != null) {
                    hideBars(f, p);
                    plugin.hall().enter(p);
                }
            }
            plugin.store().unpin(u, plugin.getServer().getPlayer(u) != null);
        }
        for (UUID u : List.copyOf(f.spectators)) {
            if (bySpectator.get(u) == f) {
                bySpectator.remove(u);
                Player p = plugin.getServer().getPlayer(u);
                if (p != null) {
                    hideBars(f, p);
                    plugin.hall().enter(p);
                    p.sendMessage(Texts.msg(plugin.lang(p), "fight.back-hall"));
                }
            }
        }
        clearArena(f.arena);
        plugin.arenas().release(f.arena);
        plugin.stateChanged();
    }

    private void clearArena(Arena arena) {
        World w = arena.world();
        for (Entity e : w.getNearbyEntities(arena.box())) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        arena.log().restore(w);
    }

    // ------------------------------------------------------------------ Verlassen, Zuschauen

    /**
     * Spieler hat das Spiel verlassen. Mitten im Kampf bleibt sein Platz {@code rueckkehr-sekunden} frei (true); ist das
     * aus (0), scheidet er sofort aus wie bisher.
     */
    public boolean quit(Player p) {
        UUID u = p.getUniqueId();
        Fight f = byPlayer.get(u);
        boolean held = false;
        if (f != null) {
            int seconds = plugin.settings().returnSeconds();
            if (f.phase != Fight.Phase.ENDED && f.alive.contains(u) && !f.away.containsKey(u) && seconds > 0) {
                hold(f, p, seconds);
                held = true;
            } else if (f.phase != Fight.Phase.ENDED && f.alive.contains(u)) {
                eliminate(f, p, f.recentDamager(u, System.currentTimeMillis()), Fight.Cause.QUIT);
            } else {
                byPlayer.remove(u);
            }
            hideBars(f, p);
        }
        Fight w = bySpectator.remove(u);
        if (w != null) {
            w.spectators.remove(u);
            hideBars(w, p);
        }
        return held;
    }

    /**
     * Spieler ist in eine andere Welt gewechselt (z. B. per Befehl eines Admins): zählt wie Verlassen, der Kampf geht
     * ohne ihn weiter. Zuschauer hören einfach auf zuzuschauen.
     */
    public void leftWorld(Player p) {
        UUID u = p.getUniqueId();
        Fight f = byPlayer.get(u);
        if (f != null) {
            if (f.phase != Fight.Phase.ENDED && f.alive.contains(u)) {
                eliminate(f, p, f.recentDamager(u, System.currentTimeMillis()), Fight.Cause.LEFT);
            } else {
                byPlayer.remove(u);
            }
            hideBars(f, p);
        }
        Fight w = bySpectator.remove(u);
        if (w != null) {
            w.spectators.remove(u);
            hideBars(w, p);
        }
    }

    /** /verlassen im Kampf oder beim Zuschauen. false = war in keinem Kampf. */
    public boolean leave(Player p) {
        UUID u = p.getUniqueId();
        Fight f = byPlayer.get(u);
        if (f != null) {
            if (f.phase != Fight.Phase.ENDED && f.alive.contains(u)) {
                eliminate(f, p, f.recentDamager(u, System.currentTimeMillis()), Fight.Cause.FORFEIT);
                return true;
            }
            byPlayer.remove(u);
            hideBars(f, p);
            plugin.hall().enter(p);
            p.sendMessage(Texts.msg(plugin.lang(p), "fight.back-hall"));
            return true;
        }
        if (bySpectator.containsKey(u)) {
            stopWatching(p);
            return true;
        }
        return false;
    }

    /** Einem Kampf zuschauen. */
    public void spectate(Player watcher, Player target) {
        String lang = plugin.lang(watcher);
        Fight f = byPlayer.get(target.getUniqueId());
        if (f == null) {
            watcher.sendMessage(Texts.msg(lang, "spectate.not-fighting", Placeholder.unparsed("name", target.getName())));
            return;
        }
        if (byPlayer.containsKey(watcher.getUniqueId())) {
            watcher.sendMessage(Texts.msg(lang, "spectate.busy"));
            return;
        }
        Fight old = bySpectator.remove(watcher.getUniqueId());
        if (old != null) {
            old.spectators.remove(watcher.getUniqueId());
            hideBars(old, watcher);
        }
        plugin.queues().leave(watcher.getUniqueId(), true);
        plugin.hall().reset(watcher);
        watcher.setGameMode(GameMode.SPECTATOR);
        watcher.teleport(f.arena.viewPoint());
        bySpectator.put(watcher.getUniqueId(), f);
        f.spectators.add(watcher.getUniqueId());
        if (f.phase == Fight.Phase.RUNNING) {
            watcher.showBossBar(bar(f, lang));
        }
        watcher.sendMessage(Texts.msg(lang, "spectate.start", Placeholder.unparsed("name", target.getName())));
    }

    private void stopWatching(Player p) {
        Fight f = bySpectator.remove(p.getUniqueId());
        if (f != null) {
            f.spectators.remove(p.getUniqueId());
            hideBars(f, p);
        }
        plugin.hall().enter(p);
        p.sendMessage(Texts.msg(plugin.lang(p), "fight.back-hall"));
    }

    // ------------------------------------------------------------------ Abschalten

    /** Alle Kämpfe sofort abbrechen (ohne Wertung), z. B. beim Abschalten oder Neubau. */
    public void abortAll() {
        for (Fight f : List.copyOf(fights)) {
            if (f.task != null) {
                f.task.cancel();
            }
            for (UUID u : List.copyOf(f.away.keySet())) {
                dropAway(f, u);
            }
            fights.remove(f);
            List<UUID> all = new ArrayList<>(f.participants());
            all.addAll(f.spectators);
            for (UUID u : all) {
                byPlayer.remove(u, f);
                bySpectator.remove(u, f);
                plugin.store().unpin(u, plugin.getServer().getPlayer(u) != null);
                Player p = plugin.getServer().getPlayer(u);
                if (p != null) {
                    hideBars(f, p);
                    plugin.net().playing(p, false);
                    plugin.hall().enter(p);
                }
            }
            clearArena(f.arena);
            plugin.arenas().release(f.arena);
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private void forEachParticipant(Fight f, java.util.function.Consumer<Player> action) {
        for (UUID u : f.participants()) {
            if (byPlayer.get(u) == f) {
                Player p = plugin.getServer().getPlayer(u);
                if (p != null) {
                    action.accept(p);
                }
            }
        }
    }

    /** Nachricht an alle im Kampf (auch Ausgeschiedene, die noch zuschauen) und an die Zuschauer. */
    private void broadcast(Fight f, String key, Function<String, TagResolver[]> resolvers) {
        List<UUID> all = new ArrayList<>(f.participants());
        all.addAll(f.spectators);
        for (UUID u : all) {
            if (byPlayer.get(u) != f && bySpectator.get(u) != f) {
                continue;
            }
            Player p = plugin.getServer().getPlayer(u);
            if (p != null) {
                String lang = plugin.lang(p);
                p.sendMessage(Texts.msg(lang, key, resolvers.apply(lang)));
            }
        }
    }
}
