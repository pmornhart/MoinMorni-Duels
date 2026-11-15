package de.moinmorni.duels;

import de.moinmorni.duels.data.Leaderboard;
import de.moinmorni.duels.data.PlayerData;
import de.moinmorni.duels.data.Store;
import de.moinmorni.duels.fight.Abilities;
import de.moinmorni.duels.fight.Fights;
import de.moinmorni.duels.hall.Hall;
import de.moinmorni.duels.hall.HallBoards;
import de.moinmorni.duels.listen.CombatListener;
import de.moinmorni.duels.listen.GuardListener;
import de.moinmorni.duels.listen.PlayerListener;
import de.moinmorni.duels.match.Queues;
import de.moinmorni.duels.menu.Menus;
import de.moinmorni.duels.net.Network;
import de.moinmorni.duels.world.Arenas;
import de.moinmorni.duels.world.Construction;
import de.moinmorni.duels.world.VoidGenerator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

/**
 * MoinMorni Duels: Wartehalle (Turnierhof), Warteschlangen mit Elo, Herausforderungen, Kits mit Editor, Arenen,
 * Ranglisten. Spricht mit dem MoinMorni-Netzwerk, wenn es da ist, und läuft sonst allein.
 */
public final class DuelsPlugin extends JavaPlugin {

    private static final String ADMIN = "moinmorni.duels.admin";
    private static final String HEALTH_OBJECTIVE = "mm_duels_hp";
    private static final long SECOND = 20L;
    private static final long LANGUAGE_CHECK = 40L;
    private static final long COOLDOWN_BAR = 4L;
    private static final long BOARD_UPDATE = 20L * 30;
    private static final long STATE_REFRESH = 20L * 60;

    private Settings settings;
    private Network net;
    private Store store;
    private Leaderboard board;
    private World world;
    private Arenas arenas;
    private Construction construction;
    private Hall hall;
    private HallBoards boards;
    private Queues queues;
    private Fights fights;
    private Challenges challenges;
    private Abilities abilities;
    private Menus menus;
    private volatile boolean ready;
    private boolean stateQueued;
    private String lastState = "";
    private int lastPlayers = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        Items.init(this);
        net = Network.connect(this);
        getLogger().info(net.connected()
                ? "Mit dem MoinMorni-Netzwerk verbunden (Server „" + net.serverName() + "“)."
                : "Kein Netzwerk-Plugin: Duels läuft allein (deutsche Texte, Statistik nur lokal).");
        store = new Store(getLogger(), getDataFolder().toPath(), settings.eloStart());
        board = new Leaderboard(getLogger(), getDataFolder().toPath());
        board.load();
        world = createWorld(settings.world());
        arenas = new Arenas(world, settings.arenaCopies());
        hall = new Hall(this, world);
        boards = new HallBoards(this, world);
        fights = new Fights(this);
        queues = new Queues(this);
        challenges = new Challenges(this);
        abilities = new Abilities(this);
        menus = new Menus(this);

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuardListener(this), this);
        Commands commands = new Commands(this);
        for (String name : List.of("duel", "spectate", "verlassen", "stats", "top", "kit", "warteschlange", "duels")) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(commands);
                c.setTabCompleter(commands);
            }
        }

        construction = new Construction(this, world);
        construction.ensure(arenas, settings.arenaCopies(), false, () -> {
            ready = true;
            getLogger().info("Duels ist bereit: " + arenas.all().size() + " Arenen.");
        });
        boards.spawn();
        healthBelowName();

        var scheduler = getServer().getScheduler();
        scheduler.runTaskTimer(this, () -> {
            queues.tick();
            challenges.tick();
            menus.refreshOpen();
        }, SECOND, SECOND);
        scheduler.runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(hall::checkLanguage),
                LANGUAGE_CHECK, LANGUAGE_CHECK);
        scheduler.runTaskTimer(this, abilities::showCooldowns, COOLDOWN_BAR, COOLDOWN_BAR);
        scheduler.runTaskTimer(this, boards::update, BOARD_UPDATE, BOARD_UPDATE);
        scheduler.runTaskTimer(this, () -> reportState(true), STATE_REFRESH, STATE_REFRESH);

        // Nach einem Neuladen: wer schon da ist, kommt in die Halle.
        for (Player p : getServer().getOnlinePlayers()) {
            data(p);
            hall.enter(p);
        }
        stateChanged();
    }

    @Override
    public void onDisable() {
        if (fights != null) {
            fights.abortAll();
        }
        if (boards != null) {
            boards.remove();
        }
        if (store != null) {
            if (board != null) {
                store.write(board.file(), board.toYaml());
            }
            store.close();
        }
    }

    private World createWorld(String name) {
        World w = getServer().getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).generator(new VoidGenerator()).environment(World.Environment.NORMAL)
                    .generateStructures(false).createWorld();
        }
        if (w == null) {
            throw new IllegalStateException("Welt " + name + " ließ sich nicht anlegen");
        }
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.BLOCK_DROPS, false);
        w.setGameRule(GameRules.ENTITY_DROPS, false);
        w.setGameRule(GameRules.FALL_DAMAGE, true);
        w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        w.setTime(6000L);
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(Difficulty.NORMAL);
        w.setSpawnLocation(new org.bukkit.Location(w, 0.5, Arenas.FLOOR_Y + 1, 12.5));
        return w;
    }

    /** Herzen unter dem Namen – im Kampf sieht man, wie es dem Gegner geht. */
    private void healthBelowName() {
        Scoreboard sb = getServer().getScoreboardManager().getMainScoreboard();
        Objective o = sb.getObjective(HEALTH_OBJECTIVE);
        if (o == null) {
            o = sb.registerNewObjective(HEALTH_OBJECTIVE, Criteria.HEALTH, Component.text("❤", NamedTextColor.RED),
                    RenderType.HEARTS);
        }
        o.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    // ---------- Zugriff für die Bausteine ----------

    public Settings settings() {
        return settings;
    }

    public Network net() {
        return net;
    }

    public Store store() {
        return store;
    }

    public Leaderboard board() {
        return board;
    }

    public World world() {
        return world;
    }

    public Arenas arenas() {
        return arenas;
    }

    public Hall hall() {
        return hall;
    }

    public HallBoards boards() {
        return boards;
    }

    public Queues queues() {
        return queues;
    }

    public Fights fights() {
        return fights;
    }

    public Challenges challenges() {
        return challenges;
    }

    public Abilities abilities() {
        return abilities;
    }

    public Menus menus() {
        return menus;
    }

    /** Arenen gebaut? Vorher gibt es keine Kämpfe. */
    public boolean ready() {
        return ready;
    }

    public String lang(Player p) {
        return net.language(p.getUniqueId());
    }

    public PlayerData data(Player p) {
        return store.get(p.getUniqueId(), p.getName());
    }

    /** Darf hier bauen (Admin im Kreativmodus)? */
    public boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission(ADMIN);
    }

    /** Zustand fürs Netzwerk (Lobby-Figur, Webseite) – gesammelt, höchstens einmal pro Tick. */
    public void stateChanged() {
        if (stateQueued) {
            return;
        }
        stateQueued = true;
        getServer().getScheduler().runTask(this, () -> {
            stateQueued = false;
            reportState(false);
        });
    }

    private void reportState(boolean force) {
        String state = fights.running() > 0 ? "running" : "waiting";
        int players = getServer().getOnlinePlayers().size();
        if (force || !state.equals(lastState) || players != lastPlayers) {
            lastState = state;
            lastPlayers = players;
            net.state(state, players);
        }
    }

    /** /duels neubauen: alle Kämpfe abbrechen, alles frisch bauen. */
    void rebuild(Runnable done) {
        if (construction.running()) {
            return;
        }
        ready = false;
        fights.abortAll();
        boards.remove();
        arenas = new Arenas(world, settings.arenaCopies());
        construction.ensure(arenas, settings.arenaCopies(), true, () -> {
            ready = true;
            done.run();
        });
        boards.spawn();
        for (Player p : getServer().getOnlinePlayers()) {
            if (hall.inHall(p)) {
                hall.enter(p);
            }
        }
    }

    /** /duels neuladen: Zeiten, Wertung und Suppe neu aus der config.yml. */
    void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
        queues.reload(settings);
    }
}
