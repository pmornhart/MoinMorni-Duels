package de.moinmorni.duels.world;

import java.util.List;
import java.util.Random;

/**
 * Baupläne der eigenen Arenen für besondere Kampfarten – alpenländisch wie der Turnierhof, eigene Entwürfe:
 * Tanzboden (Sumo), Schneefeld (Spleef), Irrgarten (Labyrinth) und Klammbrücke (Brücke).
 */
public final class SpecialArenas {

    // ---------- Tanzboden (Sumo) ----------
    /** Radius des runden Tanzbodens. */
    public static final int DANCE_RADIUS = 7;
    private static final int LAKE_Y = -22;

    // ---------- Schneefeld (Spleef) ----------
    /** Das Schneefeld reicht von -SNOW_HALF bis +SNOW_HALF. */
    public static final int SNOW_HALF = 10;
    private static final int SNOW_PIT = -10;

    // ---------- Irrgarten (Labyrinth) ----------
    /** Zellen je Seite. */
    public static final int MAZE_N = 11;
    /** Eine Zelle: zwei Blöcke Weg und eine Hecke. */
    public static final int MAZE_CELL = 3;
    /** Erste Heckenlinie (x und z). */
    public static final int MAZE_ORIGIN = -17;
    /** Gleicher Startwert, gleicher Irrgarten – in jeder Kopie. */
    public static final long MAZE_SEED = 20261116L;
    /** Anteil der Sackgassen, die einen zweiten Ausgang bekommen. */
    public static final double MAZE_BRAID = 0.55;
    /** Bis zu so vielen Seiten starten alle in einer Ecke – gleich weit vom Ziel. */
    public static final int MAZE_CORNERS = 4;
    /** Ziel in der Mitte: x und z von MAZE_GOAL_LO bis MAZE_GOAL_HI (4 × 4, genau um die Mitte des Irrgartens). */
    public static final int MAZE_GOAL_LO = -2;
    public static final int MAZE_GOAL_HI = 1;
    private static final int HEDGE = 4;

    // ---------- Klammbrücke (Brücke) ----------
    /** Tore bei z = ±BRIDGE_GOAL (x = 0), je 3 × 3 groß. */
    public static final int BRIDGE_GOAL = 26;
    /** Startreihe bei z = ±BRIDGE_SPAWN. */
    public static final int BRIDGE_SPAWN = 19;
    /** Unsichtbare Wände bei x = ±(BRIDGE_HALF_X + 1) und z = ±(BRIDGE_END + 1). */
    public static final int BRIDGE_HALF_X = 11;
    public static final int BRIDGE_END = 35;
    /** Höher als so wird nicht gebaut. */
    public static final int BRIDGE_BUILD_TOP = 10;
    /** So tief darf gebaut werden (unter der Brücke). */
    public static final int BRIDGE_BUILD_BOTTOM = -5;
    private static final int BRIDGE_ISLAND = 25;
    private static final int RIVER_Y = -30;

    private SpecialArenas() {
    }

    // ================================================================ Tanzboden

    static void tanzboden(Plan p, Random r) {
        int lake = 30;
        int shore = 37;
        for (int x = -shore - 2; x <= shore + 2; x++) {
            for (int z = -shore - 2; z <= shore + 2; z++) {
                double a = Math.atan2(z, x);
                double d = Math.sqrt(x * x + z * z);
                double edge = lake + 2.0 * Math.sin(3 * a + 0.7) + 1.2 * Math.cos(5 * a);
                if (d <= edge) {
                    p.set(x, LAKE_Y, z, "water[level=0]");
                    p.set(x, LAKE_Y - 1, z, d > edge - 3 ? "sand" : r.nextInt(4) == 0 ? "clay" : "gravel");
                    p.set(x, LAKE_Y - 2, z, "stone");
                    if (d > 12 && d < edge - 2 && r.nextInt(45) == 0) {
                        p.set(x, LAKE_Y + 1, z, "lily_pad");
                    }
                } else if (d <= edge + 7) {
                    int h = (int) Math.round((d - edge) * 0.8);
                    for (int y = LAKE_Y - 2; y <= LAKE_Y + h; y++) {
                        p.set(x, y, z, y == LAKE_Y + h ? (d < edge + 1.5 ? "gravel" : "grass_block[snowy=false]")
                                : y > LAKE_Y + h - 2 ? "dirt" : Decor.rock(r, 0));
                    }
                    int top = LAKE_Y + h + 1;
                    if (d > edge + 2.5 && r.nextInt(70) == 0) {
                        Decor.spruce(p, x, top, z, 6 + r.nextInt(5), r);
                    } else if (d > edge + 1.5 && r.nextInt(9) == 0) {
                        p.setIfEmpty(x, top, z, Decor.pick(r, "short_grass", "short_grass", "fern", "dandelion",
                                "cornflower", "oxeye_daisy"));
                    }
                }
            }
        }
        Decor.islandAt(p, shore + 2, 8, r, "dirt", 2, LAKE_Y - 3);
        // Felsnadel, die den Tanzboden trägt
        for (int y = LAKE_Y + 1; y <= -2; y++) {
            double rr = 1.8 + (-2 - y) * 0.17;
            int ri = (int) Math.ceil(rr);
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    if (x * x + z * z <= rr * rr + r.nextDouble() * 1.5) {
                        p.set(x, y, z, r.nextInt(7) == 0 ? "mossy_cobblestone" : Decor.rock(r, 0));
                    }
                }
            }
        }
        // Balken unter dem Boden
        for (int i = -(DANCE_RADIUS - 1); i <= DANCE_RADIUS - 1; i++) {
            p.set(i, -1, 0, "spruce_log[axis=x]");
            p.set(0, -1, i, "spruce_log[axis=z]");
        }
        // Der Tanzboden: Dielen, ein heller Ring, in der Mitte weiß-blaue Rauten
        for (int x = -DANCE_RADIUS; x <= DANCE_RADIUS; x++) {
            for (int z = -DANCE_RADIUS; z <= DANCE_RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > DANCE_RADIUS + 0.5) {
                    continue;
                }
                String block;
                if (d <= 2.5) {
                    block = Decor.lozenge(x, z, 1) ? "white_concrete" : "light_blue_concrete";
                } else if (d <= 3.3) {
                    block = "birch_planks";
                } else if (d >= DANCE_RADIUS - 0.4) {
                    block = "stripped_spruce_wood[axis=y]";
                } else {
                    block = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x)) / 15.0), 6) == 0
                            ? "stripped_spruce_wood[axis=y]" : "spruce_planks";
                }
                p.set(x, 0, z, block);
            }
        }
        // Laternen auf Felsen im See (tief unter dem Boden – niemand landet darauf)
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            int cx = (int) Math.round(11 * Math.cos(a));
            int cz = (int) Math.round(11 * Math.sin(a));
            for (int y = LAKE_Y; y <= -7; y++) {
                double rr = y > -10 ? 0.6 : 1.4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx * dx + dz * dz <= rr * rr + 0.4) {
                            p.set(cx + dx, y, cz + dz, Decor.rock(r, 0));
                        }
                    }
                }
            }
            p.set(cx, -6, cz, "spruce_fence");
            p.set(cx, -5, cz, "spruce_fence");
            p.set(cx, -4, cz, "lantern[hanging=false]");
        }
        // Berge rundherum
        Decor.mountain(p, 0, -80, LAKE_Y - 2, 32, 66, r);
        Decor.mountain(p, -52, -60, LAKE_Y - 2, 22, 46, r);
        Decor.mountain(p, 56, -58, LAKE_Y - 2, 24, 50, r);
        Decor.mountain(p, 78, 6, LAKE_Y - 2, 22, 40, r);
        Decor.mountain(p, -76, 14, LAKE_Y - 2, 20, 36, r);
    }

    static ArenaShape tanzbodenShape() {
        // Wie tief man fallen darf, steht in der config.yml (sumo.aus-tiefe) – der Wert hier gilt nur ohne Kampf.
        return new ArenaShape(ArenaShape.rows(3, 2, 4, 2, 4.5),
                new double[] {0.5, 7, 14.5, 180, 25}, 12, 12, 12, LAKE_Y - 3, 2.0);
    }

    // ================================================================ Schneefeld

    static void schneefeld(Plan p, Random r) {
        int wall = SNOW_HALF + 1;
        for (int x = -SNOW_HALF; x <= SNOW_HALF; x++) {
            for (int z = -SNOW_HALF; z <= SNOW_HALF; z++) {
                p.set(x, 0, z, "snow_block");
                p.set(x, SNOW_PIT, z, r.nextInt(5) == 0 ? "blue_ice" : "packed_ice");
                p.set(x, SNOW_PIT - 1, z, "packed_ice");
            }
        }
        // Eiswände rund um das Feld, oben verschneit
        for (int x = -wall; x <= wall; x++) {
            for (int z = -wall; z <= wall; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != wall) {
                    continue;
                }
                for (int y = SNOW_PIT - 1; y <= 2; y++) {
                    p.set(x, y, z, y == 2 ? "snow_block" : r.nextInt(6) == 0 ? "blue_ice" : "packed_ice");
                }
                p.set(x, 3, z, "snow[layers=2]");
                for (int y = 4; y <= 9; y++) {
                    p.setIfEmpty(x, y, z, "barrier");
                }
            }
        }
        // Gletscher rundherum
        int edge = 27;
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) <= wall) {
                    continue;
                }
                double rr = Math.pow(Math.pow(Math.abs(x), 4) + Math.pow(Math.abs(z), 4), 0.25);
                if (rr > edge - r.nextDouble() * 1.5) {
                    continue;
                }
                int top = 2 + (rr > 20 ? (int) Math.round(Math.sin(x / 4.0) + Math.cos(z / 5.0)) : 0);
                for (int y = SNOW_PIT - 1; y <= top; y++) {
                    String block = y == top ? (r.nextInt(9) == 0 ? "packed_ice" : "snow_block")
                            : y > top - 3 ? (r.nextInt(3) == 0 ? "packed_ice" : "snow_block") : Decor.rock(r, top - y);
                    p.set(x, y, z, block);
                }
                if (r.nextInt(5) == 0) {
                    p.setIfEmpty(x, top + 1, z, "snow[layers=" + (1 + r.nextInt(2)) + "]");
                }
            }
        }
        Decor.islandAt(p, edge, 16, r, null, 0, SNOW_PIT - 2);
        // Eiszapfen-Säulen und verschneite Fichten
        int[][] spikes = {{-17, -16}, {18, -12}, {-14, 18}, {21, 17}, {-22, 3}, {6, -21}};
        for (int[] s : spikes) {
            int h = 4 + r.nextInt(5);
            for (int y = 3; y < 3 + h; y++) {
                p.set(s[0], y, s[1], "packed_ice");
                if (y < 3 + h / 2) {
                    p.set(s[0] + 1, y, s[1], "packed_ice");
                    p.set(s[0], y, s[1] + 1, "packed_ice");
                }
            }
        }
        int[][] trees = {{-20, -8}, {22, 4}, {-6, 22}, {14, -22}, {-23, 20}, {23, -20}};
        for (int[] t : trees) {
            Decor.spruce(p, t[0], 3, t[1], 6 + r.nextInt(3), r);
        }
        // Berghütte am Westrand
        hut(p, -19, 3, -14, "east");
        // Laternen an den Ecken, Fahnen an den Seiten
        for (int[] c : new int[][] {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            Decor.lanternPost(p, c[0] + Integer.signum(c[0]), 3, c[1] + Integer.signum(c[1]), "spruce_fence");
        }
        int design = 0;
        for (int[] f : new int[][] {{0, -14}, {0, 14}, {-14, 0}, {14, 0}}) {
            for (int y = 3; y <= 6; y++) {
                p.set(f[0], y, f[1], "spruce_fence");
            }
            p.banner(f[0], 7, f[1], Decor.bannerBlock(design, false, "0"), Decor.bannerPatterns(design));
            design++;
        }
        // Schneeberge im Hintergrund
        Decor.mountain(p, 0, -76, -14, 30, 62, r);
        Decor.mountain(p, -60, -50, -14, 24, 50, r);
        Decor.mountain(p, 62, -46, -14, 26, 54, r);
        Decor.mountain(p, 74, 24, -14, 20, 38, r);
    }

    /** Kleine Hütte mit weißen Wänden, dunklem Gebälk und Satteldach; Tür zeigt nach {@code door}. */
    private static void hut(Plan p, int x0, int y0, int z0, String door) {
        int w = 5;
        int d = 6;
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                p.set(x, y0 - 1, z, "spruce_planks");
                boolean wallX = x == x0 || x == x0 + w - 1;
                boolean wallZ = z == z0 || z == z0 + d - 1;
                if (!wallX && !wallZ) {
                    continue;
                }
                for (int y = y0; y < y0 + 3; y++) {
                    p.set(x, y, z, wallX && wallZ ? "dark_oak_log[axis=y]" : "white_concrete");
                }
            }
        }
        int mid = x0 + w / 2;
        if ("east".equals(door)) {
            p.set(x0 + w - 1, y0, z0 + d / 2, "spruce_door[facing=east,half=lower,hinge=left,open=false,powered=false]");
            p.set(x0 + w - 1, y0 + 1, z0 + d / 2, "spruce_door[facing=east,half=upper,hinge=left,open=false,powered=false]");
            p.set(x0 + w - 1, y0 + 1, z0 + 1, "glass_pane");
            p.set(x0 + w - 1, y0 + 1, z0 + d - 2, "glass_pane");
        }
        for (int i = 0; i <= w / 2 + 1; i++) {
            int y = y0 + 3 + i;
            for (int z = z0 - 1; z <= z0 + d; z++) {
                int west = x0 - 1 + i;
                int east = x0 + w - i;
                if (west < east) {
                    p.set(west, y, z, "spruce_stairs[facing=east,half=bottom,shape=straight]");
                    p.set(east, y, z, "spruce_stairs[facing=west,half=bottom,shape=straight]");
                } else {
                    p.set(mid, y, z, "spruce_slab[type=bottom,waterlogged=false]");
                }
                if (west + 1 < east && (z == z0 || z == z0 + d - 1)) {
                    for (int x = west + 1; x < east; x++) {
                        p.set(x, y, z, "white_concrete");
                    }
                }
            }
        }
        p.set(mid, y0 + 2, z0 + d, "lantern[hanging=false]");
    }

    static ArenaShape schneefeldShape() {
        return new ArenaShape(ArenaShape.rows(7, 4, 4, 2, 7),
                new double[] {0.5, 10, 18.5, 180, 32}, 13, 13, 10, SNOW_PIT - 2, 1.2);
    }

    // ================================================================ Irrgarten

    /** Erster Wegblock einer Zelle (x oder z). */
    public static int mazeCell(int index) {
        return MAZE_ORIGIN + MAZE_CELL * index + 1;
    }

    /** Letzte Heckenlinie (x und z). */
    public static int mazeLast() {
        return MAZE_ORIGIN + MAZE_CELL * MAZE_N;
    }

    /** Höchste Höhe der Hecken samt Laternen – bis hierhin wird der Irrgarten jede Runde neu gesetzt. */
    public static int mazeTop() {
        return HEDGE + 1;
    }

    static void irrgarten(Plan p, Random r) {
        Maze maze = Maze.generate(MAZE_N, MAZE_SEED, MAZE_BRAID);
        int last = mazeLast();
        int rim = 5;
        // Boden: Wiese mit Moos, im Irrgarten Kieswege
        for (int x = MAZE_ORIGIN - rim; x <= last + rim; x++) {
            for (int z = MAZE_ORIGIN - rim; z <= last + rim; z++) {
                boolean inside = x > MAZE_ORIGIN && x < last && z > MAZE_ORIGIN && z < last;
                String block;
                if (inside) {
                    int roll = r.nextInt(100);
                    block = roll < 70 ? "grass_block[snowy=false]" : roll < 85 ? "moss_block" : "coarse_dirt";
                } else {
                    block = "grass_block[snowy=false]";
                }
                p.set(x, 0, z, block);
            }
        }
        mazeLayer(p, maze, r);
        // Platz in der Mitte: Steinpflaster, darin das Ziel
        int c = mazeCell(MAZE_N / 2);
        for (int x = c - 4; x <= c + 5; x++) {
            for (int z = c - 4; z <= c + 5; z++) {
                double d = Math.hypot(x - c - 0.5, z - c - 0.5);
                if (d <= 4.6 && p.get(x, 1, z) == null) {
                    p.set(x, 0, z, r.nextInt(5) == 0 ? "mossy_stone_bricks" : "stone_bricks");
                }
            }
        }
        mazeGoal(p);
        // Rand außen: Fichten, Zaun
        for (int i = MAZE_ORIGIN - rim; i <= last + rim; i++) {
            for (int side : new int[] {MAZE_ORIGIN - rim, last + rim}) {
                p.set(i, 1, side, "spruce_fence");
                p.set(side, 1, i, "spruce_fence");
            }
        }
        for (int k = 0; k < 16; k++) {
            int along = MAZE_ORIGIN - rim + 2 + r.nextInt(last - MAZE_ORIGIN + 2 * rim - 3);
            int out = (r.nextBoolean() ? MAZE_ORIGIN - 3 : last + 3);
            if (r.nextBoolean()) {
                Decor.spruce(p, along, 1, out, 6 + r.nextInt(4), r);
            } else {
                Decor.spruce(p, out, 1, along, 6 + r.nextInt(4), r);
            }
        }
        Decor.island(p, last + rim + 2, 15, r, "dirt", 3);
        Decor.mountain(p, 0, -72, -14, 28, 50, r);
        Decor.mountain(p, -60, -40, -14, 20, 34, r);
        Decor.mountain(p, 64, -30, -14, 22, 40, r);
    }

    /**
     * Alles im Irrgarten über dem Boden (y 1 bis {@link #mazeTop()}): Hecken, Pfosten mit Laternen, Blumen in den
     * Sackgassen. Wird beim Bau gesetzt und vor jeder Runde mit einem neuen Irrgarten ersetzt.
     */
    static void mazeLayer(Plan p, Maze maze, Random r) {
        // Hecken: senkrechte und waagrechte Stücke, Pfosten an den Kreuzungen
        for (int i = 0; i <= MAZE_N; i++) {
            int line = MAZE_ORIGIN + MAZE_CELL * i;
            for (int j = 0; j < MAZE_N; j++) {
                int from = mazeCell(j);
                boolean wallX = i == 0 || i == MAZE_N || !maze.openEast(i - 1, j);
                boolean wallZ = i == 0 || i == MAZE_N || !maze.openSouth(j, i - 1);
                for (int k = from; k < from + MAZE_CELL - 1; k++) {
                    if (wallX) {
                        hedge(p, line, k, r);
                    }
                    if (wallZ) {
                        hedge(p, k, line, r);
                    }
                }
            }
        }
        for (int i = 0; i <= MAZE_N; i++) {
            for (int j = 0; j <= MAZE_N; j++) {
                int x = MAZE_ORIGIN + MAZE_CELL * i;
                int z = MAZE_ORIGIN + MAZE_CELL * j;
                boolean any = p.get(x, 1, z - 1) != null || p.get(x, 1, z + 1) != null
                        || p.get(x - 1, 1, z) != null || p.get(x + 1, 1, z) != null;
                if (any) {
                    hedge(p, x, z, r);
                    if ((i + j) % 4 == 0 && i > 0 && j > 0 && i < MAZE_N && j < MAZE_N) {
                        p.set(x, HEDGE + 1, z, "lantern[hanging=false]");
                    }
                }
            }
        }
        // Sackgassen: Blumen
        for (int cx = 0; cx < MAZE_N; cx++) {
            for (int cz = 0; cz < MAZE_N; cz++) {
                boolean corner = (cx == 0 || cx == MAZE_N - 1) && (cz == 0 || cz == MAZE_N - 1);
                if (maze.degree(cx, cz) != 1 || corner) {
                    continue;
                }
                int x = mazeCell(cx) + r.nextInt(2);
                int z = mazeCell(cz) + r.nextInt(2);
                p.set(x, 1, z, Decor.pick(r, "poppy", "cornflower", "oxeye_daisy", "allium", "azure_bluet",
                        "lily_of_the_valley"));
            }
        }
    }

    /** Ziel in der Mitte: Goldrand, Smaragd und ein Leuchtfeuer, dessen Strahl über die Hecken hinweg den Weg zeigt. */
    private static void mazeGoal(Plan p) {
        for (int x = MAZE_GOAL_LO; x <= MAZE_GOAL_HI; x++) {
            for (int z = MAZE_GOAL_LO; z <= MAZE_GOAL_HI; z++) {
                boolean inner = x > MAZE_GOAL_LO && x < MAZE_GOAL_HI && z > MAZE_GOAL_LO && z < MAZE_GOAL_HI;
                p.set(x, 0, z, inner ? "emerald_block" : "gold_block");
                p.clear(x, 1, z);
            }
        }
        // Leuchtfeuer auf einem Sockel aus Eisen (3 × 3 darunter)
        int b = MAZE_GOAL_LO + 1;
        for (int x = b - 1; x <= b + 1; x++) {
            for (int z = b - 1; z <= b + 1; z++) {
                p.set(x, -1, z, "iron_block");
            }
        }
        p.set(b, 0, b, "beacon");
        // Laternen an den vier Ecken des Platzes
        for (int[] o : new int[][] {{MAZE_GOAL_LO - 1, MAZE_GOAL_LO - 1}, {MAZE_GOAL_HI + 1, MAZE_GOAL_LO - 1},
            {MAZE_GOAL_LO - 1, MAZE_GOAL_HI + 1}, {MAZE_GOAL_HI + 1, MAZE_GOAL_HI + 1}}) {
            p.set(o[0], 0, o[1], "chiseled_stone_bricks");
        }
    }

    private static void hedge(Plan p, int x, int z, Random r) {
        for (int y = 1; y <= HEDGE; y++) {
            String leaves;
            if (y == HEDGE) {
                leaves = r.nextInt(5) == 0 ? "flowering_azalea_leaves" : "azalea_leaves";
            } else {
                leaves = r.nextInt(10) < 7 ? "spruce_leaves" : "oak_leaves";
            }
            p.set(x, y, z, leaves + "[persistent=true]");
        }
    }

    static ArenaShape irrgartenShape() {
        int a = mazeCell(0);
        int b = mazeCell(MAZE_N - 1);
        int m = mazeCell(MAZE_N / 2);
        List<int[]> sideA = List.of(new int[] {a, a}, new int[] {a + 1, a}, new int[] {a, a + 1},
                new int[] {a + 1, a + 1});
        List<int[]> sideB = List.of(new int[] {b + 1, b + 1}, new int[] {b, b + 1}, new int[] {b + 1, b},
                new int[] {b, b});
        // Erst die vier Ecken (alle gleich weit vom Ziel), dann die Mitten der Seiten
        List<int[]> ring = List.of(new int[] {a, a}, new int[] {b + 1, a}, new int[] {b + 1, b + 1},
                new int[] {a, b + 1}, new int[] {m, a}, new int[] {b + 1, m}, new int[] {m, b + 1}, new int[] {a, m});
        return new ArenaShape(ArenaShape.spots(sideA, sideB, ring),
                new double[] {0.5, 17, 9.5, 180, 58}, 23, 23, 10, -12, 11);
    }

    // ================================================================ Klammbrücke

    static void klammbruecke(Plan p, Random r) {
        // Zwei Inseln
        for (int side : new int[] {-1, 1}) {
            int cz = side * BRIDGE_ISLAND;
            for (int x = -10; x <= 10; x++) {
                for (int z = cz - 11; z <= cz + 11; z++) {
                    double rr = Math.pow(Math.pow(Math.abs(x) / 9.0, 4) + Math.pow(Math.abs(z - cz) / 9.8, 4), 0.25);
                    if (rr > 1.0) {
                        continue;
                    }
                    p.set(x, 0, z, Math.abs(x) <= 1 && side * z < BRIDGE_GOAL - 2 ? "dirt_path"
                            : r.nextInt(12) == 0 ? "moss_block" : "grass_block[snowy=false]");
                    int depth = 3 + (int) Math.round(11 * Math.sqrt(Math.max(0, 1 - rr * rr))) + r.nextInt(2);
                    for (int d = 1; d <= depth; d++) {
                        p.set(x, -d, z, d <= 2 ? "dirt" : Decor.rock(r, d));
                    }
                    if (rr > 0.55 && r.nextInt(7) == 0 && Math.abs(x) > 2) {
                        p.setIfEmpty(x, 1, z, Decor.pick(r, "short_grass", "fern", "dandelion", "cornflower"));
                    }
                }
            }
            goal(p, side);
            String colour = side < 0 ? "blue" : "red";
            bridgeHut(p, side, colour);
            for (int x : new int[] {-4, 4}) {
                Decor.lanternPost(p, x, 1, side * BRIDGE_GOAL, "spruce_fence");
            }
            // Fahnen in Teamfarbe an der Brückenauffahrt
            for (int x : new int[] {-3, 3}) {
                int z = side * 16;
                for (int y = 1; y <= 4; y++) {
                    p.set(x, y, z, "stripped_spruce_log[axis=y]");
                }
                p.set(x, 5, z, colour + "_banner[rotation=" + (side < 0 ? "0" : "8") + "]");
            }
        }
        // Die Brücke: Dielen mit Balken am Rand, in der Mitte ein Felsvorsprung
        for (int z = -15; z <= 15; z++) {
            for (int x = -1; x <= 1; x++) {
                p.set(x, 0, z, x == 0 ? "spruce_planks" : "stripped_spruce_log[axis=z]");
            }
            if (Math.floorMod(z, 4) == 0) {
                p.set(-1, -1, z, "iron_chain[axis=y]");
                p.set(1, -1, z, "iron_chain[axis=y]");
                p.set(-1, -2, z, "iron_chain[axis=y]");
                p.set(1, -2, z, "iron_chain[axis=y]");
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 && Math.abs(z) == 3) {
                    continue;
                }
                p.set(x, 0, z, Math.abs(x) <= 1 ? "spruce_planks" : r.nextInt(3) == 0 ? "mossy_cobblestone"
                        : "cobblestone");
                for (int d = 1; d <= 4 - Math.max(Math.abs(x), Math.abs(z)) + 2; d++) {
                    p.set(x, -d, z, Decor.rock(r, d));
                }
            }
        }
        // Felswände der Klamm, unten ein Bach
        for (int z = -BRIDGE_END - 4; z <= BRIDGE_END + 4; z++) {
            for (int x = -12; x <= 12; x++) {
                p.set(x, RIVER_Y, z, "water[level=0]");
                p.set(x, RIVER_Y - 1, z, r.nextInt(3) == 0 ? "gravel" : "stone");
            }
            for (int side : new int[] {-1, 1}) {
                int top = 5 + (int) Math.round(3 * Math.sin(z / 5.0 + side) + 2 * Math.cos(z / 3.0));
                for (int w = 13; w <= 20; w++) {
                    int x = side * w;
                    int colTop = top - Math.max(0, (w - 17));
                    for (int y = RIVER_Y - 1; y <= colTop; y++) {
                        boolean face = w == 13;
                        p.set(x, y, z, face && r.nextInt(5) == 0 ? "mossy_cobblestone" : Decor.rock(r, y < -12 ? 10 : 0));
                    }
                    if (w >= 15 && r.nextInt(9) == 0) {
                        p.setIfEmpty(x, colTop + 1, z, Decor.pick(r, "short_grass", "fern"));
                    }
                }
            }
        }
        // Wasserfälle an den Wänden
        for (int[] f : new int[][] {{-13, -6}, {13, 8}}) {
            for (int y = RIVER_Y; y <= 3; y++) {
                p.set(f[0], y, f[1], "water[level=0]");
                p.set(f[0], y, f[1] + 1, "water[level=0]");
            }
        }
        // Fichten oben auf den Wänden
        for (int k = 0; k < 14; k++) {
            int side = k % 2 == 0 ? -1 : 1;
            int z = -BRIDGE_END + r.nextInt(2 * BRIDGE_END);
            int x = side * (16 + r.nextInt(4));
            int y = 1;
            while (y < 14 && p.solid(x, y, z)) {
                y++;
            }
            Decor.spruce(p, x, y, z, 6 + r.nextInt(4), r);
        }
        // Unsichtbare Wände an den Seiten und an den Enden (keine Decke)
        for (int y = 1; y <= 16; y++) {
            for (int z = -BRIDGE_END - 1; z <= BRIDGE_END + 1; z++) {
                p.setIfEmpty(-BRIDGE_HALF_X - 1, y, z, "barrier");
                p.setIfEmpty(BRIDGE_HALF_X + 1, y, z, "barrier");
            }
            for (int x = -BRIDGE_HALF_X - 1; x <= BRIDGE_HALF_X + 1; x++) {
                p.setIfEmpty(x, y, -BRIDGE_END - 1, "barrier");
                p.setIfEmpty(x, y, BRIDGE_END + 1, "barrier");
            }
        }
        Decor.mountain(p, -46, 0, RIVER_Y, 26, 70, r);
        Decor.mountain(p, 48, -10, RIVER_Y, 28, 74, r);
        Decor.mountain(p, 0, -84, RIVER_Y, 30, 66, r);
    }

    /** Tor: 3 × 3 Schacht, 4 tief, Rand in der Teamfarbe, unten Glas. Seite -1 = Norden (Blau), 1 = Süden (Rot). */
    private static void goal(Plan p, int side) {
        int gz = side * BRIDGE_GOAL;
        String colour = side < 0 ? "blue" : "red";
        for (int x = -2; x <= 2; x++) {
            for (int z = gz - 2; z <= gz + 2; z++) {
                boolean hole = Math.abs(x) <= 1 && Math.abs(z - gz) <= 1;
                for (int y = -5; y <= 0; y++) {
                    if (hole && y >= -4) {
                        p.clear(x, y, z);
                    } else if (hole) {
                        p.set(x, y, z, colour + "_stained_glass");
                    } else {
                        p.set(x, y, z, colour + "_concrete");
                    }
                }
            }
        }
    }

    /** Almhütte hinter dem Tor, Fensterläden in der Teamfarbe. */
    private static void bridgeHut(Plan p, int side, String colour) {
        int front = side * (BRIDGE_GOAL + 4);
        int back = side * (BRIDGE_GOAL + 8);
        int zMin = Math.min(front, back);
        int zMax = Math.max(front, back);
        for (int x = -4; x <= 4; x++) {
            for (int z = zMin; z <= zMax; z++) {
                p.set(x, 0, z, "spruce_planks");
                boolean wallX = Math.abs(x) == 4;
                boolean wallZ = z == zMin || z == zMax;
                if (!wallX && !wallZ) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, wallX && wallZ ? "dark_oak_log[axis=y]" : y == 3 ? "stripped_spruce_wood[axis=y]"
                            : "white_concrete");
                }
            }
        }
        String facing = side < 0 ? "south" : "north";
        p.set(0, 1, front, "spruce_door[facing=" + facing + ",half=lower,hinge=left,open=false,powered=false]");
        p.set(0, 2, front, "spruce_door[facing=" + facing + ",half=upper,hinge=left,open=false,powered=false]");
        for (int x : new int[] {-2, 2}) {
            p.set(x, 2, front, "glass_pane");
            p.set(x - 1, 2, front + side * -1, colour + "_wool");
        }
        // Satteldach (First in z-Richtung)
        for (int i = 0; i <= 5; i++) {
            int y = 4 + i;
            int west = -5 + i;
            int east = 5 - i;
            for (int z = zMin - 1; z <= zMax + 1; z++) {
                if (west < east) {
                    p.set(west, y, z, "dark_oak_stairs[facing=east,half=bottom,shape=straight]");
                    p.set(east, y, z, "dark_oak_stairs[facing=west,half=bottom,shape=straight]");
                    if (z == zMin || z == zMax) {
                        for (int x = west + 1; x < east; x++) {
                            p.set(x, y, z, "white_concrete");
                        }
                    }
                } else {
                    p.set(0, y, z, "dark_oak_slab[type=bottom,waterlogged=false]");
                }
            }
        }
        p.banner(0, 3, front - side, colour + "_wall_banner[facing=" + facing + "]",
                Decor.bannerPatterns(side < 0 ? 2 : 1));
    }

    static ArenaShape klammbrueckeShape() {
        return new ArenaShape(ArenaShape.rows(BRIDGE_SPAWN, 2, 4, 2, 6),
                new double[] {0.5, 13, BRIDGE_GOAL + 8.5, 180, 24}, BRIDGE_HALF_X + 2, BRIDGE_END + 2, 18,
                RIVER_Y - 2, 7);
    }
}
