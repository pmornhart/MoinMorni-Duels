# MoinMorni Duels

Der Duell-Modus des Minecraft-Netzwerks **MoinMorni** (moinmorni.net): 20 Kampfarten in drei Gruppen – von
**Klassisch** mit Pilzsuppe über **UHC** und **Bau-Kampf** bis **Sumo**, **Spleef**, **Labyrinth** und **Brücke** –,
1 gegen 1, 2 gegen 2 und Party-Kämpfe, **locker** oder **gewertet**. Ein Paper-Plugin für Paper 26.x (Java 25). Es
läuft im Netzwerk und auch ganz allein.

## Was drin ist

- **Turnierhof** – die Wartehalle: ein Burghof auf einer schwebenden Insel, Boden mit weiß-blauen Rauten und einem
  Smaragd in der Mitte, Banner in Smaragd, Gold und Weiß-Blau, Tribüne mit Zeltdach, Schmiede und Brotzeit-Stand.
  Schwebende Schrift zeigt die Ranglisten – jeder sieht sie in seiner Sprache.
- **Knöpfe in der Schnellleiste:** Schwert = Kampf wählen (eine Reihe je Gruppe; ein Klick auf eine Kampfart zeigt
  ihre Warteschlangen, ihre Rangliste und den Kit-Editor; Rechtsklick auf einen Spieler = herausfordern),
  Amboss = Kit bearbeiten, Gold = Rangliste & Statistik, Bett = zurück zur Lobby (das Netzwerk fragt nach, wenn
  jemand mitten im Spiel ist). Alle Menüs sind Truhen und gehen
  auch mit Bedrock (Geyser).
- **Kampfarten** – jede mit eigenem Bild, eigener Warteschlange und eigener Rangliste:

  | Gruppe | Kampfart | Ausrüstung | Besonderes |
  |---|---|---|---|
  | Klassisch | Klassisch | Eisenrüstung, Diamantschwert, Pilzsuppen | Suppe heilt sofort 3,5 ❤ |
  | Klassisch | Nur Schwert | ein Diamantschwert, sonst nichts | wer besser trifft, gewinnt |
  | Klassisch | Ritter | Eisenrüstung, Eisenschwert, Bogen, 2 Goldäpfel | |
  | Klassisch | Diamant | Diamantrüstung (leicht verzaubert), Schwert, Bogen, Goldäpfel | |
  | Klassisch | Bogen | Lederrüstung, Bogen mit unendlich Pfeilen | |
  | Klassisch | Angel | Eisenrüstung, Schwert, Angel | der Haken stupst an, beim Einholen holst du den Gegner heran |
  | Klassisch | Kombo | starke Diamantrüstung, Schwert, Goldäpfel | fast keine Pause zwischen Treffern |
  | Modern | Modern | Diamantrüstung, Schwert, Axt, Bogen, Goldäpfel, Enderperlen, Schild | Kampf wie heute |
  | Modern | Axt und Schild | Diamantrüstung, Diamantaxt, Schild | |
  | Modern | Goldapfel | sehr starke Rüstung, 12 verzauberte Goldäpfel, Stärke- und Tempo-Tränke | |
  | Modern | Tränke | Diamantrüstung, Schwert, Heiltränke zum Werfen, Tempo-Tränke, Enderperlen | |
  | Modern | Enderperlen | Eisenrüstung, Schwert, 16 Enderperlen | Landen tut nicht weh |
  | Modern | UHC | gemischte Rüstung, Schwert, Bogen, Wasser, Lava, Goldäpfel, Blöcke, Werkzeug | keine natürliche Heilung |
  | Modern | Bau-Kampf | Eisenrüstung, Schwert, Bretter, Pflasterstein, Werkzeug, Bogen | Türme, Wände, Brücken bauen |
  | Spaß | Fähigkeiten | Eisen + eine Fähigkeit mit Abklingzeit | **Bergführer** (Kletterseil: Sprung-Schub), **Schmied** (Schmiedehammer: Schild-Stoß), **Kräuterfrau** (Kräuterfläschchen: Heiltrank-Wurf), **Jäger** (Pfeilbündel: Pfeilhagel) |
  | Spaß | Sumo | nichts | kein Schaden, Treffer stoßen; wer vom Tanzboden fällt, verliert |
  | Spaß | Boxen | Diamantschwert, Schnelligkeit | kein Schaden – wer zuerst 100 Treffer landet, gewinnt |
  | Spaß | Spleef | Diamantschaufel | Schnee unter dem Gegner wegschaufeln, Schläge zählen nicht |
  | Spaß | Labyrinth | Lederrüstung, Schwert, Bogen, Kompass | jede Runde ein neuer Irrgarten; wer zuerst das Ziel in der Mitte erreicht (oder den Gegner besiegt), gewinnt |
  | Spaß | Brücke | Lederrüstung in Teamfarbe, Schwert, Bogen, Spitzhacke, Blöcke | ins gegnerische Tor springen; wer zuerst 3 Tore hat, gewinnt |

  In der Gruppe Klassisch und bei Sumo, Boxen, Labyrinth und Brücke gilt der Kampf wie früher: keine Abklingzeit
  beim Schlagen, kein Schwung-Schaden, Rückstoß hebt auch in der Luft an.

- **Warteschlangen** je Kampfart × locker/gewertet × 1 gegen 1 / 2 gegen 2. Locker: wer zuerst kommt. Gewertet: nach
  Elo, die erlaubte Spanne wächst mit der Wartezeit. Im 2 gegen 2 meldet die Party-Leitung ihre Party (zwei Leute)
  an; Einzelne werden zu Teams. Aus der Warteschlange gibt es auf Wunsch „Bester aus 3“ oder „Bester aus 5“
  (`kits.<kampfart>.runden`).
- **Arenen:** Almwiese, Burghof, Bergwerk, Wirtshausgarten für alle Kampfarten mit Schwert und Bogen, dazu eigene:
  **Tanzboden** (Sumo: runder Holzboden auf einem Felsen über einem Bergsee), **Schneefeld** (Spleef: Schneeboden
  zwischen Eiswänden über einer Gletscherspalte), **Irrgarten** (Labyrinth: Heckenlabyrinth, in der Mitte das Ziel aus
  Gold und Smaragd mit einem Lichtstrahl, der über die Hecken hinweg die Richtung zeigt; vor jeder Runde wächst ein neuer
  Irrgarten – immer punktsymmetrisch, so dass beide Startecken gleich weit vom Ziel entfernt sind) und **Klammbrücke** (Brücke: zwei Inseln mit Toren in Teamfarben, dazwischen eine Brücke über die Klamm).
  Jede schwebt auf ihrer eigenen Insel und gibt es mehrfach (`arena-kopien`), so laufen viele Kämpfe gleichzeitig.
  Alles wird beim ersten Start aus dem Code gebaut (eigene leere Welt `duels`).
- **Kampfablauf:** Countdown 3-2-1 (eingefroren), Kampf, niemand stirbt wirklich – wer fallen würde, scheidet aus und
  schaut zu (in der Brücke steht man gleich wieder am eigenen Start). Sieg, wenn nur noch ein Team steht (im Labyrinth auch, wer zuerst am Ziel ist);
  `/verlassen` = Niederlage; nach 10 Minuten unentschieden – außer ein Team liegt bei Runden, Treffern (Boxen)
  oder Toren (Brücke) vorn, dann gewinnt es. Bei „Bester aus 3/5“ gibt es zwischen den Runden 3 Sekunden Pause, dann geht es frisch am Start weiter.
  10 Sekunden nach dem Ende geht es zurück in den Turnierhof. Nichts fällt herunter. Gebaut wird nur in UHC,
  Bau-Kampf und Brücke – abbauen lässt sich nur, was im Kampf gebaut wurde, und danach steht die Arena wieder wie neu.
- **Party-Kämpfe:** zwei Party-Leitungen fordern sich mit `/duel` heraus → **Party gegen Party** (alle, die im
  Turnierhof sind); `/partykampf` → in der eigenen Party **Jeder gegen jeden**, bis einer übrig ist, oder mit
  `teams` **Zwei Teams** (die Party wird zufällig in zwei gleich große Teams geteilt – geht auch mit der Brücke), bis zu
  8 Leute (`party.max-spieler`). Alles auch über Knöpfe im Kampfmenü (Spielweise umschalten), immer locker.
- **Zurück ins Spiel:** Verbindung mitten im Kampf weg (Spiel abgestürzt, Internet weg)? Der Platz bleibt 30 Sekunden
  frei (`rueckkehr-sekunden`). Fehlt dadurch ein ganzes Team, hält der Kampf an – alle eingefroren, die Uhr steht,
  „Warte auf …“; im 2 gegen 2 kämpft das Team weiter. Wer rechtzeitig zurückkommt, steht genau wieder dort, mit
  demselben Leben und derselben Ausrüstung, nach 3-2-1 geht es weiter. Wer nicht kommt, hat verloren.
- **Kit-Editor:** jede Ausrüstung (alle Kampfarten außer Sumo, jede Fähigkeit) lässt sich selbst anordnen, nach
  Gruppen sortiert – gilt ab dem nächsten Kampf.
- **Wertung und Statistik:** Elo je Kampfart (Start 1000, K-Faktor 32), Siege, Niederlagen, Unentschieden, Kills, Tode,
  Serien. Liegt lokal (`spieler/<uuid>.yml`, `rangliste.yml`) und geht ans Netzwerk. Die Ranglisten im Turnierhof
  wechseln zwischen den Gruppen.

## Befehle

| Befehl | Was er tut |
|---|---|
| `/duel <spieler> [kampfart] [arena] [1\|3\|5]` | herausfordern (ohne Kampfart: Menü mit Arena und Runden) – gilt 60 Sekunden; fordert eine Party-Leitung eine andere heraus, kämpft Party gegen Party |
| `/duel annehmen [spieler]` · `/duel ablehnen [spieler]` | Herausforderung annehmen/ablehnen (auch `accept`/`deny`, im Chat zum Anklicken) |
| `/partykampf [kampfart] [arena] [1\|3\|5] [teams]` | Party-Leitung: Jeder gegen jeden in der eigenen Party, mit `teams` zwei zufällige Teams (ohne Kampfart: Menü) |
| `/warteschlange <kampfart> <locker\|gewertet> <1v1\|2v2>` | Gegner suchen (auch `/queue sumo ranked 1v1`); `/warteschlange verlassen` |
| `/spectate <spieler>` | einem Kampf zuschauen (`/zuschauen`) |
| `/verlassen` | Warteschlange, Kampf (zählt als Niederlage) oder Zuschauen verlassen |
| `/stats [spieler]` · `/top [kampfart]` | Statistik und Rangliste (Kampfart deutsch oder englisch, z. B. `brücke`, `bridge`) |
| `/kit [kampfart\|fähigkeit]` | Kit-Editor (ohne Angabe: Auswahl nach Gruppen) |
| `/duels info\|neubauen\|neuladen` | Verwaltung (`moinmorni.duels.admin`); `neuladen` übernimmt auch Kampfarten an/aus und ihre Regeln |

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
  `wins_<kampfart>`, `losses_<kampfart>`, `draws_<kampfart>`, gewertet `ranked_games`, `ranked_wins`, bei
  „Bester aus 3/5“ `rounds_won`, Boxen `hits_boxen` (gelandete Treffer), Brücke `goals_bruecke` (Tore), Labyrinth `goals_labyrinth` (als Erstes am Ziel), Party-Kämpfe
  `party_games`, `party_wins`
- Bestwerte: `streak_best`, gewertet `elo_<kampfart>`

Kampfarten (`<kampfart>`): `klassisch`, `schwert`, `ritter`, `diamant`, `bogen`, `angel`, `kombo`, `modern`, `axt`,
`goldapfel`, `traenke`, `perlen`, `uhc`, `baukampf`, `faehigkeiten`, `sumo`, `boxen`, `spleef`, `labyrinth`, `bruecke`.

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
Statistik und Verbindungsabbrüche (rechtzeitig zurück, zu lange weg, 2 gegen 2) – und jede neue Kampfart einmal bis
zum Sieg (Sumo gewertet, Boxen, Spleef, Labyrinth mit Ziel und neuem Irrgarten, Brücke mit Toren, UHC mit Wasser,
Bau-Kampf …), „Bester aus 3“,
Party gegen Party, Jeder gegen jeden und Zwei Teams (`test/duels-test.cjs`). Pfade und Ports lassen sich per Umgebung ändern
(siehe Kopf von `test/run.sh`), auch für Paper 26.3 über ViaBackwards (`NO_MOVE=1`).

## Einstellungen

`plugins/MoinMorniDuels/config.yml`: Rückkehr nach Verbindungsabbruch, Welt, Arena-Kopien, Zeitlimit, Countdown,
Nachspiel, Ablauf von Herausforderungen, Elo (Start, K-Faktor, Spanne), Suppen-Heilung und die Kampfarten: jede
einzeln an/aus (`kits.<kampfart>.an`) und mit Runden in der Warteschlange (`kits.<kampfart>.runden`: 1, 3 oder 5),
Sumo (`sumo.aus-tiefe`: so tief unter dem Tanzboden scheidet man aus), Boxen (`boxen.treffer`), Brücke
(`bruecke.tore`), Kombo (`kombo.treffer-pause-ticks`), Angel (`angel.zug`), Labyrinth (`labyrinth.kompass`, `labyrinth.ziel`,
`labyrinth.jede-runde-neu`) und
Party-Kämpfe (`party.max-spieler`) – mit Erklärungen in der Datei. Den Duels-Ordner aus Version 0.1.1
(`plugins/MorniDuels`) holt das Plugin beim ersten Start von selbst zurück – Spielerdaten, Rangliste und
Einstellungen bleiben.

---

Verfasst mit Claude 🤖
