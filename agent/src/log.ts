export function log(message: string): void {
    console.log(`${new Date().toISOString()} ${message}`);
}

export function fail(message: string): never {
    console.error(message);
    process.exit(1);
}
