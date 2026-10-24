import crypto from 'node:crypto';

export const TIME_CONTROLS = Object.freeze({
  '1+0': { id: '1+0', initialMs: 60_000, incrementMs: 0, category: 'bullet' },
  '2+1': { id: '2+1', initialMs: 120_000, incrementMs: 1_000, category: 'bullet' },
  '3+0': { id: '3+0', initialMs: 180_000, incrementMs: 0, category: 'blitz' },
  '3+2': { id: '3+2', initialMs: 180_000, incrementMs: 2_000, category: 'blitz' },
  '5+0': { id: '5+0', initialMs: 300_000, incrementMs: 0, category: 'blitz' },
  '5+3': { id: '5+3', initialMs: 300_000, incrementMs: 3_000, category: 'blitz' },
  '10+0': { id: '10+0', initialMs: 600_000, incrementMs: 0, category: 'rapid' },
  '10+5': { id: '10+5', initialMs: 600_000, incrementMs: 5_000, category: 'rapid' },
  '15+10': { id: '15+10', initialMs: 900_000, incrementMs: 10_000, category: 'rapid' },
  '30+0': { id: '30+0', initialMs: 1_800_000, incrementMs: 0, category: 'classical' },
  unlimited: { id: 'unlimited', initialMs: -1, incrementMs: 0, category: 'unlimited' },
});

export const id = () => crypto.randomUUID();
export const now = () => Date.now();
export const inviteCode = () => crypto.randomBytes(5).toString('base64url').replace(/[-_]/g, '').slice(0, 6).toUpperCase();
export const clampText = (value, max = 200) => String(value ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);

const blocked = ['fuck', 'shit', 'bitch', 'cunt', 'nigger', 'faggot'];
export function filterProfanity(input) {
  let text = clampText(input);
  for (const word of blocked) text = text.replace(new RegExp(`\\b${word}\\b`, 'gi'), '*'.repeat(word.length));
  return text;
}

export function expectedScore(rating, opponent) { return 1 / (1 + 10 ** ((opponent - rating) / 400)); }
export function elo(rating, opponent, score, games) {
  const k = games < 30 ? 40 : rating >= 2400 ? 10 : 20;
  return Math.round(rating + k * (score - expectedScore(rating, opponent)));
}

export function publicUser(user) {
  if (!user) return null;
  return {
    id: user.id, username: user.username, guest: Boolean(user.guest), country: user.country || '',
    ratings: { bullet: user.bullet_rating, blitz: user.blitz_rating, rapid: user.rapid_rating, classical: user.classical_rating },
    gamesPlayed: user.games_played, wins: user.wins, losses: user.losses, draws: user.draws,
  };
}
