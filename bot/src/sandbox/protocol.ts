// Messages between the bot and the supervisor in the agent's container, one JSON object per line on
// the container's stdin and stdout. The agent's MCP connections travel through them, so the
// container needs no network to reach the game.

// Where the bot's built files are mounted in the container.
export const ASSETS_MOUNT = "/opt/conduit-bot";
export const SUPERVISOR_PATH = `${ASSETS_MOUNT}/sandbox/supervisor.mjs`;
export const RELAY_PATH = `${ASSETS_MOUNT}/sandbox/relay.mjs`;
// The supervisor listens here for the relay's MCP connections.
export const SOCKET_PATH = "/tmp/conduit-mcp.sock";

export type ToContainer =
    | {
          readonly type: "start";
          // Commands that prepare the agent's home, run in order before the agent.
          readonly setup: readonly (readonly string[])[];
          readonly command: readonly string[];
      }
    | { readonly type: "mcp"; readonly id: number; readonly line: string }
    | { readonly type: "close"; readonly id: number };

export type FromContainer =
    | { readonly type: "open"; readonly id: number }
    | { readonly type: "mcp"; readonly id: number; readonly line: string }
    | { readonly type: "close"; readonly id: number }
    | {
          readonly type: "exit";
          readonly code: number | null;
          readonly stdout: string;
          readonly stderr: string;
      };
