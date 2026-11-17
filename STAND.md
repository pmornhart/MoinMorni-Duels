# Stand – MoinMorni Duels

**Was es ist:** Der Duell-Modus des MoinMorni-Netzwerks (Paper-Plugin `MoinMorniDuels`, Java 25, Paper 26.x).
Entscheidungen von Philip (14.11.2026): Klassisch (Suppe, ohne Abklingzeit) / Modern / Fähigkeiten + Kit-Editor,
gewertet + locker, 1 gegen 1 und 2 gegen 2, `/duel`, Ranglisten; Spielgefühl wie früher auf klassischen
PvP-Servern – nur Mechanik, eigene Namen und eigene Optik. Seit 0.2.0 (16.11.): 20 Kampfarten in drei Gruppen
(Klassisch, Modern, Spaß), vier eigene Arenen (Tanzboden, Schneefeld, Irrgarten, Klammbrücke), „Bester aus 1/3/5“,
Party gegen Party und Jeder gegen jeden.

## Aufbau

- `src/main/java/de/moinmorni/duels/`: `DuelsPlugin` (Start, Welt, Aufgaben), `Settings` (config.yml), `KitSettings`
  (Kampfarten an/aus, Runden, Regeln von Sumo/Boxen/Brücke/Kombo/Angel/Labyrinth/Party), `Texts` (de/en), `Ui`,
  `Items`, `Commands`, `Challenges` (/duel mit Kampfart, Arena, Runden; Party gegen Party), `PartyFights`
  (/partykampf: Jeder gegen jeden oder Zwei Teams).
- `net/`: `Network` (Schnittstelle), `Connected` (über `de.moinmorni.api.MoinMorni`), `Standalone` (ohne Netzwerk).
- `kit/`: `Kit` (20 Kampfarten, Gruppen, Regeln je Kampfart), `Ability`, `Layout` (eigene Anordnung, rein),
  `Loadouts` (Ausrüstungen; Verzauberungen und Effekte als Namen, erst auf dem Server aufgelöst).
- `match/`: `Elo`, `Matchmaker` (rein, mit Tests), `QueueKey`, `Queues` (Warteschlangen, Party).
- `fight/`: `Fight` (Teams, Art: Warteschlange/Herausforderung/Party/Jeder gegen jeden, Runden, Pause), `Fights`
  (Start, Countdown, Runden, Ausscheiden, Tore, Ziel, Treffer, Ende, Wertung, Statistik), `Tally` (Runden, Treffer,
  Tore – rein), `Zones` (Tore, Ziel im Irrgarten, Bauzonen, Sumo-Tiefe – rein), `Building` (was im Kampf gebaut und
  abgebaut werden darf), `Abilities`, `PlayerState`.
- `data/`: `KitStats`, `PlayerData` (YAML), `Store` (speichert im Hintergrund), `Leaderboard`, `DataMove` (holt den
  Datenordner aus 0.1.1 zurück).
- `world/`: `VoidGenerator`, `Plan` (Bauplan), `Decor`, `HallPlan` (Turnierhof), `ArenaType` (8 Arenen),
  `ArenaPlans` (4 Arenen für alle), `SpecialArenas` (Tanzboden, Schneefeld, Irrgarten, Klammbrücke), `ArenaShape`
  (Startplätze, Blick, Grenzen – rein), `Maze` (drehsymmetrischer Irrgarten – rein), `Arena` (auch neuer Irrgarten je
  Runde), `Arenas`, `Construction` (baut nur bei neuer Bau-Version, Halle sofort, Arenen nach und nach), `BlockLog`.
- `hall/`: `Hall` (Knöpfe, Zustand), `HallBoards` (Schrift je Sprache, wechselt zwischen den Gruppen). `menu/`:
  `Menu`, `Menus` (Kampfmenü nach Gruppen, Kampfart-Menü, Herausforderung mit Arena und Runden), `KitEditor`.
- `listen/`: `PlayerListener`, `CombatListener` (Regeln je Kampfart), `GuardListener` (Bauen, Wasser/Lava im UHC).
- `src/api/java/de/moinmorni/api/MoinMorni.java`: Kopie der Netzwerk-Schnittstelle (nicht in der Jar).
- `test/`: `run.sh` (echter Server), `duels-test.cjs` (mineflayer), `rcon.cjs`, `fake-network/` (Test-Netzwerk).

## Stand (16.11.2026)

- Version 0.2.0 (Branch `claude/mehr-kits`, baut auf 0.1.3 = `main` d1ef6c0 auf, wartet auf Philips Go): **Viel mehr
  Kampfarten** (Philip: „mehr Kits, so wie auf den großen PvP-Servern – Maze, Only Sword und sowas alles“). 20 Kampfarten in
  drei Gruppen – Klassisch (keine Abklingzeit): Klassisch, Nur Schwert, Ritter, Diamant, Bogen, Angel, Kombo; Modern:
  Modern, Axt und Schild, Goldapfel, Tränke, Enderperlen, UHC, Bau-Kampf; Spaß: Fähigkeiten, Sumo, Boxen, Spleef,
  Labyrinth, Brücke. Jede mit eigenem Symbol und kurzer Erklärung (de/en), Warteschlangen (locker/gewertet, 1v1/2v2),
  Elo, Rangliste und Statistik; Kit-Editor nach Gruppen. Vier eigene Arenen (`SpecialArenas`, Bau-Version 2): Tanzboden
  (Sumo), Schneefeld (Spleef), Irrgarten (Labyrinth), Klammbrücke (Brücke mit Toren). Regeln: Sumo ohne Schaden,
  Herunterfallen = raus (Kill für den letzten Stoß); Boxen zählt Treffer bis `boxen.treffer`; Spleef: nur der Schneeboden
  ist abbaubar, Schläge zählen nicht; Labyrinth: vor jeder Runde ein neuer Irrgarten (`Maze`, drehsymmetrisch – alle vier
  Ecken gleich weit vom Ziel; `Arena.reshuffleMaze` setzt nur geänderte Blöcke), wer zuerst auf dem Ziel in der Mitte
  steht (Gold/Smaragd mit Leuchtfeuer) oder alle Gegner besiegt, gewinnt die Runde – bis zu vier Seiten starten in den
  Ecken, mit mehr zählt nur der Kampf; Kompass zum nächsten Gegner; Brücke: Blöcke in Teamfarbe, Tor = Punkt,
  Fallen/Besiegt = zurück an den Start; UHC/Bau-Kampf/Brücke: nur eigene Blöcke abbauen, Wasser/Lava nur im UHC, danach
  wird die Arena zurückgesetzt. „Bester aus 1/3/5“ (Warteschlange je Kampfart in der config.yml, Herausforderung frei
  wählbar), `/duel <spieler> [kampfart] [arena] [1|3|5]`, Party gegen Party (zwei Party-Leitungen), in der eigenen Party
  Jeder gegen jeden oder Zwei Teams (`/partykampf … [teams]`, zufällig gleich groß geteilt, auch Brücke), Kampfmenü nach
  Gruppen, Ranglisten in der Halle wechseln. Behoben: Fehler im Protokoll beim Abschalten mitten im Kampf
  (`HallBoards.show`). Neue config-Schlüssel: `kits.<kampfart>.an`, `kits.<kampfart>.runden`, `sumo.aus-tiefe`,
  `boxen.treffer`, `bruecke.tore`, `kombo.treffer-pause-ticks`, `angel.zug`, `labyrinth.kompass`, `labyrinth.ziel`,
  `labyrinth.jede-runde-neu`, `party.max-spieler`. Neue Statistik-Werte: `hits_boxen`, `goals_bruecke`,
  `goals_labyrinth`, `rounds_won`, `party_games`, `party_wins` (dazu `games_`/`wins_`/`losses_`/`draws_`/`elo_<kampfart>`
  für alle 20). „Zurück ins Spiel“ (0.1.3) gilt in allen Kampfarten, auch zwischen Runden und mit mehr als zwei Teams.
  `test/run.sh` mit `XMX`, `XMS`, `JAVA_OPTS`, `COPIES` für einen knappen Rechner.
  TESTZEILE
- Version 0.1.3 (16.11. veröffentlicht, `main` d1ef6c0, dazu JUnit 6.1.3): **Zurück ins Spiel** (Philip: „Wenn das
  Game crasht, soll man zurück zum Spiel können“ und vor der Lobby nachfragen, solange man spielt). Netzwerk-
  Schnittstelle 2 (Kopie byte-gleich): `playing` vom Countdown bis zum Ausscheiden bzw. Ende (`Connected` meldet nur
  Änderungen), das Bett ruft `askToLobby`, Verbindung mitten im Kampf weg → Platz `rueckkehr-sekunden` frei (oben in
  der config.yml, Vorgabe 30, 0 = aus) und `holdSlot(uuid, s)`. Fehlt dadurch ein ganzes Team, hält der Kampf an
  (`Fight.paused`: alle eingefroren, keine Treffer, keine Heilung, Uhr steht, „Warte auf … – n s“ in Aktionsleiste und
  Leiste oben); im 2 gegen 2 kämpft das Team weiter. Rechtzeitig zurück → `PlayerState` stellt Ort, Spielmodus,
  Inventar, Leben, Absorption, Hunger, Effekte wieder her (Ort schon beim Betreten über
  `AsyncPlayerSpawnLocationEvent`), Abklingzeiten der Fähigkeiten bleiben, 3-2-1 → „Weiter!“, `holdSlot(uuid, 0)`.
  Zu spät → scheidet aus wie bisher (`Cause.GONE`, Niederlage), Hinweis beim nächsten Betreten; Kampfende oder Abbruch
  → `holdSlot(uuid, 0)`. Älteres Netzwerk-Plugin (`LinkageError`) → einmal Hinweis im Log, alles wie bisher.
  Test-Netzwerk schreibt `playing`, `ask-to-lobby` (fragt nur, solange „spielt“), `hold-slot` mit und spielt mit
  `api=1` ein älteres Netzwerk-Plugin. Geprüft: 46 Unit-Tests (neu `SettingsTest`), Paper 26.1 übersetzt, echter Test
  (Paper 26.1.2, `test/run.sh`, `rueckkehr-sekunden: 12`) **110/110**, Log ohne Fehler – neu: „spielt“ im Kampf und
  danach nicht mehr, in Halle/Warteschlange nie; 1 gegen 1: Bert geht → Kampf angehalten, „Warte auf Bert – 12 s“,
  holdSlot 12, Anna eingefroren, kein Sieg; Bert zurück → gleiche Stelle, gleiches Leben (14), gleiche Ausrüstung,
  „Welcome back“, 3-2-1 „Weiter!“, Uhr stand still (40 s → 39 s nach 8 s), holdSlot 0, Anna besiegt ihn; zu lange weg
  → „nicht rechtzeitig zurückgekommen“, Sieg für Anna, holdSlot 12 → 0, Hinweis beim Wiederkommen, Statistik;
  2 gegen 2: Team kämpft weiter (nicht eingefroren), Bert zurück auf seiner Seite mit Ausrüstung; Bett → `askToLobby`;
  älteres Netzwerk-Plugin → Bett geht wie bisher über `toLobby`.
- Version 0.1.2 (Branch `claude/moinmorni`, wartet auf Philips Go): **Name wieder MoinMorni** – Version 0.1.1
  (16.11. veröffentlicht) hatte Duels kurz umbenannt (Plugin `MorniDuels`); der neue Name war nicht verfügbar, darum
  ist alles wieder wie in 0.1.0 (Umbenennung per `git revert` zurückgenommen). Der Datenordner aus 0.1.1 zieht in
  `onLoad` zurück nach `plugins/MoinMorniDuels` (`DataMove`: fehlt der Ordner, nur umbenannt; sonst zusammengeführt,
  Doppeltes nach `aus-0.1.1/`); liegt die Jar aus 0.1.1 noch daneben, steht ein Hinweis im Log. `test/run.sh` löscht
  alle alten Jars im Test-Server. Geprüft: 44 Unit-Tests (4 für den Rückumzug), Paper 26.1 übersetzt; echt mit
  Paper 26.1.2: Rückumzug des Test-Ordners aus 0.1.1 (Spieler, Rangliste, `bau.yml` → kein Neubau, dessen
  config.yml in `aus-0.1.1/`, `vor-umbenennung/` kommt mit), Hinweis im Log mit einer Attrappe „MorniDuels“, danach
  `test/run.sh` **86/86**, Duels meldet sich beim Test-Netzwerk „MoinMorni“, Log ohne Fehler.
- Version 0.1.0 (15.11. veröffentlicht, Release v0.1.0): alles oben Genannte.
- Geprüft: 40 Unit-Tests (Elo, Matchmaker, Anordnung, Statistik, YAML, Texte). Echter Test mit Paper 26.1.2 und vier
  Test-Spielern (mineflayer): 86/86 (Halle, Sprache, Menüs, Kit-Editor, Warteschlange, Countdown eingefroren, Suppe,
  Kampf bis zum Ausscheiden, Sieg/Niederlage, zurück in die Halle, /stats, /top, Elo ±16, Netzwerk-Statistik und
  Zustand, Herausforderung per Befehl und per Rechtsklick, Ablehnen, Ablauf, Aufgeben, Verbindung weg, Zuschauen,
  Party-2v2, Party-Mitglied darf nicht anmelden, Fähigkeiten Bergführer und Jäger, Modern-Kit, Unentschieden nach dem
  Zeitlimit, zur Lobby). Paper 26.3 über ViaBackwards (Test-Spieler ohne Bewegung): 82/85 – nur „Rechtsklick auf einen
  Spieler“ kommt über Via nicht an.
- Nicht echt geprüft: Aussehen von Halle und Arenen im echten Client, Schmied und Kräuterfrau, alter Rückstoß
  (mineflayer wendet Geschwindigkeit nicht an), Bedrock-Spieler, Ranglisten-Schrift für Bedrock, Netzwerk mit echtem Hub.

## Offen

- Netzwerk-Repo: Einstellungs-Katalog um `rueckkehr-sekunden` ergänzen (0–600, Vorgabe 30); Nachfrage vor der Lobby
  und „Zurück ins Spiel“ baut das Netzwerk-Plugin.
- Nicht echt geprüft: Rückkehr mit echtem Client über das echte Netzwerk (Lobby-Angebot), Bedrock.
- Einstellungen (Zeitlimit, Elo, Arenen …) im Panel des Netzwerks (Einstellungs-Katalog) – gehört ins Netzwerk-Repo;
  neu seit 0.2.0: `kits.<kampfart>.an` (20 Schalter), `kits.<kampfart>.runden` (1/3/5, nicht bei `bruecke`),
  `sumo.aus-tiefe` (0,5–20), `boxen.treffer` (1–1000), `bruecke.tore` (1–20), `kombo.treffer-pause-ticks` (0–10),
  `angel.zug` (0–3), `labyrinth.kompass`, `labyrinth.ziel`, `labyrinth.jede-runde-neu` (je an/aus, Vorgabe an),
  `party.max-spieler` (2–8). Webseite/Statistik: Kampfarten und Werte `hits_boxen`, `goals_bruecke`,
  `goals_labyrinth`, `rounds_won`, `party_games`, `party_wins`.
- Philip: Go für `main` mit 0.2.0 (mehr Kampfarten).
