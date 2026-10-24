import { Chess } from 'chess.js';
import { TIME_CONTROLS, now } from './util.js';

export class GameRoom {
  constructor({ id, white, black, timeControl, onState = () => {}, onEnd = () => {}, restored = null }) {
    this.id = id;
    this.white = white;
    this.black = black;
    this.timeControl = TIME_CONTROLS[timeControl] || TIME_CONTROLS['5+0'];
    this.onState = onState;
    this.onEnd = onEnd;
    this.chess = new Chess();
    this.whiteMs = this.timeControl.initialMs;
    this.blackMs = this.timeControl.initialMs;
    this.turnStartedAt = now();
    this.createdAt = this.turnStartedAt;
    this.result = '*';
    this.reason = '';
    this.winner = null;
    this.drawOfferBy = null;
    this.chat = restored?.chat || [];
    this.ratingChanges = {};
    this.flagTimer = null;
    if (restored) {
      this.createdAt = restored.createdAt;
      for (const uci of restored.moves) this.#applyUci(uci);
      this.whiteMs = restored.whiteMs;
      this.blackMs = restored.blackMs;
      this.turnStartedAt = now(); // Server downtime pauses offline clocks rather than flagging players.
    }
    this.scheduleFlag();
  }

  colorFor(userId) { return userId === this.white.id ? 'w' : userId === this.black.id ? 'b' : null; }
  participant(userId) { return Boolean(this.colorFor(userId)); }

  remaining(color, timestamp = now()) {
    const base = color === 'w' ? this.whiteMs : this.blackMs;
    if (base < 0 || this.result !== '*') return base;
    if (this.chess.turn() !== color) return base;
    return Math.max(0, base - (timestamp - this.turnStartedAt));
  }

  move(userId, uci) {
    if (this.result !== '*') return { ok: false, error: 'Game is already over' };
    const color = this.colorFor(userId);
    if (!color) return { ok: false, error: 'You are not a player in this game' };
    if (this.chess.turn() !== color) return { ok: false, error: 'It is not your turn' };
    const remaining = this.remaining(color);
    if (remaining === 0) { this.finish(color === 'w' ? '0-1' : '1-0', 'Time forfeit', color === 'w' ? 'b' : 'w'); return { ok: false, error: 'Time expired' }; }
    try { this.#applyUci(uci); } catch { return { ok: false, error: 'Illegal move' }; }
    const increment = this.timeControl.incrementMs;
    if (color === 'w') this.whiteMs = remaining < 0 ? -1 : remaining + increment;
    else this.blackMs = remaining < 0 ? -1 : remaining + increment;
    this.turnStartedAt = now();
    this.drawOfferBy = null;
    if (this.chess.isCheckmate()) {
      const winner = this.chess.turn() === 'w' ? 'b' : 'w';
      this.finish(winner === 'w' ? '1-0' : '0-1', 'Checkmate', winner);
    } else if (this.chess.isStalemate()) this.finish('1/2-1/2', 'Stalemate');
    else if (this.chess.isThreefoldRepetition()) this.finish('1/2-1/2', 'Threefold repetition');
    else if (this.chess.isInsufficientMaterial()) this.finish('1/2-1/2', 'Insufficient material');
    else if (this.chess.isDraw()) this.finish('1/2-1/2', 'Fifty-move rule');
    else { this.scheduleFlag(); this.onState(this); }
    return { ok: true };
  }

  resign(userId) {
    const color = this.colorFor(userId);
    if (!color || this.result !== '*') return false;
    const winner = color === 'w' ? 'b' : 'w';
    this.finish(winner === 'w' ? '1-0' : '0-1', 'Resignation', winner);
    return true;
  }

  offerDraw(userId) {
    if (!this.participant(userId) || this.result !== '*') return false;
    this.drawOfferBy = userId;
    this.onState(this);
    return true;
  }

  respondDraw(userId, accepted) {
    if (!this.drawOfferBy || this.drawOfferBy === userId || !this.participant(userId) || this.result !== '*') return false;
    this.drawOfferBy = null;
    if (accepted) this.finish('1/2-1/2', 'Draw by agreement');
    else this.onState(this);
    return true;
  }

  addChat(userId, username, text) {
    if (!this.participant(userId) || this.result !== '*') return null;
    const message = { id: `${now()}-${Math.random()}`, userId, username, text, at: now() };
    this.chat = [...this.chat.slice(-49), message];
    return message;
  }

  state() {
    const history = this.chess.history({ verbose: true });
    return {
      id: this.id, fen: this.chess.fen(), turn: this.chess.turn() === 'w' ? 'WHITE' : 'BLACK',
      white: this.white, black: this.black, timeControl: this.timeControl.id,
      whiteMs: this.remaining('w'), blackMs: this.remaining('b'), serverAt: now(),
      moves: history.map(move => ({ uci: `${move.from}${move.to}${move.promotion || ''}`, san: move.san })),
      lastMove: history.length ? `${history.at(-1).from}${history.at(-1).to}${history.at(-1).promotion || ''}` : null,
      result: this.result, reason: this.reason, winner: this.winner, drawOfferBy: this.drawOfferBy,
      chat: this.chat, ratingChanges: this.ratingChanges,
    };
  }

  serialize() {
    return {
      id: this.id, createdAt: this.createdAt, white: this.white, black: this.black, timeControl: this.timeControl.id,
      moves: this.chess.history({ verbose: true }).map(m => `${m.from}${m.to}${m.promotion || ''}`),
      whiteMs: this.remaining('w'), blackMs: this.remaining('b'), chat: this.chat,
    };
  }

  close() { if (this.flagTimer) clearTimeout(this.flagTimer); }

  scheduleFlag() {
    if (this.flagTimer) clearTimeout(this.flagTimer);
    if (this.result !== '*' || this.timeControl.initialMs < 0) return;
    const color = this.chess.turn();
    const remaining = this.remaining(color);
    this.flagTimer = setTimeout(() => {
      if (this.result === '*' && this.remaining(color) === 0) this.finish(color === 'w' ? '0-1' : '1-0', 'Time forfeit', color === 'w' ? 'b' : 'w');
    }, remaining + 25);
    this.flagTimer.unref?.();
  }

  finish(result, reason, winner = null) {
    if (this.result !== '*') return;
    this.result = result;
    this.reason = reason;
    this.winner = winner;
    if (this.flagTimer) clearTimeout(this.flagTimer);
    this.onEnd(this);
  }

  #applyUci(uci) {
    const value = String(uci || '').toLowerCase();
    if (!/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(value)) throw new Error('Invalid UCI');
    this.chess.move({ from: value.slice(0, 2), to: value.slice(2, 4), ...(value[4] ? { promotion: value[4] } : {}) });
  }
}
