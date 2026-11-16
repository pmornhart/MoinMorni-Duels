package de.moinmorni.duels;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Alle Texte für Spieler, auf Deutsch und Englisch, als MiniMessage. Eigene Farben: {@code <em>} Smaragd,
 * {@code <au>} Gold, {@code <bl>} Weiß-Blau. Namen von Spielern kommen immer als {@code Placeholder.unparsed} hinein.
 */
public final class Texts {

    public static final int EMERALD = 0x2BB673;
    public static final int GOLD = 0xF2B632;
    public static final int BLUE = 0x5DA9E9;

    /** MiniMessage mit unseren Farben. */
    public static final MiniMessage MM = MiniMessage.builder()
            .editTags(t -> t.resolvers(
                    TagResolver.resolver("em", Tag.styling(TextColor.color(EMERALD))),
                    TagResolver.resolver("au", Tag.styling(TextColor.color(GOLD))),
                    TagResolver.resolver("bl", Tag.styling(TextColor.color(BLUE)))))
            .build();

    private static final Map<String, String[]> TEXTS = new LinkedHashMap<>();

    private Texts() {
    }

    private static void t(String key, String de, String en) {
        if (TEXTS.put(key, new String[] {de, en}) != null) {
            throw new IllegalStateException("Text doppelt: " + key);
        }
    }

    static {
        t("prefix", "<em>⚔</em> <au>Duels</au> <dark_gray>»</dark_gray> ", "<em>⚔</em> <au>Duels</au> <dark_gray>»</dark_gray> ");

        // ---------- Allgemein ----------
        t("only-players", "Das geht nur im Spiel.", "Only possible in-game.");
        t("not-ready", "<gray>Die Arenen werden gerade gebaut – gleich geht's los!</gray>",
                "<gray>The arenas are being built – just a moment!</gray>");
        t("join.title", "<au>⚔ Duels</au>", "<au>⚔ Duels</au>");
        t("join.subtitle", "<gray>Willkommen im <em>Turnierhof</em>!</gray>", "<gray>Welcome to the <em>Tournament Court</em>!</gray>");
        t("join.welcome", "<gray>Willkommen im <em>Turnierhof</em>! <white>Rechtsklick auf das Schwert</white>: Kampf wählen.</gray>",
                "<gray>Welcome to the <em>Tournament Court</em>! <white>Right-click the sword</white> to pick a fight.</gray>");
        t("lobby.kick", "<au>MoinMorni</au>\n<gray>Zurück in die Lobby: Bitte verbinde dich neu mit dem Netzwerk.</gray>",
                "<au>MoinMorni</au>\n<gray>Back to the lobby: please reconnect to the network.</gray>");
        t("lobby.going", "<gray>Ab in die Lobby – bis bald!</gray>", "<gray>Off to the lobby – see you soon!</gray>");

        // ---------- Gegenstände in der Wartehalle ----------
        t("item.fight", "<em>⚔ Kampf wählen</em>", "<em>⚔ Choose a fight</em>");
        t("item.fight.lore", "<gray>Rechtsklick: Kampfart und Gegner suchen.</gray>\n<gray>Rechtsklick auf einen Spieler: herausfordern.</gray>",
                "<gray>Right-click: pick a mode and find an opponent.</gray>\n<gray>Right-click a player: challenge them.</gray>");
        t("item.kit", "<au>⚒ Kit bearbeiten</au>", "<au>⚒ Edit kit</au>");
        t("item.kit.lore", "<gray>Rechtsklick: Gegenstände so anordnen, wie du willst.</gray>",
                "<gray>Right-click: arrange your items the way you like.</gray>");
        t("item.stats", "<au>★ Rangliste & Statistik</au>", "<au>★ Leaderboard & stats</au>");
        t("item.stats.lore", "<gray>Rechtsklick: deine Werte und die Besten.</gray>",
                "<gray>Right-click: your stats and the best players.</gray>");
        t("item.lobby", "<bl>⌂ Zurück zur Lobby</bl>", "<bl>⌂ Back to the lobby</bl>");
        t("item.lobby.lore", "<gray>Rechtsklick: zurück ins Dorf.</gray>", "<gray>Right-click: back to the village.</gray>");
        t("item.leave", "<red>✖ Warteschlange verlassen</red>", "<red>✖ Leave the queue</red>");
        t("item.leave.lore", "<gray>Rechtsklick: nicht mehr suchen.</gray>", "<gray>Right-click: stop searching.</gray>");

        // ---------- Kampfarten ----------
        t("kit.klassisch", "Klassisch", "Classic");
        t("kit.modern", "Modern", "Modern");
        t("kit.faehigkeiten", "Fähigkeiten", "Abilities");
        t("kit.klassisch.desc", "<gray>Eisen, Diamantschwert und Pilzsuppe.</gray>\n<gray>Keine Abklingzeit – Suppe heilt sofort 3,5 ❤.</gray>",
                "<gray>Iron, a diamond sword and mushroom soup.</gray>\n<gray>No cooldown – soup heals 3.5 ❤ at once.</gray>");
        t("kit.modern.desc", "<gray>Heutiger Kampf: Schild, Axt, Bogen,</gray>\n<gray>Goldäpfel und Enderperlen.</gray>",
                "<gray>Today's combat: shield, axe, bow,</gray>\n<gray>golden apples and ender pearls.</gray>");
        t("kit.faehigkeiten.desc", "<gray>Eisen und eine besondere Fähigkeit</gray>\n<gray>mit Abklingzeit. Klicken: Fähigkeit wählen.</gray>",
                "<gray>Iron and a special ability</gray>\n<gray>with a cooldown. Click: choose your ability.</gray>");
        t("mode.casual", "Locker", "Casual");
        t("mode.ranked", "Gewertet", "Ranked");
        t("size.1", "1 gegen 1", "1v1");
        t("size.2", "2 gegen 2", "2v2");

        // ---------- Fähigkeiten ----------
        t("ability.bergfuehrer", "Bergführer", "Mountain Guide");
        t("ability.bergfuehrer.item", "Kletterseil", "Climbing Rope");
        t("ability.bergfuehrer.desc", "<gray>Rechtsklick: kurzer Sprung nach vorn und oben.</gray>\n<gray>Die nächste Landung tut nicht weh.</gray>",
                "<gray>Right-click: a short leap forward and up.</gray>\n<gray>Your next landing doesn't hurt.</gray>");
        t("ability.schmied", "Schmied", "Blacksmith");
        t("ability.schmied.item", "Schmiedehammer", "Smith's Hammer");
        t("ability.schmied.desc", "<gray>Rechtsklick: Schild-Stoß – stößt Gegner vor dir</gray>\n<gray>weg und bremst sie kurz. Mit Schild.</gray>",
                "<gray>Right-click: shield bash – pushes opponents in front</gray>\n<gray>of you away and slows them. Comes with a shield.</gray>");
        t("ability.kraeuterfrau", "Kräuterfrau", "Herbalist");
        t("ability.kraeuterfrau.item", "Kräuterfläschchen", "Herbal Flask");
        t("ability.kraeuterfrau.desc", "<gray>Rechtsklick: wirft einen Heiltrank. Er heilt alle</gray>\n<gray>in der Nähe – auch Gegner, also gut zielen!</gray>",
                "<gray>Right-click: throws a healing potion. It heals everyone</gray>\n<gray>nearby – opponents too, so aim well!</gray>");
        t("ability.jaeger", "Jäger", "Hunter");
        t("ability.jaeger.item", "Pfeilbündel", "Arrow Bundle");
        t("ability.jaeger.desc", "<gray>Rechtsklick: Pfeilhagel – fünf Pfeile im Fächer.</gray>\n<gray>Dazu Bogen und Pfeile.</gray>",
                "<gray>Right-click: arrow volley – five arrows in a fan.</gray>\n<gray>Comes with a bow and arrows.</gray>");
        t("item.ability.name", "<au>✦ <name></au>", "<au>✦ <name></au>");
        t("ability.cooldown", "<gray>Abklingzeit: <white><secs> Sekunden</white></gray>", "<gray>Cooldown: <white><secs> seconds</white></gray>");
        t("ability.ready", "<au>✦ <name></au> <green>bereit</green>", "<au>✦ <name></au> <green>ready</green>");
        t("ability.wait", "<au>✦ <name></au> <gray>noch <white><secs> s</white></gray>", "<au>✦ <name></au> <gray><white><secs> s</white> left</gray>");
        t("ability.chosen", "<gray>Deine Fähigkeit: <au><name></au>.</gray>", "<gray>Your ability: <au><name></au>.</gray>");

        // ---------- Menüs ----------
        t("menu.fight.title", "Kampf wählen", "Choose a fight");
        t("menu.fight.info", "<au>⚔ Duels</au>", "<au>⚔ Duels</au>");
        t("menu.fight.info.lore", "<gray><white><players></white> im Turnierhof · <white><fights></white> Kämpfe laufen</gray>\n<gray>Locker: einfach drauflos. Gewertet: zählt für deine Wertung</gray>\n<gray>(Punkte, Start 1000) und die Rangliste.</gray>",
                "<gray><white><players></white> in the court · <white><fights></white> fights running</gray>\n<gray>Casual: just for fun. Ranked: counts for your rating</gray>\n<gray>(points, starting at 1000) and the leaderboard.</gray>");
        t("menu.queue.name", "<white><mode></white> <dark_gray>·</dark_gray> <em><size></em>", "<white><mode></white> <dark_gray>·</dark_gray> <em><size></em>");
        t("menu.queue.waiting", "<gray>Wartend: <white><n></white></gray>", "<gray>Waiting: <white><n></white></gray>");
        t("menu.queue.fighting", "<gray>Im Kampf: <white><n></white></gray>", "<gray>Fighting: <white><n></white></gray>");
        t("menu.queue.elo", "<gray>Deine Wertung: <au><elo></au></gray>", "<gray>Your rating: <au><elo></au></gray>");
        t("menu.queue.click", "<em>▶ Klicken zum Anmelden</em>", "<em>▶ Click to join</em>");
        t("menu.queue.here", "<au>⏳ Du wartest hier – klicken zum Verlassen</au>", "<au>⏳ You are waiting here – click to leave</au>");
        t("menu.queue.party", "<gray>Party-Leiter: dein Mitspieler kommt mit.</gray>", "<gray>Party leader: your teammate comes along.</gray>");
        t("menu.kit.ability", "<gray>Gewählt: <au><name></au> – klicken zum Wechseln</gray>", "<gray>Chosen: <au><name></au> – click to change</gray>");
        t("menu.close", "<red>Schließen</red>", "<red>Close</red>");
        t("menu.back", "<gray>← Zurück</gray>", "<gray>← Back</gray>");
        t("menu.ability.title", "Fähigkeit wählen", "Choose your ability");
        t("menu.ability.selected", "<em>✔ Ausgewählt</em>", "<em>✔ Selected</em>");
        t("menu.ability.click", "<em>▶ Klicken zum Auswählen</em>", "<em>▶ Click to choose</em>");
        t("menu.editor.choose.title", "Kit bearbeiten", "Edit kit");
        t("menu.editor.choose.lore", "<em>▶ Klicken zum Bearbeiten</em>", "<em>▶ Click to edit</em>");
        t("menu.editor.title", "Kit: <kit>", "Kit: <kit>");
        t("menu.editor.save", "<em>✔ Speichern</em>", "<em>✔ Save</em>");
        t("menu.editor.save.lore", "<gray>So kommt dein Kit in jeden Kampf.</gray>", "<gray>Your kit will look like this in every fight.</gray>");
        t("menu.editor.reset", "<au>↺ Zurücksetzen</au>", "<au>↺ Reset</au>");
        t("menu.editor.reset.lore", "<gray>Alles zurück auf den Anfangsplatz.</gray>", "<gray>Everything back to its starting place.</gray>");
        t("menu.editor.info", "<bl>ℹ So geht's</bl>", "<bl>ℹ How it works</bl>");
        t("menu.editor.info.lore", "<gray>Klicke einen Gegenstand an und dann den Platz,</gray>\n<gray>wo er hin soll. Die Reihe über dem Strich ist</gray>\n<gray>deine Schnellleiste. Rüstung bekommst du immer.</gray>",
                "<gray>Click an item, then click the slot</gray>\n<gray>where it should go. The row above the line is</gray>\n<gray>your hotbar. You always get your armour.</gray>");
        t("menu.editor.hotbar", "<gray>↑ Schnellleiste ↑</gray>", "<gray>↑ Hotbar ↑</gray>");
        t("editor.saved", "<gray>Gespeichert! So sieht dein Kit <em><kit></em> ab jetzt aus.</gray>", "<gray>Saved! This is your <em><kit></em> kit from now on.</gray>");
        t("editor.reset", "<gray>Zurückgesetzt – zum Übernehmen auf <em>Speichern</em> klicken.</gray>", "<gray>Reset – click <em>Save</em> to keep it.</gray>");
        t("editor.not-saved", "<gray>Nicht gespeichert – dein Kit bleibt, wie es war.</gray>", "<gray>Not saved – your kit stays as it was.</gray>");
        t("editor.busy", "<gray>Das geht nur in der Wartehalle.</gray>", "<gray>Only possible in the waiting hall.</gray>");
        t("menu.stats.title", "Rangliste & Statistik", "Leaderboard & stats");
        t("menu.stats.own", "<em>Deine Werte</em> <dark_gray>·</dark_gray> <white><kit></white>", "<em>Your stats</em> <dark_gray>·</dark_gray> <white><kit></white>");
        t("menu.stats.top", "<au>Rangliste</au> <dark_gray>·</dark_gray> <white><kit></white>", "<au>Leaderboard</au> <dark_gray>·</dark_gray> <white><kit></white>");
        t("menu.stats.me", "<au><name></au>", "<au><name></au>");
        t("menu.stats.me.lore", "<gray>Kämpfe: <white><games></white> · Siege: <white><wins></white></gray>\n<gray>Siege in Folge: <white><streak></white> · Rekord: <white><best></white></gray>",
                "<gray>Fights: <white><games></white> · Wins: <white><wins></white></gray>\n<gray>Wins in a row: <white><streak></white> · Record: <white><best></white></gray>");
        t("stats.line.elo", "<gray>Wertung: <au><elo></au><pos></gray>", "<gray>Rating: <au><elo></au><pos></gray>");
        t("stats.line.pos", " <dark_gray>(Platz <pos>)</dark_gray>", " <dark_gray>(#<pos>)</dark_gray>");
        t("stats.line.record", "<gray>Siege <white><wins></white> · Niederlagen <white><losses></white> · Unentschieden <white><draws></white></gray>",
                "<gray>Wins <white><wins></white> · Losses <white><losses></white> · Draws <white><draws></white></gray>");
        t("stats.line.kd", "<gray>Gegner besiegt <white><kills></white> · selbst besiegt <white><deaths></white></gray>", "<gray>Opponents defeated <white><kills></white> · defeated <white><deaths></white></gray>");
        t("stats.line.streak", "<gray>Siege in Folge <white><streak></white> · Rekord <white><best></white></gray>", "<gray>Wins in a row <white><streak></white> · Record <white><best></white></gray>");
        t("stats.top.line", "<au><pos>.</au> <white><name></white> <dark_gray>–</dark_gray> <au><elo></au> <gray>(<wins> Siege)</gray>",
                "<au><pos>.</au> <white><name></white> <dark_gray>–</dark_gray> <au><elo></au> <gray>(<wins> wins)</gray>");
        t("stats.top.empty", "<gray>Noch niemand gewertet – sei die oder der Erste!</gray>", "<gray>Nobody ranked yet – be the first!</gray>");
        t("menu.duel.title", "Herausfordern: <name>", "Challenge: <name>");
        t("menu.duel.click", "<em>▶ Klicken zum Herausfordern</em>", "<em>▶ Click to challenge</em>");
        t("menu.challenge.title", "Duell von <name>", "Duel from <name>");
        t("menu.challenge.accept", "<em>✔ Annehmen</em>", "<em>✔ Accept</em>");
        t("menu.challenge.deny", "<red>✖ Ablehnen</red>", "<red>✖ Decline</red>");
        t("menu.challenge.info", "<au><name></au> <gray>fordert dich heraus</gray>", "<au><name></au> <gray>challenges you</gray>");

        // ---------- Warteschlange ----------
        t("queue.joined", "<gray>Du suchst Gegner: <white><kit></white> · <white><mode></white> · <white><size></white>.</gray>",
                "<gray>Looking for opponents: <white><kit></white> · <white><mode></white> · <white><size></white>.</gray>");
        t("queue.joined.party", "<gray>Deine Party sucht Gegner: <white><kit></white> · <white><mode></white> · <white><size></white>.</gray>",
                "<gray>Your party is looking for opponents: <white><kit></white> · <white><mode></white> · <white><size></white>.</gray>");
        t("queue.left", "<gray>Du hast die Warteschlange verlassen.</gray>", "<gray>You left the queue.</gray>");
        t("queue.left.team", "<gray><white><name></white> hat die Warteschlange verlassen – ihr wartet nicht mehr.</gray>",
                "<gray><white><name></white> left the queue – you are no longer waiting.</gray>");
        t("queue.searching", "<au>⏳</au> <gray>Suche Gegner</gray> <dark_gray>·</dark_gray> <white><kit></white> <dark_gray>·</dark_gray> <white><mode></white> <dark_gray>·</dark_gray> <white><size></white> <dark_gray>·</dark_gray> <em><time></em>",
                "<au>⏳</au> <gray>Searching</gray> <dark_gray>·</dark_gray> <white><kit></white> <dark_gray>·</dark_gray> <white><mode></white> <dark_gray>·</dark_gray> <white><size></white> <dark_gray>·</dark_gray> <em><time></em>");
        t("queue.range", " <dark_gray>·</dark_gray> <gray>Gegner ±<range> Punkte</gray>", " <dark_gray>·</dark_gray> <gray>opponents ±<range> points</gray>");
        t("queue.busy", "<gray>Alle Arenen sind belegt – gleich bist du dran.</gray>", "<gray>All arenas are busy – you're next.</gray>");
        t("queue.not-in-hall", "<gray>Das geht nur in der Wartehalle.</gray>", "<gray>Only possible in the waiting hall.</gray>");
        t("queue.party.not-leader", "<gray>Im 2 gegen 2 meldet der Party-Leiter euch an.</gray>", "<gray>In 2v2 your party leader signs you up.</gray>");
        t("queue.party.too-big", "<gray>Für 2 gegen 2 darf eure Party höchstens 2 Leute haben – fordert euch doch mit <white>/duel</white> heraus!</gray>",
                "<gray>For 2v2 your party may have at most 2 people – challenge each other with <white>/duel</white>!</gray>");
        t("queue.party.away", "<gray><white><name></white> aus deiner Party ist nicht im Turnierhof.</gray>", "<gray><white><name></white> from your party isn't in the court.</gray>");
        t("queue.party.busy", "<gray><white><name></white> aus deiner Party ist gerade beschäftigt.</gray>", "<gray><white><name></white> from your party is busy right now.</gray>");
        t("queue.usage", "<gray>So geht's: <white>/warteschlange \\<klassisch|modern|fähigkeiten> \\<locker|gewertet> \\<1v1|2v2></white></gray>",
                "<gray>Usage: <white>/queue \\<classic|modern|abilities> \\<casual|ranked> \\<1v1|2v2></white></gray>");

        // ---------- Kampf ----------
        t("fight.found", "<gray>Gegner gefunden: <white><names></white> · Arena <em><arena></em>.</gray>", "<gray>Opponent found: <white><names></white> · arena <em><arena></em>.</gray>");
        t("fight.countdown.sub", "<gray><kit> · gegen <white><names></white></gray>", "<gray><kit> · against <white><names></white></gray>");
        t("fight.go", "<em>Los!</em>", "<em>Fight!</em>");
        t("fight.go.sub", "<gray>Viel Glück!</gray>", "<gray>Good luck!</gray>");
        t("fight.bossbar", "<white><kit></white> <dark_gray>·</dark_gray> <em><arena></em> <dark_gray>·</dark_gray> <au><time></au>",
                "<white><kit></white> <dark_gray>·</dark_gray> <em><arena></em> <dark_gray>·</dark_gray> <au><time></au>");
        t("fight.killed", "<white><victim></white> <gray>wurde von</gray> <white><killer></white> <gray>besiegt.</gray>", "<white><victim></white> <gray>was defeated by</gray> <white><killer></white><gray>.</gray>");
        t("fight.died", "<white><victim></white> <gray>ist ausgeschieden.</gray>", "<white><victim></white> <gray>is out.</gray>");
        t("fight.void", "<white><victim></white> <gray>ist in die Tiefe gestürzt.</gray>", "<white><victim></white> <gray>fell into the depths.</gray>");
        t("fight.quit", "<white><victim></white> <gray>hat das Spiel verlassen.</gray>", "<white><victim></white> <gray>left the game.</gray>");
        t("fight.left", "<white><victim></white> <gray>hat den Kampf verlassen.</gray>", "<white><victim></white> <gray>left the fight.</gray>");
        t("fight.forfeit", "<white><victim></white> <gray>hat aufgegeben.</gray>", "<white><victim></white> <gray>gave up.</gray>");
        t("fight.dead.title", "<red>Besiegt</red>", "<red>Defeated</red>");
        t("fight.dead.sub", "<gray>Du schaust jetzt zu.</gray>", "<gray>You are spectating now.</gray>");
        t("fight.win.title", "<au>Sieg!</au>", "<au>Victory!</au>");
        t("fight.win.sub", "<gray>Gut gekämpft!</gray>", "<gray>Well fought!</gray>");
        t("fight.lose.title", "<red>Niederlage</red>", "<red>Defeat</red>");
        t("fight.lose.sub", "<gray>Beim nächsten Mal!</gray>", "<gray>Next time!</gray>");
        t("fight.draw.title", "<bl>Unentschieden</bl>", "<bl>Draw</bl>");
        t("fight.draw.sub", "<gray>Die Zeit ist um.</gray>", "<gray>Time is up.</gray>");
        t("fight.result.win", "<au>★ Sieg für</au> <white><names></white><au>!</au>", "<au>★ Victory for</au> <white><names></white><au>!</au>");
        t("fight.result.health", "<gray><white><name></white> hatte noch <red><hp> ❤</red>.</gray>", "<gray><white><name></white> had <red><hp> ❤</red> left.</gray>");
        t("fight.result.draw", "<bl>Unentschieden</bl> <gray>– die Zeit ist abgelaufen.</gray>", "<bl>Draw</bl> <gray>– time ran out.</gray>");
        t("fight.result.elo", "<gray>Deine Wertung (<kit>): <au><elo></au> <delta></gray>", "<gray>Your rating (<kit>): <au><elo></au> <delta></gray>");
        t("fight.back", "<gray>In <white><n></white> Sekunden geht's zurück in die Wartehalle.</gray>", "<gray>Back to the waiting hall in <white><n></white> seconds.</gray>");
        t("fight.time-left", "<gray>Noch <au><n> Sekunden</au>!</gray>", "<gray><au><n> seconds</au> left!</gray>");
        t("fight.no-arena", "<gray>Gerade ist keine Arena frei – versucht es gleich nochmal.</gray>", "<gray>No arena is free right now – try again in a moment.</gray>");
        t("fight.gave-up", "<gray>Du hast aufgegeben – das zählt als Niederlage.</gray>", "<gray>You gave up – that counts as a loss.</gray>");
        t("fight.nothing", "<gray>Du bist gerade in keinem Kampf und in keiner Warteschlange.</gray>", "<gray>You are not in a fight or a queue right now.</gray>");
        t("fight.back-hall", "<gray>Zurück in der Wartehalle.</gray>", "<gray>Back in the waiting hall.</gray>");

        // ---------- Verbindung weg, wiederkommen ----------
        t("fight.away", "<white><victim></white> <gray>hat die Verbindung verloren – der Kampf ist angehalten. Wir warten bis zu <white><n></white> Sekunden.</gray>",
                "<white><victim></white> <gray>lost the connection – the fight is paused. We'll wait up to <white><n></white> seconds.</gray>");
        t("fight.away.on", "<white><victim></white> <gray>hat die Verbindung verloren – der Platz bleibt <white><n></white> Sekunden frei, der Kampf geht weiter.</gray>",
                "<white><victim></white> <gray>lost the connection – their place stays free for <white><n></white> seconds, the fight goes on.</gray>");
        t("fight.away.gone", "<white><victim></white> <gray>ist nicht rechtzeitig zurückgekommen.</gray>",
                "<white><victim></white> <gray>didn't make it back in time.</gray>");
        t("fight.waiting", "<au>⏳</au> <gray>Warte auf</gray> <white><name></white> <dark_gray>–</dark_gray> <au><n> s</au>",
                "<au>⏳</au> <gray>Waiting for</gray> <white><name></white> <dark_gray>–</dark_gray> <au><n> s</au>");
        t("fight.waiting.bar", "<au>⏳ Warte auf</au> <white><name></white> <dark_gray>–</dark_gray> <au><n> s</au>",
                "<au>⏳ Waiting for</au> <white><name></white> <dark_gray>–</dark_gray> <au><n> s</au>");
        t("fight.resume.sub", "<gray>Gleich geht es weiter …</gray>", "<gray>Resuming in a moment …</gray>");
        t("fight.resume.go", "<em>Weiter!</em>", "<em>Fight on!</em>");
        t("fight.welcome-back.title", "<em>Willkommen zurück!</em>", "<em>Welcome back!</em>");
        t("fight.welcome-back.sub", "<gray>Dein Kampf geht weiter.</gray>", "<gray>Your fight goes on.</gray>");
        t("fight.welcome-back", "<gray>Willkommen zurück! Du stehst wieder, wo du warst – mit deinem Leben und deiner Ausrüstung.</gray>",
                "<gray>Welcome back! You're right where you were – same health, same gear.</gray>");
        t("fight.returned", "<white><name></white> <gray>ist wieder da.</gray>", "<white><name></white> <gray>is back.</gray>");
        t("fight.missed", "<gray>Willkommen zurück! Dein Kampf ist inzwischen zu Ende – dein Ergebnis steht in <white>/stats</white>.</gray>",
                "<gray>Welcome back! Your fight is over by now – see <white>/stats</white> for the result.</gray>");

        // ---------- Herausforderung ----------
        t("duel.usage", "<gray>So geht's: <white>/duel \\<spieler> [klassisch|modern|fähigkeiten]</white> · <white>/duel annehmen</white> · <white>/duel ablehnen</white></gray>",
                "<gray>Usage: <white>/duel \\<player> [classic|modern|abilities]</white> · <white>/duel accept</white> · <white>/duel deny</white></gray>");
        t("duel.self", "<gray>Gegen dich selbst? Das wird schwierig.</gray>", "<gray>Against yourself? That'll be tricky.</gray>");
        t("duel.not-found", "<gray><white><name></white> ist gerade nicht im Turnierhof.</gray>", "<gray><white><name></white> isn't in the court right now.</gray>");
        t("duel.busy", "<gray><white><name></white> ist gerade beschäftigt.</gray>", "<gray><white><name></white> is busy right now.</gray>");
        t("duel.you-busy", "<gray>Du kämpfst gerade.</gray>", "<gray>You are in a fight.</gray>");
        t("duel.unknown-kit", "<gray>Diese Kampfart gibt es nicht: <white>klassisch</white>, <white>modern</white> oder <white>fähigkeiten</white>.</gray>",
                "<gray>Unknown mode – use <white>classic</white>, <white>modern</white> or <white>abilities</white>.</gray>");
        t("duel.sent", "<gray>Herausforderung an <white><name></white> geschickt (<em><kit></em>). Sie gilt <white><secs></white> Sekunden.</gray>",
                "<gray>Challenge sent to <white><name></white> (<em><kit></em>). It is valid for <white><secs></white> seconds.</gray>");
        t("duel.received", "<white><name></white> <gray>fordert dich zum Duell heraus:</gray> <em><kit></em><gray>!</gray>",
                "<white><name></white> <gray>challenges you to a duel:</gray> <em><kit></em><gray>!</gray>");
        t("duel.buttons", "<accept> <deny> <dark_gray>(oder /duel annehmen <name>)</dark_gray>", "<accept> <deny> <dark_gray>(or /duel accept <name>)</dark_gray>");
        t("duel.accept.button", "<em><b>[Annehmen]</b></em>", "<em><b>[Accept]</b></em>");
        t("duel.deny.button", "<red><b>[Ablehnen]</b></red>", "<red><b>[Decline]</b></red>");
        t("duel.accept.hover", "<gray>Klicken: Duell annehmen</gray>", "<gray>Click to accept the duel</gray>");
        t("duel.deny.hover", "<gray>Klicken: ablehnen</gray>", "<gray>Click to decline</gray>");
        t("duel.expired.from", "<gray>Deine Herausforderung an <white><name></white> ist abgelaufen.</gray>", "<gray>Your challenge to <white><name></white> expired.</gray>");
        t("duel.expired.to", "<gray>Die Herausforderung von <white><name></white> ist abgelaufen.</gray>", "<gray>The challenge from <white><name></white> expired.</gray>");
        t("duel.denied.from", "<gray><white><name></white> hat abgelehnt.</gray>", "<gray><white><name></white> declined.</gray>");
        t("duel.denied.to", "<gray>Abgelehnt.</gray>", "<gray>Declined.</gray>");
        t("duel.none", "<gray>Du hast keine offene Herausforderung.</gray>", "<gray>You have no open challenge.</gray>");
        t("duel.which", "<gray>Mehrere Herausforderungen – wen meinst du? <white>/duel annehmen \\<spieler></white></gray>",
                "<gray>Several challenges – whom do you mean? <white>/duel accept \\<player></white></gray>");
        t("duel.already", "<gray>Du hast <white><name></white> schon herausgefordert.</gray>", "<gray>You already challenged <white><name></white>.</gray>");

        // ---------- Zuschauen ----------
        t("spectate.usage", "<gray>So geht's: <white>/spectate \\<spieler></white></gray>", "<gray>Usage: <white>/spectate \\<player></white></gray>");
        t("spectate.not-fighting", "<gray><white><name></white> kämpft gerade nicht.</gray>", "<gray><white><name></white> isn't fighting right now.</gray>");
        t("spectate.busy", "<gray>Erst den eigenen Kampf beenden.</gray>", "<gray>Finish your own fight first.</gray>");
        t("spectate.start", "<gray>Du schaust <white><name></white> zu. Mit <white>/verlassen</white> geht's zurück.</gray>",
                "<gray>You are watching <white><name></white>. Use <white>/verlassen</white> (leave) to go back.</gray>");

        // ---------- Statistik ----------
        t("stats.header", "<au>━━ Statistik von <white><name></white> ━━</au>", "<au>━━ Stats of <white><name></white> ━━</au>");
        t("stats.kit", "<em><kit></em>", "<em><kit></em>");
        t("stats.none", "<gray><white><name></white> hat noch nicht gekämpft.</gray>", "<gray><white><name></white> hasn't fought yet.</gray>");
        t("stats.unknown", "<gray><white><name></white> kenne ich nicht.</gray>", "<gray>I don't know <white><name></white>.</gray>");
        t("top.header", "<au>━━ Rangliste <white><kit></white> (Wertung) ━━</au>", "<au>━━ Leaderboard <white><kit></white> (rating) ━━</au>");

        // ---------- Verwaltung ----------
        t("admin.usage", "<gray>/duels info · /duels neubauen · /duels neuladen</gray>", "<gray>/duels info · /duels neubauen (rebuild) · /duels neuladen (reload)</gray>");
        t("admin.rebuild", "<gray>Wartehalle und Arenen werden neu gebaut …</gray>", "<gray>Rebuilding the waiting hall and arenas …</gray>");
        t("admin.rebuilt", "<em>Fertig gebaut.</em>", "<em>Rebuilt.</em>");
        t("admin.reloaded", "<gray>Einstellungen neu geladen.</gray>", "<gray>Settings reloaded.</gray>");
        t("admin.info", "<gray>Arenen: <white><busy></white>/<white><total></white> belegt · Kämpfe: <white><fights></white> · Wartend: <white><queued></white> · Netzwerk: <white><net></white></gray>",
                "<gray>Arenas: <white><busy></white>/<white><total></white> busy · Fights: <white><fights></white> · Queued: <white><queued></white> · Network: <white><net></white></gray>");

        // ---------- Schilder in der Wartehalle ----------
        t("board.welcome", "<au><b>⚔ Turnierhof ⚔</b></au>\n<gray>Rechtsklick auf das <white>Schwert</white>: Kampf wählen</gray>\n<gray>Rechtsklick auf einen Spieler: herausfordern</gray>\n<gray>Amboss: Kit bearbeiten · Gold: Rangliste</gray>",
                "<au><b>⚔ Tournament Court ⚔</b></au>\n<gray>Right-click the <white>sword</white>: choose a fight</gray>\n<gray>Right-click a player: challenge them</gray>\n<gray>Anvil: edit kit · Gold: leaderboard</gray>");
        t("board.top", "<au><b>★ <kit></b></au>\n<gray>Rangliste · Top 5</gray>", "<au><b>★ <kit></b></au>\n<gray>Leaderboard · Top 5</gray>");
        t("board.top.line", "<au><pos>.</au> <white><name></white> <gray><elo></gray>", "<au><pos>.</au> <white><name></white> <gray><elo></gray>");
        t("board.top.empty", "<gray>Noch frei – sei die oder der Erste!</gray>", "<gray>Still empty – be the first!</gray>");

        // ---------- Arenen ----------
        t("arena.almwiese", "Almwiese", "Alpine Meadow");
        t("arena.burghof", "Burghof", "Castle Courtyard");
        t("arena.bergwerk", "Bergwerk", "Old Mine");
        t("arena.wirtshausgarten", "Wirtshausgarten", "Beer Garden");
    }

    /** Alle Schlüssel (für Tests). */
    public static Set<String> keys() {
        return Collections.unmodifiableSet(TEXTS.keySet());
    }

    /** Roher MiniMessage-Text. Unbekannte Sprache → Deutsch. */
    public static String raw(String lang, String key) {
        String[] t = TEXTS.get(key);
        if (t == null) {
            return key;
        }
        return "en".equals(lang) ? t[1] : t[0];
    }

    /** Text ohne Vorsilbe; für Gegenstände ohne kursiv. */
    public static Component text(String lang, String key, TagResolver... resolvers) {
        return MM.deserialize(raw(lang, key), resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Nachricht für den Chat, mit „⚔ Duels »“ davor. */
    public static Component msg(String lang, String key, TagResolver... resolvers) {
        return MM.deserialize(raw(lang, "prefix")).append(MM.deserialize(raw(lang, key), resolvers));
    }

    /** Mehrzeiliger Text als einzelne Zeilen (für Beschreibungen von Gegenständen). */
    public static java.util.List<Component> lines(String lang, String key, TagResolver... resolvers) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (String line : raw(lang, key).split("\n")) {
            out.add(MM.deserialize(line, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }
}
