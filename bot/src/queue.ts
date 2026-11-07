// The bot answers one request at a time, like one player in the chat. Everyone can ask at once,
// and the requests wait in line in the order they arrived.
export class RequestQueue {
    private tail: Promise<void> = Promise.resolve();
    // Players with a request in line, the first one being answered.
    private readonly players: string[] = [];

    // Whether the player already has a request in line, being answered or waiting.
    has(player: string): boolean {
        return this.players.includes(player);
    }

    // Returns how many requests come before this one.
    add(player: string, task: () => PromiseLike<void>): number {
        const ahead = this.players.length;
        this.players.push(player);

        this.tail = this.tail.then(task).then(() => {
            this.players.splice(this.players.indexOf(player), 1);
        });
        return ahead;
    }
}
