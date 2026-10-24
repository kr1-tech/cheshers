import crypto from 'node:crypto';

const production = process.env.NODE_ENV === 'production';
if (production && (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32)) {
  throw new Error('JWT_SECRET must contain at least 32 characters in production');
}

export const config = {
  production,
  port: Number(process.env.PORT || 3000),
  jwtSecret: process.env.JWT_SECRET || crypto.randomBytes(48).toString('hex'),
  dataPath: process.env.DATA_PATH || './data/cheshers.sqlite',
  corsOrigin: process.env.CORS_ORIGIN || '*',
  trustProxy: process.env.TRUST_PROXY === 'true',
};
