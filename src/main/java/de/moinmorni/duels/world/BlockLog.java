package de.moinmorni.duels.world;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Merkt sich, wie Blöcke einer Arena vor einer Änderung aussahen, und stellt sie nach dem Kampf wieder her.
 * Spieler können in Kämpfen nichts bauen oder abbauen – das hier ist das Netz für alles andere.
 */
public final class BlockLog {

    private record Pos(int x, int y, int z) {
    }

    private final Map<Pos, BlockData> original = new HashMap<>();

    public void remember(Block b) {
        original.putIfAbsent(new Pos(b.getX(), b.getY(), b.getZ()), b.getBlockData().clone());
    }

    public void remember(Block b, BlockData before) {
        original.putIfAbsent(new Pos(b.getX(), b.getY(), b.getZ()), before.clone());
    }

    public int size() {
        return original.size();
    }

    public void restore(World world) {
        original.forEach((k, d) -> world.getBlockAt(k.x(), k.y(), k.z()).setBlockData(d, false));
        original.clear();
    }
}
