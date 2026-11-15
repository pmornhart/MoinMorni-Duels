package de.moinmorni.duels.world;

import java.util.Random;

/**
 * Der Turnierhof (Wartehalle): ein Burghof auf einer schwebenden Insel, Boden mit weiß-blauen Rauten und einem
 * Smaragd in der Mitte, Mauern mit Bannern in Smaragd, Gold und Weiß-Blau, eine Tribüne mit Zeltdach im Norden,
 * Schmiede und Brotzeit-Stand an den Seiten, Ecktürme mit Fahnen. Eigener Entwurf.
 */
public final class HallPlan {

    /** Innenhof reicht von -INNER bis +INNER. */
    public static final int INNER = 20;
    /** Spielfeld der Halle für „ist in der Halle“ (mit Rand). */
    public static final int REACH = 26;
    /** Startpunkt (relativ): vor der Tribüne, Blick nach Norden. */
    public static final double SPAWN_X = 0.5;
    public static final double SPAWN_Y = 1;
    public static final double SPAWN_Z = 12.5;
    public static final float SPAWN_YAW = 180f;

    private static final int WALL = 22;
    private static final int WALL_TOP = 7;

    private HallPlan() {
    }

    public static Plan build() {
        Plan p = new Plan();
        Random r = new Random(4711);
        floor(p, r);
        walls(p, r);
        towers(p);
        stands(p);
        booths(p);
        decoration(p, r);
        Decor.island(p, WALL + 4, 16, r, "dirt", 2);
        ledge(p, r);
        p.connect();
        return p;
    }

    /** Grüner Rand außen um die Mauern (oben auf der Insel), mit ein paar Blumen. */
    private static void ledge(Plan p, Random r) {
        int edge = WALL + 6;
        String[] flowers = {"short_grass", "short_grass", "short_grass", "dandelion", "poppy", "cornflower", "azure_bluet"};
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                if ((Math.abs(x) <= WALL && Math.abs(z) <= WALL) || p.get(x, -1, z) == null || p.get(x, 0, z) != null) {
                    continue;
                }
                p.set(x, -1, z, "grass_block[snowy=false]");
                if (r.nextInt(4) == 0) {
                    p.set(x, 0, z, flowers[r.nextInt(flowers.length)]);
                }
            }
        }
    }

    private static void floor(Plan p, Random r) {
        for (int x = -INNER; x <= INNER; x++) {
            for (int z = -INNER; z <= INNER; z++) {
                double d = Math.sqrt(x * x + z * z);
                String block;
                if (x == 0 && z == 0) {
                    block = "emerald_block";
                } else if (Math.abs(x) + Math.abs(z) == 1) {
                    block = "gold_block";
                } else if (d <= 6.5) {
                    block = Decor.lozenge(x, z, 2) ? "white_concrete" : "light_blue_concrete";
                } else if (d <= 7.6) {
                    block = "chiseled_stone_bricks";
                } else if (d <= 8.6) {
                    block = "polished_andesite";
                } else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    block = "polished_andesite";
                } else {
                    int roll = r.nextInt(100);
                    block = roll < 78 ? "stone_bricks" : roll < 87 ? "mossy_stone_bricks"
                            : roll < 95 ? "cracked_stone_bricks" : "andesite";
                }
                p.set(x, 0, z, block);
            }
        }
    }

    private static void walls(Plan p, Random r) {
        for (int x = -WALL; x <= WALL; x++) {
            for (int z = -WALL; z <= WALL; z++) {
                boolean ring = Math.abs(x) > INNER || Math.abs(z) > INNER;
                if (!ring) {
                    continue;
                }
                for (int y = 0; y <= WALL_TOP; y++) {
                    int roll = r.nextInt(100);
                    p.set(x, y, z, roll < 82 ? "stone_bricks" : roll < 92 ? "mossy_stone_bricks" : "cracked_stone_bricks");
                }
                boolean outer = Math.abs(x) == WALL || Math.abs(z) == WALL;
                if (outer && Math.floorMod(x + z, 2) == 0) {
                    p.set(x, WALL_TOP + 1, z, "stone_brick_wall");
                }
                for (int y = WALL_TOP + 1; y <= WALL_TOP + 4; y++) {
                    p.setIfEmpty(x, y, z, "barrier");
                }
            }
        }
        // Tor im Süden (geschlossen, mit Fallgitter)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, INNER + 1, "air");
                p.set(x, y, WALL, "iron_bars");
            }
            p.set(x, 5, INNER + 1, "chiseled_stone_bricks");
        }
        p.set(-3, 5, INNER + 1, "chiseled_stone_bricks");
        p.set(3, 5, INNER + 1, "chiseled_stone_bricks");
        // Hohe Rückwand hinter der Tribüne (für Banner über dem Zeltdach)
        for (int x = -15; x <= 15; x++) {
            for (int y = WALL_TOP + 1; y <= WALL_TOP + 3; y++) {
                p.set(x, y, -INNER - 1, "stone_bricks");
                p.set(x, y, -WALL, "stone_bricks");
            }
            if (Math.floorMod(x, 2) == 0) {
                p.set(x, WALL_TOP + 4, -WALL, "stone_brick_wall");
            }
            p.set(x, WALL_TOP + 4, -INNER - 1, "barrier");
        }
        // Banner an den Innenseiten
        int design = 0;
        for (int i = -18; i <= 18; i += 4) {
            if (Math.abs(i) > 3) {
                p.banner(i, 5, INNER, Decor.bannerBlock(design, true, "north"), Decor.bannerPatterns(design));
            }
            p.banner(INNER, 5, i, Decor.bannerBlock(design + 1, true, "west"), Decor.bannerPatterns(design + 1));
            p.banner(-INNER, 5, i, Decor.bannerBlock(design + 2, true, "east"), Decor.bannerPatterns(design + 2));
            design++;
        }
        design = 0;
        for (int x = -14; x <= 14; x += 4) {
            p.banner(x, WALL_TOP + 2, -INNER, Decor.bannerBlock(design, true, "south"), Decor.bannerPatterns(design));
            design++;
        }
        // Fackeln zwischen den Bannern
        for (int i = -16; i <= 16; i += 8) {
            p.set(INNER, 3, i, "wall_torch[facing=west]");
            p.set(-INNER, 3, i, "wall_torch[facing=east]");
            if (Math.abs(i) > 3) {
                p.set(i, 3, INNER, "wall_torch[facing=north]");
            }
        }
    }

    private static void towers(Plan p) {
        int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        int design = 0;
        for (int[] c : corners) {
            int cx = c[0] * WALL;
            int cz = c[1] * WALL;
            for (int x = cx - 3; x <= cx + 3; x++) {
                for (int z = cz - 3; z <= cz + 3; z++) {
                    for (int y = 0; y <= 11; y++) {
                        p.set(x, y, z, Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3 || y == 0 || y == 11
                                ? (y % 4 == 3 ? "polished_andesite" : "stone_bricks") : "air");
                    }
                }
            }
            // Schießscharten
            p.set(cx, 6, cz - 3, "air");
            p.set(cx, 6, cz + 3, "air");
            p.set(cx - 3, 6, cz, "air");
            p.set(cx + 3, 6, cz, "air");
            Decor.towerRoof(p, cx, 12, cz, 3, "dark_oak_stairs", "dark_oak_planks", design++);
        }
    }

    /** Tribüne mit drei Sitzreihen und weiß-blauem Zeltdach. */
    private static void stands(Plan p) {
        for (int x = -14; x <= 14; x++) {
            p.set(x, 1, -17, "spruce_stairs[facing=north,half=bottom,shape=straight]");
            p.set(x, 1, -18, "spruce_planks");
            p.set(x, 2, -18, "spruce_stairs[facing=north,half=bottom,shape=straight]");
            p.fill(x, 1, -19, x, 2, -19, "spruce_planks");
            p.set(x, 3, -19, "spruce_stairs[facing=north,half=bottom,shape=straight]");
            p.fill(x, 1, -20, x, 3, -20, "spruce_planks");
        }
        for (int side : new int[] {-15, 15}) {
            p.fill(side, 1, -20, side, 3, -17, "stripped_spruce_log[axis=z]");
        }
        for (int x : new int[] {-14, -7, 0, 7, 14}) {
            for (int y = 1; y <= 5; y++) {
                p.set(x, y, -16, "spruce_fence");
            }
        }
        for (int x = -15; x <= 15; x++) {
            String wool = Math.floorMod(Math.floorDiv(x, 2), 2) == 0 ? "white_wool" : "light_blue_wool";
            for (int z = -20; z <= -16; z++) {
                p.set(x, 6, z, wool);
            }
        }
        for (int x = -12; x <= 12; x += 6) {
            Decor.hangingLantern(p, x, 5, -18, 0);
        }
    }

    /** Schmiede (Osten) und Brotzeit-Stand (Westen). */
    private static void booths(Plan p) {
        for (int side : new int[] {-1, 1}) {
            int x0 = side * 17;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    p.set(x0 + dx, 0, dz, "spruce_planks");
                    p.set(x0 + dx, 4, dz, "spruce_slab[type=bottom,waterlogged=false]");
                }
            }
            for (int dz : new int[] {-4, 4}) {
                for (int dx : new int[] {-2, 2}) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(x0 + dx, y, dz, "spruce_log[axis=y]");
                    }
                }
            }
            for (int dz = -4; dz <= 4; dz++) {
                String wool = Math.floorMod(dz, 2) == 0 ? (side > 0 ? "green_wool" : "yellow_wool") : "white_wool";
                p.set(x0 - side * 3, 4, dz, wool);
            }
            Decor.hangingLantern(p, x0, 3, 0, 0);
            if (side > 0) {
                p.set(x0 + 1, 1, -2, "anvil[facing=north]");
                p.set(x0 + 1, 1, 0, "smithing_table");
                p.set(x0 + 1, 1, 2, "grindstone[face=floor,facing=north]");
                p.set(x0 + 2, 1, -1, "barrel[facing=up,open=false]");
                p.set(x0 + 2, 1, 1, "blast_furnace[facing=west,lit=false]");
                p.set(x0 - 1, 1, 3, "hay_block[axis=y]");
            } else {
                p.set(x0 - 1, 1, -2, "barrel[facing=up,open=false]");
                p.set(x0 - 1, 2, -2, "barrel[facing=up,open=false]");
                p.set(x0 - 1, 1, 0, "spruce_slab[type=top,waterlogged=false]");
                p.set(x0 - 1, 2, 0, "cake[bites=0]");
                p.set(x0 - 1, 1, 2, "spruce_slab[type=top,waterlogged=false]");
                p.set(x0 - 2, 1, -1, "hay_block[axis=y]");
                p.set(x0 - 2, 1, 1, "composter[level=0]");
            }
        }
    }

    private static void decoration(Plan p, Random r) {
        for (int[] c : new int[][] {{-10, -10}, {10, -10}, {-10, 10}, {10, 10}, {-5, 16}, {5, 16}}) {
            Decor.lanternPost(p, c[0], 1, c[1], "spruce_fence");
        }
        // Büsche in den Ecken
        for (int[] c : new int[][] {{-18, -18}, {17, -18}, {-18, 17}, {17, 17}}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    p.set(c[0] + dx, 1, c[1] + dz, r.nextBoolean() ? "flowering_azalea_leaves[persistent=true]"
                            : "azalea_leaves[persistent=true]");
                }
            }
            p.set(c[0], 2, c[1], "flowering_azalea_leaves[persistent=true]");
        }
        // Blumentöpfe an der Südmauer
        String[] pots = {"potted_red_tulip", "potted_cornflower", "potted_oxeye_daisy", "potted_azure_bluet"};
        int i = 0;
        for (int x = -18; x <= 18; x += 3) {
            if (Math.abs(x) > 4) {
                p.set(x, 1, INNER, pots[i++ % pots.length]);
            }
        }
    }
}
