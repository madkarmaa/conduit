import { defineConfig, type UserConfig } from "tsdown";

const config: UserConfig = defineConfig({
    entry: "src/index.ts",
    dts: true,
    exports: true,
});

export default config;
