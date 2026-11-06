import { defineConfig } from "tsdown";

export default defineConfig({
    entry: {
        index: "src/index.ts",
        // These run inside the agent's container, which only has Node.js.
        "sandbox/supervisor": "src/sandbox/supervisor.ts",
        "sandbox/relay": "src/sandbox/relay.ts",
    },
});
