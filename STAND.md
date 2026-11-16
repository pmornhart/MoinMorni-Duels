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
- `data/`: `KitStats`, `PlayerData` (YAML), `Store` (speichert im Hintergrund), `Leaderboard`.
- `world/`: `VoidGenerator`, `Plan` (Bauplan), `Decor`, `HallPlan` (Turnierhof), `ArenaPlans` (4 Arenen), `Arena`,
  `Arenas`, `Construction` (baut nur bei neuer Bau-Version, Halle sofort, Arenen nach und nach), `BlockLog`.
- `hall/`: `Hall` (Knöpfe, Zustand), `HallBoards` (Schrift je Sprache). `menu/`: `Menu`, `Menus`, `KitEditor`.
- `listen/`: `PlayerListener`, `CombatListener`, `GuardListener`.
- `src/api/java/de/moinmorni/api/MoinMorni.java`: Kopie der Netzwerk-Schnittstelle (nicht in der Jar).
- `test/`: `run.sh` (echter Server), `duels-test.cjs` (mineflayer), `rcon.cjs`, `fake-network/` (Test-Netzwerk).

## Stand (15.11.2026)

- Version 0.1.0 (erster Stand, Branch `claude/erster-stand`, noch nicht veröffentlicht): alles oben Genannte.
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

- Philip: Bilder im echten Client ansehen; Go für `main` (dann erstes Release v0.1.0).
- Einstellungen (Zeitlimit, Elo, Arenen …) im Panel des Netzwerks (Einstellungs-Katalog) – gehört ins Netzwerk-Repo.
