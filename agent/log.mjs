export function log(message) {
    console.log(`${new Date().toISOString()} ${message}`);
}

export function fail(message) {
    console.error(message);
    process.exit(1);
}
