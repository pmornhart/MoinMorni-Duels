package net.morni.duels.fight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.morni.duels.DuelsPlugin;
import net.morni.duels.Texts;
import net.morni.duels.Ui;
import net.morni.duels.data.KitStats;
import net.morni.duels.data.PlayerData;
import net.morni.duels.hall.Hall;
import net.morni.duels.kit.Kit;
import net.morni.duels.kit.Loadouts;
import net.morni.duels.match.Elo;
import net.morni.duels.match.QueueKey;
import net.morni.duels.world.Arena;
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

    private final DuelsPlugin plugin;
    private final Map<UUID, Fight> byPlayer = new HashMap<>();
    private final Map<UUID, Fight> bySpectator = new HashMap<>();
    private final List<Fight> fights = new ArrayList<>();
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
        switch (f.phase) {
            case COUNTDOWN -> countdown(f);
            case RUNNING -> running(f);
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
        UUID v = victim.getUniqueId();
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
        };
        String victimName = f.name(v);
        String killerName = credited == null ? "" : f.name(credited);
        broadcast(f, key, lang -> new TagResolver[] {Placeholder.unparsed("victim", victimName),
                Placeholder.unparsed("killer", killerName)});
        if (cause == Fight.Cause.FORFEIT || cause == Fight.Cause.QUIT || cause == Fight.Cause.LEFT) {
            f.gaveUp.add(v);
            byPlayer.remove(v);
            hideBars(f, victim);
            if (cause == Fight.Cause.FORFEIT) {
                victim.sendMessage(Texts.msg(plugin.lang(victim), "fight.gave-up"));
                plugin.hall().enter(victim);
            }
        } else {
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

    /** Spieler hat das Spiel verlassen. */
    public void quit(Player p) {
        UUID u = p.getUniqueId();
        Fight f = byPlayer.get(u);
        if (f != null) {
            if (f.phase != Fight.Phase.ENDED && f.alive.contains(u)) {
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
