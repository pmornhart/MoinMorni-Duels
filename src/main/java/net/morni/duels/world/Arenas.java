package net.morni.duels.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Alle Arenen: jede Art gibt es mehrfach (config „arena-kopien“), weit auseinander in der eigenen Welt. Ein Kampf
 * belegt eine Kopie; danach wird sie aufgeräumt und wieder frei. So laufen viele Kämpfe gleichzeitig.
 */
public final class Arenas {

    /** Bodenhöhe von Wartehalle und Arenen. */
    public static final int FLOOR_Y = 100;
    /** Abstand zwischen Arenen (weit genug, dass man sich nicht sieht). */
    public static final int SPACING = 1000;

    private final List<Arena> all = new ArrayList<>();
    private final Map<ArenaType, Plan> plans = new EnumMap<>(ArenaType.class);
    private final Random random = new Random();

    public Arenas(World world, int copies) {
        for (ArenaType type : ArenaType.values()) {
            Plan plan = ArenaPlans.build(type);
            plans.put(type, plan);
            for (int c = 0; c < copies; c++) {
                all.add(new Arena(type, c, world, (type.ordinal() + 1) * SPACING, FLOOR_Y, c * SPACING, plan));
            }
        }
    }

    /** Baupläne (nur zum Bauen gebraucht, danach freigegeben). */
    Map<ArenaType, Plan> takePlans() {
        Map<ArenaType, Plan> copy = new EnumMap<>(plans);
        plans.clear();
        return copy;
    }

    /** Ort einer Arena-Kopie (für den Bau). */
    static int[] origin(ArenaType type, int copy) {
        return new int[] {(type.ordinal() + 1) * SPACING, FLOOR_Y, copy * SPACING};
    }

    public List<Arena> all() {
        return Collections.unmodifiableList(all);
    }

    /** Eine freie Arena belegen (zufällige Art, damit es abwechslungsreich bleibt). */
    public Optional<Arena> reserve() {
        List<Arena> free = new ArrayList<>(all.stream().filter(a -> !a.busy()).toList());
        if (free.isEmpty()) {
            return Optional.empty();
        }
        Arena a = free.get(random.nextInt(free.size()));
        a.busy(true);
        return Optional.of(a);
    }

    public void release(Arena a) {
        a.busy(false);
    }

    public boolean anyFree() {
        return all.stream().anyMatch(a -> !a.busy());
    }

    public long busyCount() {
        return all.stream().filter(Arena::busy).count();
    }

    public Optional<Arena> at(Location l) {
        return all.stream().filter(a -> a.contains(l)).findFirst();
    }
}
