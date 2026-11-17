package de.moinmorni.duels.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BoundingBox;

/** Eine Kopie einer Arena an ihrem Platz in der Welt. Während eines Kampfes ist sie belegt. */
public final class Arena {

    /** Größtes Team (so viele Startplätze hat jede Seite). */
    public static final int MAX_TEAM = 8;

    private final ArenaType type;
    private final int copy;
    private final World world;
    private final int ox;
    private final int oy;
    private final int oz;
    /** Startpunkte je Seite (0 = Norden, 1 = Süden) und Teamgröße (Index 0 = eine Person, 1 = zwei …). */
    private final Location[][][] spawns;
    /** Startpunkte für „Jeder gegen jeden“. */
    private final Location[] ring;
    private final Location view;
    private final BoundingBox box;
    private final double voidY;
    private final BlockLog log = new BlockLog();
    private boolean busy;

    Arena(ArenaType type, int copy, World world, int ox, int oy, int oz, Plan plan) {
        this.type = type;
        this.copy = copy;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        ArenaShape shape = ArenaPlans.shape(type);
        this.spawns = new Location[2][MAX_TEAM][];
        for (int side = 0; side < 2; side++) {
            float yaw = side == 0 ? 0f : 180f;
            for (int size = 1; size <= MAX_TEAM; size++) {
                List<int[]> at = shape.starts().side(side, size);
                Location[] list = new Location[at.size()];
                for (int i = 0; i < list.length; i++) {
                    int[] xz = at.get(i);
                    float facing = type == ArenaType.IRRGARTEN ? ArenaShape.yawToCentre(xz[0], xz[1]) : yaw;
                    list[i] = location(plan, xz, facing);
                }
                spawns[side][size - 1] = list;
            }
        }
        List<int[]> circle = shape.starts().ring(ArenaShape.RING);
        this.ring = new Location[circle.size()];
        for (int i = 0; i < ring.length; i++) {
            int[] xz = circle.get(i);
            ring[i] = location(plan, xz, ArenaShape.yawToCentre(xz[0], xz[1]));
        }
        double[] v = shape.view();
        this.view = new Location(world, ox + v[0], oy + v[1], oz + v[2], (float) v[3], (float) v[4]);
        this.box = new BoundingBox(ox - shape.halfX(), oy + shape.bottom(), oz - shape.halfZ(),
                ox + shape.halfX() + 1, oy + shape.top(), oz + shape.halfZ() + 1);
        this.voidY = oy + 1 - shape.fallDepth();
    }

    private Location location(Plan plan, int[] xz, float yaw) {
        return new Location(world, ox + xz[0] + 0.5, oy + plan.standY(xz[0], xz[1]), oz + xz[1] + 0.5, yaw, 0f);
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

    /** Startpunkte eines Teams. Zwei Teams stehen sich gegenüber, mehr Teams stehen im Kreis (je Team ein Platz). */
    public List<Location> spawns(int team, int teams, int teamSize) {
        if (teams <= 2) {
            Location[] list = spawns[Math.max(0, Math.min(1, team))][Math.max(0, Math.min(MAX_TEAM, teamSize) - 1)];
            return List.of(list).stream().map(Location::clone).toList();
        }
        // Irrgarten: bis zu vier Seiten in den Ecken (gleich weit vom Ziel), sonst gleichmäßig verteilt
        int index = type == ArenaType.IRRGARTEN && teams <= SpecialArenas.MAZE_CORNERS ? Math.floorMod(team, ring.length)
                : Math.floorMod(team * ring.length / Math.max(1, teams), ring.length);
        List<Location> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, teamSize); i++) {
            out.add(ring[index].clone());
        }
        return out;
    }

    /** Startpunkte einer Seite für eine Teamgröße (für zwei Teams). */
    public List<Location> spawns(int side, int teamSize) {
        return spawns(side, 2, teamSize);
    }

    /** Von hier schauen Zuschauer zu. */
    public Location viewPoint() {
        return view.clone();
    }

    public Location center() {
        return new Location(world, ox + 0.5, oy + 1, oz + 0.5);
    }

    /** Wer tiefer fällt, ist herausgefallen. */
    public double voidY() {
        return voidY;
    }

    /** Mittelpunkt der Arena (Boden = y). */
    public int ox() {
        return ox;
    }

    public int oy() {
        return oy;
    }

    public int oz() {
        return oz;
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

    /**
     * Irrgarten: ein neuer Irrgarten aus {@code seed} (Hecken, Laternen, Blumen – Boden, Ziel und Rand bleiben). Gesetzt
     * werden nur Blöcke, die sich ändern. Gibt die Zahl der geänderten Blöcke zurück (0 bei anderen Arenen).
     */
    public int reshuffleMaze(long seed) {
        if (type != ArenaType.IRRGARTEN) {
            return 0;
        }
        Plan layer = new Plan();
        SpecialArenas.mazeLayer(layer, Maze.generate(SpecialArenas.MAZE_N, seed, SpecialArenas.MAZE_BRAID),
                new Random(seed));
        BlockData air = Material.AIR.createBlockData();
        int changed = 0;
        int last = SpecialArenas.mazeLast();
        for (int x = SpecialArenas.MAZE_ORIGIN; x <= last; x++) {
            for (int z = SpecialArenas.MAZE_ORIGIN; z <= last; z++) {
                for (int y = 1; y <= SpecialArenas.mazeTop(); y++) {
                    BlockData want = layer.get(x, y, z);
                    if (want == null) {
                        want = air;
                    }
                    Block b = world.getBlockAt(ox + x, oy + y, oz + z);
                    if (!b.getBlockData().equals(want)) {
                        b.setBlockData(want, false);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    public boolean busy() {
        return busy;
    }

    void busy(boolean busy) {
        this.busy = busy;
    }
}
