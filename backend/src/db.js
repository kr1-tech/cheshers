import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import { now } from './util.js';

export function createDatabase(file) {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(path.resolve(file)), { recursive: true });
  const db = new Database(file);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE COLLATE NOCASE,
      email TEXT UNIQUE COLLATE NOCASE,
      password_hash TEXT,
      guest INTEGER NOT NULL DEFAULT 0,
      country TEXT NOT NULL DEFAULT '',
      bullet_rating INTEGER NOT NULL DEFAULT 1200,
      blitz_rating INTEGER NOT NULL DEFAULT 1200,
      rapid_rating INTEGER NOT NULL DEFAULT 1200,
      classical_rating INTEGER NOT NULL DEFAULT 1200,
      bullet_games INTEGER NOT NULL DEFAULT 0,
      blitz_games INTEGER NOT NULL DEFAULT 0,
      rapid_games INTEGER NOT NULL DEFAULT 0,
      classical_games INTEGER NOT NULL DEFAULT 0,
      games_played INTEGER NOT NULL DEFAULT 0,
      wins INTEGER NOT NULL DEFAULT 0,
      losses INTEGER NOT NULL DEFAULT 0,
      draws INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS games (
      id TEXT PRIMARY KEY, white_id TEXT NOT NULL, black_id TEXT NOT NULL,
      time_control TEXT NOT NULL, moves TEXT NOT NULL, pgn TEXT NOT NULL,
      result TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL, completed_at INTEGER NOT NULL,
      FOREIGN KEY(white_id) REFERENCES users(id), FOREIGN KEY(black_id) REFERENCES users(id)
    );
    CREATE TABLE IF NOT EXISTS active_games (
      id TEXT PRIMARY KEY, data TEXT NOT NULL, updated_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS friends (
      user_id TEXT NOT NULL, friend_id TEXT NOT NULL, created_at INTEGER NOT NULL,
      PRIMARY KEY(user_id, friend_id),
      FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(friend_id) REFERENCES users(id)
    );
    CREATE TABLE IF NOT EXISTS reports (
      id TEXT PRIMARY KEY, reporter_id TEXT NOT NULL, reported_id TEXT NOT NULL,
      game_id TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_games_players ON games(white_id, black_id, completed_at);
    CREATE INDEX IF NOT EXISTS idx_friends_user ON friends(user_id);
  `);
  return db;
}

export function userById(db, id) { return db.prepare('SELECT * FROM users WHERE id = ?').get(id); }
export function userByName(db, username) { return db.prepare('SELECT * FROM users WHERE username = ?').get(username); }
export function userByEmail(db, email) { return db.prepare('SELECT * FROM users WHERE email = ?').get(email); }
export function userByIdentity(db, identity) { return db.prepare('SELECT * FROM users WHERE username = ? OR email = ?').get(identity, identity); }
export function insertUser(db, { id, username, email = null, passwordHash = null, guest = false }) {
  db.prepare('INSERT INTO users (id, username, email, password_hash, guest, created_at) VALUES (?, ?, ?, ?, ?, ?)')
    .run(id, username, email, passwordHash, guest ? 1 : 0, now());
  return userById(db, id);
}
export function addFriends(db, a, b) {
  const insert = db.prepare('INSERT OR IGNORE INTO friends (user_id, friend_id, created_at) VALUES (?, ?, ?)');
  const transaction = db.transaction(() => { insert.run(a, b, now()); insert.run(b, a, now()); });
  transaction();
}
