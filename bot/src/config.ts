import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { catchUnwind, Err, Ok, type Result } from "results-ts";
import { z } from "zod";
import { PRESET_NAMES } from "./agents.ts";

const presetName = z.enum(PRESET_NAMES);

// Passed on as given, since every CLI has its own model names and effort levels.
const modelChoice = {
    model: z.string().min(1).optional(),
    effort: z.string().min(1).optional(),
};

const agentSchema = z.union(
    [
        presetName.transform((preset) => ({
            preset,
            args: [] as string[],
            model: undefined,
            effort: undefined,
        })),
        z.strictObject({
            preset: presetName,
            args: z.array(z.string()).default([]),
            ...modelChoice,
        }),
        z.strictObject({
            command: z.array(z.string()).min(1),
            resume: z.array(z.string()).min(1).optional(),
            image: z.string().min(1),
            ...modelChoice,
        }),
    ],
    {
        error: `Use a preset (${PRESET_NAMES.join(", ")}), { "preset", "args" } or { "command", "resume", "image" }`,
    },
);

const configSchema = z.strictObject({
    agent: agentSchema,
    keyword: z
        .string()
        .regex(/^\w+$/, "Use letters, digits and underscores only")
        .default("conduit"),
    memory: z.enum(["player", "shared", "none"]).default("player"),
    operators: z.boolean().default(true),
    allow: z.array(z.string()).default([]),
    readOnly: z.boolean().default(false),
    chat: z
        .strictObject({
            enabled: z.boolean().default(false),
            allow: z.array(z.string()).optional(),
            deny: z.array(z.string()).default([]),
            // Overrides the agent's model and effort, such as a cheaper model for chat mode.
            ...modelChoice,
        })
        .default({ enabled: false, deny: [] }),
    offline: z.boolean().default(false),
    files: z.array(z.string()).default([]),
    env: z.array(z.string().regex(/^\w+$/, "Use a variable name")).default([]),
    instructions: z.string().default(""),
    timeoutSeconds: z.number().int().positive().default(300),
    maxReplyLength: z.number().int().positive().default(1000),
    stateDirectory: z.string().default(".conduit-bot"),
});

export type AgentConfig = z.infer<typeof agentSchema>;
export type Memory = z.infer<typeof configSchema>["memory"];

export type Config = z.infer<typeof configSchema>;

export type ConfigError = { readonly path: string; readonly message: string };

export function loadConfig(path: string): Result<Config, ConfigError> {
    const fail = (message: string): ConfigError => ({ path, message });

    return readJson(path)
        .mapErr((thrown) => fail(thrown instanceof Error ? thrown.message : String(thrown)))
        .andThen((json) => {
            const parsed = configSchema.safeParse(json);
            if (!parsed.success) return Err(fail(z.prettifyError(parsed.error)));

            // Relative state directories belong next to the config file, not the working directory.
            const stateDirectory = resolve(dirname(path), parsed.data.stateDirectory);
            return Ok({ ...parsed.data, stateDirectory });
        });
}

// Reading and parsing a file can only report failures by throwing.
const readJson = catchUnwind((path: string): unknown => JSON.parse(readFileSync(path, "utf8")));
