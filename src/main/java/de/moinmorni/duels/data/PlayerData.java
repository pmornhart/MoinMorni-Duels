package de.moinmorni.duels.data;

import de.moinmorni.duels.kit.Ability;
import de.moinmorni.duels.kit.Kit;
import de.moinmorni.duels.kit.Layout;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Alles, was Duels über einen Spieler weiß: Name, Werte je Kampfart, gewählte Fähigkeit und eigene Kit-Anordnungen.
 * Liegt als YAML in {@code spieler/<uuid>.yml}.
 */
public final class PlayerData {

    private final UUID uuid;
    private final int eloStart;
    private String name;
    private Ability ability = Ability.BERGFUEHRER;
    private final Map<Kit, KitStats> kits = new EnumMap<>(Kit.class);
    private final Map<String, Layout> layouts = new LinkedHashMap<>();
    /** Siege in Folge über alle Kampfarten. */
    private int streak;
    private int bestStreak;

    public PlayerData(UUID uuid, String name, int eloStart) {
        this.uuid = uuid;
        this.name = name;
        this.eloStart = eloStart;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public Ability ability() {
        return ability;
    }

    public void ability(Ability ability) {
        this.ability = ability;
    }

    public KitStats kit(Kit kit) {
        return kits.computeIfAbsent(kit, k -> new KitStats(eloStart));
    }

    /** Hat in dieser Kampfart schon gekämpft? */
    public boolean played(Kit kit) {
        KitStats s = kits.get(kit);
        return s != null && s.games > 0;
    }

    public Layout layout(String loadoutId) {
        return layouts.getOrDefault(loadoutId, Layout.EMPTY);
    }

    public void layout(String loadoutId, Layout layout) {
        if (layout == null || layout.isEmpty()) {
            layouts.remove(loadoutId);
        } else {
            layouts.put(loadoutId, layout);
        }
    }

    public int streak() {
        return streak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    /** Serie über alle Kampfarten nachführen. */
    public void recordOverall(KitStats.Result result) {
        if (result == KitStats.Result.WIN) {
            streak++;
            bestStreak = Math.max(bestStreak, streak);
        } else if (result == KitStats.Result.LOSS) {
            streak = 0;
        }
    }

    // ---------- YAML ----------

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("faehigkeit", ability.id());
        y.set("serie", streak);
        y.set("beste-serie", bestStreak);
        kits.forEach((kit, s) -> {
            String p = "kits." + kit.id() + ".";
            y.set(p + "elo", s.elo);
            y.set(p + "siege", s.wins);
            y.set(p + "niederlagen", s.losses);
            y.set(p + "unentschieden", s.draws);
            y.set(p + "kills", s.kills);
            y.set(p + "tode", s.deaths);
            y.set(p + "spiele", s.games);
            y.set(p + "gewertet", s.rankedGames);
            y.set(p + "serie", s.streak);
            y.set(p + "beste-serie", s.bestStreak);
        });
        layouts.forEach((id, layout) -> y.set("anordnung." + id, layout.format()));
        return y.saveToString();
    }

    public static PlayerData fromYaml(UUID uuid, String text, int eloStart) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("Spielerdatei kaputt: " + e.getMessage(), e);
        }
        PlayerData d = new PlayerData(uuid, y.getString("name", "?"), eloStart);
        Ability.parse(y.getString("faehigkeit")).ifPresent(d::ability);
        d.streak = y.getInt("serie");
        d.bestStreak = y.getInt("beste-serie");
        ConfigurationSection kits = y.getConfigurationSection("kits");
        if (kits != null) {
            for (String key : kits.getKeys(false)) {
                Kit.parse(key).ifPresent(kit -> {
                    ConfigurationSection k = kits.getConfigurationSection(key);
                    if (k == null) {
                        return;
                    }
                    KitStats s = d.kit(kit);
                    s.elo = k.getInt("elo", eloStart);
                    s.wins = k.getInt("siege");
                    s.losses = k.getInt("niederlagen");
                    s.draws = k.getInt("unentschieden");
                    s.kills = k.getInt("kills");
                    s.deaths = k.getInt("tode");
                    s.games = k.getInt("spiele");
                    s.rankedGames = k.getInt("gewertet");
                    s.streak = k.getInt("serie");
                    s.bestStreak = k.getInt("beste-serie");
                });
            }
        }
        ConfigurationSection layouts = y.getConfigurationSection("anordnung");
        if (layouts != null) {
            for (String key : layouts.getKeys(false)) {
                d.layout(key, Layout.parse(layouts.getString(key)));
            }
        }
        return d;
    }
}
