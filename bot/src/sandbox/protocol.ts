// Messages between the bot and the supervisor in the agent's container, one JSON object per line on
// the container's stdin and stdout. The agent's MCP connections travel through them, so the
// container needs no network to reach the game.

export type ToContainer =
    | { readonly type: "start"; readonly command: readonly string[] }
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
