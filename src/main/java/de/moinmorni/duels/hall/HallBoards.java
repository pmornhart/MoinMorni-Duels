package de.moinmorni.duels.hall;

import de.moinmorni.duels.DuelsPlugin;
import de.moinmorni.duels.Texts;
import de.moinmorni.duels.data.Leaderboard;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.world.Arenas;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Schilder in der Wartehalle (schwebende Schrift): Begrüßung und drei Ranglisten. Jede Schrift gibt es auf Deutsch
 * und Englisch – jeder sieht nur die in seiner Sprache.
 */
public final class HallBoards {

    private static final String TAG = "moinmorni_duels";
    private static final String[] LANGS = {"de", "en"};
    private static final int TOP = 5;
    private static final int CHUNK_RADIUS = 2;

    private final DuelsPlugin plugin;
    private final World world;
    private final Map<String, List<TextDisplay>> byLang = new HashMap<>();
    private final Map<String, TextDisplay> tops = new HashMap<>();
    private final List<Chunk> held = new ArrayList<>();

    public HallBoards(DuelsPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    /** Hallen-Chunks festhalten und Schilder aufstellen (alte vorher weg). */
    public void spawn() {
        for (int cx = -CHUNK_RADIUS; cx < CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz < CHUNK_RADIUS; cz++) {
                Chunk c = world.getChunkAt(cx, cz);
                c.addPluginChunkTicket(plugin);
                held.add(c);
                for (Entity e : c.getEntities()) {
                    if (e.getScoreboardTags().contains(TAG)) {
                        e.remove();
                    }
                }
            }
        }
        byLang.clear();
        tops.clear();
        for (String lang : LANGS) {
            List<TextDisplay> list = new ArrayList<>();
            list.add(display(at(0.5, 3.4, 8.5), Texts.MM.deserialize(Texts.raw(lang, "board.welcome")), 1.0f));
            Kit[] kits = Kit.values();
            double[] xs = {-9.5, 0.5, 10.5};
            for (int i = 0; i < kits.length; i++) {
                TextDisplay d = display(at(xs[i], 7.6, -17.5), Component.empty(), 1.1f);
                tops.put(lang + ":" + kits[i].id(), d);
                list.add(d);
            }
            byLang.put(lang, list);
        }
        update();
        plugin.getServer().getOnlinePlayers().forEach(this::show);
    }

    private Location at(double x, double y, double z) {
        return new Location(world, x, Arenas.FLOOR_Y + y, z);
    }

    private TextDisplay display(Location l, Component text, float scale) {
        return world.spawn(l, TextDisplay.class, d -> {
            d.text(text);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setLineWidth(260);
            d.setBackgroundColor(Color.fromARGB(110, 12, 24, 18));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }

    /** Ranglisten neu schreiben. */
    public void update() {
        for (String lang : LANGS) {
            for (Kit kit : Kit.values()) {
                TextDisplay d = tops.get(lang + ":" + kit.id());
                if (d == null || !d.isValid()) {
                    continue;
                }
                Component text = Texts.MM.deserialize(Texts.raw(lang, "board.top"),
                        Placeholder.unparsed("kit", Texts.raw(lang, "kit." + kit.id())));
                List<Leaderboard.Row> top = plugin.board().top(kit, TOP);
                if (top.isEmpty()) {
                    text = text.append(Component.newline()).append(Texts.MM.deserialize(Texts.raw(lang, "board.top.empty")));
                }
                for (int i = 0; i < top.size(); i++) {
                    Leaderboard.Row r = top.get(i);
                    text = text.append(Component.newline()).append(Texts.MM.deserialize(Texts.raw(lang, "board.top.line"),
                            Placeholder.unparsed("pos", String.valueOf(i + 1)), Placeholder.unparsed("name", r.name()),
                            Placeholder.unparsed("elo", String.valueOf(r.elo()))));
                }
                d.text(text);
            }
        }
    }

    /** Spieler sieht die Schilder in seiner Sprache. */
    public void show(Player p) {
        String lang = plugin.lang(p);
        byLang.forEach((l, list) -> list.forEach(d -> {
            if (!d.isValid()) {
                return;
            }
            if (l.equals(lang)) {
                p.showEntity(plugin, d);
            } else {
                p.hideEntity(plugin, d);
            }
        }));
    }

    public void remove() {
        byLang.values().forEach(list -> list.forEach(Entity::remove));
        byLang.clear();
        tops.clear();
        held.forEach(c -> c.removePluginChunkTicket(plugin));
        held.clear();
    }
}
