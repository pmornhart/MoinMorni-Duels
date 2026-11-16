/**
 * Echter Test von MoinMorni Duels mit Test-Spielern (mineflayer) auf Paper 26.1.2 – gestartet von test/run.sh.
 *   node duels-test.cjs <host> <port> <rcon-host:port> <rcon-passwort-datei> <server-ordner>
 *
 * Spieler: Anna (deutsch, Party-Leiterin mit Bert), Bert (englisch), Carl, Dora (deutsch). Sprache und Party kommen
 * vom Test-Netzwerk (test/fake-network), das auch Statistik, Zustand, „zur Lobby“, „spielt gerade“ und freie Plätze
 * (Verbindung weg) in calls.log mitschreibt. Im Test bleibt ein Platz 12 Sekunden frei (rueckkehr-sekunden).
 *
 * Verfasst mit Claude 🤖
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const { rcon } = require('./rcon.cjs');

const [HOST = '127.0.0.1', PORT = '25720', RCON = '127.0.0.1:25820', PASS_FILE, SERVER] = process.argv.slice(2);
const PASS = fs.readFileSync(PASS_FILE, 'utf8').trim();
const VERSION = process.env.MC_VERSION ?? '26.1';
const HALL = { x: 0.5, y: 101, z: 12.5 };
const AFTER_MS = 4000; // nachspiel-sekunden im Test
const TIME_LIMIT_MS = 40000; // zeitlimit-sekunden im Test
const RETURN_S = 12; // rueckkehr-sekunden im Test
const NO_MOVE = process.env.NO_MOVE === '1'; // Paper 26.3 über ViaBackwards: Test-Spieler ohne Bewegung

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok) });
  console.log(`${ok ? '✓' : '✗'} ${name}${detail ? ` – ${detail}` : ''}`);
}

/** Text aus Chat-Komponenten (String, JSON oder NBT) zusammensetzen. */
function plain(v) {
  if (v == null) return '';
  if (typeof v === 'string') {
    if (v.startsWith('{') || v.startsWith('[')) {
      try {
        return plain(JSON.parse(v));
      } catch {
        return v;
      }
    }
    return v;
  }
  if (Array.isArray(v)) return v.map(plain).join('');
  if (typeof v !== 'object') return String(v);
  if (v.type === 'compound') return plain(v.value);
  if (v.type === 'list') return plain(v.value?.value ?? []);
  if (v.type === 'string') return v.value;
  let s = '';
  if ('text' in v) s += plain(v.text);
  if ('' in v) s += plain(v['']);
  if ('translate' in v) s += plain(v.translate);
  if ('extra' in v) s += plain(v.extra);
  return s;
}

function offlineUuid(name) {
  const h = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

const cmd = (...commands) => rcon(RCON, PASS, commands);
const playerFile = (name) =>
  path.join(SERVER, 'plugins/MoinMorniDuels/spieler', `${offlineUuid(name)}.yml`);
const readFile = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '');
const calls = () =>
  readFile(path.join(SERVER, 'plugins/MoinMorni/calls.log'))
    .split('\n')
    .filter(Boolean)
    .map((l) => JSON.parse(l));

async function waitFor(cond, ms = 5000, step = 100) {
  const start = Date.now();
  while (Date.now() - start < ms) {
    if (await cond()) return true;
    await sleep(step);
  }
  return Boolean(await cond());
}

let lastJoin = 0;
async function join(name) {
  const wait = lastJoin + 1500 - Date.now();
  if (wait > 0) await sleep(wait);
  lastJoin = Date.now();
  const bot = mineflayer.createBot({
    host: HOST,
    port: Number(PORT),
    username: name,
    version: VERSION,
    auth: 'offline',
    physicsEnabled: !NO_MOVE,
  });
  if (NO_MOVE) {
    // Über ViaBackwards wirft Paper 26.3 Test-Spieler wegen ihrer Bewegungs-Pakete hinaus – also keine schicken.
    const write = bot._client.write.bind(bot._client);
    const MOVES = new Set(['position', 'position_look', 'look', 'flying', 'player_input']);
    bot._client.write = (name, params) => (MOVES.has(name) ? undefined : write(name, params));
  }
  bot.chats = [];
  bot.bars = [];
  bot.titles = [];
  bot.kickReason = null;
  bot.pushes = [];
  bot.on('messagestr', (m, position) => (position === 'game_info' ? bot.bars : bot.chats).push(m));
  // Aktionsleiste kommt als eigenes Paket; Geschwindigkeit (Sprung, Rückstoß) wendet mineflayer bei sich nicht an –
  // darum die rohen Pakete mitschreiben.
  bot._client.on('action_bar', (p) => bot.bars.push(plain(p.text)));
  bot._client.on('entity_velocity', (p) => {
    if (bot.entity && p.entityId === bot.entity.id) bot.pushes.push(p.velocity);
  });
  bot._client.on('set_title_text', (p) => bot.titles.push(plain(p.text)));
  bot.bossTitle = '';
  const boss = (b) => {
    bot.bossTitle = b.title?.toString?.() ?? '';
  };
  bot.on('bossBarCreated', boss);
  bot.on('bossBarUpdated', boss);
  bot.on('kicked', (r) => {
    bot.kickReason = plain(r);
  });
  bot.on('error', (e) => console.log(`  (${name}: ${e.message})`));
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    bot.once('kicked', (r) => reject(new Error(`${name} gekickt: ${plain(r)}`)));
    setTimeout(() => reject(new Error(`${name}: kein Spawn`)), 20000);
  });
  await sleep(1500);
  return bot;
}

const slot = (bot, i) => bot.inventory.slots[36 + i];
const near = (p, q, d) => Math.abs(p.x - q.x) < d && Math.abs(p.y - q.y) < d && Math.abs(p.z - q.z) < d;
const inHall = (bot) => near(bot.entity.position, HALL, 3) && bot.game.gameMode === 'adventure';
const inArena = (bot) => Math.abs(bot.entity.position.x) > 500 || Math.abs(bot.entity.position.z) > 500;
const sees = (bot, re) => bot.chats.some((c) => re.test(c));
const sawTitle = (bot, re) => bot.titles.some((t) => re.test(t));
const clear = (...bots) =>
  bots.forEach((b) => {
    b.chats.length = 0;
    b.bars.length = 0;
    b.pushes.length = 0;
    b.titles.length = 0;
  });

async function openWithItem(bot, hotbar) {
  bot.setQuickBarSlot(hotbar);
  await sleep(200);
  const opened = new Promise((resolve) => {
    bot.once('windowOpen', resolve);
    setTimeout(() => resolve(null), 4000);
  });
  bot.activateItem();
  return opened;
}

async function nextWindow(bot) {
  return new Promise((resolve) => {
    bot.once('windowOpen', resolve);
    setTimeout(() => resolve(null), 4000);
  });
}

/** Wartet, bis der Kampf läuft (Titel „Los!“/„Fight!“). */
const started = (bot) => waitFor(() => sawTitle(bot, /Los!|Fight!/), 9000);

/** Angreifer stellt sich vor das Opfer und schlägt zu, bis es ausscheidet. */
async function fightUntilDown(attacker, victim, swordSlot) {
  attacker.setQuickBarSlot(swordSlot);
  for (let i = 0; i < 60 && victim.game.gameMode !== 'spectator'; i++) {
    const v = victim.entity.position;
    const a = attacker.entity.position;
    if (a.distanceTo(v) > 2.6) {
      // „execute at“: gleiche Welt wie das Opfer (ein einfaches tp der Konsole ginge in die Hauptwelt).
      const side = a.z > v.z ? 1 : -1;
      await cmd(`execute at ${victim.username} run tp ${attacker.username} ~ ~ ~${(side * 1.8).toFixed(1)}`);
      await sleep(250);
    }
    const target = attacker.players[victim.username]?.entity;
    if (target) {
      await attacker.lookAt(target.position.offset(0, 1.4, 0), true);
      attacker.attack(target);
    }
    await sleep(550);
  }
  return victim.game.gameMode === 'spectator';
}

/** Herausforderung schicken, annehmen und warten, bis der Kampf läuft. */
async function duel(a, b, args) {
  clear(a, b);
  a.chat(`/duel ${b.username} ${args}`);
  await waitFor(() => sees(b, /fordert dich zum Duell heraus|challenges you to a duel/), 4000);
  b.chat(`/duel annehmen ${a.username}`);
  const ok = await waitFor(() => inArena(a) && inArena(b), 8000);
  await started(a);
  return ok;
}

/** Warten, bis alle wieder in der Halle sind. */
const allInHall = (...bots) => waitFor(() => bots.every(inHall), AFTER_MS + 8000);

/** Bogen: Schütze stellt sich 6 Blöcke vor das Ziel, spannt voll und lässt los – bis das Ziel ausscheidet. */
async function shootUntilDown(shooter, victim, bowSlot) {
  shooter.setQuickBarSlot(bowSlot);
  for (let i = 0; i < 25 && victim.game.gameMode !== 'spectator'; i++) {
    const side = shooter.entity.position.z > victim.entity.position.z ? 1 : -1;
    await cmd(`execute at ${victim.username} run tp ${shooter.username} ~ ~ ~${side * 6}`);
    await sleep(300);
    const target = shooter.players[victim.username]?.entity;
    if (target) {
      await shooter.lookAt(target.position.offset(0, 1.2, 0), true);
      shooter.activateItem();
      await sleep(1150);
      shooter.deactivateItem();
    }
    await sleep(700);
  }
  return victim.game.gameMode === 'spectator';
}

/** Block per RCON prüfen (Welt „duels“). */
async function blockIs(x, y, z, block) {
  const out = (await cmd(`execute in minecraft:duels if block ${x} ${y} ${z} ${block}`)).join(' ');
  return /passed/i.test(out);
}

/** Abbauen mit Zeitlimit (verweigert der Server, kommt mineflayer sonst nie zurück). */
async function digSoon(bot, block, ms = 3000) {
  try {
    await Promise.race([bot.dig(block, true), sleep(ms)]);
  } catch {
    // abgebrochen – zählt wie nicht abgebaut
  }
  await sleep(300);
}

/** Party-Einstellungen im Test-Netzwerk austauschen (wird bei jedem Aufruf neu gelesen). */
function parties(...lines) {
  const file = path.join(SERVER, 'plugins/MoinMorni/fake.properties');
  const keep = readFile(file).split('\n').filter((l) => l && !l.startsWith('party.'));
  fs.writeFileSync(file, [...keep, ...lines].join('\n') + '\n');
}

async function main() {
  // ------------------------------------------------------------------ Ankunft
  const anna = await join('Anna');
  let bert = await join('Bert');
  check('Ankunft in der Wartehalle (Turnierhof)', inHall(anna), JSON.stringify(anna.entity.position));
  check(
    'Schnellleiste: Schwert, Amboss, Gold, Bett',
    slot(anna, 0)?.name === 'iron_sword' && slot(anna, 1)?.name === 'anvil' &&
      slot(anna, 7)?.name === 'gold_ingot' && slot(anna, 8)?.name === 'red_bed',
    [0, 1, 7, 8].map((i) => slot(anna, i)?.name).join(','),
  );
  check('Deutsch: „Kampf wählen“', plain(slot(anna, 0)?.customName).includes('Kampf wählen'), plain(slot(anna, 0)?.customName));
  check('Englisch (Sprache vom Netzwerk): „Choose a fight“', plain(slot(bert, 0)?.customName).includes('Choose a fight'),
    plain(slot(bert, 0)?.customName));
  check('Begrüßung im Chat', sees(anna, /Willkommen im Turnierhof/));

  // ------------------------------------------------------------------ Kit-Editor
  let win = await openWithItem(anna, 1);
  check('Amboss öffnet „Kit bearbeiten“', win && plain(win.title).includes('Kit bearbeiten'), win && plain(win.title));
  const editorOpen = nextWindow(anna);
  await anna.clickWindow(10, 0, 0);
  win = await editorOpen;
  check('Kit-Editor „Klassisch“ öffnet', win && plain(win.title).includes('Klassisch'), win && plain(win.title));
  check('Editor: Schwert in der Schnellleiste (Platz 27), Rest Suppen',
    win?.slots[27]?.name === 'diamond_sword' && win?.slots[31]?.name === 'mushroom_stew' &&
      win.slots.slice(0, 36).filter((s) => s?.name === 'mushroom_stew').length === 35,
    `${win?.slots[27]?.name} / ${win?.slots.slice(0, 36).filter((s) => s?.name === 'mushroom_stew').length} Suppen`);
  await anna.clickWindow(27, 0, 0); // Schwert nehmen
  await sleep(150);
  await anna.clickWindow(31, 0, 0); // auf Platz 5 der Schnellleiste (Suppe kommt auf den Mauszeiger)
  await sleep(150);
  await anna.clickWindow(27, 0, 0); // Suppe ablegen
  await sleep(150);
  await anna.clickWindow(51, 0, 0); // Speichern
  await sleep(800);
  check('Kit gespeichert (Chat)', sees(anna, /Gespeichert/), anna.chats.slice(-1)[0]);
  check('Anordnung in der Spielerdatei', /klassisch: sword:4/.test(readFile(playerFile('Anna'))),
    (readFile(playerFile('Anna')).match(/anordnung:[\s\S]*/) ?? [''])[0].trim());
  check('Nach dem Editor wieder die Knöpfe', slot(anna, 0)?.name === 'iron_sword');

  // ------------------------------------------------------------------ Locker, Klassisch, 1 gegen 1 (Menü)
  clear(anna, bert);
  win = await openWithItem(anna, 0);
  check('Schwert öffnet „Kampf wählen“', win && plain(win.title).includes('Kampf wählen'), win && plain(win.title));
  check('Menü nach Gruppen: Klassisch, Modern, Spaß (je eine Reihe)',
    plain(win?.slots[9]?.customName) === 'Klassisch' && plain(win?.slots[18]?.customName) === 'Modern' &&
      plain(win?.slots[27]?.customName) === 'Spaß', [9, 18, 27].map((i) => plain(win?.slots[i]?.customName)).join(','));
  const kitNames = (w, row) => [1, 2, 3, 4, 5, 6, 7, 8].map((c) => plain(w?.slots[row * 9 + c]?.customName)).filter(Boolean);
  check('Klassisch: Klassisch, Nur Schwert, Ritter, Diamant, Bogen, Angel, Kombo',
    kitNames(win, 1).join(',') === 'Klassisch,Nur Schwert,Ritter,Diamant,Bogen,Angel,Kombo', kitNames(win, 1).join(','));
  check('Modern: Modern, Axt und Schild, Goldapfel, Tränke, Enderperlen, UHC, Bau-Kampf',
    kitNames(win, 2).join(',') === 'Modern,Axt und Schild,Goldapfel,Tränke,Enderperlen,UHC,Bau-Kampf',
    kitNames(win, 2).join(','));
  check('Spaß: Fähigkeiten, Sumo, Boxen, Spleef, Labyrinth, Brücke',
    kitNames(win, 3).join(',') === 'Fähigkeiten,Sumo,Boxen,Spleef,Labyrinth,Brücke', kitNames(win, 3).join(','));
  check('Party-Knöpfe: Jeder gegen jeden, Party gegen Party',
    plain(win?.slots[38]?.customName).includes('Jeder gegen jeden') && plain(win?.slots[42]?.customName).includes('Party gegen Party'));
  let kitMenu = nextWindow(anna);
  await anna.clickWindow(10, 0, 0);
  win = await kitMenu;
  check('Klick auf Klassisch: eigenes Menü mit Locker/Gewertet × 1v1/2v2',
    win && plain(win.title).includes('Klassisch – Kampf wählen') &&
      plain(win.slots[21]?.customName).includes('Locker') && plain(win.slots[25]?.customName).includes('Gewertet') &&
      win.slots[25]?.count === 2, win && `${plain(win.title)} / ${plain(win.slots[25]?.customName)}`);
  await anna.clickWindow(21, 0, 0);
  await sleep(500);
  check('Anna sucht (Klassisch · Locker · 1 gegen 1)', sees(anna, /Du suchst Gegner: Klassisch · Locker · 1 gegen 1/));
  check('Knopf „Warteschlange verlassen“ erscheint', slot(anna, 4)?.name === 'barrier', slot(anna, 4)?.name);
  check('Aktionsleiste: „Suche Gegner“ mit Zeit', await waitFor(() => anna.bars.some((b) => /Suche Gegner.*0:0\d/.test(b)), 2500),
    anna.bars.slice(-1)[0]);
  win = await openWithItem(bert, 0);
  const english = win && plain(win.title).includes('Choose a fight') && plain(win.slots[18]?.customName) === 'Modern' &&
    plain(win.slots[27]?.customName) === 'Fun';
  kitMenu = nextWindow(bert);
  if (win) await bert.clickWindow(10, 0, 0);
  win = await kitMenu;
  check('Englisches Menü: „Choose a fight“, „Fun“, „Classic – choose a fight“ / „Casual“', english && win &&
    plain(win.title).includes('Classic – choose a fight') && plain(win.slots[21]?.customName).includes('Casual'),
    win && plain(win.slots[21]?.customName));
  const start1 = anna.entity.position.clone();
  await bert.clickWindow(21, 0, 0);
  const found = await waitFor(() => sees(anna, /Gegner gefunden: Bert/) && inArena(anna) && inArena(bert), 6000);
  check('Gegner gefunden → beide in einer Arena', found, `${JSON.stringify(anna.entity.position)}`);
  check('Bert liest Englisch: „Opponent found: Anna“', sees(bert, /Opponent found: Anna/));
  await sleep(300);
  if (!NO_MOVE) {
    const frozenAt = anna.entity.position.clone();
    anna.setControlState('forward', true);
    await sleep(900);
    anna.setControlState('forward', false);
    await sleep(200);
    check('Countdown: eingefroren', anna.entity.position.distanceTo(frozenAt) < 0.6,
      `bewegt ${anna.entity.position.distanceTo(frozenAt).toFixed(2)}`);
  }
  check('Countdown-Titel 3-2-1', await waitFor(() => ['3', '2', '1'].every((n) => anna.titles.includes(n)), 4000),
    anna.titles.join(' | '));
  check('„Los!“ / „Fight!“', (await started(anna)) && (await started(bert)));
  check('Eigene Anordnung im Kampf: Schwert auf Platz 5', slot(anna, 4)?.name === 'diamond_sword' &&
    slot(anna, 0)?.name === 'mushroom_stew', `0=${slot(anna, 0)?.name} 4=${slot(anna, 4)?.name}`);
  check('Bert: Standard-Kit (Schwert auf Platz 1), Eisenrüstung',
    slot(bert, 0)?.name === 'diamond_sword' && bert.inventory.slots[6]?.name === 'iron_chestplate',
    `${slot(bert, 0)?.name}, ${bert.inventory.slots[6]?.name}`);
  check('Kampf im Abenteuermodus (nicht bauen)', anna.game.gameMode === 'adventure');
  check('Arena weit weg von der Halle', start1.distanceTo(anna.entity.position) > 500);

  // Suppe: Schaden nehmen, Suppe essen
  await cmd('damage Anna 12 minecraft:out_of_world');
  await waitFor(() => anna.health <= 9, 2000);
  const before = anna.health;
  anna.setQuickBarSlot(0);
  await sleep(300);
  anna.activateItem();
  await waitFor(() => anna.health >= before + 6, 2000);
  check('Pilzsuppe heilt 3,5 Herzen', anna.health - before >= 6.9, `${before} → ${anna.health}`);
  check('Leere Schüssel ist weg (Platz frei)', !slot(anna, 0), slot(anna, 0)?.name);

  // Kampf bis einer ausscheidet
  clear(anna, bert);
  const down = await fightUntilDown(anna, bert, 4);
  check('Bert scheidet aus → Zuschauer statt Tod', down && bert.game.gameMode === 'spectator', bert.game.gameMode);
  check('Sieg für Anna (Chat, Titel)', await waitFor(() => sees(anna, /Sieg für Anna/) && sawTitle(anna, /Sieg/), 3000),
    anna.chats.slice(-3).join(' | '));
  check('Bert (englisch): „Victory for Anna“, „Defeat“', sees(bert, /Victory for Anna/) && sawTitle(bert, /Defeat/),
    bert.chats.slice(-3).join(' | '));
  check('Restleben des Siegers wird genannt', sees(anna, /Anna hatte noch [\d,]+ ❤/), anna.chats.find((c) => c.includes('❤')));
  check('Nach dem Nachspiel zurück in der Halle',
    await waitFor(() => inHall(anna) && inHall(bert), AFTER_MS + 5000),
    `${JSON.stringify(anna.entity.position)} ${anna.game.gameMode}`);
  check('Halle: Knöpfe wieder da', slot(bert, 0)?.name === 'iron_sword' && slot(bert, 8)?.name === 'red_bed');

  // ------------------------------------------------------------------ Statistik
  clear(anna, bert);
  anna.chat('/stats');
  await sleep(800);
  check('/stats: Anna 1 Sieg in Klassisch', sees(anna, /Siege 1 · Niederlagen 0/), anna.chats.join(' | '));
  bert.chat('/stats');
  await sleep(800);
  check('/stats (englisch): Bert 1 Niederlage', sees(bert, /Wins 0 · Losses 1/), bert.chats.join(' | '));
  check('Spielerdatei: Siege und Kills', /klassisch:[\s\S]*siege: 1[\s\S]*kills: 1/.test(readFile(playerFile('Anna'))));
  let log = calls();
  const annaStats = log.find((c) => c.t === 'stats' && c.name === 'Anna');
  check('Netzwerk-Statistik für Anna: wins, wins_klassisch, games, kills, streak_best',
    annaStats && annaStats.mode === 'duels' && annaStats.add.wins === 1 && annaStats.add.wins_klassisch === 1 &&
      annaStats.add.games === 1 && annaStats.add.kills === 1 && annaStats.best.streak_best === 1 &&
      !('elo_klassisch' in annaStats.best), JSON.stringify(annaStats));
  const bertStats = log.find((c) => c.t === 'stats' && c.name === 'Bert');
  check('Netzwerk-Statistik für Bert: losses, deaths', bertStats && bertStats.add.losses === 1 &&
    bertStats.add.deaths === 1 && bertStats.add.losses_klassisch === 1, JSON.stringify(bertStats));
  const plays = (name) => log.filter((c) => c.t === 'playing' && c.name === name).map((c) => c.playing);
  check('Netzwerk: „spielt gerade“ im Kampf, danach (ausgeschieden, Ende) nicht mehr',
    plays('Anna')[0] === true && plays('Anna').includes(false) && plays('Bert')[0] === true && plays('Bert')[1] === false,
    `Anna ${plays('Anna').join(',')} · Bert ${plays('Bert').join(',')}`);
  check('Netzwerk: in Halle und Warteschlange nie „spielt gerade“',
    log.findIndex((c) => c.t === 'playing') > log.findIndex((c) => c.t === 'state' && c.state === 'waiting'));
  const states = log.filter((c) => c.t === 'state').map((c) => c.state);
  check('Zustand ans Netzwerk: „running“ und danach „waiting“',
    states.includes('running') && states.lastIndexOf('waiting') > states.indexOf('running'), states.join(','));

  win = await openWithItem(anna, 7);
  const statsLore = win ? plain(win.slots[10]?.customLore) : '';
  check('Gold öffnet „Rangliste & Statistik“ mit eigenen Werten und Ranglisten je Kampfart',
    win && plain(win.title).includes('Rangliste & Statistik') && plain(win.slots[10]?.customName) === 'Klassisch' &&
      statsLore.includes('Deine Werte') && statsLore.includes('Rangliste') && plain(win.slots[33]?.customName) === 'Brücke',
    win && `${plain(win.slots[10]?.customName)} / ${statsLore.slice(0, 120)}`);
  if (win) anna.closeWindow(win);
  await sleep(300);

  // ------------------------------------------------------------------ Herausforderung (Fähigkeiten)
  clear(anna, bert);
  anna.chat('/duel Bert faehigkeiten');
  await sleep(800);
  check('Herausforderung geschickt (gilt 10 s im Test)', sees(anna, /Herausforderung an Bert geschickt \(Fähigkeiten\)\. Sie gilt 10 Sekunden/),
    anna.chats.join(' | '));
  check('Bert bekommt sie mit [Accept] [Decline]', sees(bert, /challenges you to a duel: Abilities/) &&
    sees(bert, /\[Accept\] \[Decline\]/), bert.chats.join(' | '));
  bert.chat('/duel accept Anna');
  check('Annehmen → Kampf beginnt', await waitFor(() => inArena(anna) && inArena(bert), 6000));
  await started(anna);
  check('Fähigkeiten-Kit: Eisenschwert + Kletterseil (Bergführer)',
    slot(anna, 0)?.name === 'iron_sword' && slot(anna, 1)?.name === 'lead', `${slot(anna, 0)?.name} ${slot(anna, 1)?.name}`);
  anna.setQuickBarSlot(1);
  await sleep(300);
  check('Aktionsleiste: Kletterseil bereit', anna.bars.some((b) => /Kletterseil bereit/.test(b)), anna.bars.slice(-1)[0]);
  anna.bars.length = 0;
  anna.pushes.length = 0;
  anna.activateItem();
  await sleep(1000);
  const leap = anna.pushes.find((v) => v.y > 0.5);
  check('Kletterseil: Server gibt Sprung-Schub nach vorn und oben', leap && Math.hypot(leap.x, leap.z) > 1.0,
    JSON.stringify(leap ?? anna.pushes));
  check('Abklingzeit in der Aktionsleiste', anna.bars.some((b) => /Kletterseil noch [\d,]+ s/.test(b)),
    anna.bars.slice(-1)[0]);
  anna.pushes.length = 0;
  anna.activateItem();
  await sleep(400);
  check('Während der Abklingzeit kein zweiter Sprung', !anna.pushes.some((v) => v.y > 0.5));
  clear(anna, bert);
  bert.chat('/verlassen');
  check('Bert gibt auf → zählt als Niederlage', await waitFor(() => sees(bert, /You gave up – that counts as a loss/), 3000),
    bert.chats.join(' | '));
  check('Aufgeben: sofort zurück in der Halle', await waitFor(() => inHall(bert), 3000));
  check('Sieg für Anna nach Aufgabe', await waitFor(() => sees(anna, /Sieg für Anna/), 3000));
  await waitFor(() => inHall(anna), AFTER_MS + 5000);

  // ------------------------------------------------------------------ Gewertet (Elo)
  clear(anna, bert);
  anna.chat('/warteschlange klassisch gewertet 1v1');
  await sleep(400);
  bert.chat('/queue classic ranked 1v1');
  check('Gewertet: Kampf gefunden', await waitFor(() => inArena(anna) && inArena(bert), 6000));
  await started(anna);
  clear(anna, bert);
  bert.chat('/verlassen');
  check('Elo nach Sieg: 1016 (+16)', await waitFor(() => sees(anna, /Deine Wertung \(Klassisch\): 1016 \+16/), 4000),
    anna.chats.join(' | '));
  check('Elo nach Niederlage: 984 (-16)', await waitFor(() => sees(bert, /Your rating \(Classic\): 984 -16/), 4000),
    bert.chats.join(' | '));
  await waitFor(() => inHall(anna), AFTER_MS + 5000);
  clear(anna);
  anna.chat('/top klassisch');
  await sleep(800);
  check('/top: Anna vorne mit 1016, Bert mit 984', sees(anna, /1\. Anna – 1016/) && sees(anna, /2\. Bert – 984/),
    anna.chats.join(' | '));
  log = calls();
  const ranked = log.filter((c) => c.t === 'stats' && c.name === 'Anna' && c.add.ranked_games === 1)[0];
  check('Netzwerk: Bestwert elo_klassisch = 1016', ranked && ranked.best.elo_klassisch === 1016, JSON.stringify(ranked));
  check('Rangliste in rangliste.yml', /Anna[\s\S]*klassisch:[\s\S]*elo: 1016/.test(
    readFile(path.join(SERVER, 'plugins/MoinMorniDuels/rangliste.yml'))));

  // ------------------------------------------------------------------ Verbindung weg, rechtzeitig zurück (1 gegen 1)
  clear(anna, bert);
  let mark = calls().length;
  anna.chat('/duel Bert klassisch');
  await sleep(600);
  bert.chat('/duel accept');
  check('Rückkehr: Kampf Anna gegen Bert beginnt', await waitFor(() => inArena(anna) && inArena(bert), 6000));
  await started(anna);
  await cmd('damage Bert 6 minecraft:out_of_world');
  await waitFor(() => bert.health <= 14.5, 2000);
  const hpBefore = bert.health;
  const posBefore = bert.entity.position.clone();
  const soupsBefore = bert.inventory.slots.filter((it) => it?.name === 'mushroom_stew').length;
  const clockOf = (t) => {
    const m = /(\d+):(\d\d)\s*$/.exec(t);
    return m ? Number(m[1]) * 60 + Number(m[2]) : null;
  };
  await waitFor(() => clockOf(anna.bossTitle) != null, 3000);
  const clockBefore = clockOf(anna.bossTitle);
  const pausedSince = Date.now();
  clear(anna);
  bert.quit();
  check(`Verbindung weg: Kampf angehalten, Anna erfährt es (wartet ${RETURN_S} s)`,
    await waitFor(() => sees(anna, new RegExp(`Bert hat die Verbindung verloren – der Kampf ist angehalten\\. Wir warten bis zu ${RETURN_S} Sekunden`)), 4000),
    anna.chats.join(' | '));
  check('Anzeige „Warte auf Bert – … s“', await waitFor(() => anna.bars.some((b) => /Warte auf Bert – \d+ s/.test(b)), 3000),
    anna.bars.slice(-1)[0]);
  check(`Netzwerk: Platz für Bert ${RETURN_S} s frei (holdSlot)`,
    calls().slice(mark).some((c) => c.t === 'hold-slot' && c.name === 'Bert' && c.seconds === RETURN_S),
    JSON.stringify(calls().slice(mark).filter((c) => c.t === 'hold-slot')));
  if (!NO_MOVE) {
    const frozenAt = anna.entity.position.clone();
    anna.setControlState('forward', true);
    await sleep(900);
    anna.setControlState('forward', false);
    await sleep(200);
    check('Angehalten: Anna steht eingefroren', anna.entity.position.distanceTo(frozenAt) < 0.6,
      `bewegt ${anna.entity.position.distanceTo(frozenAt).toFixed(2)}`);
  }
  await sleep(1500);
  check('Kein Sieg, solange Bert noch zurückkommen kann', !sees(anna, /Sieg für/), anna.chats.join(' | '));
  clear(anna);
  bert = await join('Bert');
  check('Bert ist rechtzeitig zurück: wieder in der Arena (nicht in der Halle)', inArena(bert) && bert.game.gameMode === 'adventure',
    `${JSON.stringify(bert.entity.position)} ${bert.game.gameMode}`);
  check('Bert steht, wo er war', bert.entity.position.distanceTo(posBefore) < 1.5,
    `${posBefore} → ${bert.entity.position}`);
  check('Bert hat dasselbe Leben', Math.abs(bert.health - hpBefore) < 0.6, `${hpBefore} → ${bert.health}`);
  check('Bert hat seine Ausrüstung (Schwert, Rüstung, Suppen)',
    slot(bert, 0)?.name === 'diamond_sword' && bert.inventory.slots[6]?.name === 'iron_chestplate' &&
      bert.inventory.slots.filter((it) => it?.name === 'mushroom_stew').length === soupsBefore,
    `${slot(bert, 0)?.name} ${bert.inventory.slots[6]?.name} ${soupsBefore} Suppen`);
  check('Bert (englisch): „Welcome back“', await waitFor(() => sees(bert, /Welcome back! You're right where you were/), 3000),
    bert.chats.join(' | '));
  check('Anna: „Bert ist wieder da“, dann 3-2-1 und „Weiter!“',
    await waitFor(() => sees(anna, /Bert ist wieder da/) && ['3', '2', '1'].every((n) => anna.titles.includes(n)) &&
      sawTitle(anna, /^Weiter!$/), 7000), `${anna.chats.join(' | ')} || ${anna.titles.join(' | ')}`);
  await sleep(1200);
  const clockAfter = clockOf(anna.bossTitle);
  check('Die Uhr stand still: weiter mit derselben Restzeit', clockBefore != null && clockAfter != null &&
    clockBefore - clockAfter <= 2 && Date.now() - pausedSince >= 6000,
    `${clockBefore} s → ${clockAfter} s nach ${((Date.now() - pausedSince) / 1000).toFixed(1)} s`);
  let fresh = calls().slice(mark);
  check('Netzwerk: Platz wieder zu (0) und Bert „spielt“ wieder',
    fresh.some((c) => c.t === 'hold-slot' && c.name === 'Bert' && c.seconds === 0) &&
      fresh.filter((c) => c.t === 'playing' && c.name === 'Bert').slice(-1)[0]?.playing === true,
    JSON.stringify(fresh.filter((c) => c.name === 'Bert' && c.t !== 'stats')));
  check('Weiter mit demselben Stand: Anna besiegt Bert', await fightUntilDown(anna, bert, 4) &&
    (await waitFor(() => sees(anna, /Sieg für Anna/), 3000)), anna.chats.slice(-3).join(' | '));
  await waitFor(() => inHall(anna) && inHall(bert), AFTER_MS + 5000);

  // ------------------------------------------------------------------ Zuschauen + zu lange weg = Niederlage
  const carl = await join('Carl');
  // Rechtsklick mit dem Schwert auf einen Spieler: herausfordern (Menü), dann ablehnen.
  clear(anna, carl);
  carl.setQuickBarSlot(0);
  await sleep(300);
  const duelMenu = nextWindow(carl);
  const annaEntity = carl.players.Anna?.entity;
  if (annaEntity) carl.activateEntity(annaEntity);
  win = await duelMenu;
  check('Rechtsklick auf Anna öffnet „Herausfordern: Anna“', win && plain(win.title).includes('Herausfordern: Anna'),
    win ? plain(win.title) : `Anna sichtbar: ${Boolean(annaEntity)}`);
  if (win) await carl.clickWindow(10, 0, 0);
  check('Anna bekommt die Herausforderung (Klassisch)', await waitFor(() => sees(anna, /Carl fordert dich zum Duell heraus: Klassisch/), 3000),
    anna.chats.join(' | '));
  anna.chat('/duel ablehnen');
  check('Ablehnen: Carl erfährt es', await waitFor(() => sees(carl, /Anna hat abgelehnt/), 3000), carl.chats.join(' | '));
  clear(anna, bert, carl);
  anna.chat('/duel Bert klassisch');
  await sleep(600);
  bert.chat('/duel annehmen');
  await waitFor(() => inArena(anna) && inArena(bert), 6000);
  await started(anna);
  carl.chat('/spectate Anna');
  check('Zuschauen: Carl im Zuschauermodus bei der Arena',
    await waitFor(() => carl.game.gameMode === 'spectator' && inArena(carl), 4000),
    `${carl.game.gameMode} ${JSON.stringify(carl.entity.position)}`);
  check('Zuschauen: Hinweis mit /verlassen', sees(carl, /Du schaust Anna zu/));
  mark = calls().length;
  bert.quit();
  check('Verbindung weg: Anna erfährt es, der Platz bleibt frei', await waitFor(() => sees(anna, /Bert hat die Verbindung verloren/), 4000),
    anna.chats.join(' | '));
  check(`Zu lange weg (über ${RETURN_S} s) = Niederlage: Sieg für Anna`,
    await waitFor(() => sees(anna, /Bert ist nicht rechtzeitig zurückgekommen/) && sees(anna, /Sieg für Anna/), RETURN_S * 1000 + 5000),
    anna.chats.join(' | '));
  fresh = calls().slice(mark).filter((c) => c.t === 'hold-slot' && c.name === 'Bert').map((c) => c.seconds);
  check('Netzwerk: Platz erst frei, danach wieder zu (holdSlot 12, dann 0)', fresh.join(',') === `${RETURN_S},0`, fresh.join(','));
  check('Zuschauer sieht das Ergebnis', await waitFor(() => sees(carl, /Sieg für Anna/), 3000), carl.chats.join(' | '));
  check('Zuschauer nach dem Nachspiel zurück in der Halle', await waitFor(() => inHall(carl), AFTER_MS + 5000),
    `${carl.game.gameMode} ${JSON.stringify(carl.entity.position)}`);
  await waitFor(() => inHall(anna), 3000);
  let bert2 = await join('Bert');
  check('Zu spät zurück: in der Halle, mit Hinweis „Your fight is over by now“', inHall(bert2) &&
    (await waitFor(() => sees(bert2, /Your fight is over by now/), 3000)), bert2.chats.join(' | '));
  bert2.chat('/stats');
  await sleep(800);
  check('Bert nach dem Wiederkommen: 5 Niederlagen in Klassisch/Fähigkeiten gesamt',
    sees(bert2, /Losses 4/) && sees(bert2, /Losses 1/), bert2.chats.filter((c) => c.includes('Losses')).join(' | '));

  // ------------------------------------------------------------------ 2 gegen 2 mit Party
  const dora = await join('Dora');
  clear(anna, bert2, carl, dora);
  anna.chat('/warteschlange klassisch locker 2v2');
  check('Party-Leiterin meldet die Party an', await waitFor(() => sees(anna, /Deine Party sucht Gegner/) &&
    sees(bert2, /Your party is looking for opponents/), 3000), `${anna.chats.join(' | ')} || ${bert2.chats.join(' | ')}`);
  carl.chat('/warteschlange klassisch locker 2v2');
  await sleep(300);
  check('Allein im 2v2: Carl wartet noch', !inArena(carl));
  dora.chat('/warteschlange klassisch locker 2v2');
  check('2 gegen 2 beginnt', await waitFor(() => [anna, bert2, carl, dora].every(inArena), 6000));
  const side = (b) => Math.sign(b.entity.position.z - Math.round(b.entity.position.z / 1000) * 1000);
  check('Anna und Bert auf derselben Seite, Carl und Dora gegenüber',
    side(anna) === side(bert2) && side(carl) === side(dora) && side(anna) !== side(carl),
    [anna, bert2, carl, dora].map((b) => `${b.username}:${side(b)}`).join(' '));
  await started(anna);
  clear(anna, bert2, carl, dora);
  bert2.quit();
  check(`2 gegen 2: Bert weg – der Platz bleibt ${RETURN_S} s frei, das Team kämpft weiter`,
    await waitFor(() => sees(anna, new RegExp(`Bert hat die Verbindung verloren – der Platz bleibt ${RETURN_S} Sekunden frei, der Kampf geht weiter`)), 4000),
    anna.chats.join(' | '));
  if (!NO_MOVE) {
    const at = carl.entity.position.clone();
    carl.setControlState('back', true);
    await sleep(700);
    carl.setControlState('back', false);
    await sleep(200);
    check('2 gegen 2: nicht angehalten (Carl kann sich bewegen)', carl.entity.position.distanceTo(at) > 0.6,
      `bewegt ${carl.entity.position.distanceTo(at).toFixed(2)}`);
  }
  bert2 = await join('Bert');
  check('2 gegen 2: Bert zurück im Kampf, auf seiner Seite, mit Ausrüstung',
    inArena(bert2) && side(bert2) === side(anna) && slot(bert2, 0)?.name === 'diamond_sword' &&
      (await waitFor(() => sees(bert2, /Welcome back/), 3000)),
    `${JSON.stringify(bert2.entity.position)} ${slot(bert2, 0)?.name}`);
  clear(anna, bert2, carl, dora);
  carl.chat('/verlassen');
  await sleep(500);
  check('Nach Carls Aufgabe läuft der Kampf weiter', !sees(anna, /Sieg für/));
  dora.chat('/verlassen');
  check('Sieg für Anna & Bert', await waitFor(() => sees(anna, /Sieg für Anna & Bert/), 4000), anna.chats.join(' | '));
  await waitFor(() => inHall(anna) && inHall(bert2), AFTER_MS + 5000);

  // Im 2 gegen 2 meldet nur die Party-Leitung an
  clear(bert2);
  bert2.chat('/queue classic casual 2v2');
  check('Party-Mitglied (nicht Leitung): Hinweis statt Anmeldung',
    await waitFor(() => sees(bert2, /In 2v2 your party leader signs you up/), 3000), bert2.chats.join(' | '));

  // ------------------------------------------------------------------ Fähigkeit wählen: Jäger (Pfeilhagel)
  clear(anna, bert2);
  win = await openWithItem(anna, 0);
  const abilitiesKit = nextWindow(anna);
  if (win) await anna.clickWindow(28, 0, 0);
  win = await abilitiesKit;
  const abilityMenu = nextWindow(anna);
  if (win) await anna.clickWindow(19, 0, 0);
  win = await abilityMenu;
  check('Fähigkeiten-Symbol öffnet „Fähigkeit wählen“ (4 Fähigkeiten)',
    win && plain(win.title).includes('Fähigkeit wählen') && [10, 12, 14, 16].every((i) => win.slots[i]),
    win && [10, 12, 14, 16].map((i) => win.slots[i]?.name).join(','));
  const back = nextWindow(anna);
  if (win) await anna.clickWindow(16, 0, 0);
  await back;
  check('Jäger gewählt', await waitFor(() => sees(anna, /Deine Fähigkeit: Jäger/), 3000) &&
    /faehigkeit: jaeger/.test(readFile(playerFile('Anna'))), anna.chats.join(' | '));
  anna.closeWindow(anna.currentWindow ?? win);
  await sleep(300);
  anna.chat('/duel Bert faehigkeiten');
  await sleep(500);
  bert2.chat('/duel accept');
  await waitFor(() => inArena(anna) && inArena(bert2), 6000);
  await started(anna);
  check('Jäger-Kit: Pfeilbündel, Bogen, Pfeile', slot(anna, 1)?.name === 'feather' && slot(anna, 2)?.name === 'bow' &&
    slot(anna, 8)?.name === 'arrow', [1, 2, 8].map((i) => slot(anna, i)?.name).join(','));
  anna.setQuickBarSlot(1);
  await sleep(300);
  anna.activateItem();
  await sleep(200);
  const arrows = (await cmd('execute as Anna at @s if entity @e[type=minecraft:arrow,distance=..30]')).join(' ');
  check('Pfeilhagel: fünf Pfeile', /count: 5/i.test(arrows), arrows);
  const info = (await cmd('duels info')).join(' ').replace(/§./g, '');
  check('/duels info: 1 Arena belegt, 1 Kampf (16 Arenen: 8 Arten × 2)', /1\/16 belegt.*Kämpfe: 1/.test(info), info);
  bert2.chat('/verlassen');
  await waitFor(() => inHall(anna) && inHall(bert2), AFTER_MS + 6000);

  // ------------------------------------------------------------------ Modern + Zeitlimit = Unentschieden
  clear(anna, carl);
  anna.chat('/duel Carl modern');
  await sleep(600);
  carl.chat('/duel annehmen Anna');
  await waitFor(() => inArena(anna) && inArena(carl), 6000);
  await started(anna);
  // Während Anna und Carl warten: Herausforderung läuft ab, /stats für andere (englisch)
  clear(dora, bert2);
  dora.chat('/duel Bert klassisch');
  bert2.chat('/stats Anna');
  check('/stats Anna auf Englisch', await waitFor(() => sees(bert2, /Stats of Anna/) && sees(bert2, /Rating: 1016 \(#1\)/), 3000),
    bert2.chats.join(' | '));
  check('Herausforderung läuft nach 10 s ab', await waitFor(() => sees(dora, /Deine Herausforderung an Bert ist abgelaufen/) &&
    sees(bert2, /The challenge from Dora expired/), 14000), dora.chats.join(' | '));
  const names = [0, 1, 2, 3, 4, 8].map((i) => slot(anna, i)?.name);
  check('Modern-Kit: Schwert, Axt, Bogen, Goldäpfel, Perlen, Pfeile + Schild',
    names.join(',') === 'diamond_sword,diamond_axe,bow,golden_apple,ender_pearl,arrow' &&
      anna.inventory.slots[45]?.name === 'shield', `${names.join(',')} / ${anna.inventory.slots[45]?.name}`);
  check('Unentschieden nach dem Zeitlimit', await waitFor(() => sees(anna, /Unentschieden/) &&
    sawTitle(carl, /Unentschieden/), TIME_LIMIT_MS + 5000, 500), anna.chats.slice(-3).join(' | '));
  await waitFor(() => inHall(anna) && inHall(carl), AFTER_MS + 5000);
  clear(anna);
  anna.chat('/stats');
  await sleep(800);
  check('Unentschieden in der Statistik (Modern)', sees(anna, /Siege 0 · Niederlagen 0 · Unentschieden 1/),
    anna.chats.join(' | '));

  // ================================================================== Neue Kampfarten (0.2.0)
  const fakeProps = path.join(SERVER, 'plugins/MoinMorni/fake.properties');
  const originalProps = readFile(fakeProps);
  /** Mittelpunkt der Arena aus dem Startplatz (rel = Startplatz relativ zur Mitte). */
  const origin = (bot, rx, rz) => ({
    x: Math.floor(bot.entity.position.x) - rx,
    y: Math.floor(bot.entity.position.y) - 1,
    z: Math.floor(bot.entity.position.z) - rz,
  });
  const armor = (bot) => [5, 6, 7, 8].map((i) => bot.inventory.slots[i]?.name ?? '-').join(',');
  const fight = async (args, check1) => {
    const ok = await duel(anna, carl, args);
    check(`${args}: Kampf beginnt`, ok, `${JSON.stringify(anna.entity.position)}`);
    if (check1) await check1();
  };
  const winByFight = async (label, swordSlot) => {
    clear(anna, carl);
    const down = await fightUntilDown(anna, carl, swordSlot);
    check(`${label}: echt gekämpft bis zum Sieg`, down && (await waitFor(() => sees(anna, /Sieg für Anna/), 4000)),
      anna.chats.slice(-3).join(' | '));
    await allInHall(anna, carl);
  };

  // ---------- Nur Schwert
  await fight('schwert', async () => {
    check('Nur Schwert: nur ein Diamantschwert, keine Rüstung', slot(anna, 0)?.name === 'diamond_sword' &&
      anna.inventory.slots.filter(Boolean).length === 1 && armor(anna) === '-,-,-,-',
      `${anna.inventory.slots.filter(Boolean).map((i) => i.name).join(',')}`);
  });
  await winByFight('Nur Schwert', 0);

  // ---------- Ritter (Eisen)
  await fight('ritter', async () => {
    check('Ritter: Eisenschwert, Eisenrüstung, Bogen, Goldäpfel', slot(anna, 0)?.name === 'iron_sword' &&
      armor(anna) === 'iron_helmet,iron_chestplate,iron_leggings,iron_boots' && slot(anna, 1)?.name === 'bow' &&
      slot(anna, 2)?.name === 'golden_apple', `${slot(anna, 0)?.name} ${armor(anna)}`);
  });
  await winByFight('Ritter', 0);

  // ---------- Diamant
  await fight('diamant', async () => {
    check('Diamant: Diamantschwert und Diamantrüstung', slot(anna, 0)?.name === 'diamond_sword' &&
      armor(anna) === 'diamond_helmet,diamond_chestplate,diamond_leggings,diamond_boots', armor(anna));
  });
  await winByFight('Diamant', 0);

  // ---------- Bogen
  await fight('bogen', async () => {
    check('Bogen: Bogen, ein Pfeil (unendlich), Lederrüstung', slot(anna, 0)?.name === 'bow' &&
      slot(anna, 8)?.name === 'arrow' && armor(anna).startsWith('leather_helmet'), `${slot(anna, 0)?.name} ${armor(anna)}`);
  });
  clear(anna, carl);
  check('Bogen: mit Pfeilen bis zum Sieg', (await shootUntilDown(anna, carl, 0)) &&
    (await waitFor(() => sees(anna, /Sieg für Anna/), 4000)), `${carl.health} ${anna.chats.slice(-2).join(' | ')}`);
  check('Bogen: der Pfeil ist noch da (unendlich)', slot(anna, 8)?.name === 'arrow');
  await allInHall(anna, carl);

  // ---------- Angel
  await fight('angel', async () => {
    check('Angel: Eisenschwert und Angel', slot(anna, 0)?.name === 'iron_sword' && slot(anna, 1)?.name === 'fishing_rod',
      `${slot(anna, 0)?.name} ${slot(anna, 1)?.name}`);
  });
  let pulled = false;
  for (let i = 0; i < 4 && !pulled; i++) {
    await cmd('execute at Carl run tp Anna ~ ~ ~-4 0 0');
    await sleep(400);
    anna.setQuickBarSlot(1);
    const t = anna.players.Carl?.entity;
    if (t) await anna.lookAt(t.position.offset(0, 1.0, 0), true);
    carl.pushes.length = 0;
    anna.activateItem();
    await sleep(900);
    anna.activateItem();
    pulled = await waitFor(() => carl.pushes.some((v) => v.z < -0.15), 1500);
  }
  check('Angel: Einholen zieht den Gegner heran', pulled, JSON.stringify(carl.pushes.slice(-3)));
  await winByFight('Angel', 0);

  // ---------- Kombo
  await fight('kombo', async () => {
    check('Kombo: Diamant, Schärfe-Schwert, Goldäpfel', slot(anna, 0)?.name === 'diamond_sword' &&
      slot(anna, 1)?.name === 'golden_apple' && armor(anna).startsWith('diamond_helmet'), armor(anna));
  });
  await winByFight('Kombo', 0);

  // ---------- Axt und Schild
  await fight('axt', async () => {
    check('Axt und Schild: Diamantaxt und Schild', slot(anna, 0)?.name === 'diamond_axe' &&
      anna.inventory.slots[45]?.name === 'shield', `${slot(anna, 0)?.name} ${anna.inventory.slots[45]?.name}`);
  });
  await winByFight('Axt und Schild', 0);

  // ---------- Goldapfel
  await fight('goldapfel', async () => {
    check('Goldapfel: verzauberte Goldäpfel, Tränke, Diamantrüstung', slot(anna, 1)?.name === 'enchanted_golden_apple' &&
      slot(anna, 2)?.name === 'potion' && slot(anna, 4)?.name === 'potion' && armor(anna).startsWith('diamond_helmet'),
      [0, 1, 2, 4].map((i) => slot(anna, i)?.name).join(','));
  });
  await winByFight('Goldapfel', 0);

  // ---------- Tränke
  await fight('traenke', async () => {
    const splash = anna.inventory.slots.filter((it) => it?.name === 'splash_potion').length;
    check('Tränke: Inventar voller Wurf-Heiltränke, Perlen, Schnelligkeit', splash >= 30 &&
      slot(anna, 1)?.name === 'ender_pearl' && slot(anna, 2)?.name === 'potion', `${splash} Wurftränke`);
  });
  await cmd('damage Anna 8 minecraft:out_of_world');
  await waitFor(() => anna.health <= 12.5, 2000);
  const hpPotion = anna.health;
  anna.setQuickBarSlot(3);
  await sleep(250);
  await anna.look(anna.entity.yaw, -Math.PI / 2, true);
  anna.activateItem();
  check('Tränke: geworfener Heiltrank heilt', await waitFor(() => anna.health >= hpPotion + 4, 4000),
    `${hpPotion} → ${anna.health}`);
  await winByFight('Tränke', 0);

  // ---------- Enderperlen
  await fight('perlen', async () => {
    check('Enderperlen: 16 Perlen', slot(anna, 1)?.name === 'ender_pearl' && slot(anna, 1)?.count === 16,
      `${slot(anna, 1)?.name} ×${slot(anna, 1)?.count}`);
  });
  const pearlFrom = anna.entity.position.clone();
  anna.setQuickBarSlot(1);
  await sleep(250);
  await anna.look(anna.entity.yaw, -0.35, true);
  anna.activateItem();
  check('Enderperlen: Perle bringt dich weiter', await waitFor(() => anna.entity.position.distanceTo(pearlFrom) > 3, 4000),
    `${pearlFrom} → ${anna.entity.position}`);
  await sleep(500);
  check('Enderperlen: Landen tut nicht weh', anna.health >= 19.9, String(anna.health));
  await winByFight('Enderperlen', 0);

  // ---------- UHC
  await fight('uhc', async () => {
    check('UHC: Wasser, Lava, Goldäpfel, Bruchstein, Bogen; Überlebensmodus',
      slot(anna, 2)?.name === 'water_bucket' && slot(anna, 3)?.name === 'lava_bucket' &&
        slot(anna, 4)?.name === 'golden_apple' && slot(anna, 5)?.name === 'cobblestone' && slot(anna, 1)?.name === 'bow' &&
        anna.game.gameMode === 'survival', `${[1, 2, 3, 4, 5].map((i) => slot(anna, i)?.name).join(',')} ${anna.game.gameMode}`);
  });
  const uo = origin(anna, 0, -16);
  await cmd('damage Anna 4 minecraft:out_of_world');
  await waitFor(() => anna.health <= 16.5, 2000);
  const hpUhc = anna.health;
  await sleep(4500);
  check('UHC: keine natürliche Heilung', anna.health <= hpUhc + 0.01, `${hpUhc} → ${anna.health}`);
  anna.setQuickBarSlot(4);
  await sleep(250);
  try {
    await Promise.race([anna.consume(), sleep(4000)]);
  } catch {
    // weiter
  }
  check('UHC: Goldapfel heilt', await waitFor(() => anna.health > hpUhc + 1, 6000), `${hpUhc} → ${anna.health}`);
  anna.setQuickBarSlot(5);
  await sleep(250);
  try {
    await Promise.race([anna.placeBlock(anna.blockAt(new Vec3(uo.x + 2, uo.y, uo.z - 16)), new Vec3(0, 1, 0)), sleep(3000)]);
  } catch {
    // geprüft wird am Block
  }
  check('UHC: Block setzen erlaubt', await blockIs(uo.x + 2, uo.y + 1, uo.z - 16, 'minecraft:cobblestone'));
  anna.setQuickBarSlot(7);
  await digSoon(anna, anna.blockAt(new Vec3(uo.x + 2, uo.y + 1, uo.z - 16)));
  check('UHC: eigenen Block wieder abbauen', await blockIs(uo.x + 2, uo.y + 1, uo.z - 16, 'minecraft:air'));
  const floorName = anna.blockAt(new Vec3(uo.x - 1, uo.y, uo.z - 16))?.name;
  await digSoon(anna, anna.blockAt(new Vec3(uo.x - 1, uo.y, uo.z - 16)));
  check('UHC: Boden der Arena bleibt (nur eigene Blöcke abbauen)',
    floorName && (await blockIs(uo.x - 1, uo.y, uo.z - 16, `minecraft:${floorName}`)), floorName);
  anna.setQuickBarSlot(2);
  await sleep(250);
  await anna.lookAt(new Vec3(uo.x + 3.5, uo.y + 1, uo.z - 13.5), true);
  anna.activateItem();
  check('UHC: Wasser ausgießen', await waitFor(() => blockIs(uo.x + 3, uo.y + 1, uo.z - 14, 'minecraft:water'), 3000));
  await sleep(1500);
  check('UHC: Wasser fließt in der Arena', await blockIs(uo.x + 4, uo.y + 1, uo.z - 14, 'minecraft:water'));
  clear(anna);
  carl.chat('/verlassen');
  await waitFor(() => sees(anna, /Sieg für Anna/), 4000);
  await allInHall(anna, carl);
  check('UHC: nach dem Kampf ist die Arena wie vorher (Wasser weg)',
    (await blockIs(uo.x + 3, uo.y + 1, uo.z - 14, 'minecraft:air')) && (await blockIs(uo.x + 4, uo.y + 1, uo.z - 14, 'minecraft:air')));

  // ---------- Bau-Kampf
  await fight('baukampf', async () => {
    check('Bau-Kampf: Bretter, Bruchstein, Spitzhacke; Überlebensmodus', slot(anna, 1)?.name === 'spruce_planks' &&
      slot(anna, 3)?.name === 'cobblestone' && slot(anna, 4)?.name === 'diamond_pickaxe' && anna.game.gameMode === 'survival',
      `${[1, 3, 4].map((i) => slot(anna, i)?.name).join(',')} ${anna.game.gameMode}`);
  });
  const bo = origin(anna, 0, -16);
  anna.setQuickBarSlot(1);
  await sleep(250);
  try {
    await Promise.race([anna.placeBlock(anna.blockAt(new Vec3(bo.x + 1, bo.y, bo.z - 15)), new Vec3(0, 1, 0)), sleep(3000)]);
  } catch {
    // geprüft wird am Block
  }
  check('Bau-Kampf: Bretter gesetzt', await blockIs(bo.x + 1, bo.y + 1, bo.z - 15, 'minecraft:spruce_planks'));
  await winByFight('Bau-Kampf', 0);
  check('Bau-Kampf: nach dem Kampf ist der Block wieder weg', await blockIs(bo.x + 1, bo.y + 1, bo.z - 15, 'minecraft:air'));

  // ---------- Sumo (Warteschlange, gewertet)
  clear(anna, carl);
  anna.chat('/warteschlange sumo gewertet 1v1');
  await sleep(400);
  carl.chat('/warteschlange sumo gewertet 1v1');
  check('Sumo: Warteschlange je Kampfart → Tanzboden', await waitFor(() => inArena(anna) && inArena(carl) &&
    sees(anna, /Arena Tanzboden/), 8000), anna.chats.join(' | '));
  check('Sumo: Hinweis zu Beginn', sees(anna, /Sumo: Stoß den Gegner vom Tanzboden/));
  await started(anna);
  check('Sumo: nichts in der Hand, Startplätze 6 Blöcke auseinander', anna.inventory.slots.filter(Boolean).length === 0 &&
    Math.abs(anna.entity.position.distanceTo(carl.entity.position) - 6) < 1.2,
    `${anna.entity.position.distanceTo(carl.entity.position).toFixed(1)}`);
  anna.setQuickBarSlot(0);
  carl.pushes.length = 0;
  for (let i = 0; i < 4; i++) {
    await cmd('execute at Carl run tp Anna ~ ~ ~-1.8 0 0');
    await sleep(200);
    const t = anna.players.Carl?.entity;
    if (t) {
      await anna.lookAt(t.position.offset(0, 1.4, 0), true);
      anna.attack(t);
    }
    await sleep(600);
  }
  check('Sumo: Treffer stoßen weg, tun aber nicht weh', carl.health >= 19.9 && carl.pushes.length > 0,
    `${carl.health} ❤, ${carl.pushes.length} Stöße`);
  clear(anna, carl);
  await cmd('execute at Carl run tp Carl ~ ~ ~9');
  check('Sumo: Carl fällt vom Tanzboden → Sieg für Anna (gestoßen = Kill)',
    await waitFor(() => sees(anna, /Carl wurde von Anna vom Tanzboden gestoßen/) && sees(anna, /Sieg für Anna/), 5000),
    anna.chats.join(' | '));
  check('Sumo: eigene Wertung je Kampfart (1016)', await waitFor(() => sees(anna, /Deine Wertung \(Sumo\): 1016 \+16/), 3000),
    anna.chats.join(' | '));
  await allInHall(anna, carl);
  clear(anna);
  anna.chat('/top sumo');
  await sleep(800);
  check('/top sumo: Anna 1016, Carl 984', sees(anna, /1\. Anna – 1016/) && sees(anna, /2\. Carl – 984/), anna.chats.join(' | '));
  log = calls();
  check('Netzwerk: elo_sumo, wins_sumo', log.some((c) => c.t === 'stats' && c.name === 'Anna' && c.best.elo_sumo === 1016 &&
    c.add.wins_sumo === 1), JSON.stringify(log.filter((c) => c.t === 'stats' && c.name === 'Anna').slice(-1)));
  clear(carl);
  carl.chat('/stats');
  await sleep(800);
  check('Elo je Kampfart: Carl Sumo 984 (Platz 2), Klassisch unverändert 1000',
    sees(carl, /Wertung: 984 \(Platz 2\)/) && sees(carl, /Wertung: 1000/), carl.chats.join(' | '));

  // ---------- Boxen (Test: 10 Treffer)
  await fight('boxen', async () => {
    check('Boxen: Schwert, Schnelligkeit, Hinweis mit Treffern', slot(anna, 0)?.name === 'diamond_sword' &&
      sees(anna, /Boxen: Wer zuerst 10 Treffer landet/), anna.chats.join(' | '));
  });
  clear(anna, carl);
  anna.setQuickBarSlot(0);
  for (let i = 0; i < 40 && !sees(anna, /Sieg für Anna/); i++) {
    const v = carl.entity.position;
    if (anna.entity.position.distanceTo(v) > 2.6) {
      await cmd(`execute at Carl run tp Anna ~ ~ ~${anna.entity.position.z > v.z ? 1.8 : -1.8}`);
      await sleep(200);
    }
    const t = anna.players.Carl?.entity;
    if (t) {
      await anna.lookAt(t.position.offset(0, 1.4, 0), true);
      anna.attack(t);
    }
    await sleep(600);
  }
  check('Boxen: 10 Treffer = Sieg, Leben bleibt voll', sees(anna, /Sieg für Anna/) && carl.health >= 19.9,
    `${carl.health} ❤ · ${anna.chats.slice(-2).join(' | ')}`);
  check('Boxen: Treffer in der Aktionsleiste und am Ende', anna.bars.some((b) => /Treffer: \d+ : \d+/.test(b)) &&
    sees(anna, /Treffer zum Schluss: 10:0/), `${anna.bars.slice(-1)[0]} · ${anna.chats.filter((c) => c.includes('Treffer')).join(' | ')}`);
  await allInHall(anna, carl);
  check('Netzwerk: hits_boxen = 10', calls().some((c) => c.t === 'stats' && c.name === 'Anna' && c.add.hits_boxen === 10));

  // ---------- Spleef
  await fight('spleef', async () => {
    check('Spleef: Schneefeld, Diamantschaufel, Überlebensmodus', sees(anna, /Arena Schneefeld/) &&
      slot(anna, 0)?.name === 'diamond_shovel' && anna.game.gameMode === 'survival', `${slot(anna, 0)?.name} ${anna.game.gameMode}`);
  });
  const so = origin(anna, 0, -7);
  const under = new Vec3(Math.floor(carl.entity.position.x), so.y, Math.floor(carl.entity.position.z));
  check('Spleef: Carl steht auf Schnee', await blockIs(under.x, under.y, under.z, 'minecraft:snow_block'));
  const attackHp = carl.health;
  await cmd('execute at Carl run tp Anna ~ ~ ~-2 0 30');
  await sleep(400);
  const t2 = anna.players.Carl?.entity;
  if (t2) anna.attack(t2);
  await sleep(500);
  check('Spleef: Schlagen zählt nicht', carl.health >= attackHp && carl.health >= 19.9, String(carl.health));
  clear(anna, carl);
  anna.setQuickBarSlot(0);
  await digSoon(anna, anna.blockAt(under));
  check('Spleef: Schnee unter Carl weggeschaufelt → Carl bricht durch, Sieg für Anna',
    await waitFor(() => sees(anna, /Carl ist durchs Schneefeld gebrochen/) && sees(anna, /Sieg für Anna/), 5000),
    anna.chats.join(' | '));
  await allInHall(anna, carl);
  check('Spleef: Schneefeld danach wieder heil', await blockIs(under.x, under.y, under.z, 'minecraft:snow_block'));

  // ---------- Labyrinth: Ziel in der Mitte, jede Runde ein neuer (gerechter) Irrgarten
  await fight('labyrinth', async () => {
    check('Labyrinth: Irrgarten, Start in entgegengesetzten Ecken', sees(anna, /Arena Irrgarten/) &&
      anna.entity.position.distanceTo(carl.entity.position) > 40, anna.entity.position.distanceTo(carl.entity.position).toFixed(1));
    check('Labyrinth: Schwert, Bogen, Kompass', slot(anna, 0)?.name === 'iron_sword' && slot(anna, 1)?.name === 'bow' &&
      slot(anna, 2)?.name === 'compass', [0, 1, 2].map((i) => slot(anna, i)?.name).join(','));
    check('Labyrinth: Hinweis aufs Ziel', sees(anna, /Wer zuerst das Ziel in der Mitte erreicht/), anna.chats.join(' | '));
  });
  const lo = origin(anna, -16, -16);
  const MAZE_N = 11;
  /** Welche Heckenstücke stehen gerade? Je inneres Stück eine Stelle: senkrecht (x-Linie), waagrecht (z-Linie). */
  const hedges = () => {
    let sig = '';
    for (let i = 1; i < MAZE_N; i++) {
      for (let j = 0; j < MAZE_N; j++) {
        const line = -17 + 3 * i;
        const along = -17 + 3 * j + 1;
        for (const [x, z] of [[line, along], [along, line]]) {
          const b = anna.blockAt(new Vec3(lo.x + x, lo.y + 2, lo.z + z));
          sig += b && b.name.endsWith('leaves') ? '1' : '0';
        }
      }
    }
    return sig;
  };
  /** Kürzester Weg (in Zellen) von beiden Startecken zur Mitte – aus den Hecken, die im Spiel stehen. */
  const mazeDistances = (sig) => {
    const wall = (i, j, k) => sig[((i - 1) * MAZE_N + j) * 2 + k] === '1';
    const open = (x, z, dx, dz) => {
      if (dx === 1) return x + 1 < MAZE_N && !wall(x + 1, z, 0);
      if (dx === -1) return x > 0 && !wall(x, z, 0);
      if (dz === 1) return z + 1 < MAZE_N && !wall(z + 1, x, 1);
      return z > 0 && !wall(z, x, 1);
    };
    const bfs = (sx, sz) => {
      const dist = new Map([[`${sx},${sz}`, 0]]);
      const queue = [[sx, sz]];
      while (queue.length) {
        const [x, z] = queue.shift();
        for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const k = `${x + dx},${z + dz}`;
          if (!dist.has(k) && open(x, z, dx, dz)) {
            dist.set(k, dist.get(`${x},${z}`) + 1);
            queue.push([x + dx, z + dz]);
          }
        }
      }
      return dist.get('5,5') ?? -1;
    };
    return [bfs(0, 0), bfs(MAZE_N - 1, MAZE_N - 1)];
  };
  const mazeA = hedges();
  const distA = mazeDistances(mazeA);
  check('Labyrinth: Hecken stehen, beide Ecken gleich weit vom Ziel', mazeA.includes('1') && mazeA.includes('0') &&
    distA[0] > 0 && distA[0] === distA[1], `${distA.join(' / ')} Zellen`);
  check('Labyrinth: Ziel in der Mitte mit Leuchtfeuer und Goldrand',
    (await blockIs(lo.x - 1, lo.y, lo.z - 1, 'minecraft:beacon')) && (await blockIs(lo.x + 1, lo.y, lo.z + 1, 'minecraft:gold_block')) &&
      (await blockIs(lo.x, lo.y, lo.z, 'minecraft:emerald_block')));
  clear(anna, carl);
  // Anna steht kurz vor dem Ziel auf dem Platz in der Mitte und läuft hinein.
  await cmd(`execute in minecraft:duels run tp Anna ${lo.x - 1 + 0.5} ${lo.y + 1} ${lo.z - 4 + 0.5} 0 0`);
  await sleep(600);
  check('Labyrinth: kurz vor dem Ziel zählt noch nichts', !sees(anna, /Ziel!/), anna.chats.join(' | '));
  if (NO_MOVE) {
    await cmd(`execute in minecraft:duels run tp Anna ${lo.x - 1 + 0.5} ${lo.y + 1} ${lo.z - 1 + 0.5}`);
  } else {
    await anna.lookAt(new Vec3(lo.x - 0.5, lo.y + 1.6, lo.z + 0.5), true);
    anna.setControlState('forward', true);
    await waitFor(() => sees(anna, /Ziel!/), 5000);
    anna.setControlState('forward', false);
  }
  check('Labyrinth: Anna läuft aufs Ziel → Ziel! und Sieg für Anna',
    await waitFor(() => sees(anna, /Ziel! Anna hat als Erstes die Mitte des Irrgartens erreicht/) && sees(anna, /Sieg für Anna/), 5000),
    anna.chats.join(' | '));
  check('Labyrinth: Carl liest „Zu spät!“', await waitFor(() => sawTitle(carl, /Zu spät/), 2000), carl.titles.join(' | '));
  await allInHall(anna, carl);
  check('Netzwerk: goals_labyrinth = 1, wins_labyrinth', calls().some((c) => c.t === 'stats' && c.name === 'Anna' &&
    c.add.goals_labyrinth === 1 && c.add.wins_labyrinth === 1));
  // Zweiter Kampf: ein anderer Irrgarten, wieder gerecht – diesmal wird gekämpft.
  await fight('labyrinth');
  const mazeB = hedges();
  const distB = mazeDistances(mazeB);
  check('Labyrinth: neuer Kampf → neuer Irrgarten, wieder gleich weit', mazeB !== mazeA && distB[0] > 0 && distB[0] === distB[1],
    `${distB.join(' / ')} Zellen, ${[...mazeA].filter((c, i) => c !== mazeB[i]).length} Stücke anders`);
  await cmd(`execute in minecraft:duels run tp Anna ${lo.x + 3 + 0.5} ${lo.y + 1} ${lo.z - 4 + 0.5}`,
    `execute in minecraft:duels run tp Carl ${lo.x + 3 + 0.5} ${lo.y + 1} ${lo.z + 2 + 0.5}`);
  await sleep(500);
  await winByFight('Labyrinth', 0);

  // ---------- Brücke (Test: 2 Tore)
  await fight('bruecke', async () => {
    check('Brücke: Klammbrücke, Blöcke in Teamfarbe (Blau/Rot)', sees(anna, /Arena Klammbrücke/) &&
      slot(anna, 3)?.name === 'blue_concrete' && slot(carl, 3)?.name === 'red_concrete' && anna.game.gameMode === 'survival',
      `${slot(anna, 3)?.name} ${slot(carl, 3)?.name}`);
  });
  const ro = origin(anna, 0, -19);
  anna.setQuickBarSlot(3);
  await sleep(250);
  try {
    await Promise.race([anna.placeBlock(anna.blockAt(new Vec3(ro.x + 2, ro.y, ro.z - 19)), new Vec3(0, 1, 0)), sleep(3000)]);
  } catch {
    // geprüft wird am Block
  }
  check('Brücke: Block in Teamfarbe setzen', await blockIs(ro.x + 2, ro.y + 1, ro.z - 19, 'minecraft:blue_concrete'));
  clear(anna, carl);
  await cmd('execute at Carl run tp Carl ~11 ~ ~');
  check('Brücke: Carl stürzt in die Klamm → kommt am Start wieder (scheidet nicht aus)',
    await waitFor(() => sees(anna, /Carl ist in die Klamm gestürzt/), 5000) &&
      (await waitFor(() => Math.abs(carl.entity.position.z - (ro.z + 19.5)) < 2 && carl.game.gameMode === 'survival', 4000)),
    `${anna.chats.join(' | ')} · ${JSON.stringify(carl.entity.position)} ${carl.game.gameMode}`);
  clear(anna, carl);
  await cmd(`execute in minecraft:duels run tp Anna ${ro.x + 0.5} ${ro.y + 3} ${ro.z + 26.5}`);
  check('Brücke: Anna springt ins rote Tor → Tor! 1:0', await waitFor(() => sees(anna, /Tor! Anna punktet für Blau – Stand 1:0/), 5000),
    anna.chats.join(' | '));
  check('Brücke: nach dem Tor alle zurück am Start', await waitFor(() => Math.abs(anna.entity.position.z - (ro.z - 18.5)) < 2, 4000),
    JSON.stringify(anna.entity.position));
  await started(anna);
  clear(anna, carl);
  await cmd(`execute in minecraft:duels run tp Carl ${ro.x + 0.5} ${ro.y + 3} ${ro.z + 26.5}`);
  check('Brücke: ins eigene Tor = zurück an den Start, kein Punkt', await waitFor(() => sees(anna, /Carl ist ins eigene Tor gesprungen/), 5000) &&
    !sees(anna, /Stand 1:1/), anna.chats.join(' | '));
  await sleep(1600);
  anna.titles.length = 0;
  await cmd(`execute in minecraft:duels run tp Anna ${ro.x + 0.5} ${ro.y + 3} ${ro.z + 26.5}`);
  check('Brücke: zweites Tor → Sieg für Anna (2 Tore)', await waitFor(() => sees(anna, /Stand 2:0/) && sees(anna, /Sieg für Anna/), 6000),
    anna.chats.join(' | '));
  check('Brücke: Endstand der Tore', sees(anna, /Tore zum Schluss: 2:0/));
  await allInHall(anna, carl);
  check('Brücke: gesetzter Block danach wieder weg', await blockIs(ro.x + 2, ro.y + 1, ro.z - 19, 'minecraft:air'));
  check('Netzwerk: goals_bruecke = 2', calls().some((c) => c.t === 'stats' && c.name === 'Anna' && c.add.goals_bruecke === 2));

  // ---------- /duel mit Arena und „Bester aus 3“
  clear(anna, carl);
  anna.chat('/duel Carl klassisch burghof 3');
  check('Herausforderung mit Arena und Runden', await waitFor(() => sees(carl, /Klassisch · Bester aus 3 · Burghof/), 4000),
    carl.chats.join(' | '));
  carl.chat('/duel annehmen');
  await waitFor(() => inArena(anna) && inArena(carl), 8000);
  check('Bester aus 3: Arena Burghof, Hinweis', sees(anna, /Arena Burghof/) && sees(anna, /Bester aus 3: wer zuerst 2 Runden gewinnt/),
    anna.chats.join(' | '));
  await started(anna);
  clear(anna, carl);
  check('Runde 1 an Anna', (await fightUntilDown(anna, carl, 0)) &&
    (await waitFor(() => sees(anna, /Runde 1 an Anna – Stand 1:0/), 4000)), anna.chats.join(' | '));
  check('Keine Siegerehrung nach Runde 1', !sees(anna, /Sieg für/));
  check('Runde 2: Carl ist wieder dabei', await waitFor(() => carl.game.gameMode === 'adventure' && inArena(carl) &&
    anna.titles.includes('1'), 9000), `${carl.game.gameMode} ${JSON.stringify(carl.entity.position)}`);
  await started(anna);
  clear(anna, carl);
  check('Runde 2 an Anna → Sieg 2:0', (await fightUntilDown(anna, carl, 0)) &&
    (await waitFor(() => sees(anna, /Sieg für Anna/) && sees(anna, /Endstand nach Runden: 2:0/), 5000)), anna.chats.join(' | '));
  await allInHall(anna, carl);
  check('Netzwerk: rounds_won = 2', calls().some((c) => c.t === 'stats' && c.name === 'Anna' && c.add.rounds_won === 2));

  // ---------- Kampfart ausgeschaltet (config.yml + /duels neuladen)
  const configFile = path.join(SERVER, 'plugins/MoinMorniDuels/config.yml');
  const configBefore = readFile(configFile);
  fs.writeFileSync(configFile, `${configBefore}\nkits:\n  kombo:\n    an: false\n`);
  await cmd('duels neuladen');
  clear(anna);
  anna.chat('/duel Carl kombo');
  check('Ausgeschaltete Kampfart: nicht herausfordern', await waitFor(() => sees(anna, /gerade ausgeschaltet/), 3000),
    anna.chats.join(' | '));
  fs.writeFileSync(configFile, configBefore);
  await cmd('duels neuladen');

  // ---------- Party gegen Party
  parties('party.Anna=Anna,Bert', 'party.Carl=Carl,Dora');
  clear(anna, bert2, carl, dora);
  anna.chat('/duel Carl ritter');
  check('Party gegen Party: beide leiten eine Party', await waitFor(() => sees(carl, /Ritter · Party gegen Party/), 4000),
    carl.chats.join(' | '));
  carl.chat('/duel annehmen');
  check('Party gegen Party: alle vier in der Arena', await waitFor(() => [anna, bert2, carl, dora].every(inArena), 8000));
  check('Party gegen Party: Anna & Bert gegen Carl & Dora',
    side(anna) === side(bert2) && side(carl) === side(dora) && side(anna) !== side(carl),
    [anna, bert2, carl, dora].map((b) => `${b.username}:${side(b)}`).join(' '));
  await started(anna);
  clear(anna, bert2, carl, dora);
  carl.chat('/verlassen');
  dora.chat('/verlassen');
  check('Party gegen Party: Sieg für Anna & Bert', await waitFor(() => sees(anna, /Sieg für Anna & Bert/), 5000), anna.chats.join(' | '));
  await allInHall(anna, bert2, carl, dora);
  check('Netzwerk: party_games/party_wins', calls().some((c) => c.t === 'stats' && c.name === 'Bert' && c.add.party_games === 1 &&
    c.add.party_wins === 1));

  // ---------- Party: Jeder gegen jeden
  parties('party.Anna=Anna,Bert,Dora');
  clear(anna, bert2, dora);
  bert2.chat('/partykampf schwert');
  check('Jeder gegen jeden: nur die Party-Leitung startet', await waitFor(() => sees(bert2, /Your party leader starts that/), 3000),
    bert2.chats.join(' | '));
  anna.chat('/partykampf schwert');
  check('Jeder gegen jeden: alle drei in der Arena', await waitFor(() => [anna, bert2, dora].every(inArena), 8000) &&
    sees(anna, /Party-Kampf „Jeder gegen jeden“: Nur Schwert mit Anna, Bert, Dora/), anna.chats.join(' | '));
  const spread = [anna.entity.position.distanceTo(bert2.entity.position), anna.entity.position.distanceTo(dora.entity.position),
    bert2.entity.position.distanceTo(dora.entity.position)];
  check('Jeder gegen jeden: verschiedene Startplätze im Kreis', spread.every((d) => d > 5), spread.map((d) => d.toFixed(1)).join(','));
  await started(anna);
  clear(anna, bert2, dora);
  bert2.chat('/verlassen');
  await sleep(600);
  check('Jeder gegen jeden: nach Berts Aufgabe läuft es weiter', !sees(anna, /Sieg für/));
  dora.chat('/verlassen');
  check('Jeder gegen jeden: Sieg für Anna', await waitFor(() => sees(anna, /Sieg für Anna/), 5000), anna.chats.join(' | '));
  await allInHall(anna, bert2, dora);

  // ---------- Party: zwei Teams (zufällig geteilt – geht auch mit der Brücke)
  parties('party.Anna=Anna,Bert,Carl,Dora');
  clear(anna, bert2, carl, dora);
  anna.chat('/partykampf bruecke teams');
  check('Zwei Teams: alle vier in der Arena', await waitFor(() => [anna, bert2, carl, dora].every(inArena), 8000) &&
    sees(anna, /Party-Kampf in zwei Teams: Brücke – \S+ & \S+ gegen \S+ & \S+/), anna.chats.join(' | '));
  const sides = [anna, bert2, carl, dora].map(side);
  check('Zwei Teams: 2 gegen 2 auf beiden Seiten', sides.filter((x) => x === sides[0]).length === 2, sides.join(','));
  check('Zwei Teams: Blöcke in Teamfarbe', [anna, bert2, carl, dora].every((b) => /blue_concrete|red_concrete/.test(slot(b, 3)?.name ?? '')),
    [anna, bert2, carl, dora].map((b) => slot(b, 3)?.name).join(','));
  await started(anna);
  clear(anna, bert2, carl, dora);
  const others = [bert2, carl, dora].filter((b) => side(b) !== side(anna));
  for (const b of others) b.chat('/verlassen');
  const mates = [anna, bert2, carl, dora].filter((b) => side(b) === side(anna)).map((b) => b.username);
  check('Zwei Teams: Sieg für Annas Team', await waitFor(() => sees(anna, new RegExp(`Sieg für (${mates.join(' & ')}|${[...mates].reverse().join(' & ')})`)), 5000),
    anna.chats.join(' | '));
  await allInHall(anna, bert2, carl, dora);
  check('Netzwerk: Zwei Teams zählt als Party-Kampf', calls().some((c) => c.t === 'stats' && c.name === 'Anna' &&
    c.add.party_wins === 1 && c.add.wins_bruecke === 1));
  fs.writeFileSync(fakeProps, originalProps);

  // ------------------------------------------------------------------ Zur Lobby
  dora.setQuickBarSlot(8);
  await sleep(300);
  dora.activateItem();
  check('Bett „Zurück zur Lobby“ → Netzwerk fragt (askToLobby), Dora spielt nicht → gleich in die Lobby',
    await waitFor(() => dora.kickReason !== null, 4000) &&
      calls().some((c) => c.t === 'ask-to-lobby' && c.name === 'Dora' && c.playing === false) &&
      !calls().some((c) => c.t === 'to-lobby' && c.name === 'Dora'),
    dora.kickReason ?? 'nicht gekickt');

  log = calls();
  check('Netzwerk: Statistik nach jedem Kampf (mindestens 12 Meldungen)',
    log.filter((c) => c.t === 'stats').length >= 12, String(log.filter((c) => c.t === 'stats').length));

  // Älteres Netzwerk-Plugin (ohne Methoden ab Version 2): „zur Lobby“ geht wie bisher sofort.
  fs.appendFileSync(path.join(SERVER, 'plugins/MoinMorni/fake.properties'), 'api=1\n');
  carl.setQuickBarSlot(8);
  await sleep(300);
  carl.activateItem();
  check('Älteres Netzwerk-Plugin: Bett schickt wie bisher sofort in die Lobby (toLobby)',
    await waitFor(() => carl.kickReason !== null, 4000) && calls().some((c) => c.t === 'old-api' && c.method === 'askToLobby') &&
      calls().some((c) => c.t === 'to-lobby' && c.name === 'Carl'), carl.kickReason ?? 'nicht gekickt');

  for (const b of [anna, bert2]) b.quit();
  await sleep(500);
  const failed = results.filter((r) => !r.ok).length;
  console.log(`\n${results.length - failed}/${results.length} bestanden`);
  process.exit(failed ? 1 : 0);
}

setTimeout(() => {
  console.log('Zeit abgelaufen');
  console.log(`${results.filter((r) => r.ok).length}/${results.length} bis hierhin bestanden`);
  process.exit(2);
}, 1_800_000);

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
