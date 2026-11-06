import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { catchUnwind } from "results-ts";
import { log } from "../log.ts";

// Files in the host's home, by their path relative to it. An agent's files are optional, since it
// can log in through a variable instead, but a missing file the config asks for is worth a warning.
export function homeFiles(
    agentFiles: readonly string[],
    configFiles: readonly string[],
): Map<string, string> {
    const files = new Map<string, string>();

    for (const path of [...agentFiles, ...configFiles]) {
        const source = join(homedir(), path);

        if (existsSync(source)) files.set(path, source);
        else if (configFiles.includes(path)) log(`Not copying ~/${path}, it does not exist`);
    }

    return files;
}

// Config files can read secrets from variables, such as OpenCode's {env:API_KEY}, so the agent
// needs those variables too.
export function referencedVariables(files: Iterable<string>): string[] {
    const names = new Set<string>();

    for (const file of files) {
        const content = readText(file).unwrapOr("");
        for (const match of content.matchAll(/\{env:(\w+)\}/g)) names.add(match[1] ?? "");
    }

    return [...names].filter((name) => name.length > 0);
}

export function passedVariables(names: readonly string[]): Record<string, string> {
    const variables: Record<string, string> = {};

    for (const name of names) {
        const value = process.env[name];

        if (value === undefined) log(`Not passing ${name}, it is not set`);
        else variables[name] = value;
    }

    return variables;
}

// The directory with the built bot, which the container mounts for the supervisor and the relay.
// From source, that is dist.
export function builtFiles(): string {
    if (existsSync(join(import.meta.dirname, "sandbox", "supervisor.mjs")))
        return import.meta.dirname;
    return join(import.meta.dirname, "../../dist");
}

// Reading a file can only report failures by throwing.
const readText = catchUnwind((path: string): string => readFileSync(path, "utf8"));
