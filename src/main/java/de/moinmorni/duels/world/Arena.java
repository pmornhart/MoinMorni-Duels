package de.moinmorni.duels.world;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Eine Kopie einer Arena an ihrem Platz in der Welt. Während eines Kampfes ist sie belegt. */
public final class Arena {

    private final ArenaType type;
    private final int copy;
    private final World world;
    private final int ox;
    private final int oy;
    private final int oz;
    /** Startpunkte je Seite (0 = Norden, 1 = Süden) und Teamgröße (Index 0 = 1 gegen 1, 1 = 2 gegen 2). */
    private final Location[][][] spawns;
    private final BoundingBox box;
    private final BlockLog log = new BlockLog();
    private boolean busy;

    Arena(ArenaType type, int copy, World world, int ox, int oy, int oz, Plan plan) {
        this.type = type;
        this.copy = copy;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.spawns = new Location[2][2][];
        int[][] xs = {{0}, {-2, 2}};
        for (int side = 0; side < 2; side++) {
            int z = side == 0 ? -ArenaPlans.SPAWN_Z : ArenaPlans.SPAWN_Z;
            float yaw = side == 0 ? 0f : 180f;
            for (int size = 0; size < 2; size++) {
                Location[] list = new Location[xs[size].length];
                for (int i = 0; i < list.length; i++) {
                    int x = xs[size][i];
                    list[i] = new Location(world, ox + x + 0.5, oy + plan.standY(x, z), oz + z + 0.5, yaw, 0f);
                }
                spawns[side][size] = list;
            }
        }
        int h = ArenaPlans.HALF + 1;
        this.box = new BoundingBox(ox - h, oy - 12, oz - h, ox + h + 1, oy + ArenaPlans.TOP + 4, oz + h + 1);
    }

    public ArenaType type() {
        return type;
    }

    public int copy() {
        return copy;
    }

    public World world() {
        return world;
    }

    /** Startpunkte einer Seite für eine Teamgröße (1 oder 2). */
    public List<Location> spawns(int side, int teamSize) {
        Location[] list = spawns[side][Math.max(0, Math.min(1, teamSize - 1))];
        return List.of(list).stream().map(Location::clone).toList();
    }

    /** Von hier schauen Zuschauer zu: über der Mitte, mit Blick auf beide Seiten. */
    public Location viewPoint() {
        return new Location(world, ox + 0.5, oy + 9, oz + 0.5 + ArenaPlans.SPAWN_Z + 6, 180f, 30f);
    }

    public Location center() {
        return new Location(world, ox + 0.5, oy + 1, oz + 0.5);
    }

    /** Wer tiefer fällt, ist in die Tiefe gestürzt. */
    public double voidY() {
        return oy - 10;
    }

    public BoundingBox box() {
        return box.clone();
    }

    public boolean contains(Location l) {
        return l.getWorld() == world && box.contains(l.toVector());
    }

    public BlockLog log() {
        return log;
    }

    public boolean busy() {
        return busy;
    }

    void busy(boolean busy) {
        this.busy = busy;
    }
}
