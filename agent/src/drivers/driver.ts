import type { RunRequest } from "../requests.ts";

/** One run as a driver gets it. */
export type DriverRun = {
    request: RunRequest;
    /** The CLI that the runner installed. Every SDK starts this binary, unmodified. */
    executable: string;
    /** The conversation's folder, where the CLI runs. */
    folder: string;
    /** The CLI's environment. Its HOME holds only the logins the run may use. */
    env: Record<string, string>;
    /** The CLI's session to continue, if the conversation has one. */
    sessionId: string | undefined;
    abort: AbortController;
};

/** The agent's last answer, and the session that continues the conversation. */
export type DriverAnswer = { output: string; sessionId: string | undefined };
