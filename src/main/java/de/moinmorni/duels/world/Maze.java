package de.moinmorni.duels.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Ein Irrgarten aus {@code n × n} Zellen (n ungerade), ohne Minecraft (gut zu prüfen). Erst ein „perfekter“ Irrgarten
 * (jede Zelle genau auf einem Weg erreichbar, Tiefensuche von der Mitte aus), dann wird ein Teil der Sackgassen
 * geöffnet, damit es Rundwege gibt und niemand ewig in einer Sackgasse feststeckt. Die mittleren 3 × 3 Zellen sind ein
 * offener Platz (dort liegt das Ziel).
 *
 * <p>Der Irrgarten ist <b>drehsymmetrisch</b> wie ein Schlossgarten: um 90° um die Mitte gedreht sieht er genau gleich
 * aus. So ist der Weg von jeder der vier Ecken zum Ziel genau gleich lang – jede Runde ein anderer Irrgarten und
 * trotzdem gerecht, auch wenn bis zu vier Leute gegeneinander laufen.
 */
public final class Maze {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Vier Vierteldrehungen. */
    private static final int TURNS = 4;

    private final int n;
    /** Offen zwischen (x, z) und (x + 1, z). */
    private final boolean[][] east;
    /** Offen zwischen (x, z) und (x, z + 1). */
    private final boolean[][] south;

    private Maze(int n) {
        this.n = n;
        this.east = new boolean[n][n];
        this.south = new boolean[n][n];
    }

    /**
     * @param n     Zellen je Seite (mindestens 5)
     * @param seed  Startwert – gleicher Wert, gleicher Irrgarten
     * @param braid Anteil der Sackgassen, die einen zweiten Ausgang bekommen (0 … 1)
     */
    public static Maze generate(int n, long seed, double braid) {
        if (n < 5) {
            throw new IllegalArgumentException("Irrgarten zu klein: " + n);
        }
        if (n % 2 == 0) {
            throw new IllegalArgumentException("Irrgarten braucht eine Mitte (ungerade Zahl): " + n);
        }
        Maze m = new Maze(n);
        Random r = new Random(seed);
        boolean[][] seen = new boolean[n][n];
        Deque<int[]> stack = new ArrayDeque<>();
        int c = n / 2;
        stack.push(new int[] {c, c});
        seen[c][c] = true;
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<int[]> next = new ArrayList<>();
            for (int[] d : DIRS) {
                int x = cur[0] + d[0];
                int z = cur[1] + d[1];
                if (m.inside(x, z) && !seen[x][z]) {
                    next.add(new int[] {x, z});
                }
            }
            if (next.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] to = next.get(r.nextInt(next.size()));
            // Weg und seine drei Drehbilder (die besuchten Zellen bleiben dabei immer symmetrisch)
            m.openTurned(cur[0], cur[1], to[0], to[1]);
            int[] t = to;
            for (int k = 0; k < TURNS; k++) {
                seen[t[0]][t[1]] = true;
                t = m.turn(t[0], t[1]);
            }
            stack.push(to);
        }
        // Platz in der Mitte
        int lo = n / 2 - 1;
        int hi = n / 2 + 1;
        for (int x = lo; x <= hi; x++) {
            for (int z = lo; z <= hi; z++) {
                if (x < hi) {
                    m.east[x][z] = true;
                }
                if (z < hi) {
                    m.south[x][z] = true;
                }
            }
        }
        // Sackgassen öffnen (Rundwege)
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                if (m.degree(x, z) != 1 || r.nextDouble() >= braid) {
                    continue;
                }
                List<int[]> closed = new ArrayList<>();
                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (m.inside(nx, nz) && !m.open(x, z, d)) {
                        closed.add(new int[] {nx, nz});
                    }
                }
                if (!closed.isEmpty()) {
                    // am liebsten zu einer anderen Sackgasse durchbrechen – das löst gleich zwei auf
                    int[] pick = closed.stream().filter(o -> m.degree(o[0], o[1]) == 1).findFirst()
                            .orElse(closed.get(r.nextInt(closed.size())));
                    m.openTurned(x, z, pick[0], pick[1]);
                }
            }
        }
        return m;
    }

    public int size() {
        return n;
    }

    /** Zelle um 90° um die Mitte gedreht. */
    public int[] turn(int x, int z) {
        return new int[] {n - 1 - z, x};
    }

    /** Durchgang zwischen zwei Nachbarzellen öffnen – und genauso in den drei Drehbildern. */
    private void openTurned(int x1, int z1, int x2, int z2) {
        int[] a = {x1, z1};
        int[] b = {x2, z2};
        for (int k = 0; k < TURNS; k++) {
            open(a[0], a[1], b[0], b[1]);
            a = turn(a[0], a[1]);
            b = turn(b[0], b[1]);
        }
    }

    /** Offen zwischen zwei Nachbarzellen? */
    public boolean linked(int x1, int z1, int x2, int z2) {
        if (!inside(x1, z1) || !inside(x2, z2)) {
            return false;
        }
        return open(x1, z1, new int[] {x2 - x1, z2 - z1});
    }

    /** Sieht der Irrgarten um 90° gedreht genau gleich aus? */
    public boolean symmetric() {
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                int[] p = turn(x, z);
                for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
                    if (!inside(x + d[0], z + d[1])) {
                        continue;
                    }
                    int[] q = turn(x + d[0], z + d[1]);
                    if (linked(x, z, x + d[0], z + d[1]) != linked(p[0], p[1], q[0], q[1])) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < n && z < n;
    }

    /** Offen zwischen (x, z) und (x + 1, z)? */
    public boolean openEast(int x, int z) {
        return inside(x, z) && x + 1 < n && east[x][z];
    }

    /** Offen zwischen (x, z) und (x, z + 1)? */
    public boolean openSouth(int x, int z) {
        return inside(x, z) && z + 1 < n && south[x][z];
    }

    private boolean open(int x, int z, int[] d) {
        if (d[0] == 1) {
            return openEast(x, z);
        } else if (d[0] == -1) {
            return openEast(x - 1, z);
        } else if (d[1] == 1) {
            return openSouth(x, z);
        }
        return openSouth(x, z - 1);
    }

    private void open(int x1, int z1, int x2, int z2) {
        if (x2 == x1 + 1) {
            east[x1][z1] = true;
        } else if (x2 == x1 - 1) {
            east[x2][z2] = true;
        } else if (z2 == z1 + 1) {
            south[x1][z1] = true;
        } else if (z2 == z1 - 1) {
            south[x2][z2] = true;
        }
    }

    /** Wie viele Ausgänge hat eine Zelle? 1 = Sackgasse. */
    public int degree(int x, int z) {
        int d = 0;
        for (int[] dir : DIRS) {
            if (inside(x + dir[0], z + dir[1]) && open(x, z, dir)) {
                d++;
            }
        }
        return d;
    }

    public int deadEnds() {
        int count = 0;
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                if (degree(x, z) == 1) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Schritte (Zellen) von einer Zelle zur anderen, -1 = nicht erreichbar. */
    public int distance(int fx, int fz, int tx, int tz) {
        int[][] dist = new int[n][n];
        for (int[] row : dist) {
            java.util.Arrays.fill(row, -1);
        }
        Deque<int[]> queue = new ArrayDeque<>();
        dist[fx][fz] = 0;
        queue.add(new int[] {fx, fz});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : DIRS) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                if (inside(x, z) && dist[x][z] < 0 && open(c[0], c[1], d)) {
                    dist[x][z] = dist[c[0]][c[1]] + 1;
                    queue.add(new int[] {x, z});
                }
            }
        }
        return dist[tx][tz];
    }

    /** Erreicht man von (0, 0) aus jede Zelle? */
    public boolean connected() {
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                if (distance(0, 0, x, z) < 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
