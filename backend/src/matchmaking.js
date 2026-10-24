export class Matchmaker {
  constructor() { this.queue = []; }
  add(entry) {
    this.remove(entry.userId);
    const candidates = this.queue
      .filter(other => other.timeControl === entry.timeControl && other.userId !== entry.userId)
      .map(other => ({ other, difference: Math.abs(other.rating - entry.rating), wait: Math.max(Date.now() - other.joinedAt, Date.now() - entry.joinedAt) }))
      .filter(item => item.difference <= Math.min(800, 100 + Math.floor(item.wait / 5000) * 50))
      .sort((a, b) => a.difference - b.difference || a.other.joinedAt - b.other.joinedAt);
    if (!candidates.length) { this.queue.push(entry); return null; }
    const opponent = candidates[0].other;
    this.queue = this.queue.filter(item => item.userId !== opponent.userId);
    return opponent;
  }
  remove(userId) { const before = this.queue.length; this.queue = this.queue.filter(item => item.userId !== userId); return before !== this.queue.length; }
  waiting(userId) { return this.queue.findIndex(item => item.userId === userId) + 1; }
}
