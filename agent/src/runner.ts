// Takes agent runs from the Conduit mod over HTTP and runs them one at a time with the agent CLI.
// The CLI is not part of the image. The runner installs it into the home volume on first use and
// updates it once a day while idle, so it stays when Compose recreates the container.
//
//   bun src/runner.ts           serves the API
//   bun src/runner.ts install   installs a CLI and exits, for conduit-login
import { createApp, isBusy } from "./app.ts";
import {
    ensureInstalled,
    installedClis,
    scheduleUpdates,
    versionOf,
    versions,
} from "./installs.ts";
import { fail, log } from "./log.ts";
import { Cli } from "./requests.ts";

const PORT = Number(process.env.CONDUIT_RUNNER_PORT ?? 8080);
const SECRET = process.env.CONDUIT_RUNNER_SECRET ?? "";
// Bearer tokens may only use these characters, so Hono's bearer auth refuses any other secret.
const SECRET_PATTERN = /^[A-Za-z0-9._~+/-]{16,}=*$/;

if (process.argv[2] === "install") {
    const cli = Cli.safeParse(process.argv[3]);
    if (!cli.success) fail(`Usage: runner.ts install ${Cli.options.join("|")}`);
    await ensureInstalled(cli.data);
    process.exit(0);
}

if (!SECRET_PATTERN.test(SECRET)) {
    fail("Set CONDUIT_RUNNER_SECRET to at least 16 letters, digits or -._~+/ characters");
}

for (const cli of installedClis()) versions[cli] = await versionOf(cli);

// The mod waits on /run until the agent answers, often for minutes without a byte on the
// connection, so the server must never close it for being idle.
Bun.serve({ port: PORT, fetch: createApp(SECRET).fetch, idleTimeout: 0 });
log(`Listening on port ${PORT}`);

scheduleUpdates(isBusy);
