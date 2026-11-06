import { defineConfig } from "tsdown";

export default defineConfig({
    entry: "src/index.ts",
    // The client is not published, so the package carries it along with the protos it loads.
    deps: { alwaysBundle: ["@conduit/client"] },
    copy: [{ from: "../src/main/proto/conduit/v1/*.proto", to: "dist/proto/conduit/v1" }],
});
