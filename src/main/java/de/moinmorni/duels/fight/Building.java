package de.moinmorni.duels.fight;

import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.world.Arena;
import de.moinmorni.duels.world.ArenaType;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Bauen und Schaufeln im Kampf: In UHC, Bau-Kampf und Brücke darf man Blöcke setzen und nur selbst gesetzte wieder
 * abbauen (die Arena bleibt heil), in UHC auch Wasser und Lava ausgießen; beim Spleef lässt sich der Schnee des
 * Spielfelds wegschaufeln. Alles, was sich ändert, merkt sich die Arena – nach dem Kampf (und zwischen zwei Runden)
 * steht sie wieder genau wie vorher.
 */
public final class Building {

    private final DuelsPlugin plugin;

    public Building(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Der laufende Kampf des Spielers, wenn er gerade bauen könnte. */
    private Optional<Fight> live(Player p) {
        return plugin.fights().of(p.getUniqueId())
                .filter(f -> f.live() && f.isAlive(p.getUniqueId()));
    }

    private static int[] rel(Fight f, Block b) {
        Arena a = f.arena();
        return new int[] {b.getX() - a.ox(), b.getY() - a.oy(), b.getZ() - a.oz()};
    }

    /** Darf hier gebaut werden (Arena-Art, Höhe, Tore frei)? */
    private static boolean buildable(Fight f, Block b) {
        if (!f.arena().contains(b.getLocation().add(0.5, 0.5, 0.5))) {
            return false;
        }
        int[] r = rel(f, b);
        return f.arena().type() == ArenaType.KLAMMBRUECKE ? Zones.bridgeBuildable(r[0], r[1], r[2])
                : f.arena().type().normal() && Zones.arenaBuildable(r[0], r[1], r[2]);
    }

    public boolean mayPlace(Player p, Block b) {
        return live(p).filter(f -> f.kit().building() && buildable(f, b)).isPresent();
    }

    public boolean mayBreak(Player p, Block b) {
        Optional<Fight> of = live(p);
        if (of.isEmpty()) {
            return false;
        }
        Fight f = of.get();
        if (f.kit().building()) {
            return f.placed(b.getX(), b.getY(), b.getZ());
        }
        if (f.kit() == Kit.SPLEEF && f.arena().type() == ArenaType.SCHNEEFELD && b.getType() == Material.SNOW_BLOCK) {
            int[] r = rel(f, b);
            return Zones.spleefBreakable(r[0], r[1], r[2]);
        }
        return false;
    }

    /** Eimer ausgießen (UHC): Wasser und Lava. */
    public boolean mayEmpty(Player p, Block b) {
        return live(p).filter(f -> f.kit().liquids() && buildable(f, b)).isPresent();
    }

    /** Eimer füllen: nur, was man in diesem Kampf selbst ausgegossen hat. */
    public boolean mayFill(Player p, Block b) {
        return live(p).filter(f -> f.kit().liquids() && f.placed(b.getX(), b.getY(), b.getZ())).isPresent();
    }

    /** Gesetzt (auch Wasser/Lava aus dem Eimer): darf wieder weg. */
    public void placed(Player p, Block b) {
        plugin.fights().of(p.getUniqueId()).ifPresent(f -> f.placedAdd(b.getX(), b.getY(), b.getZ()));
    }

    /** Abgebaut oder Eimer gefüllt. */
    public void removed(Player p, Block b) {
        plugin.fights().of(p.getUniqueId()).ifPresent(f -> f.placedRemove(b.getX(), b.getY(), b.getZ()));
    }

    /** Der Kampf mit Wasser und Lava, in dessen Arena dieser Block liegt (dort darf es fließen). */
    public Optional<Fight> liquids(Block b) {
        return plugin.arenas().at(b.getLocation()).filter(Arena::busy)
                .flatMap(a -> plugin.fights().in(a))
                .filter(f -> f.kit().liquids() && f.phase() != Fight.Phase.ENDED);
    }
}
