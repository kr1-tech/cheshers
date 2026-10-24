import test from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import { io as connect } from 'socket.io-client';
import { createCheshersServer } from '../src/server.js';

function event(socket, name, predicate = () => true, timeout = 6000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { socket.off(name, listener); reject(new Error(`Timed out waiting for ${name}`)); }, timeout);
    const listener = payload => {
      if (!predicate(payload)) return;
      clearTimeout(timer); socket.off(name, listener); resolve(payload);
    };
    socket.on(name, listener);
  });
}
function connected(socket) {
  return new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('connect_error', reject); });
}

test('email registration and login work', async () => {
  const server = createCheshersServer({ dbPath: ':memory:', secret: 'integration-test-secret-32-characters' });
  const credentials = { username: 'TestPlayer', email: 'player@example.com', password: 'correct-horse-42' };
  const registered = await request(server.app).post('/auth/register').send(credentials).expect(201);
  assert.equal(registered.body.user.username, 'TestPlayer');
  await request(server.app).post('/auth/login').send({ username: credentials.email, password: credentials.password }).expect(200);
  await request(server.app).post('/auth/login').send({ username: credentials.email, password: 'wrong-password' }).expect(401);
  await server.close();
});

test('two real clients matchmake, sync a move and chat, reconnect, and finish rated', async () => {
  const server = createCheshersServer({ dbPath: ':memory:', secret: 'integration-test-secret-32-characters' });
  const address = await server.start(0);
  const base = `http://127.0.0.1:${address.port}`;
  const a = (await request(server.app).post('/auth/guest').expect(201)).body;
  const b = (await request(server.app).post('/auth/guest').expect(201)).body;
  const socketA = connect(base, { auth: { token: a.token }, transports: ['websocket'], reconnection: false });
  const socketB = connect(base, { auth: { token: b.token }, transports: ['websocket'], reconnection: false });
  let resumedSocket = null;
  try {
    await Promise.all([connected(socketA), connected(socketB)]);
    const startedA = event(socketA, 'game:started');
    const startedB = event(socketB, 'game:started');
    socketA.emit('queue:join', { timeControl: '5+0' });
    socketB.emit('queue:join', { timeControl: '5+0' });
    const [startA, startB] = await Promise.all([startedA, startedB]);
    assert.equal(startA.id, startB.id);
    const whiteSocket = startA.white.id === a.user.id ? socketA : socketB;
    const blackSocket = whiteSocket === socketA ? socketB : socketA;
    const moveA = event(socketA, 'game:state', state => state.moves.length === 1);
    const moveB = event(socketB, 'game:state', state => state.moves.length === 1);
    whiteSocket.emit('game:move', { gameId: startA.id, uci: 'e2e4' });
    const [afterA, afterB] = await Promise.all([moveA, moveB]);
    assert.equal(afterA.fen, afterB.fen);
    assert.equal(afterA.moves[0].san, 'e4');

    const chatA = event(socketA, 'game:chat');
    const chatB = event(socketB, 'game:chat');
    whiteSocket.emit('game:chat', { gameId: startA.id, text: 'Good luck!' });
    assert.equal((await chatA).text, 'Good luck!');
    assert.equal((await chatB).text, 'Good luck!');

    // Drop the black player's socket and restore the authoritative room on a fresh connection.
    const blackAccount = startA.black.id === a.user.id ? a : b;
    blackSocket.close();
    resumedSocket = connect(base, { auth: { token: blackAccount.token }, transports: ['websocket'], reconnection: false });
    await connected(resumedSocket);
    const resumedState = event(resumedSocket, 'game:state', state => state.moves.length === 1);
    resumedSocket.emit('game:resume', { gameId: startA.id });
    assert.equal((await resumedState).id, startA.id);

    const overAEvent = event(whiteSocket, 'game:over');
    const overBEvent = event(resumedSocket, 'game:over');
    resumedSocket.emit('game:resign', { gameId: startA.id });
    const [overA, overB] = await Promise.all([overAEvent, overBEvent]);
    assert.equal(overA.result, '1-0');
    assert.equal(overA.reason, 'Resignation');
    assert.equal(Object.keys(overA.ratingChanges).length, 2);
    assert.equal(overB.id, overA.id);
  } finally {
    socketA.close(); socketB.close(); resumedSocket?.close(); await server.close();
  }
});
