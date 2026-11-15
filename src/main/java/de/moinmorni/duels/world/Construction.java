package de.moinmorni.duels.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Baut Wartehalle und Arenen aus den Bauplänen – nur wenn sich etwas geändert hat (Bau-Version oder Zahl der
 * Kopien, gemerkt in {@code bau.yml}). Die Halle entsteht sofort, die Arenen Stück für Stück (höchstens
 * {@link #BUDGET_NANOS} pro Tick), damit der Server dabei flüssig bleibt. Vorher wird abgeräumt, was der alte Bau
 * belegt hatte.
 */
public final class Construction {

    /** Bau-Version: erhöhen, wenn sich Halle oder Arenen ändern – dann wird beim nächsten Start neu gebaut. */
    public static final int VERSION = 1;
    private static final long BUDGET_NANOS = 25_000_000L;
    private static final int CHECK_EVERY = 256;

    /** Ein Arbeitsschritt; {@code true} = fertig. */
    private interface Job {
        boolean step(long deadline);
    }

    private final Plugin plugin;
    private final Logger log;
    private final World world;
    private final Path stateFile;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private BukkitTask task;
    private int placed;

    public Construction(Plugin plugin, World world) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.world = world;
        this.stateFile = plugin.getDataFolder().toPath().resolve("bau.yml");
    }

    public boolean running() {
        return task != null;
    }

    /**
     * Baut, falls nötig. {@code done} läuft auf dem Hauptthread, sobald alles steht (auch sofort, wenn nichts zu tun
     * ist).
     */
    public void ensure(Arenas arenas, int copies, boolean force, Runnable done) {
        YamlConfiguration state = readState();
        boolean current = !force && state.getInt("version") == VERSION && state.getInt("kopien") == copies;
        Map<ArenaType, Plan> plans = arenas.takePlans();
        if (current) {
            done.run();
            return;
        }
        log.info("Wartehalle und Arenen werden gebaut (Bau-Version " + VERSION + ", " + copies + " Kopien je Arena) …");
        long start = System.currentTimeMillis();
        List<String> boxes = new ArrayList<>();
        // Alten Bau abräumen: die Halle sofort (sie wird gleich neu gebaut), die Arenen nach und nach
        List<int[]> later = new ArrayList<>();
        for (String box : state.getStringList("bereiche")) {
            int[] b = parseBox(box);
            if (b == null) {
                continue;
            }
            if (Math.abs(b[0]) < Arenas.SPACING / 2 && Math.abs(b[2]) < Arenas.SPACING / 2) {
                jobs.add(clearJob(b));
            } else {
                later.add(b);
            }
        }
        // Halle zuerst (sofort, damit niemand ins Leere fällt)
        Plan hall = HallPlan.build();
        boxes.add(boxOf(hall, 0, Arenas.FLOOR_Y, 0));
        jobs.add(placeJob(hall, 0, Arenas.FLOOR_Y, 0));
        runNow();
        later.forEach(b -> jobs.add(clearJob(b)));
        for (ArenaType type : ArenaType.values()) {
            Plan plan = plans.get(type);
            for (int c = 0; c < copies; c++) {
                int[] o = Arenas.origin(type, c);
                boxes.add(boxOf(plan, o[0], o[1], o[2]));
                jobs.add(placeJob(plan, o[0], o[1], o[2]));
            }
        }
        jobs.add(deadline -> {
            YamlConfiguration fresh = new YamlConfiguration();
            fresh.set("version", VERSION);
            fresh.set("kopien", copies);
            fresh.set("bereiche", boxes);
            writeState(fresh);
            log.info("Fertig gebaut: " + placed + " Blöcke in " + (System.currentTimeMillis() - start) / 1000 + " s.");
            done.run();
            return true;
        });
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Erste Schritte (abräumen + Halle) gleich ausführen. */
    private void runNow() {
        while (!jobs.isEmpty()) {
            Job job = jobs.peekFirst();
            if (job.step(Long.MAX_VALUE)) {
                jobs.pollFirst();
            }
        }
    }

    private void tick() {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (!jobs.isEmpty() && System.nanoTime() < deadline) {
            Job job = jobs.peekFirst();
            boolean finished;
            try {
                finished = job.step(deadline);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Bauschritt fehlgeschlagen – wird übersprungen", e);
                finished = true;
            }
            if (finished) {
                jobs.pollFirst();
            }
        }
        if (jobs.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private Job placeJob(Plan plan, int ox, int oy, int oz) {
        List<Long> keys = new ArrayList<>(plan.size());
        List<BlockData> data = new ArrayList<>(plan.size());
        plan.forEach((k, d) -> {
            keys.add(k);
            data.add(d);
        });
        List<Plan.Banner> banners = plan.banners();
        int[] index = {0};
        return deadline -> {
            int i = index[0];
            int n = 0;
            while (i < keys.size()) {
                long k = keys.get(i);
                world.getBlockAt(ox + Plan.keyX(k), oy + Plan.keyY(k), oz + Plan.keyZ(k)).setBlockData(data.get(i), false);
                i++;
                placed++;
                if (++n % CHECK_EVERY == 0 && System.nanoTime() > deadline) {
                    index[0] = i;
                    return false;
                }
            }
            index[0] = i;
            for (Plan.Banner b : banners) {
                Block block = world.getBlockAt(ox + b.x(), oy + b.y(), oz + b.z());
                BlockState st = block.getState();
                if (st instanceof org.bukkit.block.Banner banner) {
                    banner.setPatterns(b.patterns());
                    banner.update(true, false);
                }
            }
            return true;
        };
    }

    private Job clearJob(int[] b) {
        int[] cursor = {b[0], b[1], b[2]};
        boolean[] entitiesGone = {false};
        return deadline -> {
            if (!entitiesGone[0]) {
                for (Entity e : world.getEntities()) {
                    if (!(e instanceof Player)) {
                        org.bukkit.Location l = e.getLocation();
                        if (l.getX() >= b[0] && l.getX() <= b[3] + 1 && l.getZ() >= b[2] && l.getZ() <= b[5] + 1) {
                            e.remove();
                        }
                    }
                }
                entitiesGone[0] = true;
            }
            int n = 0;
            while (cursor[0] <= b[3]) {
                Block block = world.getBlockAt(cursor[0], cursor[1], cursor[2]);
                if (block.getType() != Material.AIR) {
                    block.setType(Material.AIR, false);
                }
                if (++cursor[2] > b[5]) {
                    cursor[2] = b[2];
                    if (++cursor[1] > b[4]) {
                        cursor[1] = b[1];
                        cursor[0]++;
                    }
                }
                if (++n % CHECK_EVERY == 0 && System.nanoTime() > deadline) {
                    return false;
                }
            }
            return true;
        };
    }

    private static String boxOf(Plan plan, int ox, int oy, int oz) {
        int[] b = plan.bounds();
        return (ox + b[0]) + " " + (oy + b[1]) + " " + (oz + b[2]) + " " + (ox + b[3]) + " " + (oy + b[4]) + " "
                + (oz + b[5]);
    }

    private static int[] parseBox(String s) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length != 6) {
            return null;
        }
        int[] b = new int[6];
        try {
            for (int i = 0; i < 6; i++) {
                b[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return b;
    }

    private YamlConfiguration readState() {
        YamlConfiguration y = new YamlConfiguration();
        if (Files.isRegularFile(stateFile)) {
            try {
                y.loadFromString(Files.readString(stateFile, StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                log.warning("bau.yml ließ sich nicht lesen – es wird neu gebaut: " + e.getMessage());
            }
        }
        return y;
    }

    private void writeState(YamlConfiguration y) {
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, y.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.log(Level.WARNING, "bau.yml ließ sich nicht schreiben", e);
        }
    }
}
