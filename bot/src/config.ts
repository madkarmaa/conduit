import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { catchUnwind, Err, Ok, type Result } from "results-ts";
import * as z from "zod";
import { AGENT_NAMES } from "./agents.ts";
import { errorMessage } from "./log.ts";
import { expandVariables } from "./variables.ts";

// The bot passes the model and effort to the CLI as given, since every CLI names them its own way.
// Both are required, so a CLI default never decides what a request costs.
const agentSchema = z.strictObject({
    agent: z.enum(AGENT_NAMES, { error: `Use one of ${AGENT_NAMES.join(", ")}` }),
    model: z.string({ error: "Set the model, as the agent CLI names it" }).min(1),
    effort: z.string({ error: "Set the reasoning effort, as the agent CLI names it" }).min(1),
    // Extra arguments for the agent's CLI.
    args: z.array(z.string()).default([]),
});

const configSchema = z.strictObject({
    keyword: z
        .string()
        .regex(/^\w+$/, "Use letters, digits and underscores only")
        .default("conduit"),
    memory: z.enum(["player", "shared", "none"]).default("player"),
    instructions: z.string().default(""),
    agents: z.strictObject({
        full: agentSchema,
        // Chat mode is off without its agent.
        chat: agentSchema.optional(),
    }),
    access: z
        .strictObject({
            operators: z.boolean().default(true),
            allow: z.array(z.string()).default([]),
            chat: z.boolean().default(true),
        })
        .prefault({}),
    tools: z
        .strictObject({
            readOnly: z.boolean().default(false),
            chat: z
                .strictObject({
                    allow: z.array(z.string()).optional(),
                    deny: z.array(z.string()).default([]),
                })
                .prefault({}),
        })
        .prefault({}),
    sandbox: z
        .strictObject({
            offline: z.boolean().default(false),
            timeoutSeconds: z.number().int().positive().default(300),
            files: z.array(z.string()).default([]),
            env: z.record(z.string().regex(/^\w+$/, "Use a variable name"), z.string()).default({}),
        })
        .prefault({}),
    replies: z.strictObject({ maxLength: z.number().int().positive().default(1000) }).prefault({}),
    stateDirectory: z.string().default(".conduit-bot"),
});

export type Config = z.infer<typeof configSchema>;

export type Memory = Config["memory"];

export type AgentConfig = Config["agents"]["full"];

export type ConfigError = { readonly path: string; readonly message: string };

// Loads the .env file next to the config first. Variables that are already set keep their value.
export function loadConfig(path: string): Result<Config, ConfigError> {
    const fail = (message: string): ConfigError => ({ path, message });

    return loadEnvFile(join(dirname(path), ".env"))
        .andThen(() => readJson(path))
        .mapErr((thrown) => fail(errorMessage(thrown)))
        .andThen((json) => expandVariables(json, process.env).mapErr(fail))
        .andThen((json) => {
            const parsed = configSchema.safeParse(json);
            if (!parsed.success) return Err(fail(z.prettifyError(parsed.error)));

            // Relative state directories belong next to the config file, not the working directory.
            const stateDirectory = resolve(dirname(path), parsed.data.stateDirectory);
            return Ok({ ...parsed.data, stateDirectory });
        });
}

// Chat mode needs its agent, and the config can still turn it off.
export function chatAgent(config: Pick<Config, "agents" | "access">): AgentConfig | undefined {
    return config.access.chat ? config.agents.chat : undefined;
}

// Reading and parsing a file can only report failures by throwing.
const readJson = catchUnwind((path: string): unknown => JSON.parse(readFileSync(path, "utf8")));

const loadEnvFile = catchUnwind((path: string): void => {
    if (existsSync(path)) process.loadEnvFile(path);
});
