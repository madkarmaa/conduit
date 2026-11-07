// A timeout that stops counting while any approval is open.
export class PausableTimer {
    private remaining: number;
    private startedAt = 0;
    private timeout: NodeJS.Timeout | undefined;
    private pauses = 0;

    constructor(
        milliseconds: number,
        private readonly onTimeout: () => void,
    ) {
        this.remaining = milliseconds;
        this.run();
    }

    readonly pause = () => {
        if (this.pauses++ > 0) return;
        clearTimeout(this.timeout);
        this.remaining -= Date.now() - this.startedAt;
    };

    readonly resume = () => {
        if (--this.pauses === 0) this.run();
    };

    stop() {
        clearTimeout(this.timeout);
        this.pauses = Number.POSITIVE_INFINITY;
    }

    private run() {
        this.startedAt = Date.now();
        this.timeout = setTimeout(this.onTimeout, Math.max(this.remaining, 0));
    }
}
