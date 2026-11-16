# MoinMorni Duels

@STAND.md

## Regeln für dieses Projekt

- Repo ist **öffentlich**: keine Geheimnisse, Tokens oder privaten Daten committen (Geheimes nur in `.env`, nie im Repo).
- Texte im Spiel auf Deutsch (Standard) und Englisch, freundlich, keine Fachbegriffe ohne Erklärung. Alle Texte stehen
  in `Texts.java` (MiniMessage, beide Sprachen; `TextsTest` prüft Platzhalter und dass Minecraft jedes Zeichen kann).
- **„Verfasst mit Claude 🤖“** als letzte Zeile in Commit-Messages (durch eine Leerzeile getrennt), in PRs, Issues und
  Release-Notes; einmal als Fußzeile im README.
- Commits mit Stichpunkten `- Neu:` / `- Behoben:` / `- Verbessert:` / `- Entfernt:`.
- Keine fremden Marken, Figuren, Namen, Texte oder Grafiken (keine Mojang-, Nintendo-, Hypixel- oder MCPVP-Inhalte):
  nur das Spielgefühl als Vorbild, eigene Namen, eigene Bauten.
- Die Netzwerk-Schnittstelle `src/api/java/de/moinmorni/api/MoinMorni.java` ist eine Kopie aus `pmornhart/moinmorni.net`
  (`plugins/moinmorni-network/src/main/java/de/moinmorni/api/MoinMorni.java`) und muss **genau gleich** bleiben. Sie wird
  nur mitübersetzt und kommt **nicht** in die Jar (die echten Klassen bringt das Netzwerk-Plugin mit).
- Duels muss auch ohne Netzwerk laufen (`softdepend: [MoinMorni]`; nur `net/Connected` kennt die Netzwerk-Klassen).
- `DuelsPlugin.NAME_0_1_1` (Plugin-Name in Version 0.1.1, `MorniDuels`) bleibt stehen: Damit holt Duels beim Start
  den Datenordner aus 0.1.1 zurück (`data/DataMove`) – der einzige gewollte Rest dieses Namens im Code.
- Paper 26.x, Java 25. Der Code muss auch gegen Paper 26.1 übersetzen (`mvn -Ppaper-26.1 compile`, läuft in der CI).
- Neue Version: `<version>` in `pom.xml` erhöhen und `docs/releases/v<version>.md` anlegen; ein Push auf `main` baut das
  Release mit `MoinMorni-Duels.jar` – das Netzwerk holt es sich von dort.
- Vor jeder Veröffentlichung (Merge nach `main`) Philips Go abwarten.
- Ändern sich Turnierhof oder Arenen: `Construction.VERSION` erhöhen – dann baut der Server beim nächsten Start neu.
- Nach jeder sichtbaren Änderung Bilder wie im echten Minecraft (echte Client-Texturen nur lokal, nie ins Repo).
- „Alles im Panel“: jede Einstellung, die per Befehl geht, muss auch im Panel gehen. Darum hat Duels keine
  Einstellungs-Befehle, nur die `config.yml`; die Anbindung an den Einstellungs-Katalog gehört ins Netzwerk-Repo.
- Echter Test nach jeder Änderung am Spielablauf: `test/run.sh` (siehe README).
- `STAND.md` nach jedem größeren Schritt aktualisieren.
