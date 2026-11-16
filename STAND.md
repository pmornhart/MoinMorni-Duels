# Stand – MoinMorni Duels

**Was es ist:** Der Duell-Modus des MoinMorni-Netzwerks (Paper-Plugin `MoinMorniDuels`, Java 25, Paper 26.x).
Entscheidungen von Philip (14.11.2026): Klassisch (Suppe, ohne Abklingzeit) / Modern / Fähigkeiten + Kit-Editor,
gewertet + locker, 1 gegen 1 und 2 gegen 2, `/duel`, Ranglisten; Spielgefühl wie früher auf klassischen
PvP-Servern – nur Mechanik, eigene Namen und eigene Optik.

## Aufbau

- `src/main/java/de/moinmorni/duels/`: `DuelsPlugin` (Start, Welt, Aufgaben), `Settings` (config.yml), `Texts` (de/en),
  `Ui`, `Items`, `Commands`, `Challenges` (/duel).
- `net/`: `Network` (Schnittstelle), `Connected` (über `de.moinmorni.api.MoinMorni`), `Standalone` (ohne Netzwerk).
- `kit/`: `Kit`, `Ability`, `Layout` (eigene Anordnung, rein), `Loadouts` (Ausrüstungen).
- `match/`: `Elo`, `Matchmaker` (rein, mit Tests), `QueueKey`, `Queues` (Warteschlangen, Party).
- `fight/`: `Fight`, `Fights` (Start, Countdown, Ausscheiden, Ende, Wertung, Statistik), `Abilities`.
- `data/`: `KitStats`, `PlayerData` (YAML), `Store` (speichert im Hintergrund), `Leaderboard`, `DataMove` (holt den
  Datenordner aus 0.1.1 zurück).
- `world/`: `VoidGenerator`, `Plan` (Bauplan), `Decor`, `HallPlan` (Turnierhof), `ArenaPlans` (4 Arenen), `Arena`,
  `Arenas`, `Construction` (baut nur bei neuer Bau-Version, Halle sofort, Arenen nach und nach), `BlockLog`.
- `hall/`: `Hall` (Knöpfe, Zustand), `HallBoards` (Schrift je Sprache). `menu/`: `Menu`, `Menus`, `KitEditor`.
- `listen/`: `PlayerListener`, `CombatListener`, `GuardListener`.
- `src/api/java/de/moinmorni/api/MoinMorni.java`: Kopie der Netzwerk-Schnittstelle (nicht in der Jar).
- `test/`: `run.sh` (echter Server), `duels-test.cjs` (mineflayer), `rcon.cjs`, `fake-network/` (Test-Netzwerk).

## Stand (16.11.2026)

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

- Philip: Go für `main` (dann Release v0.1.2). Das Netzwerk holt dann wieder `MoinMorni-Duels.jar` (Plugin
  `MoinMorniDuels`) und muss die Jar aus 0.1.1 vom Duels-Server löschen.
- Einstellungen (Zeitlimit, Elo, Arenen …) im Panel des Netzwerks (Einstellungs-Katalog) – gehört ins Netzwerk-Repo.
