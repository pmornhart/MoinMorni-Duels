# MoinMorni Duels

Der Duell-Modus des Minecraft-Netzwerks **MoinMorni** (moinmorni.net): 1 gegen 1 und 2 gegen 2 – **Klassisch** mit
Pilzsuppe, **Modern** oder mit **Fähigkeiten**, **locker** oder **gewertet**. Ein Paper-Plugin für Paper 26.x
(Java 25). Es läuft im Netzwerk und auch ganz allein.

## Was drin ist

- **Turnierhof** – die Wartehalle: ein Burghof auf einer schwebenden Insel, Boden mit weiß-blauen Rauten und einem
  Smaragd in der Mitte, Banner in Smaragd, Gold und Weiß-Blau, Tribüne mit Zeltdach, Schmiede und Brotzeit-Stand.
  Schwebende Schrift zeigt die Ranglisten – jeder sieht sie in seiner Sprache.
- **Knöpfe in der Schnellleiste:** Schwert = Kampf wählen (Rechtsklick auf einen Spieler = herausfordern),
  Amboss = Kit bearbeiten, Gold = Rangliste & Statistik, Bett = zurück zur Lobby (das Netzwerk fragt nach, wenn
  jemand mitten im Spiel ist). Alle Menüs sind Truhen und gehen
  auch mit Bedrock (Geyser).
- **Kampfarten**

  | Kampfart | Ausrüstung | Besonderes |
  |---|---|---|
  | Klassisch | Eisenrüstung, Diamantschwert, Pilzsuppen | keine Abklingzeit beim Schlagen, Suppe heilt sofort 3,5 ❤, Rückstoß hebt auch in der Luft an |
  | Modern | Diamantrüstung, Schwert, Axt, Bogen, Goldäpfel, Enderperlen, Schild | Kampf wie heute |
  | Fähigkeiten | Eisen + eine Fähigkeit mit Abklingzeit | **Bergführer** (Kletterseil: Sprung-Schub), **Schmied** (Schmiedehammer: Schild-Stoß), **Kräuterfrau** (Kräuterfläschchen: Heiltrank-Wurf), **Jäger** (Pfeilbündel: Pfeilhagel) |

- **Warteschlangen** je Kampfart × locker/gewertet × 1 gegen 1 / 2 gegen 2. Locker: wer zuerst kommt. Gewertet: nach
  Elo, die erlaubte Spanne wächst mit der Wartezeit. Im 2 gegen 2 meldet die Party-Leitung ihre Party (zwei Leute)
  an; Einzelne werden zu Teams.
- **Arenen:** Almwiese, Burghof, Bergwerk, Wirtshausgarten – jede schwebt auf ihrer eigenen Insel und gibt es mehrfach
  (`arena-kopien`), so laufen viele Kämpfe gleichzeitig. Alles wird beim ersten Start aus dem Code gebaut (eigene
  leere Welt `duels`).
- **Kampfablauf:** Countdown 3-2-1 (eingefroren), Kampf, niemand stirbt wirklich – wer fallen würde, scheidet aus und
  schaut zu. Sieg, wenn das andere Team draußen ist; `/verlassen` = Niederlage; nach 10 Minuten unentschieden.
  10 Sekunden später geht es zurück in den Turnierhof. Nichts fällt herunter, niemand baut.
- **Zurück ins Spiel:** Verbindung mitten im Kampf weg (Spiel abgestürzt, Internet weg)? Der Platz bleibt 30 Sekunden
  frei (`rueckkehr-sekunden`). Fehlt dadurch ein ganzes Team, hält der Kampf an – alle eingefroren, die Uhr steht,
  „Warte auf …“; im 2 gegen 2 kämpft das Team weiter. Wer rechtzeitig zurückkommt, steht genau wieder dort, mit
  demselben Leben und derselben Ausrüstung, nach 3-2-1 geht es weiter. Wer nicht kommt, hat verloren.
- **Kit-Editor:** jede Ausrüstung (Klassisch, Modern, jede Fähigkeit) lässt sich selbst anordnen – gilt ab dem
  nächsten Kampf.
- **Wertung und Statistik:** Elo je Kampfart (Start 1000, K-Faktor 32), Siege, Niederlagen, Unentschieden, Kills, Tode,
  Serien. Liegt lokal (`spieler/<uuid>.yml`, `rangliste.yml`) und geht ans Netzwerk.

## Befehle

| Befehl | Was er tut |
|---|---|
| `/duel <spieler> [klassisch\|modern\|fähigkeiten]` | herausfordern (ohne Kampfart: Menü) – gilt 60 Sekunden |
| `/duel annehmen [spieler]` · `/duel ablehnen [spieler]` | Herausforderung annehmen/ablehnen (auch `accept`/`deny`, im Chat zum Anklicken) |
| `/warteschlange <kampfart> <locker\|gewertet> <1v1\|2v2>` | Gegner suchen (auch `/queue classic ranked 1v1`); `/warteschlange verlassen` |
| `/spectate <spieler>` | einem Kampf zuschauen (`/zuschauen`) |
| `/verlassen` | Warteschlange, Kampf (zählt als Niederlage) oder Zuschauen verlassen |
| `/stats [spieler]` · `/top [kampfart]` | Statistik und Rangliste |
| `/kit [kampfart\|fähigkeit]` | Kit-Editor |
| `/duels info\|neubauen\|neuladen` | Verwaltung (`moinmorni.duels.admin`) |

## Im Netzwerk

Das Netzwerk-Plugin („MoinMorni“) stellt die Schnittstelle `de.moinmorni.api.MoinMorni` bereit; Duels nutzt sie, wenn
sie da ist (`softdepend`): Sprache der Spieler, Party, Statistik, Zustand für Lobby-Figur und Webseite, zurück zur
Lobby. Ohne Netzwerk: deutsche Texte, Statistik nur lokal, „zur Lobby“ trennt die Verbindung mit einem Hinweis.

Ab Schnittstelle 2 meldet Duels außerdem, wer gerade spielt (`playing`: vom Countdown bis zum Ausscheiden oder zum
Ende), hält nach einem Verbindungsabbruch den Platz frei (`holdSlot`: die Lobby bietet „Zurück ins Spiel“ an, `0` nimmt
das Angebot zurück) und schickt mit dem Bett über `askToLobby` – das Netzwerk fragt nach, wenn jemand mitten im Spiel
ist. Ein älteres Netzwerk-Plugin kennt das nicht; dann läuft alles wie bisher.

Statistik nach jedem Kampf (`stats("duels", …)`):

- hochgezählt: `games`, `wins`, `losses`, `draws`, `kills`, `deaths`, je Kampfart `games_<kampfart>`,
  `wins_<kampfart>`, `losses_<kampfart>`, `draws_<kampfart>`, gewertet `ranked_games`, `ranked_wins`
- Bestwerte: `streak_best`, gewertet `elo_<kampfart>` (`klassisch`, `modern`, `faehigkeiten`)

Zustand: `waiting` (kein Kampf) oder `running`, dazu die Spielerzahl.

Bei jedem Push auf `main` baut GitHub ein Release mit `MoinMorni-Duels.jar` (Version aus `pom.xml`); das Netzwerk holt
sich das neueste.

## Bauen und testen

```bash
mvn package                      # Tests + target/MoinMorni-Duels.jar (Java 25)
mvn -Ppaper-26.1 compile         # passt der Code auch zu Paper 26.1?
test/run.sh                      # echter Test: Paper-Server + Test-Spieler (mineflayer)
```

`test/run.sh` baut das Plugin und ein Test-Netzwerk (`test/fake-network`: tut so, als wäre es das Netzwerk-Plugin –
Bert spricht Englisch, Anna und Bert sind eine Party, alle Meldungen landen in `calls.log`), richtet einen
Paper-26.1.2-Server ein (offline, Port 25720, RCON 25820) und schickt vier Test-Spieler durch Halle, Menüs,
Kit-Editor, Warteschlangen, Kämpfe, Suppe, Fähigkeiten, Herausforderungen, Zuschauen, Party-2v2, Unentschieden,
Statistik und Verbindungsabbrüche (rechtzeitig zurück, zu lange weg, 2 gegen 2) (`test/duels-test.cjs`). Pfade und Ports lassen sich per Umgebung ändern (siehe Kopf von `test/run.sh`),
auch für Paper 26.3 über ViaBackwards (`NO_MOVE=1`).

## Einstellungen

`plugins/MoinMorniDuels/config.yml`: Rückkehr nach Verbindungsabbruch, Welt, Arena-Kopien, Zeitlimit, Countdown,
Nachspiel, Ablauf von Herausforderungen, Elo (Start, K-Faktor, Spanne) und Suppen-Heilung – mit Erklärungen in der
Datei. Den Duels-Ordner
aus Version 0.1.1 (`plugins/MorniDuels`) holt das Plugin beim ersten Start von selbst zurück – Spielerdaten,
Rangliste und Einstellungen bleiben.

---

Verfasst mit Claude 🤖
