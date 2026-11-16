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
import de.moinmorni.duels.world.ArenaType;
import de.moinmorni.duels.world.SpecialArenas;
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
 * zu), Runden („Bester aus 3/5“), Ende (Sieg, Niederlage, Unentschieden nach Zeitlimit), Wertung und Statistik, zurück
 * in die Halle. Dazu die besonderen Kampfarten: Treffer zählen (Boxen), Tore und Wiederkommen (Brücke), Kompass zum
 * Gegner (Labyrinth).
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
    /** Pause zwischen zwei Runden. */
    private static final int BREAK_SECONDS = 3;
    /** Wer gerade wiederkommt (Brücke), zählt so lange nicht noch einmal als gestürzt. */
    private static final long RESPAWN_GUARD_MS = 1500L;
    private final Map<UUID, Long> respawned = new HashMap<>();
    /** Startwerte für neue Irrgärten. */
    private static final java.util.Random MAZE_RANDOM = new java.util.Random();

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

    /** Der Kampf, der gerade in dieser Arena läuft. */
    public Optional<Fight> in(Arena arena) {
        return fights.stream().filter(f -> f.arena == arena).findFirst();
    }

    // ------------------------------------------------------------------ Start

    /**
     * Ein Kampf: Kampfart, gewertet?, wie er zustande kam, die Teams (zwei oder bei „Jeder gegen jeden“ mehr),
     * gewünschte Arena ({@code null} = egal) und Runden (Bester aus 1, 3 oder 5).
     */
    public record Request(Kit kit, boolean ranked, Fight.Kind kind, List<List<UUID>> teams, ArenaType arena,
                          int bestOf) {
    }

    /** Kampf starten (2 Teams, eine Runde); false, wenn jemand fehlt oder keine Arena frei ist. */
    public boolean start(Kit kit, boolean ranked, List<UUID> teamA, List<UUID> teamB, boolean challenge) {
        return start(new Request(kit, ranked, challenge ? Fight.Kind.DUEL : Fight.Kind.QUEUE, List.of(teamA, teamB),
                null, 1));
    }

    /** Kampf starten; false, wenn jemand fehlt oder keine Arena frei ist. */
    public boolean start(Request r) {
        Kit kit = r.kit();
        if (r.teams().size() < 2 || r.teams().stream().anyMatch(List::isEmpty)) {
            return false;
        }
        List<UUID> everyone = new ArrayList<>();
        r.teams().forEach(everyone::addAll);
        for (UUID u : everyone) {
            Player p = plugin.getServer().getPlayer(u);
            if (p == null || !plugin.hall().inHall(p)) {
                return false;
            }
        }
        Optional<Arena> arena = plugin.arenas().reserve(kit, r.arena());
        if (arena.isEmpty()) {
            return false;
        }
        Fight f = new Fight(nextId++, kit, r.ranked(), r.kind(), r.bestOf(), arena.get(), r.teams());
        fights.add(f);
        newMaze(f);
        for (int t = 0; t < f.teams.size(); t++) {
            List<UUID> team = f.teams.get(t);
            List<Location> spawns = f.arena.spawns(t, f.teams.size(), team.size());
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
            if (f.tally.bestOf() > 1) {
                p.sendMessage(Texts.msg(lang, "fight.bestof", Placeholder.unparsed("n", String.valueOf(f.tally.bestOf())),
                        Placeholder.unparsed("wins", String.valueOf(f.tally.needed()))));
            }
            String hint = kit == Kit.LABYRINTH && !goalCounts(f) ? "hint.labyrinth.kampf" : "hint." + kit.id();
            if (Texts.has(hint)) {
                p.sendMessage(Texts.msg(lang, hint, hintValues()));
            }
        }
        f.countdown = plugin.settings().countdownSeconds();
        f.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(f), 1L, TICKS_PER_SECOND);
        plugin.stateChanged();
        return true;
    }

    private String opponentNames(Fight f, UUID u) {
        return String.join(" & ", f.opponents(u).stream().map(f::name).toList());
    }

    /** Zahlen für die Hinweise zu Beginn („wer zuerst 100 Treffer landet“). */
    private TagResolver[] hintValues() {
        return new TagResolver[] {
            Placeholder.unparsed("hits", String.valueOf(plugin.kitSettings().boxingHits())),
            Placeholder.unparsed("goals", String.valueOf(plugin.kitSettings().bridgeGoals()))};
    }

    /** Ausrüstung anlegen, alles zurücksetzen. */
    private void prepare(Player p, Fight f, PlayerData d) {
        plugin.hall().reset(p);
        p.setSaturation(f.kit.classicCombat() ? CLASSIC_SATURATION : MODERN_SATURATION);
        combat(p, f);
        if (f.kit.survival()) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        Loadouts.Loadout l = Loadouts.of(f.kit, d.ability());
        Loadouts.apply(p, l, d.layout(l.id()), plugin.lang(p), Math.max(0, f.team(p.getUniqueId())));
        if (f.kit == Kit.LABYRINTH && !plugin.kitSettings().mazeCompass()) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                if (Loadouts.COMPASS.equals(de.moinmorni.duels.Items.kitTag(p.getInventory().getItem(i)))) {
                    p.getInventory().setItem(i, null);
                }
            }
        }
        plugin.abilities().reset(p);
    }

    /**
     * Kampfgefühl der Kampfart: ohne Abklingzeit beim Schlagen (klassisch) oder wie heute; bei Kombo kurze Pause
     * zwischen Treffern. Gilt nur, solange der Spieler da ist – darum auch beim Wiederkommen.
     */
    private void combat(Player p, Fight f) {
        Hall.attackSpeed(p, f.kit.classicCombat() ? NO_COOLDOWN : DEFAULT_ATTACK_SPEED);
        if (f.kit == Kit.KOMBO) {
            // Paper wertet einen Treffer nur, wenn seit dem letzten mehr als die Hälfte dieser Zeit vergangen ist.
            p.setMaximumNoDamageTicks(2 * plugin.kitSettings().comboPauseTicks());
        }
    }

    /** Startplatz eines Teilnehmers (für Wiederkommen und neue Runden). */
    private Location spawnOf(Fight f, UUID u) {
        int team = Math.max(0, f.team(u));
        List<UUID> members = f.teams.get(team);
        List<Location> spawns = f.arena.spawns(team, f.teams.size(), members.size());
        return spawns.get(Math.max(0, members.indexOf(u)) % spawns.size());
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
            case BREAK -> {
                if (f.paused) {
                    pausedTick(f);
                } else if (--f.breakLeft <= 0) {
                    nextRound(f);
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
                Ui.title(p, num, Texts.text(lang, f.tally.bestOf() > 1 ? "fight.countdown.round" : "fight.countdown.sub",
                        Placeholder.unparsed("kit", Texts.raw(lang, "kit." + f.kit.id())),
                        Placeholder.unparsed("round", String.valueOf(f.round)),
                        Placeholder.unparsed("names", opponentNames(f, p.getUniqueId()))), 900);
                Ui.sound(p, "block.note_block.pling", 0.8f + 0.2f * (4 - Math.min(3, n)));
            });
            return;
        }
        f.phase = Fight.Phase.RUNNING;
        if (f.startedAt == 0) {
            f.startedAt = System.currentTimeMillis();
        }
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
            finish(f, f.tally.leader());
            return;
        }
        long now = System.currentTimeMillis();
        double voidY = voidY(f);
        for (UUID u : List.copyOf(f.alive)) {
            Player p = plugin.getServer().getPlayer(u);
            if (p != null && p.getWorld() == f.arena.world() && p.getLocation().getY() < voidY) {
                down(f, p, f.recentDamager(u, now), Fight.Cause.VOID);
            }
        }
        special(f);
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
        boolean gone = false;
        for (int t = 0; t < f.teams.size(); t++) {
            gone |= teamGone(f, t);
        }
        if (gone) {
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
        for (int t = 0; t < f.teams.size(); t++) {
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
        if (f.fresh.remove(u)) {
            // Inzwischen neue Runde oder ein Tor: frisch an den eigenen Start
            prepare(p, f, f.data.get(u));
            p.teleport(spawnOf(f, u));
        } else {
            a.state().restore(p);
            combat(p, f);
        }
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
            String score = scoreText(f, lang);
            bar.name(Texts.text(lang, score.isEmpty() ? "fight.bossbar" : "fight.bossbar.score",
                    Placeholder.unparsed("kit", Texts.raw(lang, "kit." + f.kit.id())),
                    Placeholder.unparsed("arena", Texts.raw(lang, f.arena.type().textKey())),
                    Placeholder.unparsed("score", score),
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
            case VOID -> f.kit == Kit.SUMO ? (credited != null ? "fight.void.sumo.by" : "fight.void.sumo")
                    : f.kit == Kit.SPLEEF ? "fight.void.spleef" : "fight.void";
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

    /**
     * Steht nur noch ein Team (oder keins), ist die Runde vorbei. Ist von den anderen Teams niemand mehr dabei (alle
     * aufgegeben oder weg), ist der ganze Kampf vorbei.
     */
    private void checkEnd(Fight f) {
        List<Integer> standing = new ArrayList<>();
        List<Integer> present = new ArrayList<>();
        for (int t = 0; t < f.teams.size(); t++) {
            List<UUID> team = f.teams.get(t);
            if (team.stream().anyMatch(f.alive::contains)) {
                standing.add(t);
            }
            if (team.stream().anyMatch(u -> !f.gaveUp.contains(u))) {
                present.add(t);
            }
        }
        if (standing.size() > 1) {
            return;
        }
        int roundWinner = standing.size() == 1 ? standing.get(0) : -1;
        if (present.size() <= 1) {
            finish(f, present.size() == 1 ? present.get(0) : roundWinner);
            return;
        }
        roundOver(f, roundWinner);
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
        boolean rated = f.ranked && f.teams.size() == 2;
        double[] avg = rated ? new double[] {average(f, 0), average(f, 1)} : new double[0];
        int k = plugin.settings().kFactor();
        for (UUID u : f.participants()) {
            int team = f.team(u);
            KitStats.Result r = f.gaveUp.contains(u) ? KitStats.Result.LOSS
                    : winner < 0 ? KitStats.Result.DRAW : team == winner ? KitStats.Result.WIN : KitStats.Result.LOSS;
            PlayerData d = f.data.get(u);
            KitStats s = d.kit(f.kit);
            int delta = rated ? Elo.delta(avg[team], avg[1 - team], r.score(), k) : 0;
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
            if (rated) {
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
            resultScore(f);
            for (UUID u : f.teams.get(winner)) {
                Player p = plugin.getServer().getPlayer(u);
                if (p != null && f.alive.contains(u) && f.kit.hitsHurt()) {
                    double hp = p.getHealth();
                    broadcast(f, "fight.result.health", lang -> new TagResolver[] {
                            Placeholder.unparsed("name", p.getName()), Placeholder.unparsed("hp", Ui.hearts(hp, lang))});
                    firework(p.getLocation());
                }
            }
        } else {
            broadcast(f, "fight.result.draw", lang -> new TagResolver[0]);
            resultScore(f);
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
        int hits = f.hits.getOrDefault(u, 0);
        if (hits > 0) {
            add.put("hits_boxen", (long) hits);
        }
        int goals = f.goals.getOrDefault(u, 0);
        if (goals > 0) {
            add.put("goals_bruecke", (long) goals);
        }
        int finishes = f.finishes.getOrDefault(u, 0);
        if (finishes > 0) {
            add.put("goals_labyrinth", (long) finishes);
        }
        int rounds = f.tally.rounds(f.team(u));
        if (f.tally.bestOf() > 1 && rounds > 0) {
            add.put("rounds_won", (long) rounds);
        }
        if (f.kind == Fight.Kind.PARTY || f.kind == Fight.Kind.FFA) {
            add.put("party_games", 1L);
            if (r == KitStats.Result.WIN) {
                add.put("party_wins", 1L);
            }
        }
        Map<String, Long> best = new LinkedHashMap<>();
        best.put("streak_best", (long) d.bestStreak());
        if (f.ranked && f.teams.size() == 2) {
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
        f.participants().forEach(respawned::remove);
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

    // ------------------------------------------------------------------ Runden

    /** Eine Runde ist vorbei: Kampf entschieden, oder kurze Pause und die nächste Runde. */
    private void roundOver(Fight f, int winner) {
        if (f.tally.bestOf() <= 1) {
            finish(f, winner);
            return;
        }
        if (winner >= 0 && f.tally.roundWon(winner)) {
            finish(f, winner);
            return;
        }
        String score = f.tally.roundsText();
        if (winner >= 0) {
            String names = f.teamNames(winner);
            broadcast(f, "fight.round.won", lang -> new TagResolver[] {Placeholder.unparsed("names", names),
                    Placeholder.unparsed("round", String.valueOf(f.round)), Placeholder.unparsed("score", score)});
        } else {
            broadcast(f, "fight.round.draw", lang -> new TagResolver[] {
                    Placeholder.unparsed("round", String.valueOf(f.round)), Placeholder.unparsed("score", score)});
        }
        forEachParticipant(f, p -> {
            String lang = plugin.lang(p);
            int team = f.team(p.getUniqueId());
            String key = winner < 0 ? "fight.round.draw.title" : team == winner ? "fight.round.win.title"
                    : "fight.round.lose.title";
            Ui.title(p, Texts.text(lang, key), Texts.text(lang, "fight.round.sub",
                    Placeholder.unparsed("score", score)), 1800);
            Ui.sound(p, team == winner ? "entity.player.levelup" : "block.note_block.bass", 1.2f);
        });
        f.phase = Fight.Phase.BREAK;
        f.breakLeft = BREAK_SECONDS;
    }

    /** Nächste Runde: Arena zurücksetzen, alle (außer wer aufgegeben hat) wieder an den Start. */
    private void nextRound(Fight f) {
        f.round++;
        f.tally.newRound();
        clearArena(f.arena);
        newMaze(f);
        f.placed.clear();
        f.alive.clear();
        f.lastDamager.clear();
        f.lastDamageAt.clear();
        for (UUID u : f.participants()) {
            if (f.gaveUp.contains(u)) {
                continue;
            }
            if (f.away.containsKey(u)) {
                // Platz ist frei gehalten: bleibt dabei und kommt frisch an den Start, wenn er wiederkommt
                f.alive.add(u);
                f.fresh.add(u);
            } else if (plugin.getServer().getPlayer(u) == null || byPlayer.get(u) != f) {
                f.gaveUp.add(u);
            }
        }
        placeAll(f, true);
        f.phase = Fight.Phase.COUNTDOWN;
        f.countdown = plugin.settings().countdownSeconds();
        checkEnd(f);
        updatePause(f);
    }

    /** Alle Teilnehmer an ihren Startplatz, Ausrüstung frisch ({@code revive} = Ausgeschiedene kommen wieder). */
    private void placeAll(Fight f, boolean revive) {
        for (int t = 0; t < f.teams.size(); t++) {
            List<UUID> team = f.teams.get(t);
            List<Location> spawns = f.arena.spawns(t, f.teams.size(), team.size());
            for (int i = 0; i < team.size(); i++) {
                UUID u = team.get(i);
                Player p = plugin.getServer().getPlayer(u);
                if (p == null || f.gaveUp.contains(u) || byPlayer.get(u) != f || !revive && !f.alive.contains(u)) {
                    continue;
                }
                f.alive.add(u);
                prepare(p, f, f.data.get(u));
                p.teleportAsync(spawns.get(i % spawns.size()));
            }
        }
    }

    /** Endstand (Runden, Treffer, Tore) für alle. */
    private void resultScore(Fight f) {
        if (f.tally.bestOf() > 1) {
            String score = f.tally.roundsText();
            broadcast(f, "fight.result.rounds", lang -> new TagResolver[] {Placeholder.unparsed("score", score)});
        }
        if (f.kit == Kit.BOXEN) {
            String score = f.tally.scoreText();
            broadcast(f, "boxen.result", lang -> new TagResolver[] {Placeholder.unparsed("score", score)});
        } else if (f.kit == Kit.BRUECKE) {
            String score = f.tally.scoreText();
            broadcast(f, "bruecke.result", lang -> new TagResolver[] {Placeholder.unparsed("score", score)});
        }
    }

    /** Stand für die Leiste oben: Runde, Treffer, Tore (leer = nichts zu zählen). */
    private String scoreText(Fight f, String lang) {
        List<String> parts = new ArrayList<>();
        if (f.tally.bestOf() > 1) {
            parts.add(Texts.plain(lang, "score.round", Placeholder.unparsed("round", String.valueOf(f.round)),
                    Placeholder.unparsed("score", f.tally.roundsText())));
        }
        if (f.kit == Kit.BOXEN) {
            parts.add(Texts.plain(lang, "score.hits", Placeholder.unparsed("score", f.tally.scoreText()),
                    Placeholder.unparsed("target", String.valueOf(plugin.kitSettings().boxingHits()))));
        } else if (f.kit == Kit.BRUECKE) {
            parts.add(Texts.plain(lang, "score.goals", Placeholder.unparsed("score", f.tally.scoreText()),
                    Placeholder.unparsed("target", String.valueOf(plugin.kitSettings().bridgeGoals()))));
        }
        return String.join(" · ", parts);
    }

    // ------------------------------------------------------------------ Besondere Kampfarten

    /** Ab hier ist man herausgefallen (Sumo: Tiefe aus der config.yml). */
    public double voidY(Fight f) {
        if (f.kit == Kit.SUMO) {
            return Zones.sumoVoid(f.arena.oy(), plugin.kitSettings().sumoDepth());
        }
        return f.arena.voidY();
    }

    /**
     * Besiegt oder gestürzt: bei der Brücke kommt man an seinem Start wieder, sonst scheidet man aus.
     */
    public void down(Fight f, Player p, UUID killer, Fight.Cause cause) {
        UUID u = p.getUniqueId();
        boolean back = cause == Fight.Cause.KILLED || cause == Fight.Cause.DIED || cause == Fight.Cause.VOID;
        if (f.kit.respawns() && back && f.phase == Fight.Phase.RUNNING && f.alive.contains(u)) {
            Long last = respawned.get(u);
            if (last == null || System.currentTimeMillis() - last > RESPAWN_GUARD_MS) {
                respawn(f, p, killer, cause, false);
            }
            return;
        }
        eliminate(f, p, killer, cause);
    }

    /** Brücke: zählt als Tod (Kill für den Gegner), dann sofort zurück an den eigenen Start, Ausrüstung frisch. */
    private void respawn(Fight f, Player p, UUID killer, Fight.Cause cause, boolean ownGoal) {
        UUID v = p.getUniqueId();
        respawned.put(v, System.currentTimeMillis());
        f.deaths.merge(v, 1, Integer::sum);
        UUID credited = killer != null && !killer.equals(v) && f.team(killer) >= 0 && !f.sameTeam(killer, v)
                ? killer : null;
        if (credited != null) {
            f.kills.merge(credited, 1, Integer::sum);
            Player k = plugin.getServer().getPlayer(credited);
            if (k != null) {
                Ui.sound(k, "entity.experience_orb.pickup", 1.4f);
            }
        }
        String key = ownGoal ? "bruecke.own-goal" : cause == Fight.Cause.VOID
                ? (credited != null ? "bruecke.fell.by" : "bruecke.fell")
                : (credited != null ? "fight.killed" : "fight.died");
        String victimName = f.name(v);
        String killerName = credited == null ? "" : f.name(credited);
        broadcast(f, key, lang -> new TagResolver[] {Placeholder.unparsed("victim", victimName),
                Placeholder.unparsed("killer", killerName)});
        Location to = spawnOf(f, v);
        prepare(p, f, f.data.get(v));
        String lang = plugin.lang(p);
        Ui.title(p, Texts.text(lang, "bruecke.back.title"), Texts.text(lang, "bruecke.back.sub"), 1200);
        Ui.sound(p, "entity.player.hurt", 0.8f);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && byPlayer.get(v) == f) {
                p.setFallDistance(0f);
                p.teleport(to);
            }
        });
    }

    /**
     * Brücke: wer ins Tor der anderen springt, punktet; ins eigene Tor springen ist wie herunterfallen. Labyrinth: wer
     * zuerst auf dem Ziel in der Mitte steht, gewinnt die Runde.
     */
    public void moved(Fight f, Player p, Location to) {
        UUID u = p.getUniqueId();
        if (f.phase != Fight.Phase.RUNNING || !f.alive.contains(u) || f.paused) {
            return;
        }
        if (f.kit == Kit.LABYRINTH) {
            if (goalCounts(f) && Zones.mazeGoal(to.getBlockX() - f.arena.ox(),
                    to.getBlockY() - f.arena.oy(), to.getBlockZ() - f.arena.oz())) {
                reachedGoal(f, p);
            }
            return;
        }
        if (f.kit != Kit.BRUECKE) {
            return;
        }
        int goal = Zones.bridgeGoal(to.getBlockX() - f.arena.ox(), to.getBlockY() - f.arena.oy(),
                to.getBlockZ() - f.arena.oz());
        if (goal < 0) {
            return;
        }
        int team = f.team(u);
        if (goal == team) {
            Long last = respawned.get(u);
            if (last == null || System.currentTimeMillis() - last > RESPAWN_GUARD_MS) {
                respawn(f, p, null, Fight.Cause.VOID, true);
            }
            return;
        }
        scored(f, p, team);
    }

    private void scored(Fight f, Player p, int team) {
        UUID u = p.getUniqueId();
        f.goals.merge(u, 1, Integer::sum);
        boolean won = f.tally.add(team, 1, plugin.kitSettings().bridgeGoals());
        String name = p.getName();
        String score = f.tally.scoreText();
        broadcast(f, "bruecke.goal", lang -> new TagResolver[] {Placeholder.unparsed("name", name),
                Placeholder.unparsed("team", Texts.raw(lang, team == 0 ? "team.blau" : "team.rot")),
                Placeholder.unparsed("score", score)});
        forEachParticipant(f, q -> {
            String lang = plugin.lang(q);
            Ui.title(q, Texts.text(lang, "bruecke.goal.title"), Texts.text(lang, "bruecke.goal.sub",
                    Placeholder.unparsed("name", name), Placeholder.unparsed("score", score)), 1500);
            Ui.sound(q, f.team(q.getUniqueId()) == team ? "entity.firework_rocket.twinkle" : "block.note_block.bass",
                    1.0f);
        });
        if (won) {
            finish(f, team);
            return;
        }
        // Alle zurück an den Start, kurz eingefroren, dann weiter (gesetzte Blöcke bleiben)
        for (UUID m : f.participants()) {
            respawned.put(m, System.currentTimeMillis());
            if (f.away.containsKey(m)) {
                f.fresh.add(m);
            }
        }
        placeAll(f, false);
        f.phase = Fight.Phase.COUNTDOWN;
        f.countdown = plugin.settings().countdownSeconds();
    }

    /** Labyrinth: neuer Irrgarten vor jeder Runde (oder immer derselbe, wenn das in der config.yml aus ist). */
    private void newMaze(Fight f) {
        if (f.kit != Kit.LABYRINTH) {
            return;
        }
        long seed = plugin.kitSettings().mazeReshuffle() ? MAZE_RANDOM.nextLong() : SpecialArenas.MAZE_SEED;
        f.arena.reshuffleMaze(seed);
    }

    /**
     * Zählt im Labyrinth das Ziel? Nur wenn es in der config.yml an ist und alle in einer Ecke starten (bis zu vier
     * Seiten) – mit mehr Leuten starten manche näher am Ziel, dann zählt nur der Kampf.
     */
    private boolean goalCounts(Fight f) {
        return plugin.kitSettings().mazeGoal() && f.teams.size() <= SpecialArenas.MAZE_CORNERS;
    }

    /** Labyrinth: jemand steht als Erstes auf dem Ziel – sein Team gewinnt die Runde. */
    private void reachedGoal(Fight f, Player p) {
        UUID u = p.getUniqueId();
        int team = f.team(u);
        if (team < 0) {
            return;
        }
        f.finishes.merge(u, 1, Integer::sum);
        String name = p.getName();
        broadcast(f, "labyrinth.goal", lang -> new TagResolver[] {Placeholder.unparsed("name", name)});
        forEachParticipant(f, q -> {
            String lang = plugin.lang(q);
            boolean own = f.team(q.getUniqueId()) == team;
            Ui.title(q, Texts.text(lang, own ? "labyrinth.goal.title" : "labyrinth.goal.lost"),
                    Texts.text(lang, "labyrinth.goal.sub", Placeholder.unparsed("name", name)), 1500);
            Ui.sound(q, own ? "entity.firework_rocket.twinkle" : "block.note_block.bass", 1.0f);
        });
        firework(f.arena.center());
        roundOver(f, team);
    }

    /** Boxen: ein Treffer zählt; wer (bzw. welches Team) zuerst genug hat, gewinnt die Runde. */
    public void boxHit(Fight f, Player attacker) {
        UUID a = attacker.getUniqueId();
        int team = f.team(a);
        if (team < 0 || f.phase != Fight.Phase.RUNNING) {
            return;
        }
        f.hits.merge(a, 1, Integer::sum);
        if (f.tally.add(team, 1, plugin.kitSettings().boxingHits())) {
            String names = f.teamNames(team);
            String score = f.tally.scoreText();
            broadcast(f, "boxen.reached", lang -> new TagResolver[] {Placeholder.unparsed("names", names),
                    Placeholder.unparsed("score", score)});
            roundOver(f, team);
        }
    }

    /** Jede Sekunde im laufenden Kampf: Kompass zum Gegner, Treffer in der Aktionsleiste, Tore prüfen. */
    private void special(Fight f) {
        if (f.kit == Kit.LABYRINTH && plugin.kitSettings().mazeCompass()) {
            for (UUID u : f.alive) {
                Player p = plugin.getServer().getPlayer(u);
                if (p == null) {
                    continue;
                }
                Player nearest = null;
                double best = Double.MAX_VALUE;
                for (UUID o : f.opponents(u)) {
                    Player q = plugin.getServer().getPlayer(o);
                    if (q != null && f.alive.contains(o) && q.getWorld() == p.getWorld()) {
                        double d = q.getLocation().distanceSquared(p.getLocation());
                        if (d < best) {
                            best = d;
                            nearest = q;
                        }
                    }
                }
                if (nearest != null) {
                    p.setCompassTarget(nearest.getLocation());
                }
            }
        }
        if (f.kit == Kit.LABYRINTH) {
            for (UUID u : List.copyOf(f.alive)) {
                Player p = plugin.getServer().getPlayer(u);
                if (p != null && f.phase == Fight.Phase.RUNNING) {
                    moved(f, p, p.getLocation());
                }
            }
        } else if (f.kit == Kit.BOXEN) {
            int target = plugin.kitSettings().boxingHits();
            for (UUID u : f.alive) {
                Player p = plugin.getServer().getPlayer(u);
                int team = f.team(u);
                if (p == null || team < 0) {
                    continue;
                }
                int other = 0;
                for (int t = 0; t < f.teams.size(); t++) {
                    if (t != team) {
                        other = Math.max(other, f.tally.score(t));
                    }
                }
                String lang = plugin.lang(p);
                p.sendActionBar(Texts.text(lang, "boxen.bar", Placeholder.unparsed("own", String.valueOf(f.tally.score(team))),
                        Placeholder.unparsed("other", String.valueOf(other)),
                        Placeholder.unparsed("target", String.valueOf(target))));
            }
        } else if (f.kit == Kit.BRUECKE) {
            for (UUID u : List.copyOf(f.alive)) {
                Player p = plugin.getServer().getPlayer(u);
                if (p != null && f.phase == Fight.Phase.RUNNING) {
                    moved(f, p, p.getLocation());
                }
            }
        }
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
