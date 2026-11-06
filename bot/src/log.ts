export function log(message: string) {
    console.error(`[${new Date().toISOString()}] ${message}`);
}
