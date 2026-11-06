export function log(message: string) {
    console.error(`[${new Date().toISOString()}] ${message}`);
}

// Libraries can throw anything, not only errors.
export function errorMessage(thrown: unknown): string {
    return Error.isError(thrown) ? thrown.message : String(thrown);
}
