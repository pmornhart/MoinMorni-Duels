package net.morni.duels.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.morni.duels.kit.Kit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Ranglisten je Kampfart (nach Elo) und ein Namensverzeichnis aller, die schon gekämpft haben – damit /top und
 * /stats auch ohne Netzwerk gehen. Liegt in {@code rangliste.yml}.
 */
public final class Leaderboard {

    /** Eine Zeile der Rangliste. */
    public record Row(UUID uuid, String name, int elo, int wins, int rankedGames) {
    }

    private static final Comparator<Row> ORDER = Comparator.comparingInt(Row::elo).reversed()
            .thenComparing(Comparator.comparingInt(Row::wins).reversed())
            .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));

    private final Logger log;
    private final Path file;
    private final Map<Kit, Map<UUID, Row>> rows = new EnumMap<>(Kit.class);
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    public Leaderboard(Logger log, Path dataFolder) {
        this.log = log;
        this.file = dataFolder.resolve("rangliste.yml");
        for (Kit kit : Kit.values()) {
            rows.put(kit, new HashMap<>());
        }
    }

    public void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            log.warning("rangliste.yml ließ sich nicht lesen: " + e.getMessage());
            return;
        }
        ConfigurationSection players = y.getConfigurationSection("spieler");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection p = players.getConfigurationSection(key);
            if (p == null) {
                continue;
            }
            String name = p.getString("name", "?");
            remember(uuid, name);
            for (Kit kit : Kit.values()) {
                ConfigurationSection k = p.getConfigurationSection(kit.id());
                if (k != null && k.getInt("gewertet") > 0) {
                    rows.get(kit).put(uuid, new Row(uuid, name, k.getInt("elo"), k.getInt("siege"),
                            k.getInt("gewertet")));
                }
            }
        }
    }

    /** Als YAML (zum Speichern im Hintergrund). */
    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        names.forEach((uuid, name) -> y.set("spieler." + uuid + ".name", name));
        rows.forEach((kit, map) -> map.values().forEach(r -> {
            String p = "spieler." + r.uuid() + "." + kit.id() + ".";
            y.set(p + "elo", r.elo());
            y.set(p + "siege", r.wins());
            y.set(p + "gewertet", r.rankedGames());
        }));
        return y.saveToString();
    }

    public Path file() {
        return file;
    }

    /** Werte eines Spielers übernehmen (nach einem Kampf). */
    public void update(PlayerData d) {
        remember(d.uuid(), d.name());
        for (Kit kit : Kit.values()) {
            KitStats s = d.kit(kit);
            if (s.rankedGames > 0) {
                rows.get(kit).put(d.uuid(), new Row(d.uuid(), d.name(), s.elo, s.wins, s.rankedGames));
            }
        }
    }

    public void remember(UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String old = names.put(uuid, name);
        if (old != null && !old.equalsIgnoreCase(name)) {
            byName.remove(old.toLowerCase(Locale.ROOT));
        }
        byName.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    public Optional<UUID> byName(String name) {
        return Optional.ofNullable(name == null ? null : byName.get(name.toLowerCase(Locale.ROOT)));
    }

    /** Die Besten einer Kampfart (nur wer gewertet gespielt hat). */
    public List<Row> top(Kit kit, int limit) {
        List<Row> list = new ArrayList<>(rows.get(kit).values());
        list.sort(ORDER);
        return list.subList(0, Math.min(limit, list.size()));
    }

    /** Platz in der Rangliste (1 = Erster), 0 = noch nicht drin. */
    public int position(Kit kit, UUID uuid) {
        Row own = rows.get(kit).get(uuid);
        if (own == null) {
            return 0;
        }
        int better = 0;
        for (Row r : rows.get(kit).values()) {
            if (ORDER.compare(r, own) < 0) {
                better++;
            }
        }
        return better + 1;
    }
}
