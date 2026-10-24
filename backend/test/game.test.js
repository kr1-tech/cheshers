import test from 'node:test';
import assert from 'node:assert/strict';
import { GameRoom } from '../src/game.js';
import { Matchmaker } from '../src/matchmaking.js';

const white = { id: 'white', username: 'White' };
const black = { id: 'black', username: 'Black' };
function room(onEnd = () => {}) {
  return new GameRoom({ id: 'test', white, black, timeControl: '5+0', onEnd });
}

test('server accepts legal UCI and rejects illegal moves without changing FEN', () => {
  const game = room();
  const before = game.chess.fen();
  assert.equal(game.move('white', 'e2e5').ok, false);
  assert.equal(game.chess.fen(), before);
  assert.equal(game.move('black', 'e7e5').ok, false);
  assert.equal(game.move('white', 'e2e4').ok, true);
  assert.equal(game.move('black', 'e7e5').ok, true);
  assert.equal(game.chess.history().length, 2);
  game.close();
});

test('checkmate and resignation produce authoritative results', () => {
  let ended;
  const game = room(value => { ended = value; });
  game.move('white', 'f2f3'); game.move('black', 'e7e5'); game.move('white', 'g2g4'); game.move('black', 'd8h4');
  assert.equal(game.result, '0-1');
  assert.equal(game.reason, 'Checkmate');
  assert.equal(ended, game);
  game.close();
  const second = room();
  assert.equal(second.resign('black'), true);
  assert.equal(second.result, '1-0');
  second.close();
});

test('clock applies increment only to the mover', () => {
  const game = new GameRoom({ id: 'clock', white, black, timeControl: '2+1' });
  assert.equal(game.move('white', 'e2e4').ok, true);
  assert.ok(game.whiteMs > 120_000 && game.whiteMs <= 121_000);
  assert.ok(game.blackMs <= 120_000);
  game.close();
});

test('matchmaker respects time control and rating bands', () => {
  const matcher = new Matchmaker();
  const first = { userId: 'a', timeControl: '5+0', rating: 1200, joinedAt: Date.now() };
  assert.equal(matcher.add(first), null);
  assert.equal(matcher.add({ userId: 'b', timeControl: '3+0', rating: 1200, joinedAt: Date.now() }), null);
  const match = matcher.add({ userId: 'c', timeControl: '5+0', rating: 1210, joinedAt: Date.now() });
  assert.equal(match.userId, 'a');
});
