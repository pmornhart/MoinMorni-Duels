/**
 * Echter Test von Morni Duels mit Test-Spielern (mineflayer) auf Paper 26.1.2 – gestartet von test/run.sh.
 *   node duels-test.cjs <host> <port> <rcon-host:port> <rcon-passwort-datei> <server-ordner>
 *
 * Spieler: Anna (deutsch, Party-Leiterin mit Bert), Bert (englisch), Carl, Dora (deutsch). Sprache und Party kommen
 * vom Test-Netzwerk (test/fake-network), das auch Statistik, Zustand und „zur Lobby“ in calls.log mitschreibt.
 *
 * Verfasst mit Claude 🤖
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const mineflayer = require('mineflayer');
const { rcon } = require('./rcon.cjs');

const [HOST = '127.0.0.1', PORT = '25720', RCON = '127.0.0.1:25820', PASS_FILE, SERVER] = process.argv.slice(2);
const PASS = fs.readFileSync(PASS_FILE, 'utf8').trim();
const VERSION = process.env.MC_VERSION ?? '26.1';
const HALL = { x: 0.5, y: 101, z: 12.5 };
const AFTER_MS = 4000; // nachspiel-sekunden im Test
const TIME_LIMIT_MS = 40000; // zeitlimit-sekunden im Test
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
  path.join(SERVER, 'plugins/MorniDuels/spieler', `${offlineUuid(name)}.yml`);
const readFile = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '');
const calls = () =>
  readFile(path.join(SERVER, 'plugins/MorniNet/calls.log'))
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

async function main() {
  // ------------------------------------------------------------------ Ankunft
  const anna = await join('Anna');
  const bert = await join('Bert');
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
  check('Menü zeigt Locker/Gewertet × 1v1/2v2',
    plain(win?.slots[12]?.customName).includes('Locker') && plain(win?.slots[16]?.customName).includes('Gewertet') &&
      win?.slots[16]?.count === 2, plain(win?.slots[16]?.customName));
  await anna.clickWindow(12, 0, 0);
  await sleep(500);
  check('Anna sucht (Klassisch · Locker · 1 gegen 1)', sees(anna, /Du suchst Gegner: Klassisch · Locker · 1 gegen 1/));
  check('Knopf „Warteschlange verlassen“ erscheint', slot(anna, 4)?.name === 'barrier', slot(anna, 4)?.name);
  check('Aktionsleiste: „Suche Gegner“ mit Zeit', await waitFor(() => anna.bars.some((b) => /Suche Gegner.*0:0\d/.test(b)), 2500),
    anna.bars.slice(-1)[0]);
  win = await openWithItem(bert, 0);
  check('Englisches Menü: „Choose a fight“ / „Casual“', win && plain(win.title).includes('Choose a fight') &&
    plain(win.slots[12]?.customName).includes('Casual'), win && plain(win.slots[12]?.customName));
  const start1 = anna.entity.position.clone();
  await bert.clickWindow(12, 0, 0);
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
  const states = log.filter((c) => c.t === 'state').map((c) => c.state);
  check('Zustand ans Netzwerk: „running“ und danach „waiting“',
    states.includes('running') && states.lastIndexOf('waiting') > states.indexOf('running'), states.join(','));

  win = await openWithItem(anna, 7);
  check('Gold öffnet „Rangliste & Statistik“ mit eigenen Werten und Ranglisten',
    win && plain(win.title).includes('Rangliste & Statistik') &&
      plain(win.slots[11]?.customName).includes('Deine Werte') && plain(win.slots[20]?.customName).includes('Rangliste'),
    win && `${plain(win.slots[11]?.customName)} / ${plain(win.slots[20]?.customName)}`);
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
    readFile(path.join(SERVER, 'plugins/MorniDuels/rangliste.yml'))));

  // ------------------------------------------------------------------ Zuschauen + Verbindung weg = Niederlage
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
  if (win) await carl.clickWindow(11, 0, 0);
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
  bert.quit();
  check('Verbindung weg = Niederlage: Sieg für Anna', await waitFor(() => sees(anna, /Bert hat das Spiel verlassen/) &&
    sees(anna, /Sieg für Anna/), 4000), anna.chats.join(' | '));
  check('Zuschauer sieht das Ergebnis', await waitFor(() => sees(carl, /Sieg für Anna/), 3000), carl.chats.join(' | '));
  check('Zuschauer nach dem Nachspiel zurück in der Halle', await waitFor(() => inHall(carl), AFTER_MS + 5000),
    `${carl.game.gameMode} ${JSON.stringify(carl.entity.position)}`);
  await waitFor(() => inHall(anna), 3000);
  const bert2 = await join('Bert');
  bert2.chat('/stats');
  await sleep(800);
  check('Bert nach dem Wiederkommen: 4 Niederlagen in Klassisch/Fähigkeiten gesamt',
    sees(bert2, /Losses 3/) && sees(bert2, /Losses 1/), bert2.chats.filter((c) => c.includes('Losses')).join(' | '));

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
  const abilityMenu = nextWindow(anna);
  if (win) await anna.clickWindow(28, 0, 0);
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
  check('/duels info: 1 Arena belegt, 1 Kampf', /1\/8 belegt.*Kämpfe: 1/.test(info), info);
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

  // ------------------------------------------------------------------ Zur Lobby
  dora.setQuickBarSlot(8);
  await sleep(300);
  dora.activateItem();
  check('Bett „Zurück zur Lobby“ → Netzwerk schickt in die Lobby',
    await waitFor(() => dora.kickReason !== null, 4000) && calls().some((c) => c.t === 'to-lobby' && c.name === 'Dora'),
    dora.kickReason ?? 'nicht gekickt');

  log = calls();
  check('Netzwerk: Statistik nach jedem Kampf (mindestens 12 Meldungen)',
    log.filter((c) => c.t === 'stats').length >= 12, String(log.filter((c) => c.t === 'stats').length));

  for (const b of [anna, bert2, carl]) b.quit();
  await sleep(500);
  const failed = results.filter((r) => !r.ok).length;
  console.log(`\n${results.length - failed}/${results.length} bestanden`);
  process.exit(failed ? 1 : 0);
}

setTimeout(() => {
  console.log('Zeit abgelaufen');
  console.log(`${results.filter((r) => r.ok).length}/${results.length} bis hierhin bestanden`);
  process.exit(2);
}, 480_000);

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
