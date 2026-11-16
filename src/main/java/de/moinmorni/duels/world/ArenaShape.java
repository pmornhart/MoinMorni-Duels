package de.moinmorni.duels.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Lage einer Arena-Art, relativ zum Mittelpunkt (Boden = y 0): Startplätze je Seite und für „Jeder gegen jeden“,
 * Zuschauerplatz, Grenzen der Arena und ab welcher Tiefe man herausgefallen ist. Ohne Minecraft (gut zu prüfen).
 *
 * @param starts     Startplätze
 * @param view       Zuschauerplatz: x, y, z, Blickrichtung (yaw), Neigung (pitch)
 * @param halfX      Arena reicht von -halfX bis +halfX
 * @param halfZ      … und von -halfZ bis +halfZ
 * @param top        oberste Höhe der Arena
 * @param bottom     unterste Höhe der Arena (negativ)
 * @param fallDepth  so tief unter der Standhöhe ist man herausgefallen
 */
public record ArenaShape(Starts starts, double[] view, int halfX, int halfZ, int top, int bottom, double fallDepth) {

    /** Höchstens so viele Startplätze im Kreis („Jeder gegen jeden“). */
    public static final int RING = 8;

    /** Startplätze: je Seite (0 = Norden, 1 = Süden) und Teamgröße; dazu ein Kreis für „Jeder gegen jeden“. */
    public interface Starts {
        /** (x, z) für {@code teamSize} Leute auf einer Seite. */
        List<int[]> side(int side, int teamSize);

        /** (x, z) für {@code count} Plätze im Kreis. */
        List<int[]> ring(int count);
    }

    /**
     * Reihen gegenüber: Seite 0 bei z = -front, Seite 1 bei z = +front, nebeneinander im Abstand {@code spacing},
     * höchstens {@code perRow} je Reihe, weitere Reihen dahinter ({@code rowStep}); Kreis mit Radius {@code radius}.
     */
    public static Starts rows(int front, int spacing, int perRow, int rowStep, double radius) {
        return new Starts() {
            @Override
            public List<int[]> side(int side, int teamSize) {
                List<int[]> out = new ArrayList<>();
                int sign = side == 0 ? -1 : 1;
                int left = Math.max(1, teamSize);
                int row = 0;
                while (left > 0) {
                    int k = Math.min(perRow, left);
                    for (int i = 0; i < k; i++) {
                        int x = (int) Math.round((i - (k - 1) / 2.0) * spacing);
                        out.add(new int[] {x, sign * (front + row * rowStep)});
                    }
                    left -= k;
                    row++;
                }
                return out;
            }

            @Override
            public List<int[]> ring(int count) {
                return circle(radius, count);
            }
        };
    }

    /**
     * Feste Felder (Irrgarten): Seite 0 und Seite 1 je eine Liste von Plätzen (werden der Reihe nach vergeben,
     * bei mehr Leuten wieder von vorn), für „Jeder gegen jeden“ eigene Plätze.
     */
    public static Starts spots(List<int[]> sideA, List<int[]> sideB, List<int[]> ringSpots) {
        return new Starts() {
            @Override
            public List<int[]> side(int side, int teamSize) {
                List<int[]> from = side == 0 ? sideA : sideB;
                List<int[]> out = new ArrayList<>();
                for (int i = 0; i < Math.max(1, teamSize); i++) {
                    out.add(from.get(i % from.size()));
                }
                return out;
            }

            @Override
            public List<int[]> ring(int count) {
                List<int[]> out = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    out.add(ringSpots.get(i % ringSpots.size()));
                }
                return out;
            }
        };
    }

    /** Plätze gleichmäßig im Kreis, der erste im Norden. */
    public static List<int[]> circle(double radius, int count) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / count;
            out.add(new int[] {(int) Math.round(radius * Math.cos(a)), (int) Math.round(radius * Math.sin(a))});
        }
        return out;
    }

    /** Blickrichtung (yaw) von (x, z) zur Mitte. */
    public static float yawToCentre(double x, double z) {
        if (Math.abs(x) < 1.0E-6 && Math.abs(z) < 1.0E-6) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(x, -z));
    }
}
