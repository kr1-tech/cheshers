import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import { rateLimit } from 'express-rate-limit';
import { createServer } from 'node:http';
import { Server } from 'socket.io';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';
import { config } from './config.js';
import { addFriends, createDatabase, userById, userByName } from './db.js';
import { authenticateRequest, authenticateToken, installAuthRoutes } from './auth.js';
import { GameRoom } from './game.js';
import { Matchmaker } from './matchmaking.js';
import { TIME_CONTROLS, elo, filterProfanity, id as makeId, now, publicUser } from './util.js';

export function createCheshersServer({ dbPath = config.dataPath, secret = config.jwtSecret } = {}) {
  const db = createDatabase(dbPath);
  const app = express();
  const httpServer = createServer(app);
  const io = new Server(httpServer, {
    cors: { origin: config.corsOrigin === '*' ? true : config.corsOrigin.split(',') },
    transports: ['websocket'], maxHttpBufferSize: 64_000, pingInterval: 25_000, pingTimeout: 20_000,
  });
  const rooms = new Map();
  const matchmaker = new Matchmaker();
  const challenges = new Map();

  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', 1);
  app.use(helmet());
  app.use(cors({ origin: config.corsOrigin === '*' ? true : config.corsOrigin.split(',') }));
  app.use(express.json({ limit: '32kb' }));
  app.use('/auth', rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: 'draft-8', legacyHeaders: false }));
  installAuthRoutes(app, db, secret);

  app.get('/health', (_req, res) => res.json({ ok: true, activeGames: rooms.size, time: now() }));
  app.get('/leaderboard', (req, res) => {
    const category = ['bullet', 'blitz', 'rapid', 'classical'].includes(req.query.category) ? req.query.category : 'blitz';
    const scope = ['global', 'friends', 'weekly'].includes(req.query.scope) ? req.query.scope : 'global';
    const ratingColumn = `${category}_rating`;
    const gamesColumn = `${category}_games`;
    let friendIds = null;
    if (scope === 'friends') {
      const user = authenticateRequest(req, db, secret);
      if (!user) return res.status(401).json({ error: 'Sign in to view friends' });
      friendIds = [user.id, ...db.prepare('SELECT friend_id FROM friends WHERE user_id = ?').all(user.id).map(row => row.friend_id)];
    }
    const where = friendIds ? `WHERE u.id IN (${friendIds.map(() => '?').join(',')})` : '';
    if (scope === 'weekly') {
      const cutoff = now() - 7 * 86_400_000;
      const entries = db.prepare(`
        SELECT u.username, u.${ratingColumn} AS rating, u.${gamesColumn} AS gamesPlayed, u.country,
          COALESCE(SUM(CASE WHEN (g.result = '1-0' AND g.white_id = u.id) OR (g.result = '0-1' AND g.black_id = u.id) THEN 1 ELSE 0 END), 0) AS weeklyScore
        FROM users u LEFT JOIN games g ON (g.white_id = u.id OR g.black_id = u.id) AND g.completed_at >= ?
        ${where} GROUP BY u.id ORDER BY weeklyScore DESC, rating DESC LIMIT 50
      `).all(cutoff, ...(friendIds || []));
      return res.json({ entries });
    }
    const entries = db.prepare(`SELECT u.username, u.${ratingColumn} AS rating, u.${gamesColumn} AS gamesPlayed, u.country FROM users u ${where} ORDER BY rating DESC LIMIT 50`)
      .all(...(friendIds || []));
    res.json({ entries });
  });

  function saveActive(room) {
    db.prepare('INSERT INTO active_games (id, data, updated_at) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at')
      .run(room.id, JSON.stringify(room.serialize()), now());
  }
  function onState(room) { saveActive(room); io.to(room.id).emit('game:state', room.state()); }
  function onEnd(room) {
    room.chess.header('Result', room.result, 'White', room.white.username, 'Black', room.black.username, 'Event', 'Cheshers online');
    const category = room.timeControl.category;
    room.ratingChanges = {};
    const startingUsers = { w: userById(db, room.white.id), b: userById(db, room.black.id) };
    const update = db.transaction(() => {
      for (const [color, userId] of [['w', room.white.id], ['b', room.black.id]]) {
        const user = startingUsers[color];
        const score = room.winner == null ? 0.5 : room.winner === color ? 1 : 0;
        let nextRating = null;
        if (category !== 'unlimited') {
          const opponent = startingUsers[color === 'w' ? 'b' : 'w'];
          const ratingColumn = `${category}_rating`;
          const gamesColumn = `${category}_games`;
          const oldRating = user[ratingColumn];
          nextRating = elo(oldRating, opponent[ratingColumn], score, user[gamesColumn]);
          db.prepare(`UPDATE users SET ${ratingColumn} = ?, ${gamesColumn} = ${gamesColumn} + 1 WHERE id = ?`).run(nextRating, userId);
          room.ratingChanges[userId] = { delta: nextRating - oldRating, rating: nextRating, category };
        }
        db.prepare(`UPDATE users SET games_played = games_played + 1, wins = wins + ?, losses = losses + ?, draws = draws + ? WHERE id = ?`)
          .run(score === 1 ? 1 : 0, score === 0 ? 1 : 0, score === 0.5 ? 1 : 0, userId);
      }
      db.prepare('INSERT INTO games (id, white_id, black_id, time_control, moves, pgn, result, reason, created_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
        .run(room.id, room.white.id, room.black.id, room.timeControl.id, JSON.stringify(room.serialize().moves), room.chess.pgn(), room.result, room.reason, room.createdAt, now());
      db.prepare('DELETE FROM active_games WHERE id = ?').run(room.id);
    });
    update();
    io.to(room.id).emit('game:over', room.state());
    rooms.delete(room.id);
    room.close();
  }

  for (const row of db.prepare('SELECT data FROM active_games').all()) {
    try {
      const saved = JSON.parse(row.data);
      const room = new GameRoom({ id: saved.id, white: saved.white, black: saved.black, timeControl: saved.timeControl, restored: saved, onState, onEnd });
      rooms.set(room.id, room);
    } catch { db.prepare('DELETE FROM active_games WHERE id = ?').run(JSON.parse(row.data).id); }
  }

  function activeForUser(userId) {
    return [...rooms.values()].find(room => room.result === '*' && room.participant(userId));
  }
  function createGame(first, second, timeControl) {
    if (!TIME_CONTROLS[timeControl]) throw new Error('Unsupported time control');
    const firstWhite = Math.random() < 0.5;
    const whiteUser = userById(db, firstWhite ? first.userId : second.userId);
    const blackUser = userById(db, firstWhite ? second.userId : first.userId);
    const room = new GameRoom({
      id: makeId(), white: publicUser(whiteUser), black: publicUser(blackUser), timeControl, onState, onEnd,
    });
    rooms.set(room.id, room);
    saveActive(room);
    for (const userId of [whiteUser.id, blackUser.id]) io.in(`user:${userId}`).socketsJoin(room.id);
    io.to(room.id).emit('game:started', room.state());
    return room;
  }

  io.use((socket, next) => {
    const user = authenticateToken(socket.handshake.auth?.token, db, secret);
    if (!user) return next(new Error('Authentication required'));
    socket.data.user = user;
    next();
  });

  io.on('connection', socket => {
    const user = socket.data.user;
    socket.join(`user:${user.id}`);
    matchmaker.remove(user.id);
    const active = activeForUser(user.id);
    if (active) {
      socket.join(active.id);
      socket.emit('game:available', { gameId: active.id });
      socket.emit('game:state', active.state());
    }
    socket.emit('session:ready', { user: publicUser(user), serverAt: now() });

    socket.on('queue:join', payload => {
      const parsed = z.object({ timeControl: z.string().refine(value => Boolean(TIME_CONTROLS[value])) }).safeParse(payload);
      if (!parsed.success) return socket.emit('error:message', { message: 'Choose a valid time control' });
      if (activeForUser(user.id)) return socket.emit('error:message', { message: 'Finish or resign your active game first' });
      const category = TIME_CONTROLS[parsed.data.timeControl].category;
      const entry = { userId: user.id, timeControl: parsed.data.timeControl, rating: category === 'unlimited' ? 1200 : user[`${category}_rating`], joinedAt: now() };
      const opponent = matchmaker.add(entry);
      if (opponent) createGame(entry, opponent, entry.timeControl);
      else socket.emit('queue:waiting', { position: matchmaker.waiting(user.id), timeControl: entry.timeControl });
    });
    socket.on('queue:leave', () => { matchmaker.remove(user.id); socket.emit('queue:left'); });

    socket.on('challenge:create', payload => {
      const parsed = z.object({ timeControl: z.string().refine(value => Boolean(TIME_CONTROLS[value])), username: z.string().trim().max(20).optional() }).safeParse(payload);
      if (!parsed.success) return socket.emit('error:message', { message: 'Invalid challenge' });
      const code = makeId().replaceAll('-', '').slice(0, 6).toUpperCase();
      challenges.set(code, { code, hostId: user.id, timeControl: parsed.data.timeControl, targetUsername: parsed.data.username?.toLowerCase(), expiresAt: now() + 600_000 });
      setTimeout(() => challenges.delete(code), 600_000).unref?.();
      socket.emit('challenge:created', { code, timeControl: parsed.data.timeControl });
      if (parsed.data.username) {
        const target = userByName(db, parsed.data.username);
        if (target) io.to(`user:${target.id}`).emit('challenge:invite', { code, from: publicUser(user), timeControl: parsed.data.timeControl });
      }
    });
    socket.on('challenge:join', payload => {
      const parsed = z.object({ code: z.string().trim().min(4).max(12) }).safeParse(payload);
      const challenge = parsed.success ? challenges.get(parsed.data.code.toUpperCase()) : null;
      if (!challenge || challenge.expiresAt < now()) return socket.emit('error:message', { message: 'Invite code is invalid or expired' });
      if (challenge.hostId === user.id) return socket.emit('error:message', { message: 'You cannot join your own challenge' });
      if (challenge.targetUsername && challenge.targetUsername !== user.username.toLowerCase()) return socket.emit('error:message', { message: 'This challenge is for another player' });
      challenges.delete(challenge.code);
      addFriends(db, challenge.hostId, user.id);
      createGame({ userId: challenge.hostId }, { userId: user.id }, challenge.timeControl);
    });

    socket.on('game:resume', payload => {
      const room = rooms.get(String(payload?.gameId || ''));
      if (!room || !room.participant(user.id)) return socket.emit('error:message', { message: 'Game is unavailable' });
      socket.join(room.id);
      socket.emit('game:state', room.state());
    });
    socket.on('game:move', payload => {
      const room = rooms.get(String(payload?.gameId || ''));
      if (!room) return socket.emit('error:message', { message: 'Game is unavailable' });
      const result = room.move(user.id, payload?.uci);
      if (!result.ok) socket.emit('error:message', { message: result.error });
    });
    socket.on('game:resign', payload => { rooms.get(String(payload?.gameId || ''))?.resign(user.id); });
    socket.on('game:draw:offer', payload => { rooms.get(String(payload?.gameId || ''))?.offerDraw(user.id); });
    socket.on('game:draw:respond', payload => { rooms.get(String(payload?.gameId || ''))?.respondDraw(user.id, payload?.accepted === true); });
    socket.on('game:chat', payload => {
      const room = rooms.get(String(payload?.gameId || ''));
      if (!room || !room.participant(user.id)) return;
      const times = (socket.data.chatTimes || []).filter(time => now() - time < 2_000);
      if (times.length >= 3) return socket.emit('error:message', { message: 'Chat is rate limited' });
      times.push(now()); socket.data.chatTimes = times;
      const text = filterProfanity(payload?.text);
      if (!text) return;
      const message = room.addChat(user.id, user.username, text);
      if (message) { saveActive(room); io.to(room.id).emit('game:chat', message); }
    });
    socket.on('game:report', payload => {
      const room = rooms.get(String(payload?.gameId || ''));
      const message = room?.chat.find(item => item.id === payload?.messageId);
      if (!room || !message || message.userId === user.id) return;
      db.prepare('INSERT INTO reports (id, reporter_id, reported_id, game_id, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)')
        .run(makeId(), user.id, message.userId, room.id, filterProfanity(payload?.reason).slice(0, 100), now());
      socket.emit('report:received');
    });
    socket.on('spectate:join', payload => {
      const room = rooms.get(String(payload?.gameId || ''));
      if (!room) return socket.emit('error:message', { message: 'Game is unavailable' });
      const friend = db.prepare('SELECT 1 FROM friends WHERE user_id = ? AND (friend_id = ? OR friend_id = ?) LIMIT 1').get(user.id, room.white.id, room.black.id);
      if (!friend && !room.participant(user.id)) return socket.emit('error:message', { message: 'Only friends can spectate' });
      socket.join(room.id);
      socket.emit('game:state', room.state());
    });
    socket.on('disconnect', () => { matchmaker.remove(user.id); });
  });

  return {
    app, io, db, rooms, httpServer,
    start(port = config.port) { return new Promise(resolve => httpServer.listen(port, '0.0.0.0', () => resolve(httpServer.address()))); },
    async close() {
      for (const room of rooms.values()) room.close();
      await io.close();
      if (httpServer.listening) await new Promise(resolve => httpServer.close(resolve));
      db.close();
    },
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = createCheshersServer();
  await server.start();
  console.log(`Cheshers backend listening on port ${server.httpServer.address().port}`);
  const shutdown = async () => { await server.close(); process.exit(0); };
  process.on('SIGTERM', shutdown); process.on('SIGINT', shutdown);
}
