package net.morni.duels.world;

import java.util.Random;

/**
 * Baupläne der Arenen. Spielfläche je 49 × 49 Blöcke ({@code -HALF … HALF}), Startpunkte bei z = ∓16, rundherum
 * eine unsichtbare Wand. Jede Arena schwebt auf ihrer eigenen Insel.
 */
public final class ArenaPlans {

    /** Spielfläche reicht von -HALF bis +HALF. */
    public static final int HALF = 24;
    /** Startreihe der beiden Seiten (z). */
    public static final int SPAWN_Z = 16;
    /** Höhe der unsichtbaren Wand. */
    public static final int TOP = 20;

    private ArenaPlans() {
    }

    public static Plan build(ArenaType type) {
        Plan p = new Plan();
        Random r = new Random(1000L + type.ordinal() * 7919L);
        switch (type) {
            case ALMWIESE -> almwiese(p, r);
            case BURGHOF -> burghof(p, r);
            case BERGWERK -> bergwerk(p, r);
            case WIRTSHAUSGARTEN -> wirtshausgarten(p, r);
        }
        Decor.barrierBox(p, HALF, TOP, type != ArenaType.BERGWERK);
        p.connect();
        return p;
    }

    /** Liegt (x, z) bei einem Startpunkt? Dort bleibt der Boden frei und eben. */
    private static boolean spawnZone(int x, int z) {
        return Math.abs(x) <= 5 && Math.abs(Math.abs(z) - SPAWN_Z) <= 3;
    }

    // ---------------------------------------------------------------- Almwiese

    private static void almwiese(Plan p, Random r) {
        int edge = HALF + 5;
        String[] flowers = {"dandelion", "poppy", "cornflower", "oxeye_daisy", "azure_bluet", "allium"};
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                int h = hill(x, z);
                if (spawnZone(x, z) || x * x + z * z < 30) {
                    h = 0;
                }
                p.set(x, 0, z, h > 0 ? "dirt" : "grass_block[snowy=false]");
                for (int y = 1; y <= h; y++) {
                    p.set(x, y, z, y == h ? "grass_block[snowy=false]" : "dirt");
                }
                if (Math.abs(x) <= HALF && Math.abs(z) <= HALF && !spawnZone(x, z)) {
                    int roll = r.nextInt(100);
                    if (roll < 14) {
                        p.set(x, h + 1, z, "short_grass");
                    } else if (roll < 21) {
                        p.set(x, h + 1, z, flowers[r.nextInt(flowers.length)]);
                    }
                }
            }
        }
        // Zaun rundherum
        for (int i = -HALF; i <= HALF; i++) {
            fenceOnGround(p, i, -HALF);
            fenceOnGround(p, i, HALF);
            fenceOnGround(p, -HALF, i);
            fenceOnGround(p, HALF, i);
        }
        // Deckung: Heuhaufen und Felsbrocken
        for (int side : new int[] {-1, 1}) {
            int hx = side * 12;
            p.fill(hx, 1, -1, hx + side, 1, 0, "hay_block[axis=y]");
            p.set(hx, 2, -1, "hay_block[axis=x]");
            boulder(p, side * 6, side * 6, r);
            boulder(p, -side * 7, side * 5, r);
        }
        // Marterl (Wegkreuz) am Rand
        p.set(-20, 1, 0, "spruce_log[axis=y]");
        p.set(-20, 2, 0, "spruce_log[axis=y]");
        p.set(-20, 3, 0, "spruce_planks");
        p.set(-20, 4, 0, "spruce_slab[type=bottom,waterlogged=false]");
        p.set(-19, 3, 0, "spruce_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
        p.set(-19, 2, 0, "potted_cornflower");
        // Fichten draußen
        for (int i = 0; i < 18; i++) {
            int x = (r.nextBoolean() ? 1 : -1) * (HALF + 3 + r.nextInt(3));
            int z = r.nextInt(2 * HALF + 1) - HALF;
            if (r.nextBoolean()) {
                int t = x;
                x = z;
                z = t;
            }
            Decor.spruce(p, x, 1 + Math.max(0, hill(x, z)), z, 6 + r.nextInt(4), r);
        }
        Decor.island(p, HALF + 6, 18, r, "dirt", 3);
        // Berge im Hintergrund
        Decor.mountain(p, 0, -64, -14, 28, 46, r);
        Decor.mountain(p, -38, -56, -12, 18, 30, r);
        Decor.mountain(p, 40, -58, -12, 20, 34, r);
        Decor.mountain(p, 60, 10, -14, 18, 26, r);
    }

    /** Sanfte Hügel: 0 bis 2 Blöcke hoch. */
    private static int hill(int x, int z) {
        double v = 1.3 * Math.sin(x / 5.5 + 1.0) * Math.cos(z / 6.5) + 0.8 * Math.sin((x + z) / 9.0);
        return (int) Math.max(0, Math.min(2, Math.round(v)));
    }

    private static void fenceOnGround(Plan p, int x, int z) {
        int y = 1;
        while (y < 6 && p.solid(x, y, z)) {
            y++;
        }
        p.set(x, y, z, "spruce_fence");
        p.clear(x, y + 1, z);
    }

    private static void boulder(Plan p, int cx, int cz, Random r) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 2; y++) {
                    if (y == 2 && Math.abs(dx) + Math.abs(dz) == 2) {
                        continue;
                    }
                    p.set(cx + dx, y, cz + dz, Decor.pick(r, "mossy_cobblestone", "cobblestone", "stone", "andesite"));
                }
            }
        }
        p.set(cx, 3, cz, "moss_carpet");
    }

    // ---------------------------------------------------------------- Burghof

    private static void burghof(Plan p, Random r) {
        int wall = HALF + 1;
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                String block;
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    block = "polished_andesite";
                } else if (r.nextInt(14) == 0) {
                    block = Decor.pick(r, "gravel", "cobblestone", "mossy_cobblestone");
                } else {
                    int roll = r.nextInt(100);
                    block = roll < 70 ? "stone_bricks" : roll < 84 ? "mossy_stone_bricks" : "cracked_stone_bricks";
                }
                p.set(x, 0, z, block);
            }
        }
        // Mauern (3 dick, 10 hoch) mit Zinnen
        for (int x = -wall - 2; x <= wall + 2; x++) {
            for (int z = -wall - 2; z <= wall + 2; z++) {
                if (Math.abs(x) <= HALF && Math.abs(z) <= HALF) {
                    continue;
                }
                for (int y = 0; y <= 10; y++) {
                    int roll = r.nextInt(100);
                    p.set(x, y, z, roll < 80 ? "stone_bricks" : roll < 90 ? "mossy_stone_bricks" : "cracked_stone_bricks");
                }
                boolean outer = Math.abs(x) == wall + 2 || Math.abs(z) == wall + 2;
                if (outer && Math.floorMod(x + z, 2) == 0) {
                    p.set(x, 11, z, "stone_brick_wall");
                }
            }
        }
        // Banner und Fackeln innen
        int design = 0;
        for (int i = -20; i <= 20; i += 5) {
            p.banner(i, 7, -HALF, Decor.bannerBlock(design, true, "south"), Decor.bannerPatterns(design));
            p.banner(i, 7, HALF, Decor.bannerBlock(design + 1, true, "north"), Decor.bannerPatterns(design + 1));
            p.banner(-HALF, 7, i, Decor.bannerBlock(design + 2, true, "east"), Decor.bannerPatterns(design + 2));
            p.banner(HALF, 7, i, Decor.bannerBlock(design, true, "west"), Decor.bannerPatterns(design));
            design++;
        }
        for (int i = -22; i <= 22; i += 5) {
            p.set(i, 4, -HALF, "wall_torch[facing=south]");
            p.set(i, 4, HALF, "wall_torch[facing=north]");
            p.set(-HALF, 4, i, "wall_torch[facing=east]");
            p.set(HALF, 4, i, "wall_torch[facing=west]");
        }
        // Ecktürme
        int t = wall + 1;
        int[][] corners = {{-t, -t}, {t, -t}, {-t, t}, {t, t}};
        design = 0;
        for (int[] c : corners) {
            for (int x = c[0] - 4; x <= c[0] + 4; x++) {
                for (int z = c[1] - 4; z <= c[1] + 4; z++) {
                    for (int y = 0; y <= 15; y++) {
                        p.set(x, y, z, y % 5 == 4 ? "polished_andesite" : "stone_bricks");
                    }
                }
            }
            Decor.towerRoof(p, c[0], 16, c[1], 4, "dark_oak_stairs", "dark_oak_planks", design++);
        }
        // Torhaus im Süden (geschlossen)
        for (int x = -3; x <= 3; x++) {
            for (int y = 1; y <= 5; y++) {
                if (Math.abs(x) <= 2 && y <= 4) {
                    p.set(x, y, HALF + 1, "air");
                    p.set(x, y, HALF + 2, "air");
                    p.set(x, y, HALF + 3, "iron_bars");
                }
            }
        }
        // Brunnen in der Mitte (ohne echtes Wasser)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    p.set(0, 0, 0, "light_blue_stained_glass");
                } else {
                    p.set(dx, 1, dz, "chiseled_stone_bricks");
                }
            }
        }
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            p.set(c[0], 2, c[1], "spruce_fence");
            p.set(c[0], 3, c[1], "spruce_fence");
        }
        p.fill(-1, 4, -1, 1, 4, 1, "spruce_slab[type=bottom,waterlogged=false]");
        p.set(0, 3, 0, "lantern[hanging=true]");
        // Übungspuppen, Fässer, Kisten
        for (int side : new int[] {-1, 1}) {
            dummy(p, side * 18, -8);
            dummy(p, side * 18, 8);
            p.set(side * 9, 1, -9, "barrel[facing=up,open=false]");
            p.set(side * 9, 2, -9, "barrel[facing=up,open=false]");
            p.set(side * 10, 1, -9, "barrel[facing=up,open=false]");
            p.set(side * 9, 1, 9, "spruce_planks");
            p.set(side * 10, 1, 9, "spruce_planks");
            p.set(side * 9, 2, 9, "spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
            p.set(side * 21, 1, 0, "anvil[facing=east]");
            p.set(side * 21, 1, 2, "grindstone[face=floor,facing=east]");
        }
        Decor.island(p, wall + 6, 15, r, null, 0);
    }

    private static void dummy(Plan p, int x, int z) {
        p.set(x, 1, z, "spruce_fence");
        p.set(x, 2, z, "hay_block[axis=y]");
        p.set(x, 3, z, "carved_pumpkin[facing=" + (x < 0 ? "east" : "west") + "]");
    }

    // ---------------------------------------------------------------- Bergwerk

    private static void bergwerk(Plan p, Random r) {
        int wall = HALF + 1;
        int ceiling = 11;
        for (int x = -wall - 2; x <= wall + 2; x++) {
            for (int z = -wall - 2; z <= wall + 2; z++) {
                boolean inside = Math.abs(x) <= HALF && Math.abs(z) <= HALF;
                if (inside) {
                    p.set(x, 0, z, r.nextInt(5) == 0 ? Decor.pick(r, "gravel", "andesite", "tuff", "coarse_dirt")
                            : "stone");
                    for (int y = ceiling; y <= ceiling + 2; y++) {
                        p.set(x, y, z, oreRock(r));
                    }
                    if (r.nextInt(70) == 0 && !spawnZone(x, z)) {
                        p.set(x, ceiling - 1, z, "pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]");
                    }
                } else {
                    for (int y = 0; y <= ceiling + 2; y++) {
                        p.set(x, y, z, oreRock(r));
                    }
                }
            }
        }
        // Stützen mit Balken und Grubenlampen
        for (int z : new int[] {-12, 0, 12}) {
            for (int x : new int[] {-22, -10, 10, 22}) {
                for (int y = 1; y < ceiling - 1; y++) {
                    p.set(x, y, z, "spruce_log[axis=y]");
                }
            }
            for (int x = -HALF; x <= HALF; x++) {
                p.set(x, ceiling - 1, z, "stripped_spruce_log[axis=x]");
            }
            for (int x = -20; x <= 20; x += 5) {
                p.set(x, ceiling - 2, z, "lantern[hanging=true]");
            }
        }
        for (int x = -20; x <= 20; x += 8) {
            for (int z = -20; z <= 20; z += 8) {
                if (Math.abs(z) % 12 != 0) {
                    Decor.hangingLantern(p, x, ceiling - 1, z, 1);
                }
            }
        }
        // Gleise an den Seiten
        for (int z = -HALF + 1; z <= HALF - 1; z++) {
            p.set(-19, 1, z, "rail[shape=north_south,waterlogged=false]");
            p.set(19, 1, z, "rail[shape=north_south,waterlogged=false]");
        }
        // Felspfeiler (Deckung) und Erzhaufen
        for (int[] c : new int[][] {{-7, -6}, {7, 6}, {-6, 7}, {6, -7}}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int y = 1; y < ceiling; y++) {
                        p.set(c[0] + dx, y, c[1] + dz, oreRock(r));
                    }
                }
            }
        }
        for (int[] c : new int[][] {{-14, -3}, {14, 3}, {0, -9}, {0, 9}}) {
            p.set(c[0], 1, c[1], Decor.pick(r, "raw_iron_block", "coal_block", "raw_gold_block"));
            p.set(c[0] + 1, 1, c[1], "dripstone_block");
            p.set(c[0] + 1, 2, c[1], "pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]");
        }
        Decor.island(p, wall + 4, 12, r, null, 0);
    }

    private static String oreRock(Random r) {
        int roll = r.nextInt(1000);
        if (roll < 40) {
            return "coal_ore";
        } else if (roll < 65) {
            return "iron_ore";
        } else if (roll < 85) {
            return "copper_ore";
        } else if (roll < 95) {
            return "gold_ore";
        } else if (roll < 101) {
            return "emerald_ore";
        } else if (roll < 300) {
            return Decor.pick(r, "andesite", "tuff", "deepslate", "cobblestone");
        }
        return "stone";
    }

    // ---------------------------------------------------------------- Wirtshausgarten

    private static void wirtshausgarten(Plan p, Random r) {
        int edge = HALF + 6;
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                boolean inside = Math.abs(x) <= HALF - 3 && Math.abs(z) <= HALF - 3;
                String block = inside
                        ? (r.nextInt(7) == 0 ? "coarse_dirt" : "gravel")
                        : "grass_block[snowy=false]";
                p.set(x, 0, z, block);
            }
        }
        // Hecke rundherum
        for (int i = -HALF - 1; i <= HALF + 1; i++) {
            for (int y = 1; y <= 2; y++) {
                String leaves = r.nextInt(5) == 0 ? "flowering_azalea_leaves[persistent=true]" : "azalea_leaves[persistent=true]";
                p.set(i, y, -HALF - 1, leaves);
                p.set(i, y, HALF + 1, leaves);
                p.set(-HALF - 1, y, i, leaves);
                p.set(HALF + 1, y, i, leaves);
            }
        }
        // Kastanienbäume
        for (int[] c : new int[][] {{-11, -9}, {11, -9}, {-11, 9}, {11, 9}}) {
            Decor.broadTree(p, c[0], 1, c[1], "oak_log", "oak_leaves", 5, 4, r);
        }
        // Biertische mit Bänken
        for (int side : new int[] {-1, 1}) {
            for (int[] range : new int[][] {{-12, -8}, {8, 12}}) {
                for (int z = range[0]; z <= range[1]; z++) {
                    int x = side * 18;
                    p.set(x, 1, z, "spruce_slab[type=top,waterlogged=false]");
                    p.set(x - 1, 1, z, "spruce_slab[type=bottom,waterlogged=false]");
                    p.set(x + 1, 1, z, "spruce_slab[type=bottom,waterlogged=false]");
                }
                p.set(side * 18, 2, range[0] + 2, "decorated_pot[facing=north,cracked=false,waterlogged=false]");
            }
            Decor.lanternPost(p, side * 22, 1, -22, "spruce_fence");
            Decor.lanternPost(p, side * 22, 1, 22, "spruce_fence");
            Decor.lanternPost(p, side * 22, 1, 0, "spruce_fence");
        }
        // Fass-Ausschank in der Mitte
        p.set(0, 1, 0, "barrel[facing=up,open=false]");
        p.set(1, 1, 0, "barrel[facing=up,open=false]");
        p.set(0, 2, 0, "barrel[facing=east,open=false]");
        wirtshaus(p);
        maibaum(p, 0, HALF + 4);
        Decor.island(p, edge, 14, r, "dirt", 3);
    }

    /** Wirtshaus hinter der Hecke im Norden: weiße Wände, dunkles Fachwerk, Fenster mit Läden, Satteldach. */
    private static void wirtshaus(Plan p) {
        int x1 = -12;
        int x2 = 12;
        int zFront = -HALF - 3;
        int zBack = zFront - 8;
        int height = 7;
        for (int x = x1; x <= x2; x++) {
            for (int z = zBack; z <= zFront; z++) {
                p.set(x, 0, z, "stone_bricks");
                boolean wallX = x == x1 || x == x2;
                boolean wallZ = z == zBack || z == zFront;
                if (!wallX && !wallZ) {
                    continue;
                }
                for (int y = 1; y <= height; y++) {
                    boolean frame = (wallX && wallZ) || y == 4 || y == height
                            || (wallZ && Math.floorMod(x - x1, 4) == 0) || (wallX && Math.floorMod(z - zBack, 4) == 0);
                    p.set(x, y, z, frame ? "dark_oak_log[axis=y]" : "white_concrete");
                }
            }
        }
        // Fenster vorne mit Läden und Blumen
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            if (Math.abs(x) <= 1) {
                continue;
            }
            for (int y : new int[] {2, 5}) {
                p.set(x, y, zFront, "glass_pane");
                p.set(x - 1, y, zFront + 1, "spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
                p.set(x + 1, y, zFront + 1, "spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
            }
            p.set(x, 1, zFront + 1, "potted_red_tulip");
        }
        // Tür
        p.set(0, 1, zFront, "spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]");
        p.set(0, 2, zFront, "spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]");
        p.set(0, 3, zFront + 1, "lantern[hanging=false]");
        p.set(0, 3, zFront, "dark_oak_log[axis=y]");
        // Satteldach (First in Ost-West-Richtung)
        int mid = (zFront + zBack) / 2;
        int layers = (zFront - zBack) / 2 + 2;
        for (int i = 0; i < layers; i++) {
            int y = height + 1 + i;
            int south = zFront + 1 - i;
            int north = zBack - 1 + i;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (south > north) {
                    p.set(x, y, south, "dark_oak_stairs[facing=north,half=bottom,shape=straight]");
                    p.set(x, y, north, "dark_oak_stairs[facing=south,half=bottom,shape=straight]");
                } else {
                    p.set(x, y, mid, "dark_oak_slab[type=bottom,waterlogged=false]");
                }
            }
            for (int z = north + 1; z < south; z++) {
                p.set(x1, y, z, "white_concrete");
                p.set(x2, y, z, "white_concrete");
            }
        }
        p.set(x1 + 3, height + layers, mid, "bricks");
        p.set(x1 + 3, height + layers + 1, mid, "bricks");
    }

    /** Maibaum: weiß-blau gestreift, oben ein Kranz und eine Fahne. */
    private static void maibaum(Plan p, int x, int z) {
        int height = 22;
        for (int y = 1; y <= height; y++) {
            p.set(x, y, z, Math.floorMod(y / 2, 2) == 0 ? "white_concrete" : "light_blue_concrete");
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int d = Math.abs(dx) + Math.abs(dz);
                if (d >= 2 && d <= 3) {
                    p.set(x + dx, height - 3, z + dz, "spruce_leaves[persistent=true]");
                }
            }
        }
        p.set(x, height + 1, z, "spruce_fence");
        p.banner(x, height + 2, z, Decor.bannerBlock(2, false, "8"), Decor.bannerPatterns(2));
    }
}
