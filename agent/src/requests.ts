// What the mod sends to start a run.
import { z } from "zod";

export const Cli = z.enum(["claude", "codex", "opencode"]);
export type Cli = z.infer<typeof Cli>;

export const RunRequest = z.object({
    cli: Cli,
    model: z.string().min(1),
    effort: z.string().min(1),
    args: z.array(z.string()),
    web: z.boolean(),
    prompt: z.string().min(1),
    // The name becomes a folder, so it must not reach outside the conversations folder.
    conversation: z.string().regex(/^[A-Za-z0-9_-]{1,100}$/, "must be a simple name"),
    resume: z.boolean(),
    mcp: z.object({ url: z.string().min(1), token: z.string().min(1) }),
    timeoutSeconds: z.number().positive(),
});
export type RunRequest = z.infer<typeof RunRequest>;
