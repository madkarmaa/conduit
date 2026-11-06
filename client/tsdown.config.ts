import { defineConfig, type UserConfig } from "tsdown";

const config: UserConfig = defineConfig({
    entry: "src/index.ts",
    dts: true,
    exports: true,
    // The published client loads the protos it ships next to its bundle.
    copy: [{ from: "../src/main/proto/conduit/v1/*.proto", to: "dist/proto/conduit/v1" }],
});

export default config;
