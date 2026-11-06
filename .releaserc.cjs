const { readFileSync } = require("node:fs");

// Minecraft release ids have two or three numbers, such as 26.3 or 1.21.11, so the id is used as it
// is instead of being padded with a patch number.
const minecraftVersion = readFileSync(`${__dirname}/gradle.properties`, "utf8").match(
    /^minecraft_version=(.+)$/m,
)?.[1];
if (!minecraftVersion) throw new Error("gradle.properties sets no minecraft_version");

module.exports = {
    branches: ["main"],
    plugins: [
        "@semantic-release/commit-analyzer",
        "@semantic-release/release-notes-generator",
        "./.github/scripts/release.cjs",
        [
            "@semantic-release/github",
            {
                releaseNameTemplate: `<%= nextRelease.version %>+${minecraftVersion}`,
                assets: [
                    {
                        path: ["build/libs/conduit-*.jar", "!build/libs/*-sources.jar"],
                        label: "Mod jar",
                    },
                ],
            },
        ],
        [
            "@semantic-release/git",
            {
                assets: [
                    "gradle.properties",
                    "client/package.json",
                    "mcp/package.json",
                    "bot/package.json",
                    "bun.lock",
                ],
                message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
            },
        ],
    ],
};
