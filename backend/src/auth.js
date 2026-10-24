import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import { insertUser, userByEmail, userById, userByIdentity, userByName } from './db.js';
import { id as makeId, publicUser } from './util.js';

const username = z.string().trim().min(3).max(20).regex(/^[\p{L}\p{N}_ .-]+$/u);
const registerSchema = z.object({ username, email: z.string().trim().email().max(254), password: z.string().min(8).max(72), country: z.string().trim().max(2).optional() });
const loginSchema = z.object({ username: z.string().trim().min(1).max(30), password: z.string().min(1).max(72) });

export function installAuthRoutes(app, db, secret) {
  const tokenFor = user => jwt.sign({ sub: user.id }, secret, { expiresIn: '30d', issuer: 'cheshers' });
  const response = user => ({ token: tokenFor(user), user: publicUser(user) });

  app.post('/auth/guest', (_req, res) => {
    let user;
    do { user = `Guest-${Math.floor(1000 + Math.random() * 9000)}`; } while (userByName(db, user));
    const created = insertUser(db, { id: makeId(), username: user, guest: true });
    res.status(201).json(response(created));
  });

  app.post('/auth/register', async (req, res) => {
    const parsed = registerSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: 'Use a valid username, email, and an 8–72 character password' });
    if (userByName(db, parsed.data.username)) return res.status(409).json({ error: 'Username is already taken' });
    if (userByEmail(db, parsed.data.email)) return res.status(409).json({ error: 'Email is already registered' });
    const passwordHash = await bcrypt.hash(parsed.data.password, 12);
    try {
      const user = insertUser(db, { id: makeId(), username: parsed.data.username, email: parsed.data.email.toLowerCase(), passwordHash });
      if (parsed.data.country) db.prepare('UPDATE users SET country = ? WHERE id = ?').run(parsed.data.country.toUpperCase(), user.id);
      res.status(201).json(response(userById(db, user.id)));
    } catch { res.status(409).json({ error: 'Username is already taken' }); }
  });

  app.post('/auth/login', async (req, res) => {
    const parsed = loginSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: 'Invalid credentials' });
    const user = userByIdentity(db, parsed.data.username);
    if (!user?.password_hash || !(await bcrypt.compare(parsed.data.password, user.password_hash))) return res.status(401).json({ error: 'Invalid credentials' });
    res.json(response(user));
  });

  app.get('/profile', (req, res) => {
    const user = authenticateRequest(req, db, secret);
    if (!user) return res.status(401).json({ error: 'Authentication required' });
    res.json({ user: publicUser(user) });
  });
}

export function authenticateRequest(req, db, secret) {
  const [scheme, token] = String(req.headers.authorization || '').split(' ');
  if (scheme !== 'Bearer' || !token) return null;
  try { return userById(db, jwt.verify(token, secret, { issuer: 'cheshers' }).sub); } catch { return null; }
}

export function authenticateToken(token, db, secret) {
  try { return userById(db, jwt.verify(token, secret, { issuer: 'cheshers' }).sub); } catch { return null; }
}
