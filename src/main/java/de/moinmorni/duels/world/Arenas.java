package de.moinmorni.duels.world;

import de.moinmorni.duels.kit.Kit;
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
 * belegt eine Kopie; danach wird sie aufgeräumt und wieder frei. So laufen viele Kämpfe gleichzeitig. Die besonderen
 * Kampfarten (Sumo, Spleef, Labyrinth, Brücke) kämpfen in ihrer eigenen Arena, alle anderen in den vier normalen.
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
                int[] o = origin(type, c);
                all.add(new Arena(type, c, world, o[0], o[1], o[2], plan));
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

    /**
     * Eine freie Arena für eine Kampfart belegen: die eigene Arena der Kampfart, sonst die gewünschte normale Arena,
     * sonst eine zufällige normale (damit es abwechslungsreich bleibt).
     */
    public Optional<Arena> reserve(Kit kit, ArenaType wanted) {
        List<Arena> free = new ArrayList<>(all.stream().filter(a -> !a.busy() && fits(a, kit, wanted)).toList());
        if (free.isEmpty()) {
            return Optional.empty();
        }
        Arena a = free.get(random.nextInt(free.size()));
        a.busy(true);
        return Optional.of(a);
    }

    private static boolean fits(Arena a, Kit kit, ArenaType wanted) {
        if (!a.type().fits(kit)) {
            return false;
        }
        return wanted == null || !wanted.normal() || !a.type().normal() || a.type() == wanted;
    }

    public void release(Arena a) {
        a.busy(false);
    }

    /** Ist für diese Kampfart gerade eine Arena frei? */
    public boolean anyFree(Kit kit) {
        return all.stream().anyMatch(a -> !a.busy() && a.type().fits(kit));
    }

    public long busyCount() {
        return all.stream().filter(Arena::busy).count();
    }

    public Optional<Arena> at(Location l) {
        return all.stream().filter(a -> a.contains(l)).findFirst();
    }
}
